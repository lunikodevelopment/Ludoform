package dev.lunikodevelopment.ludoform.transfer

import android.content.Context
import android.provider.DocumentsContract
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import dev.lunikodevelopment.ludoform.data.library.LibraryDao
import dev.lunikodevelopment.ludoform.data.library.RommDao
import dev.lunikodevelopment.ludoform.data.library.SaveSyncDao
import dev.lunikodevelopment.ludoform.data.library.SaveSyncEntity
import dev.lunikodevelopment.ludoform.data.library.SaveSyncState
import dev.lunikodevelopment.ludoform.romm.RommAddress
import dev.lunikodevelopment.ludoform.romm.RommRequestException
import dev.lunikodevelopment.ludoform.romm.RommSavesApi
import dev.lunikodevelopment.ludoform.romm.RommSettings
import dev.lunikodevelopment.ludoform.romm.SaveRules
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

@EntryPoint
@InstallIn(SingletonComponent::class)
interface SaveSyncEntryPoint {
    fun libraryDao(): LibraryDao

    fun rommDao(): RommDao

    fun saveSyncDao(): SaveSyncDao
}

/** How the player settled a conflict on the Save conflict screen. */
enum class SaveChoice { DEVICE, SERVER, BOTH }

/**
 * Syncs one game's save with RomM (D32). Without [CHOICE] it applies [SaveRules]; with one it settles a conflict the
 * way the player chose. It never deletes: a save replaced on the device is copied to the backups first, and a device
 * save chosen over the server's is uploaded as a new server save, so the server keeps its own.
 */
class SaveSyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    private val entry by lazy { EntryPointAccessors.fromApplication(applicationContext, SaveSyncEntryPoint::class.java) }

    override suspend fun doWork(): Result =
        withContext(Dispatchers.IO) {
            val gameId = inputData.getString(GAME) ?: return@withContext Result.success()
            val choice = inputData.getString(CHOICE)?.let { SaveChoice.valueOf(it) }
            val settings = RommSettings(applicationContext)
            val server = settings.server() ?: return@withContext Result.success()
            val password = settings.password() ?: return@withContext Result.success()
            val base = RommAddress.parse(server.address) ?: return@withContext Result.success()
            val game = entry.libraryDao().getGame(gameId) ?: return@withContext Result.success()
            val rom = entry.rommDao().romForGame(gameId) ?: return@withContext Result.success()
            val files = entry.libraryDao().getGameFiles(gameId)
            if (files.isEmpty()) return@withContext Result.success()
            val slot = SaveLocator.locate(applicationContext.contentResolver, game.platformId, files) ?: return@withContext Result.success()
            val dao = entry.saveSyncDao()
            val before = dao.get(gameId) ?: SaveSyncEntity(gameId, rom.id, slot.fileName)
            val api = RommSavesApi(base, server.username, password)
            try {
                val local = slot.existing?.let { read(it) }
                val localHash = local?.let(::md5)
                val saves = api.list(rom.id)
                val remote =
                    if (slot.existing != null) {
                        SaveRules.pick(saves, slot.extension)
                    } else {
                        SaveLocator.extensionsFor(game.platformId).firstNotNullOfOrNull { SaveRules.pick(saves, it) }
                    }
                val action =
                    when (choice) {
                        null ->
                            SaveRules.decide(
                                localHash,
                                remote,
                                remote?.contentHash,
                                before.baselineHash,
                                before.serverSaveId,
                                before.serverUpdatedAt,
                            )
                        SaveChoice.SERVER -> remote?.let { SaveRules.Action.Download(it) } ?: SaveRules.Action.Upload
                        SaveChoice.DEVICE, SaveChoice.BOTH -> if (local == null) SaveRules.Action.Nothing else SaveRules.Action.Upload
                    }
                val after =
                    when (action) {
                        SaveRules.Action.Nothing ->
                            before.copy(
                                baselineHash = localHash ?: before.baselineHash,
                                serverSaveId = remote?.id ?: before.serverSaveId,
                                serverUpdatedAt = remote?.updatedAt ?: before.serverUpdatedAt,
                            ).settled()
                        SaveRules.Action.Upload -> {
                            if (choice == SaveChoice.BOTH && remote != null) backup(gameId, remote.fileName, api.download(remote))
                            // The baseline's own server save is updated in place; any other case adds a new one.
                            val mine = remote != null && remote.id == before.serverSaveId && choice == null
                            val uploaded =
                                if (mine) {
                                    api.update(remote!!.id, slot.fileName, local!!, localHash!!)
                                } else {
                                    api.upload(rom.id, slot.fileName, local!!, EMULATOR, localHash!!)
                                }
                            before.copy(
                                baselineHash = localHash,
                                serverSaveId = uploaded.id,
                                serverUpdatedAt = uploaded.updatedAt,
                            ).settled()
                        }
                        is SaveRules.Action.Download -> {
                            val content = api.download(action.save)
                            write(slot, local, content, gameId)
                                ?: return@withContext fail(before, "Ludoform can't write saves to this game's folder.")
                            before.copy(
                                baselineHash = md5(content),
                                serverSaveId = action.save.id,
                                serverUpdatedAt = action.save.updatedAt,
                                fileName = slot.fileName,
                            ).settled()
                        }
                        is SaveRules.Action.Conflict ->
                            before.copy(
                                state = SaveSyncState.CONFLICT,
                                conflictServerSaveId = action.save.id,
                                conflictServerAt = action.save.updatedAtMillis,
                                conflictServerSize = action.save.sizeBytes,
                                conflictServerFrom = action.save.emulator,
                                conflictDeviceAt = slot.lastModified,
                                conflictDeviceSize = slot.size,
                            )
                    }
                dao.put(after)
                cleanBackups()
                Result.success()
            } catch (error: RommRequestException) {
                android.util.Log.w("SaveSync", "$gameId: ${error.kind} ${error.message}")
                if (error.kind == RommRequestException.Kind.UNREACHABLE || error.kind == RommRequestException.Kind.SERVER) {
                    // The offline backlog: kept PENDING and retried when the network returns.
                    dao.put(before.copy(state = SaveSyncState.PENDING, note = "${error.kind}: ${error.message}"))
                    if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.success()
                } else if (error.kind == RommRequestException.Kind.NOT_FOUND) {
                    fail(before, "RomM lists this save but can't send its file.")
                } else {
                    fail(before, "RomM refused the save.")
                }
            }
        }

    private fun SaveSyncEntity.settled() =
        copy(
            state = SaveSyncState.IN_SYNC,
            note = null,
            syncedAt = System.currentTimeMillis(),
            conflictServerSaveId = null,
            conflictServerAt = null,
            conflictServerSize = null,
            conflictServerFrom = null,
            conflictDeviceAt = null,
            conflictDeviceSize = null,
        )

    private suspend fun fail(
        sync: SaveSyncEntity,
        note: String,
    ): Result {
        entry.saveSyncDao().put(sync.copy(state = SaveSyncState.FAILED, note = note))
        return Result.success()
    }

    private fun read(uri: android.net.Uri): ByteArray? =
        runCatching { applicationContext.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()

    /** Writes the server's save next to the ROM, backing up the device's copy first; null when the folder is read-only. */
    private fun write(
        slot: SaveSlot,
        old: ByteArray?,
        content: ByteArray,
        gameId: String,
    ): Unit? {
        val resolver = applicationContext.contentResolver
        if (old != null) backup(gameId, slot.fileName, old)
        return runCatching {
            val target =
                slot.existing ?: DocumentsContract.createDocument(resolver, slot.parent, "application/octet-stream", slot.fileName)
                    ?: error("Cannot create the save")
            checkNotNull(resolver.openOutputStream(target, "wt")).use { it.write(content) }
        }.getOrNull()
    }

    private fun backup(
        gameId: String,
        fileName: String,
        content: ByteArray,
    ) {
        val folder = applicationContext.filesDir.resolve("save-backups").resolve(md5(gameId.toByteArray()))
        folder.mkdirs()
        folder.resolve("${System.currentTimeMillis()}-$fileName").writeBytes(content)
    }

    /** Backups older than 30 days go; the board promises the save not picked is kept that long. */
    private fun cleanBackups() {
        val limit = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(BACKUP_DAYS)
        applicationContext.filesDir.resolve("save-backups").walkBottomUp()
            .filter { it.isFile && it.lastModified() < limit }
            .forEach { it.delete() }
    }

    companion object {
        private const val GAME = "game"
        private const val CHOICE = "choice"
        private const val EMULATOR = "Ludoform"
        private const val MAX_ATTEMPTS = 10
        private const val BACKUP_DAYS = 30L

        fun md5(bytes: ByteArray): String = MessageDigest.getInstance("MD5").digest(bytes).joinToString("") { "%02x".format(it) }

        fun workName(gameId: String) = "save-sync-$gameId"

        /**
         * Syncs [gameId]'s save. [now] runs at once, used right before a launch; otherwise it waits for a network,
         * which is how a save made offline reaches the server later.
         */
        fun enqueue(
            context: Context,
            gameId: String,
            choice: SaveChoice? = null,
            now: Boolean = false,
        ) {
            val data = Data.Builder().putString(GAME, gameId).apply { if (choice != null) putString(CHOICE, choice.name) }.build()
            val request =
                OneTimeWorkRequestBuilder<SaveSyncWorker>()
                    .setInputData(data)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                    .apply {
                        if (now) {
                            setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                        } else {
                            setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                        }
                    }.build()
            WorkManager.getInstance(context).enqueueUniqueWork(workName(gameId), ExistingWorkPolicy.REPLACE, request)
        }
    }
}
