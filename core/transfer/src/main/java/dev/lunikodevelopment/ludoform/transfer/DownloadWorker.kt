package dev.lunikodevelopment.ludoform.transfer

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.net.Uri
import android.provider.DocumentsContract
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
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import dev.lunikodevelopment.ludoform.data.library.DownloadDao
import dev.lunikodevelopment.ludoform.data.library.DownloadEntity
import dev.lunikodevelopment.ludoform.data.library.DownloadProblem
import dev.lunikodevelopment.ludoform.data.library.DownloadState
import dev.lunikodevelopment.ludoform.library.LibraryScans
import dev.lunikodevelopment.ludoform.library.PlatformRegistry
import dev.lunikodevelopment.ludoform.romm.RommAddress
import dev.lunikodevelopment.ludoform.romm.RommSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import java.util.concurrent.TimeUnit

@EntryPoint
@InstallIn(SingletonComponent::class)
interface DownloadWorkerEntryPoint {
    fun downloadDao(): DownloadDao
}

/**
 * Works through the download queue one game at a time. It runs in the foreground with a notification, stops a
 * game when the player pauses or cancels it, and asks WorkManager to retry with backoff when the network or server
 * fails; the next run resumes from the partial file.
 */
class DownloadWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    private val dao by lazy { EntryPointAccessors.fromApplication(applicationContext, DownloadWorkerEntryPoint::class.java).downloadDao() }

    override suspend fun doWork(): Result =
        withContext(Dispatchers.IO) {
            val settings = RommSettings(applicationContext)
            val server = settings.server()
            val password = settings.password()
            val base = server?.let { RommAddress.parse(it.address) }
            val downloads = DownloadSettings(applicationContext)
            while (true) {
                val download = dao.next() ?: return@withContext Result.success()
                if (server == null || password == null || base == null) {
                    fail(download, DownloadProblem.SIGN_IN)
                    continue
                }
                runCatching { setForeground(foreground(download.title, 0)) }
                val running = download.copy(state = DownloadState.RUNNING, problem = null)
                dao.update(running)
                val outcome = transfer(running, base, Credentials.basic(server.username, password, Charsets.UTF_8), downloads)
                if (outcome == RETRY_LATER) return@withContext Result.retry()
            }
            @Suppress("UNREACHABLE_CODE")
            Result.success()
        }

    /** Returns [RETRY_LATER] when the whole queue should wait for WorkManager's backoff. */
    private suspend fun transfer(
        download: DownloadEntity,
        base: okhttp3.HttpUrl,
        authorization: String,
        settings: DownloadSettings,
    ): Int {
        val resolver = applicationContext.contentResolver
        val folder = targetFolder(download) ?: return fail(download, DownloadProblem.FOLDER)
        val part =
            download.partUri?.let { Uri.parse(it) }?.takeIf { DocumentFile.fromSingleUri(applicationContext, it)?.exists() == true }
                ?: folder.createFile("application/octet-stream", download.fileName + PART)?.uri
                ?: return fail(download, DownloadProblem.FOLDER)
        val offset = DocumentFile.fromSingleUri(applicationContext, part)?.length() ?: 0
        var current = download.copy(partUri = part.toString(), bytesDone = offset)
        dao.update(current)
        if (current.totalBytes > 0 && freeBytes(folder.uri)?.let { it < current.totalBytes - offset + SPACE_MARGIN } == true) {
            return fail(current, DownloadProblem.NO_SPACE)
        }
        val url =
            base.newBuilder().addPathSegments("api/roms/${download.rommId}/content").addPathSegment(download.fileName).build()
        var lastSaved = 0L
        val outcome =
            RangeDownloader().download(
                url = url,
                authorization = authorization,
                offset = offset,
                open = { append -> checkNotNull(resolver.openOutputStream(part, if (append) "wa" else "wt")) },
                limitBytesPerSecond = { settings.speedLimit },
                shouldStop = { runBlocking { dao.get(download.id)?.state != DownloadState.RUNNING } },
                onProgress = { done, total, speed ->
                    val now = System.currentTimeMillis()
                    if (now - lastSaved >= 1000) {
                        lastSaved = now
                        val knownTotal = if (total > 0) total else current.totalBytes
                        current = current.copy(bytesDone = done, totalBytes = knownTotal, bytesPerSecond = speed)
                        runBlocking { if (dao.get(download.id)?.state == DownloadState.RUNNING) dao.update(current) }
                        val percent = if (current.totalBytes > 0) (done * 100 / current.totalBytes).toInt() else 0
                        runCatching { runBlocking { setForeground(foreground(download.title, percent)) } }
                    }
                },
            )
        return when (outcome) {
            is DownloadOutcome.Complete -> {
                val finalName = renameWhenDone(part, download.fileName)
                if (finalName == null) {
                    fail(current, DownloadProblem.FOLDER)
                } else {
                    dao.update(current.copy(state = DownloadState.DONE, bytesDone = outcome.bytes, bytesPerSecond = 0, partUri = finalName))
                    // The scan adds the game as a local one, and the RomM pass then retires its dimmed copy.
                    LibraryScans.enqueue(applicationContext, download.folderUri)
                    0
                }
            }
            DownloadOutcome.Stopped -> {
                val now = dao.get(download.id)
                if (now == null) {
                    // Cancelled: the partial file goes too.
                    runCatching { DocumentsContract.deleteDocument(resolver, part) }
                } else {
                    dao.update(
                        now.copy(
                            bytesDone = DocumentFile.fromSingleUri(applicationContext, part)?.length() ?: now.bytesDone,
                            bytesPerSecond = 0,
                        ),
                    )
                }
                0
            }
            is DownloadOutcome.Retry -> {
                val attempts = current.attempts + 1
                if (attempts >= MAX_ATTEMPTS) {
                    fail(current.copy(attempts = attempts), DownloadProblem.NETWORK)
                } else {
                    dao.update(current.copy(state = DownloadState.QUEUED, attempts = attempts, bytesPerSecond = 0))
                    RETRY_LATER
                }
            }
            DownloadOutcome.SignIn -> fail(current, DownloadProblem.SIGN_IN)
            DownloadOutcome.NotFound -> fail(current, DownloadProblem.NOT_FOUND)
            DownloadOutcome.NoSpace -> fail(current, DownloadProblem.NO_SPACE)
            is DownloadOutcome.Refused -> fail(current, DownloadProblem.SERVER)
        }
    }

    private suspend fun fail(
        download: DownloadEntity,
        problem: DownloadProblem,
    ): Int {
        dao.update(download.copy(state = DownloadState.FAILED, problem = problem, bytesPerSecond = 0))
        return 0
    }

    /**
     * The chosen folder, or its subfolder for the platform when the chosen folder is a collection of platform folders
     * (as a library root is), so the scanner finds the game where it expects it.
     */
    private fun targetFolder(download: DownloadEntity): DocumentFile? {
        val tree = DocumentFile.fromTreeUri(applicationContext, Uri.parse(download.folderUri)) ?: return null
        if (!tree.canWrite()) return null
        val registry = PlatformRegistry(applicationContext)
        if (registry.findFolder(tree.name.orEmpty())?.id == download.platformId) return tree
        val existing = tree.listFiles().firstOrNull { it.isDirectory && registry.findFolder(it.name.orEmpty())?.id == download.platformId }
        if (existing != null) return existing
        return tree.createDirectory(download.platformId)
    }

    /** Renames `name.part` to `name`; returns the final document's uri, or null when the name is taken. */
    private fun renameWhenDone(
        part: Uri,
        name: String,
    ): String? = runCatching { DocumentsContract.renameDocument(applicationContext.contentResolver, part, name)?.toString() }.getOrNull()

    /** Free space on the storage volume holding [treeUri], from its provider root; null when unknown. */
    private fun freeBytes(treeUri: Uri): Long? =
        runCatching {
            val rootId = DocumentsContract.getTreeDocumentId(treeUri).substringBefore(':')
            val roots = DocumentsContract.buildRootsUri(treeUri.authority)
            applicationContext.contentResolver.query(
                roots,
                arrayOf(DocumentsContract.Root.COLUMN_ROOT_ID, DocumentsContract.Root.COLUMN_AVAILABLE_BYTES),
                null,
                null,
                null,
            )?.use { cursor ->
                generateSequence { if (cursor.moveToNext()) cursor.getString(0) to cursor.getLong(1) else null }
                    .firstOrNull { it.first == rootId }?.second
            }
        }.getOrNull()

    private fun foreground(
        title: String,
        percent: Int,
    ): ForegroundInfo {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Downloads", NotificationManager.IMPORTANCE_LOW))
        val notification =
            NotificationCompat.Builder(applicationContext, CHANNEL)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle("Downloading $title")
                .setProgress(100, percent, percent == 0)
                .setOngoing(true)
                .setSilent(true)
                .build()
        return ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }

    companion object {
        const val WORK_NAME = "downloads"
        private const val PART = ".part"
        private const val CHANNEL = "downloads"
        private const val NOTIFICATION_ID = 4101
        private const val RETRY_LATER = 1
        private const val MAX_ATTEMPTS = 8
        private const val SPACE_MARGIN = 64L * 1024 * 1024

        /**
         * Starts the queue under the current holds. [restart] replaces a running worker, used when the holds change;
         * otherwise a run is appended so a game added during the last one is never stranded.
         */
        fun enqueue(
            context: Context,
            restart: Boolean = false,
        ) {
            val settings = DownloadSettings(context)
            val constraints =
                Constraints.Builder()
                    .setRequiredNetworkType(if (settings.wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                    .setRequiresCharging(settings.chargingOnly)
                    .build()
            val request =
                OneTimeWorkRequestBuilder<DownloadWorker>()
                    .setConstraints(constraints)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                    .build()
            val policy = if (restart) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.APPEND_OR_REPLACE
            WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, policy, request)
        }
    }
}
