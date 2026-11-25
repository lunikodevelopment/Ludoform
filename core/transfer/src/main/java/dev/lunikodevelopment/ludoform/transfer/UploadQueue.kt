package dev.lunikodevelopment.ludoform.transfer

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import dagger.hilt.android.EntryPointAccessors
import dev.lunikodevelopment.ludoform.data.library.ServerCheck
import dev.lunikodevelopment.ludoform.data.library.UploadEntity
import dev.lunikodevelopment.ludoform.data.library.UploadState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Puts games in the upload queue (D37) and changes queued ones. */
object UploadQueue {
    sealed interface Added {
        data class Queued(val id: Long) : Added

        /** The game is already in the queue (and not finished). */
        data class AlreadyQueued(val id: Long) : Added

        /** No upload server has passed its test. */
        data object NoServer : Added

        /** Files of the game are gone, or a disc it names is not with it. */
        data class Missing(val names: List<String>) : Added

        data object NotOnDevice : Added
    }

    private fun entry(context: Context) =
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            UploadWorkerEntryPoint::class.java,
        )

    /** Queues [gameId] with all its files for the first server that passed its test, after a missing-file check. */
    suspend fun add(
        context: Context,
        gameId: String,
    ): Added =
        withContext(Dispatchers.IO) {
            val entry = entry(context)
            val dao = entry.uploadDao()
            dao.activeFor(gameId)?.let { return@withContext Added.AlreadyQueued(it.id) }
            val game = entry.libraryDao().getGame(gameId) ?: return@withContext Added.NotOnDevice
            val gameFiles = entry.libraryDao().getGameFiles(gameId)
            if (gameFiles.isEmpty()) return@withContext Added.NotOnDevice
            val server =
                entry.uploadServerDao().all().firstOrNull { it.check == ServerCheck.OK } ?: return@withContext Added.NoServer
            val files =
                gameFiles.map { file ->
                    val size = DocumentFile.fromSingleUri(context, Uri.parse(file.uri))?.takeIf { it.exists() }?.length() ?: -1
                    UploadFile(file.uri, file.displayName, size)
                }
            val resolver = context.contentResolver
            val missing =
                UploadPlan.missing(
                    files,
                    exists = { it.size >= 0 },
                    readText = { file ->
                        runCatching {
                            resolver.openInputStream(
                                Uri.parse(file.uri),
                            )?.use { it.readNBytes(65_536).toString(Charsets.UTF_8).removePrefix("") }
                        }.getOrNull()
                    },
                )
            if (missing.isNotEmpty()) return@withContext Added.Missing(missing)
            val fsSlug =
                entry.rommDao().getPlatforms().firstOrNull { it.platformId == game.platformId }?.fsSlug
                    ?: UploadPlan.defaultFsSlug(game.platformId)
            val id =
                dao.insert(
                    UploadEntity(
                        gameId = gameId,
                        title = game.title,
                        platformId = game.platformId,
                        serverId = server.id,
                        fsSlug = fsSlug,
                        remoteDir = "",
                        files = UploadPlan.encode(files),
                        totalBytes = files.sumOf { it.size },
                        createdAt = System.currentTimeMillis(),
                    ),
                )
            UploadWorker.enqueue(context)
            Added.Queued(id)
        }

    /** Pause while it runs or waits, Resume when paused, Retry when it failed. */
    suspend fun primary(
        context: Context,
        id: Long,
    ) = withContext(Dispatchers.IO) {
        val dao = entry(context).uploadDao()
        val upload = dao.get(id) ?: return@withContext
        when (upload.state) {
            UploadState.QUEUED, UploadState.RUNNING -> dao.update(upload.copy(state = UploadState.PAUSED, bytesPerSecond = 0))
            UploadState.PAUSED, UploadState.FAILED -> {
                dao.retry(id)
                UploadWorker.enqueue(context)
            }
            UploadState.DONE -> Unit
        }
    }

    /** Removes the row; a running upload sees it gone and deletes its unfinished file on the server. */
    suspend fun cancel(
        context: Context,
        id: Long,
    ) = withContext(Dispatchers.IO) { entry(context).uploadDao().delete(id) }

    suspend fun cancelAll(context: Context) =
        withContext(Dispatchers.IO) {
            val dao = entry(context).uploadDao()
            dao.active().forEach { dao.delete(it.id) }
        }

    suspend fun clearFinished(context: Context) = withContext(Dispatchers.IO) { entry(context).uploadDao().clearDone() }
}
