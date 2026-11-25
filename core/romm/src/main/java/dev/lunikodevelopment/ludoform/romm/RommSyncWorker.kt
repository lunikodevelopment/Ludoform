package dev.lunikodevelopment.ludoform.romm

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import dev.lunikodevelopment.ludoform.data.library.RommDao
import dev.lunikodevelopment.ludoform.library.PlatformRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

@EntryPoint
@InstallIn(SingletonComponent::class)
interface RommWorkerEntryPoint {
    fun rommDao(): RommDao
}

/** Brings the RomM library into Room (D30). [Mode] says how much of the server it asks for. */
class RommSyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    enum class Mode {
        /** Changes since the last sync, or everything when a full sync is due. */
        SYNC,

        /** Everything on the server. */
        FULL,

        /** No network: re-match saved ROMs after a library scan or a Sync switch. */
        LOCAL,

        /** Remove every RomM game and table row, after the server is removed. */
        CLEAR,
    }

    override suspend fun doWork(): Result =
        withContext(Dispatchers.IO) {
            val dao = EntryPointAccessors.fromApplication(applicationContext, RommWorkerEntryPoint::class.java).rommDao()
            val settings = RommSettings(applicationContext)
            val registry = PlatformRegistry(applicationContext)
            val mode = inputData.getString(MODE)?.let { Mode.valueOf(it) } ?: Mode.SYNC
            val server = settings.server()
            val serverKey = server?.let { it.address + "|" + it.username }
            if (mode == Mode.CLEAR || serverKey == null || settings.syncedServer != serverKey) {
                clear(dao, registry)
                settings.syncWatermark = null
                settings.lastFullSync = 0
                settings.syncedServer = null
                if (mode == Mode.CLEAR || serverKey == null) return@withContext Result.success()
            }
            if (mode == Mode.LOCAL) {
                reconcile(dao, registry, null, emptyList(), null)
                // The count follows a Sync switch; the time stays that of the last talk with the server.
                settings.lastSync()?.let { last -> settings.saveSync(last.copy(games = dao.getRoms().count { it.gameId != null })) }
                return@withContext Result.success()
            }
            val password = settings.password() ?: return@withContext fail(settings, RommRequestException.Kind.SIGN_IN)
            val base = RommAddress.parse(server!!.address) ?: return@withContext fail(settings, RommRequestException.Kind.UNREACHABLE)
            val api = RommLibraryApi(base, server.username, password)
            val now = System.currentTimeMillis()
            // Rows synced before names and covers were kept (D34) need one full pass to get them.
            val full =
                mode == Mode.FULL || settings.syncWatermark == null || now - settings.lastFullSync > FULL_EVERY_MILLIS ||
                    settings.syncFormat < SYNC_FORMAT
            try {
                val platforms = api.platforms()
                val fetched = mutableListOf<RemoteRom>()
                api.roms(if (full) null else settings.syncWatermark) { fetched.addAll(it) }
                val identifiers = api.identifiers()
                reconcile(dao, registry, platforms, fetched, identifiers)
                settings.syncedServer = serverKey
                (listOfNotNull(settings.syncWatermark) + fetched.map { it.updatedAt }).filter { it.isNotBlank() }.maxOrNull()
                    ?.let { settings.syncWatermark = it }
                if (full) {
                    settings.lastFullSync = now
                    settings.syncFormat = SYNC_FORMAT
                }
                val shown = dao.getRoms().count { it.gameId != null }
                settings.saveSync(RommSyncResult(System.currentTimeMillis(), shown, null))
                Result.success()
            } catch (error: RommRequestException) {
                fail(settings, error.kind)
            }
        }

    private suspend fun reconcile(
        dao: RommDao,
        registry: PlatformRegistry,
        platforms: List<RemotePlatform>?,
        fetched: List<RemoteRom>,
        identifiers: Set<Int>?,
    ) {
        val input =
            RommReconciler.Input(
                serverPlatforms = platforms,
                savedPlatforms = dao.getPlatforms(),
                fetchedRoms = fetched,
                identifiers = identifiers,
                savedRoms = dao.getRoms(),
                localFiles = dao.getLocalFiles(),
                remoteGameIds = dao.getRemoteGameIds().toSet(),
                now = System.currentTimeMillis(),
            )
        dao.apply(RommReconciler.reconcile(input, registry))
    }

    private suspend fun clear(
        dao: RommDao,
        registry: PlatformRegistry,
    ) {
        if (dao.getPlatforms().isEmpty() && dao.getRoms().isEmpty() && dao.getRemoteGameIds().isEmpty()) return
        reconcile(dao, registry, emptyList(), emptyList(), null)
    }

    private fun fail(
        settings: RommSettings,
        kind: RommRequestException.Kind,
    ): Result {
        settings.saveSync(RommSyncResult(System.currentTimeMillis(), settings.lastSync()?.games ?: 0, kind))
        return Result.success()
    }

    companion object {
        const val WORK_NAME = "romm-sync"
        private const val PERIODIC_NAME = "romm-sync-periodic"
        private const val MODE = "mode"

        /** Bumped when a sync keeps more of each ROM; a lower stored value makes the next sync a full one. */
        private const val SYNC_FORMAT = 1
        private val FULL_EVERY_MILLIS = TimeUnit.HOURS.toMillis(24)

        fun enqueue(
            context: Context,
            mode: Mode = Mode.SYNC,
        ) {
            val request =
                OneTimeWorkRequestBuilder<RommSyncWorker>()
                    .setInputData(Data.Builder().putString(MODE, mode.name).build())
                    .build()
            WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        }

        /** Whether the saved ROM rows predate what the sync keeps now, so a full sync should run soon (D34). */
        fun needsFullSync(settings: RommSettings): Boolean = settings.syncFormat < SYNC_FORMAT

        /** Every six hours on any network while a server is saved. */
        fun schedule(context: Context) {
            val request =
                PeriodicWorkRequestBuilder<RommSyncWorker>(6, TimeUnit.HOURS)
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun unschedule(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_NAME)
        }
    }
}
