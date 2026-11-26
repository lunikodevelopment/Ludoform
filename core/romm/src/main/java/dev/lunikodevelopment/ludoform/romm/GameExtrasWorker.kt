package dev.lunikodevelopment.ludoform.romm

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Fetches a RomM game's achievements and manual for the in-game menu (D48), when the game starts. Network work stays
 * in a job (BUILD-PROMPT); the menu only reads what this stored. A failure keeps what was stored before.
 */
class GameExtrasWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result =
        withContext(Dispatchers.IO) {
            val gameId = inputData.getString(GAME_ID) ?: return@withContext Result.success()
            val dao = EntryPointAccessors.fromApplication(applicationContext, RommWorkerEntryPoint::class.java).rommDao()
            val rommId = dao.romForGame(gameId)?.id ?: return@withContext Result.success()
            val settings = RommSettings(applicationContext)
            val server = settings.server() ?: return@withContext Result.success()
            val password = settings.password() ?: return@withContext Result.success()
            val base = RommAddress.parse(server.address) ?: return@withContext Result.success()
            val api = RommExtrasApi(base, server.username, password)
            val store = GameExtrasStore(applicationContext)
            try {
                val body = api.rom(rommId)
                val rom = RommExtrasApi.parseRom(body)
                val achievements =
                    if (rom.raId != null && rom.achievements.isNotEmpty()) {
                        RommExtrasApi.applyProgress(rom.achievements, rom.raId, api.me(), RommExtrasApi.rawIds(body))
                    } else {
                        rom.achievements
                    }
                val manual = rom.manualPath?.let { manual(api, store.manuals, rommId, it) }
                store.save(gameId, GameExtras(rommId, achievements, manual, System.currentTimeMillis()))
                Result.success()
            } catch (error: RommRequestException) {
                if (error.kind == RommRequestException.Kind.UNREACHABLE && runAttemptCount < 2) Result.retry() else Result.success()
            }
        }

    /** The manual's PDF, downloaded once per ROM; null when it isn't a PDF or is too large. */
    private fun manual(
        api: RommExtrasApi,
        folder: File,
        rommId: Int,
        path: String,
    ): String? {
        val file = folder.resolve("$rommId.pdf")
        if (file.exists() && file.length() > 0) return file.path
        val bytes = runCatching { api.download(path) }.getOrNull() ?: return null
        if (bytes.size > MAX_MANUAL_BYTES || !bytes.copyOfRange(0, minOf(5, bytes.size)).contentEquals("%PDF-".toByteArray())) {
            return null
        }
        val temp = File(file.path + ".part")
        temp.writeBytes(bytes)
        return if (temp.renameTo(file)) file.path else null
    }

    companion object {
        private const val GAME_ID = "game_id"
        private const val MAX_MANUAL_BYTES = 100L * 1024 * 1024

        fun enqueue(
            context: Context,
            gameId: String,
        ) {
            val request =
                OneTimeWorkRequestBuilder<GameExtrasWorker>()
                    .setInputData(workDataOf(GAME_ID to gameId))
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .build()
            WorkManager.getInstance(context).enqueueUniqueWork("extras:$gameId", ExistingWorkPolicy.REPLACE, request)
        }
    }
}
