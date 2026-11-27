package dev.lunikodevelopment.ludoform.transfer

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import dev.lunikodevelopment.ludoform.data.library.ServerCheck
import dev.lunikodevelopment.ludoform.data.library.UploadServerDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@EntryPoint
@InstallIn(SingletonComponent::class)
interface UploadServerEntryPoint {
    fun uploadServerDao(): UploadServerDao
}

/** Tests one upload server and stores the outcome, including the host key it offered, on its row. */
class UploadServerCheckWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result =
        withContext(Dispatchers.IO) {
            val dao = EntryPointAccessors.fromApplication(applicationContext, UploadServerEntryPoint::class.java).uploadServerDao()
            val id = inputData.getLong(SERVER, -1)
            val server = dao.get(id) ?: return@withContext Result.success()
            val password = UploadSecrets(applicationContext).password(id)
            val result =
                if (password == null) {
                    ProbeResult(ServerCheck.SIGN_IN, "Type the password again.")
                } else {
                    ServerProbe.test(
                        ServerAccess(
                            server.protocol,
                            server.host,
                            server.port,
                            server.username,
                            password,
                            server.folder,
                            server.trustedKey,
                        ),
                    )
                }
            // Edited or removed while the test ran: the result describes another server.
            val now = dao.get(id)
            if (now != null && now.sameAccess(server)) {
                dao.setCheck(id, result.check, result.detail, result.offeredKey, System.currentTimeMillis())
            }
            Result.success()
        }

    private fun dev.lunikodevelopment.ludoform.data.library.UploadServerEntity.sameAccess(
        other: dev.lunikodevelopment.ludoform.data.library.UploadServerEntity,
    ) = protocol == other.protocol && host == other.host && port == other.port && username == other.username &&
        folder == other.folder && trustedKey == other.trustedKey

    companion object {
        private const val SERVER = "server"

        fun workName(id: Long) = "upload-server-check-$id"

        suspend fun enqueue(
            context: Context,
            dao: UploadServerDao,
            id: Long,
        ) {
            dao.get(id)?.let { dao.setCheck(id, ServerCheck.CHECKING, null, it.offeredKey, it.checkedAt) }
            val request =
                OneTimeWorkRequestBuilder<UploadServerCheckWorker>()
                    .setInputData(Data.Builder().putLong(SERVER, id).build())
                    .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                    .build()
            WorkManager.getInstance(context).enqueueUniqueWork(workName(id), ExistingWorkPolicy.REPLACE, request)
        }
    }
}
