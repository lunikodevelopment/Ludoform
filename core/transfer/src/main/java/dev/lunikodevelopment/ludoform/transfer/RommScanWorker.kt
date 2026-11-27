package dev.lunikodevelopment.ludoform.transfer

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.hilt.android.EntryPointAccessors
import dev.lunikodevelopment.ludoform.data.library.UploadScan
import dev.lunikodevelopment.ludoform.romm.RommAddress
import dev.lunikodevelopment.ludoform.romm.RommRequestException
import dev.lunikodevelopment.ludoform.romm.RommSettings
import dev.lunikodevelopment.ludoform.romm.RommSyncWorker
import dev.lunikodevelopment.ludoform.romm.RommTasksApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * After uploads: asks RomM to scan the platform folders that got games, follows the scan, then syncs the
 * library so the games show as on RomM. RomM refusing the scan (no admin rights) is reported on the rows, not retried.
 */
class RommScanWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result =
        withContext(Dispatchers.IO) {
            val dao = EntryPointAccessors.fromApplication(applicationContext, UploadWorkerEntryPoint::class.java).uploadDao()
            val waiting = dao.waitingForScan()
            if (waiting.isEmpty()) return@withContext Result.success()
            val ids = waiting.map { it.id }
            val settings = RommSettings(applicationContext)
            val server = settings.server()
            val password = settings.password()
            val base = server?.let { RommAddress.parse(it.address) }
            if (server == null || password == null || base == null) {
                dao.setScan(ids, UploadScan.FAILED)
                return@withContext Result.success()
            }
            val api = RommTasksApi(base, server.username, password)
            val task =
                try {
                    api.scan(waiting.map { it.fsSlug })
                } catch (error: RommRequestException) {
                    if (error.kind == RommRequestException.Kind.SIGN_IN) {
                        dao.setScan(ids, UploadScan.REFUSED)
                        return@withContext Result.success()
                    }
                    if (runAttemptCount + 1 >= MAX_ATTEMPTS) {
                        dao.setScan(ids, UploadScan.FAILED)
                        return@withContext Result.success()
                    }
                    return@withContext Result.retry()
                }
            dao.setScan(ids, UploadScan.RUNNING)
            var outcome = UploadScan.DONE
            val deadline = System.currentTimeMillis() + FOLLOW_MILLIS
            while (System.currentTimeMillis() < deadline) {
                delay(POLL_MILLIS)
                val status = runCatching { api.status(task) }.getOrNull() ?: continue
                if (status in RommTasksApi.FINISHED) break
                if (status in RommTasksApi.ENDED_BADLY) {
                    outcome = UploadScan.FAILED
                    break
                }
            }
            // A scan still running after the follow time is left to RomM; the next sync finds what it added.
            dao.setScan(ids, outcome)
            RommSyncWorker.enqueue(applicationContext)
            Result.success()
        }

    companion object {
        private const val WORK_NAME = "romm-scan"
        private const val MAX_ATTEMPTS = 5
        private const val POLL_MILLIS = 5_000L
        private val FOLLOW_MILLIS = TimeUnit.MINUTES.toMillis(8)

        fun enqueue(context: Context) {
            val request =
                OneTimeWorkRequestBuilder<RommScanWorker>()
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                    .build()
            WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        }
    }
}
