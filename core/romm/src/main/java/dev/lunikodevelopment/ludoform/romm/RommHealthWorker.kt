package dev.lunikodevelopment.ludoform.romm

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
                    RommSyncWorker.enqueue(applicationContext)
                    RommSyncWorker.schedule(applicationContext)
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
    }
}
