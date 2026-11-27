package dev.lunikodevelopment.ludoform.romm

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.lunikodevelopment.ludoform.data.RommOutage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/** Runs the health check for the saved server and stores the result in [RommSettings]. */
class RommHealthWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result =
        withContext(Dispatchers.IO) {
            val settings = RommSettings(applicationContext)
            val server = settings.server() ?: return@withContext Result.success()
            val password = settings.password()
            val health =
                if (password == null) {
                    RommHealth.SignInFailed("")
                } else {
                    RommClient().check(server.address, server.username, password)
                }
            // The server may have been edited or removed while the check ran; the result then describes nothing.
            if (settings.server() == server) {
                settings.saveCheck(RommCheck(health, System.currentTimeMillis()))
                // A working server is synced at once and then every six hours.
                if (health is RommHealth.Connected) {
                    RommOutages.reachable(applicationContext)
                    RommSyncWorker.enqueue(applicationContext)
                    RommSyncWorker.schedule(applicationContext)
                } else {
                    RommOutages.outageOf(health)?.let { RommOutages.report(applicationContext, it) }
                }
            }
            Result.success()
        }

    companion object {
        const val WORK_NAME = "romm-health"

        fun enqueue(context: Context) {
            val request =
                OneTimeWorkRequestBuilder<RommHealthWorker>()
                    .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                    .build()
            WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
        }

        /** The automatic next try while RomM is down: after [delayMillis], once there is a network. */
        internal fun scheduleRetry(
            context: Context,
            delayMillis: Long,
        ) {
            val request =
                OneTimeWorkRequestBuilder<RommHealthWorker>()
                    .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .build()
            WorkManager.getInstance(context).enqueueUniqueWork(RETRY_NAME, ExistingWorkPolicy.REPLACE, request)
        }

        internal fun cancelRetry(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(RETRY_NAME)
        }

        private const val RETRY_NAME = "romm-retry"
    }
}

/**
 * Keeps track of RomM being down. Background jobs report failed and successful requests here; while it is
 * down a health check runs again by itself, later each time, and the screens say so. Local games are never held up.
 */
object RommOutages {
    fun report(
        context: Context,
        outage: RommOutage,
        now: Long = System.currentTimeMillis(),
    ) {
        val wait = RommSettings(context).reportOutage(outage, now)
        RommHealthWorker.scheduleRetry(context, wait)
    }

    fun reachable(context: Context) {
        RommSettings(context).reportReachable()
        RommHealthWorker.cancelRetry(context)
    }

    /** The outage a health check found; null for answers that are a settings problem, not the server being down. */
    fun outageOf(health: RommHealth): RommOutage? =
        when (health) {
            is RommHealth.Unreachable -> RommOutage.UNREACHABLE
            is RommHealth.ServerError -> RommOutage.SERVER_ERROR
            is RommHealth.SignInFailed -> RommOutage.SIGN_IN
            else -> null
        }

    /** The outage a failed request means; null for answers that are not the server being down. */
    fun outageOf(kind: RommRequestException.Kind): RommOutage? =
        when (kind) {
            RommRequestException.Kind.UNREACHABLE -> RommOutage.UNREACHABLE
            RommRequestException.Kind.SERVER -> RommOutage.SERVER_ERROR
            RommRequestException.Kind.SIGN_IN -> RommOutage.SIGN_IN
            else -> null
        }
}
