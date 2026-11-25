package dev.lunikodevelopment.ludoform.transfer

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.documentfile.provider.DocumentFile
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.hierynomus.mssmb2.SMBApiException
import com.hierynomus.smbj.common.SMBRuntimeException
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import dev.lunikodevelopment.ludoform.data.library.RommDao
import dev.lunikodevelopment.ludoform.data.library.ServerCheck
import dev.lunikodevelopment.ludoform.data.library.UploadDao
import dev.lunikodevelopment.ludoform.data.library.UploadEntity
import dev.lunikodevelopment.ludoform.data.library.UploadProblem
import dev.lunikodevelopment.ludoform.data.library.UploadScan
import dev.lunikodevelopment.ludoform.data.library.UploadServerDao
import dev.lunikodevelopment.ludoform.data.library.UploadServerEntity
import dev.lunikodevelopment.ludoform.data.library.UploadState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import net.schmizz.sshj.sftp.SFTPException
import java.io.IOException
import java.util.concurrent.TimeUnit

@EntryPoint
@InstallIn(SingletonComponent::class)
interface UploadWorkerEntryPoint {
    fun uploadDao(): UploadDao

    fun uploadServerDao(): UploadServerDao

    fun rommDao(): RommDao

    fun libraryDao(): dev.lunikodevelopment.ludoform.data.library.LibraryDao
}

/** The upload holds (D37): Wi-Fi only (on by default, games are large) and a RomM scan after uploads (on). */
class UploadSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("uploads", Context.MODE_PRIVATE)

    var wifiOnly: Boolean
        get() = prefs.getBoolean("wifi_only", true)
        set(value) = prefs.edit().putBoolean("wifi_only", value).apply()

    var scanAfter: Boolean
        get() = prefs.getBoolean("scan_after", true)
        set(value) = prefs.edit().putBoolean("scan_after", value).apply()

    val changes: Flow<Unit> =
        callbackFlow {
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> trySend(Unit) }
            prefs.registerOnSharedPreferenceChangeListener(listener)
            trySend(Unit)
            awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
        }
}

/**
 * Works through the upload queue one game at a time (D37), in the foreground with a notification. Each file goes to a
 * hidden part file and is renamed when complete; an interrupted file resumes from the part file's size. When the queue
 * is empty it starts the RomM scan of the platforms it filled.
 */
class UploadWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    private val entry by lazy { EntryPointAccessors.fromApplication(applicationContext, UploadWorkerEntryPoint::class.java) }
    private val dao by lazy { entry.uploadDao() }

    override suspend fun doWork(): Result =
        withContext(Dispatchers.IO) {
            while (true) {
                val upload = dao.next() ?: break
                val server = entry.uploadServerDao().get(upload.serverId)
                val password = server?.let { UploadSecrets(applicationContext).password(it.id) }
                if (server == null || password == null) {
                    fail(upload, UploadProblem.NO_SERVER)
                    continue
                }
                runCatching { setForeground(foreground(upload.title, 0)) }
                val running = upload.copy(state = UploadState.RUNNING, problem = null, detail = null)
                dao.update(running)
                if (send(running, server, password) == RETRY_LATER) return@withContext Result.retry()
            }
            if (dao.waitingForScan().isNotEmpty()) RommScanWorker.enqueue(applicationContext)
            Result.success()
        }

    /** Returns [RETRY_LATER] when the whole queue should wait for WorkManager's backoff. */
    private suspend fun send(
        upload: UploadEntity,
        server: UploadServerEntity,
        password: String,
    ): Int {
        val files = UploadPlan.decode(upload.files)
        val resolver = applicationContext.contentResolver
        val missing =
            UploadPlan.missing(
                files,
                exists = { DocumentFile.fromSingleUri(applicationContext, Uri.parse(it.uri))?.exists() == true },
                readText = { file ->
                    runCatching {
                        resolver.openInputStream(Uri.parse(file.uri))?.use { stream ->
                            stream.readNBytes(65_536).toString(Charsets.UTF_8).removePrefix("")
                        }
                    }.getOrNull()
                },
            )
        if (missing.isNotEmpty()) return fail(upload, UploadProblem.MISSING_FILES, missing.joinToString(", "))
        val access =
            ServerAccess(server.protocol, server.host, server.port, server.username, password, server.folder, server.trustedKey)
        var current = upload
        try {
            val open =
                when (val result = RemoteFolders.open(access)) {
                    is RemoteOpen.Refused -> return fail(upload, problemFor(result.result.check), result.result.detail)
                    is RemoteOpen.Opened -> result
                }
            open.folder.use { folder ->
                val dir =
                    upload.remoteDir.ifEmpty {
                        val platformDir = UploadPlan.platformDir(upload.fsSlug, folder.isFolder(upload.fsSlug + "/roms"))
                        UploadPlan.remoteDir(platformDir, upload.title, files)
                    }
                current = current.copy(remoteDir = dir)
                dao.update(current)
                folder.makeFolders(dir)
                for (index in upload.fileIndex until files.size) {
                    val file = files[index]
                    val target = "$dir/${file.name}"
                    val part = "$dir/${UploadPlan.partName(file.name)}"
                    val before = files.take(index).sumOf { it.size }
                    val existing = folder.size(target)
                    if (existing != null) {
                        // The same file is already there: nothing to send. A different one is never overwritten.
                        if (existing != file.size) return fail(current, UploadProblem.EXISTS, file.name)
                        current = current.copy(fileIndex = index + 1, bytesDone = before + file.size)
                        dao.update(current)
                        continue
                    }
                    val offset = folder.size(part)?.takeIf { it <= file.size } ?: 0L
                    val input =
                        resolver.openInputStream(Uri.parse(file.uri))
                            ?: return fail(current, UploadProblem.MISSING_FILES, file.name)
                    val completed =
                        input.use { stream ->
                            skipFully(stream, offset)
                            var lastSaved = 0L
                            var lastBytes = 0L
                            folder.write(part, offset, stream) { written ->
                                val now = System.currentTimeMillis()
                                if (now - lastSaved < 1000) return@write true
                                val speed = if (lastSaved == 0L) 0 else (written - lastBytes) * 1000 / (now - lastSaved)
                                lastSaved = now
                                lastBytes = written
                                runBlocking {
                                    val state = dao.get(upload.id)?.state
                                    if (state != UploadState.RUNNING) return@runBlocking false
                                    current = current.copy(bytesDone = before + offset + written, bytesPerSecond = speed)
                                    dao.update(current)
                                    val percent = if (current.totalBytes > 0) (current.bytesDone * 100 / current.totalBytes).toInt() else 0
                                    runCatching { setForeground(foreground(upload.title, percent)) }
                                    true
                                }
                            }
                        }
                    if (!completed) {
                        val now = dao.get(upload.id)
                        if (now == null) {
                            // Cancelled: the unfinished file goes; files already complete stay on the server.
                            runCatching { folder.delete(part) }
                        } else {
                            dao.update(now.copy(bytesDone = before + (folder.size(part) ?: 0), bytesPerSecond = 0))
                        }
                        return 0
                    }
                    folder.rename(part, target)
                    current = current.copy(fileIndex = index + 1, bytesDone = before + file.size)
                    dao.update(current)
                }
            }
            // Cancelled just as the last file finished: the row stays gone.
            if (dao.get(upload.id) == null) return 0
            val scan = if (UploadSettings(applicationContext).scanAfter) UploadScan.WAITING else UploadScan.NONE
            dao.update(
                current.copy(
                    state = UploadState.DONE,
                    bytesDone = current.totalBytes,
                    bytesPerSecond = 0,
                    finishedAt = System.currentTimeMillis(),
                    scan = scan,
                ),
            )
            entry.uploadServerDao().get(
                server.id,
            )?.let { entry.uploadServerDao().update(it.copy(lastUploadAt = System.currentTimeMillis())) }
            return 0
        } catch (error: SFTPException) {
            return fail(current, UploadProblem.NOT_WRITABLE, error.message)
        } catch (error: SMBApiException) {
            return fail(current, UploadProblem.NOT_WRITABLE, error.status.name)
        } catch (error: IOException) {
            return retryOrFail(current)
        } catch (error: SMBRuntimeException) {
            return retryOrFail(current)
        }
    }

    private fun skipFully(
        stream: java.io.InputStream,
        count: Long,
    ) {
        var left = count
        while (left > 0) {
            val skipped = stream.skip(left)
            if (skipped <= 0) {
                if (stream.read() < 0) throw IOException("The file is shorter than its part on the server")
                left--
            } else {
                left -= skipped
            }
        }
    }

    private suspend fun retryOrFail(upload: UploadEntity): Int {
        val now = dao.get(upload.id) ?: return 0
        if (now.state != UploadState.RUNNING) return 0
        val attempts = upload.attempts + 1
        return if (attempts >= MAX_ATTEMPTS) {
            fail(upload.copy(attempts = attempts), UploadProblem.NETWORK)
        } else {
            dao.update(upload.copy(state = UploadState.QUEUED, attempts = attempts, bytesPerSecond = 0))
            RETRY_LATER
        }
    }

    private fun problemFor(check: ServerCheck): UploadProblem =
        when (check) {
            ServerCheck.SIGN_IN -> UploadProblem.SIGN_IN
            ServerCheck.HOST_KEY_UNKNOWN, ServerCheck.HOST_KEY_CHANGED -> UploadProblem.HOST_KEY
            ServerCheck.NO_FOLDER -> UploadProblem.NO_FOLDER
            ServerCheck.NOT_WRITABLE -> UploadProblem.NOT_WRITABLE
            ServerCheck.UNREACHABLE -> UploadProblem.UNREACHABLE
            else -> UploadProblem.FAILED
        }

    private suspend fun fail(
        upload: UploadEntity,
        problem: UploadProblem,
        detail: String? = null,
    ): Int {
        // A row cancelled meanwhile stays gone.
        if (dao.get(upload.id) == null) return 0
        dao.update(upload.copy(state = UploadState.FAILED, problem = problem, detail = detail, bytesPerSecond = 0))
        return 0
    }

    private fun foreground(
        title: String,
        percent: Int,
    ): ForegroundInfo {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Uploads", NotificationManager.IMPORTANCE_LOW))
        val notification =
            NotificationCompat.Builder(applicationContext, CHANNEL)
                .setSmallIcon(android.R.drawable.stat_sys_upload)
                .setContentTitle("Uploading $title")
                .setProgress(100, percent, percent == 0)
                .setOngoing(true)
                .setSilent(true)
                .build()
        return ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }

    companion object {
        const val WORK_NAME = "uploads"
        private const val CHANNEL = "uploads"
        private const val NOTIFICATION_ID = 4102
        private const val RETRY_LATER = 1
        private const val MAX_ATTEMPTS = 8

        /** Starts the queue under the current holds; [restart] replaces a running worker when the holds change. */
        fun enqueue(
            context: Context,
            restart: Boolean = false,
        ) {
            val constraints =
                Constraints.Builder()
                    .setRequiredNetworkType(if (UploadSettings(context).wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                    .build()
            val request =
                OneTimeWorkRequestBuilder<UploadWorker>()
                    .setConstraints(constraints)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                    .build()
            val policy = if (restart) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.APPEND_OR_REPLACE
            WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, policy, request)
        }
    }
}
