package dev.lunikodevelopment.ludoform

import android.app.ActivityOptions
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.work.WorkInfo
import androidx.work.WorkManager
import dagger.hilt.android.AndroidEntryPoint
import dev.lunikodevelopment.ludoform.data.AppIntent
import dev.lunikodevelopment.ludoform.data.AppStore
import dev.lunikodevelopment.ludoform.data.Destination
import dev.lunikodevelopment.ludoform.data.DownloadHolds
import dev.lunikodevelopment.ludoform.data.DownloadRowState
import dev.lunikodevelopment.ludoform.data.EmulatorChoice
import dev.lunikodevelopment.ludoform.data.GameScreen
import dev.lunikodevelopment.ludoform.data.ImportPlan
import dev.lunikodevelopment.ludoform.data.ImportReview
import dev.lunikodevelopment.ludoform.data.LaunchRequest
import dev.lunikodevelopment.ludoform.data.MenuState
import dev.lunikodevelopment.ludoform.data.PreferenceScope
import dev.lunikodevelopment.ludoform.data.RommField
import dev.lunikodevelopment.ludoform.data.RommPlatformRow
import dev.lunikodevelopment.ludoform.data.SaveConflictInfo
import dev.lunikodevelopment.ludoform.data.SaveResolution
import dev.lunikodevelopment.ludoform.data.ScanState
import dev.lunikodevelopment.ludoform.data.ScreenSetting
import dev.lunikodevelopment.ludoform.data.ServerField
import dev.lunikodevelopment.ludoform.data.ThemeChoice
import dev.lunikodevelopment.ludoform.data.TransferHold
import dev.lunikodevelopment.ludoform.data.TransferTab
import dev.lunikodevelopment.ludoform.data.UiAction
import dev.lunikodevelopment.ludoform.data.UploadHold
import dev.lunikodevelopment.ludoform.data.UploadHolds
import dev.lunikodevelopment.ludoform.data.UploadRowState
import dev.lunikodevelopment.ludoform.data.Zones
import dev.lunikodevelopment.ludoform.data.library.DownloadDao
import dev.lunikodevelopment.ludoform.data.library.DownloadEntity
import dev.lunikodevelopment.ludoform.data.library.DownloadFolderEntity
import dev.lunikodevelopment.ludoform.data.library.DownloadState
import dev.lunikodevelopment.ludoform.data.library.LibraryRepository
import dev.lunikodevelopment.ludoform.data.library.RommDao
import dev.lunikodevelopment.ludoform.data.library.SaveSyncDao
import dev.lunikodevelopment.ludoform.data.library.SaveSyncState
import dev.lunikodevelopment.ludoform.data.library.UploadDao
import dev.lunikodevelopment.ludoform.data.library.UploadProtocol
import dev.lunikodevelopment.ludoform.data.library.UploadServerDao
import dev.lunikodevelopment.ludoform.data.library.UploadServerEntity
import dev.lunikodevelopment.ludoform.display.DeckController
import dev.lunikodevelopment.ludoform.input.FocusSpot
import dev.lunikodevelopment.ludoform.library.LibraryScans
import dev.lunikodevelopment.ludoform.menu.InGameMenuService
import dev.lunikodevelopment.ludoform.menu.MenuButtons
import dev.lunikodevelopment.ludoform.model.GameSummary
import dev.lunikodevelopment.ludoform.model.PlatformCatalog
import dev.lunikodevelopment.ludoform.romm.RommAddress
import dev.lunikodevelopment.ludoform.romm.RommHealthWorker
import dev.lunikodevelopment.ludoform.romm.RommSettings
import dev.lunikodevelopment.ludoform.romm.RommSyncWorker
import dev.lunikodevelopment.ludoform.theme.LudoformTheme
import dev.lunikodevelopment.ludoform.transfer.ArtWorker
import dev.lunikodevelopment.ludoform.transfer.DownloadSettings
import dev.lunikodevelopment.ludoform.transfer.DownloadWorker
import dev.lunikodevelopment.ludoform.transfer.ImportScanner
import dev.lunikodevelopment.ludoform.transfer.SaveChoice
import dev.lunikodevelopment.ludoform.transfer.SaveLocator
import dev.lunikodevelopment.ludoform.transfer.SaveSyncWorker
import dev.lunikodevelopment.ludoform.transfer.UploadQueue
import dev.lunikodevelopment.ludoform.transfer.UploadSecrets
import dev.lunikodevelopment.ludoform.transfer.UploadServerCheckWorker
import dev.lunikodevelopment.ludoform.transfer.UploadSettings
import dev.lunikodevelopment.ludoform.transfer.UploadWorker
import dev.lunikodevelopment.ludoform.ui.DeckRoot
import dev.lunikodevelopment.ludoform.ui.DesignFixtures
import dev.lunikodevelopment.ludoform.ui.LocalRommForm
import dev.lunikodevelopment.ludoform.ui.LudoController
import dev.lunikodevelopment.ludoform.ui.PlatformActions
import dev.lunikodevelopment.ludoform.ui.RommForm
import dev.lunikodevelopment.ludoform.ui.SharedServerForm
import dev.lunikodevelopment.ludoform.ui.SingleScreenRoot
import dev.lunikodevelopment.ludoform.ui.StageRoot
import dev.lunikodevelopment.ludoform.ui.ThemeStore
import dev.lunikodevelopment.ludoform.ui.ludoPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity(), PlatformActions {
    @Inject lateinit var store: AppStore

    @Inject lateinit var diagnostics: Diagnostics

    @Inject lateinit var libraryRepository: LibraryRepository

    @Inject lateinit var gameLauncher: GameLauncher

    @Inject lateinit var playSessions: PlaySessions

    @Inject lateinit var rommDao: RommDao

    @Inject lateinit var downloadDao: DownloadDao

    @Inject lateinit var saveSyncDao: SaveSyncDao

    @Inject lateinit var uploadServerDao: UploadServerDao

    @Inject lateinit var uploadDao: UploadDao

    @Inject lateinit var libraryDao: dev.lunikodevelopment.ludoform.data.library.LibraryDao

    private val uploadSecrets by lazy { UploadSecrets(this) }

    private val serverForm get() = SharedServerForm

    /** A server just saved; when its test asks to trust a host key, "Trust this server?" opens (board 08). */
    private var awaitingTrust: Long? = null

    private lateinit var downloadSettings: DownloadSettings
    private lateinit var uploadSettings: UploadSettings

    /**
     * The game waiting for the player to choose its platform's download folder. Kept across a recreation (the picker
     * can rotate the top screen), and looked up in the store, which outlives the activity.
     */
    private var pendingDownload: String? = null

    /** One launch at a time: a second Play press while the first is being prepared is ignored. */
    private var launching = false

    private lateinit var controller: LudoController
    private lateinit var rommSettings: RommSettings
    private val rommForm = RommForm()
    private lateinit var deckController: DeckController
    private lateinit var tvController: dev.lunikodevelopment.ludoform.display.TvController

    /** Debug builds started with `--ez fixtures true` show the boards' sample games instead of the database. */
    private val useFixtures: Boolean get() = DesignFixtures.AVAILABLE && intent.getBooleanExtra("fixtures", false)

    private val exportScanReport =
        registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
            if (uri != null) {
                lifecycleScope.launch {
                    val success =
                        withContext(Dispatchers.IO) {
                            runCatching {
                                val report = filesDir.resolve("library-scan-report.txt")
                                check(report.exists())
                                checkNotNull(contentResolver.openOutputStream(uri, "w")).use { output ->
                                    report.inputStream().use { it.copyTo(output) }
                                }
                            }.isSuccess
                        }
                    toast(if (success) "Scan report saved" else "Could not save scan report")
                }
            }
        }
    private val chooseFolder =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) {
                try {
                    // Write too, so a save from RomM can be put next to its game (D32); read alone still works.
                    try {
                        contentResolver.takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                        )
                    } catch (_: SecurityException) {
                        contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    LibraryScans.enqueue(this, uri.toString())
                    refreshFolderAccess()
                } catch (_: SecurityException) {
                    toast("Folder access was not granted")
                }
            }
        }

    /** Downloads need write access, which library folders (read-only grants) do not have. */
    private val chooseDownloadFolder =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            val game = pendingDownload?.let { id -> store.state.value.library?.game(id) }
            pendingDownload = null
            diagnostics.record(
                "Download: folder ${if (uri == null) "not chosen" else "chosen"}${if (game == null) " (no game waiting)" else ""}",
            )
            if (uri == null || game == null) return@registerForActivityResult
            try {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            } catch (_: SecurityException) {
                return@registerForActivityResult toast("Folder access was not granted")
            }
            lifecycleScope.launch {
                downloadDao.setFolder(DownloadFolderEntity(game.platformId, uri.toString()))
                queueDownload(game, uri.toString())
            }
        }

    /** Import a folder (D38): read access is enough, and the folder is remembered as the quick upload folder. */
    private val chooseImportFolder =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            diagnostics.record("Import: folder ${if (uri == null) "not chosen" else "chosen"}")
            if (uri == null) return@registerForActivityResult
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: SecurityException) {
                return@registerForActivityResult toast("Folder access was not granted")
            }
            uploadSettings.quickFolder = uri.toString()
            val name = androidx.documentfile.provider.DocumentFile.fromTreeUri(this, uri)?.name ?: "the folder"
            openImportReview(name) { ImportScanner.folder(this, uri) }
        }

    /** Music (D47): an audio file read through a persisted grant, so it still plays after a restart. */
    private val chooseMusicFile =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            diagnostics.record("Music: file ${if (uri == null) "not chosen" else "chosen"}")
            if (uri == null) return@registerForActivityResult
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: SecurityException) {
                return@registerForActivityResult toast("File access was not granted")
            }
            val name =
                androidx.documentfile.provider.DocumentFile.fromSingleUri(this, uri)?.name?.substringBeforeLast('.') ?: "Chosen file"
            updateFeel { it.copy(music = true, musicTrack = uri.toString(), musicName = name) }
        }

    private lateinit var uiFeedback: dev.lunikodevelopment.ludoform.feel.UiFeedback
    private lateinit var music: dev.lunikodevelopment.ludoform.feel.MusicPlayer

    /** Keys held on Choose the menu button (D40): two held together become the candidate pair. */
    private val menuKeysDown = mutableSetOf<Int>()
    private var menuKeysPaired = false
    private var menuCandidate: Pair<Int, Int>? = null

    /** The import review's view of the server, kept while it is open so row changes need no database read. */
    private var importServer: ImportPlan.Server? = null

    private val exportLog =
        registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
            if (uri != null) {
                diagnostics.export(
                    uri,
                ) { success -> runOnUiThread { toast(if (success) "Diagnostics saved" else "Could not save diagnostics") } }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingDownload = savedInstanceState?.getString(PENDING_DOWNLOAD)
        hideSystemBars()
        controller = LudoController(store, this)
        rommSettings = RommSettings(this)
        downloadSettings = DownloadSettings(this)
        uploadSettings = UploadSettings(this)
        observeRomm()
        observeUploadServers()
        // A check each time Ludoform opens notices a server that went down (or came back) while it was closed (D35).
        if (!useFixtures && rommSettings.server() != null) RommHealthWorker.enqueue(this)
        observeDownloads()
        observeUploads()
        observeMenuScreen()
        store.dispatch(AppIntent.ThemeChanged(ThemeStore.load(this)))
        store.dispatch(
            AppIntent.FeelChanged(dev.lunikodevelopment.ludoform.feel.FeelStore.load(this).copy(wallpaperColor = wallpaperColor())),
        )
        uiFeedback = dev.lunikodevelopment.ludoform.feel.UiFeedback(this)
        music =
            dev.lunikodevelopment.ludoform.feel.MusicPlayer(this) { problem ->
                diagnostics.record(problem)
                store.dispatch(AppIntent.ShowNotice("Can't play the music file. Choose another in Settings → Sound and feel."))
            }
        observeFeel()
        refreshMenu()
        if (!getSharedPreferences(QUICKSTART_PREFS, MODE_PRIVATE).getBoolean(QUICKSTART_DONE, false)) {
            store.dispatch(AppIntent.Navigate(Destination.Quickstart))
        }
        observeSaveConflicts()
        store.dispatch(AppIntent.GameScreenChanged(gameLauncher.gameScreenDefault()))
        observeLibrary()
        observeScans()
        deckController =
            DeckController(
                activity = this,
                createDeckView = { deckContext ->
                    ComposeView(deckContext).apply {
                        setViewTreeLifecycleOwner(this@MainActivity)
                        setViewTreeViewModelStoreOwner(this@MainActivity)
                        setViewTreeSavedStateRegistryOwner(this@MainActivity)
                        setContent {
                            val state by store.state.collectAsState()
                            LudoformTheme(chosen = ludoPalette(state)) {
                                // In couch mode the Thor's own screens stay black (D44); black is off on OLED.
                                if (state.couchActive) {
                                    androidx.compose.foundation.layout.Box(
                                        Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black),
                                    )
                                } else {
                                    DeckRoot(state, controller.events, store::dispatch)
                                }
                            }
                        }
                    }
                },
                onAttachedChanged = { store.dispatch(AppIntent.DeckConnectionChanged(it)) },
                onKeyEvent = ::routeKey,
                onDiagnostic = { message ->
                    store.dispatch(AppIntent.DisplayDiagnostic(message))
                    diagnostics.record("Display: $message")
                },
            )
        tvController =
            dev.lunikodevelopment.ludoform.display.TvController(
                activity = this,
                createView = { tvContext ->
                    ComposeView(tvContext).apply {
                        setViewTreeLifecycleOwner(this@MainActivity)
                        setViewTreeViewModelStoreOwner(this@MainActivity)
                        setViewTreeSavedStateRegistryOwner(this@MainActivity)
                        setContent {
                            val state by store.state.collectAsState()
                            LudoformTheme(chosen = ludoPalette(state)) {
                                CompositionLocalProvider(LocalRommForm provides rommForm) {
                                    SingleScreenRoot(state, controller.events, store::dispatch)
                                }
                            }
                        }
                    }
                },
                onTvChanged = { id -> store.dispatch(AppIntent.CouchChanged(couchEnabled(), id != null)) },
                onKeyEvent = ::routeKey,
                onDiagnostic = { message -> diagnostics.record("Display: $message") },
            )
        tvController.enabled = couchEnabled()
        store.dispatch(AppIntent.CouchChanged(couchEnabled(), false))
        setContent {
            val state by store.state.collectAsState()
            BackHandler(enabled = state.canGoBack) { store.dispatch(AppIntent.Back) }
            LudoformTheme(chosen = ludoPalette(state)) {
                CompositionLocalProvider(LocalRommForm provides rommForm) {
                    if (state.couchActive && state.gameOnDeck == null) {
                        androidx.compose.foundation.layout.Box(
                            Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black),
                        )
                    } else if (state.deckAttached || state.gameOnDeck != null) {
                        StageRoot(state, controller.events, store::dispatch)
                    } else {
                        SingleScreenRoot(state, controller.events, store::dispatch)
                    }
                }
            }
        }
    }

    private fun observeLibrary() {
        if (useFixtures) {
            store.dispatch(AppIntent.LibraryChanged(DesignFixtures.snapshot))
            store.dispatch(AppIntent.ScanChanged(ScanState(finishedOnce = true, status = "Design fixtures")))
            diagnostics.record("Library: showing debug design fixtures")
            return
        }
        lifecycleScope.launch {
            var games: Set<String>? = null
            libraryRepository.snapshots.collect { snapshot ->
                store.dispatch(AppIntent.LibraryChanged(snapshot))
                refreshFolderAccess()
                // New or synced games may need covers (D34); the job does nothing when none do.
                val ids = snapshot.games.mapTo(HashSet()) { it.id }
                if (ids != games) {
                    games = ids
                    ArtWorker.enqueue(this@MainActivity)
                }
                // At most one library rebuild a second; updates in between are dropped for the newest (D34). Every
                // rebuild re-lays out thousands of games, and covers arriving one by one ran Ludoform out of memory.
                delay(LIBRARY_REFRESH_MILLIS)
            }
        }
        lifecycleScope.launch {
            // A sync brings RomM's names and covers (D34): after each one, fetch the covers that changed.
            var lastSync: Long? = null
            rommSettings.changes.collect {
                val at = rommSettings.lastSync()?.at
                if (at != null && lastSync != null && at != lastSync) ArtWorker.enqueue(this@MainActivity)
                lastSync = at
            }
        }
        if (rommSettings.server() != null && RommSyncWorker.needsFullSync(rommSettings)) {
            RommSyncWorker.enqueue(this, RommSyncWorker.Mode.SYNC)
        }
    }

    private fun observeScans() {
        if (useFixtures) return
        lifecycleScope.launch {
            WorkManager.getInstance(this@MainActivity).getWorkInfosForUniqueWorkFlow(LibraryScans.workName()).collect { items ->
                val current = items.firstOrNull { !it.state.isFinished } ?: items.lastOrNull()
                val status =
                    when (current?.state) {
                        WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> "Scan queued"
                        WorkInfo.State.RUNNING -> "Scanning library…"
                        WorkInfo.State.SUCCEEDED -> current.outputData.getString("summary") ?: "Scan complete"
                        WorkInfo.State.FAILED -> "Scan failed"
                        WorkInfo.State.CANCELLED -> "Scan cancelled"
                        null -> "No scan yet"
                    }
                // A finished scan may have brought RomM games onto the device: match them again, without the network.
                val finished = current != null && current.state == WorkInfo.State.SUCCEEDED && store.state.value.scan.running
                if (finished && rommSettings.server() != null) RommSyncWorker.enqueue(this@MainActivity, RommSyncWorker.Mode.LOCAL)
                store.dispatch(
                    AppIntent.ScanChanged(
                        ScanState(
                            running = current != null && !current.state.isFinished,
                            finishedOnce = current == null || current.state.isFinished,
                            status = status,
                            skipped = current?.outputData?.getString("skipped").orEmpty(),
                        ),
                    ),
                )
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) { refreshFolderAccess() }
        }
    }

    /** Settings shows the saved server, its last check, and "Checking…" while the check job runs. */
    private fun observeRomm() {
        val work = WorkManager.getInstance(this)
        val checks = work.getWorkInfosForUniqueWorkFlow(RommHealthWorker.WORK_NAME)
        val syncs = work.getWorkInfosForUniqueWorkFlow(RommSyncWorker.WORK_NAME)
        val time = java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT)
        lifecycleScope.launch {
            combine(
                rommSettings.changes,
                checks,
                syncs,
                rommDao.observePlatforms(),
                saveSyncDao.observeAll(),
            ) { _, checkItems, syncItems, platforms, saves ->
                val checking = checkItems.any { !it.state.isFinished }
                val syncing = syncItems.any { it.state == WorkInfo.State.RUNNING }
                RommStatusText.state(rommSettings.server(), rommSettings.lastCheck(), checking).let { base ->
                    if (!base.configured) {
                        base
                    } else {
                        base.copy(
                            syncing = syncing,
                            syncLine = RommStatusText.syncLine(rommSettings.lastSync(), syncing) { time.format(java.util.Date(it)) },
                            platforms =
                                platforms.map {
                                    RommPlatformRow(
                                        it.id,
                                        it.name,
                                        it.romCount,
                                        it.platformId?.let {
                                                id ->
                                            PlatformCatalog.info(id).shortName
                                        },
                                        it.sync,
                                    )
                                },
                            offline =
                                rommSettings.offline()?.copy(savesWaiting = saves.count { it.state == SaveSyncState.PENDING }),
                        )
                    }
                }
            }.collect { state ->
                val before = store.state.value.romm
                if (state != before) {
                    if (state.status != before.status) diagnostics.record("RomM: ${state.status}")
                    if (state.offline?.outage != before.offline?.outage) {
                        diagnostics.record(state.offline?.let { "RomM: down (${it.outage})" } ?: "RomM: reachable")
                    }
                    // Back after being down: the saves made meanwhile go up now (the offline backlog, D32).
                    if (before.offline != null && state.offline == null && state.configured) uploadWaitingSaves()
                    store.dispatch(AppIntent.RommChanged(state))
                }
            }
        }
    }

    private fun uploadWaitingSaves() {
        lifecycleScope.launch { saveSyncDao.pending().forEach { SaveSyncWorker.enqueue(this@MainActivity, it.gameId) } }
    }

    /** The queue as the Transfers screen shows it, re-read when a row, a hold or the network or charger changes. */
    private fun observeDownloads() {
        val conditions =
            kotlinx.coroutines.flow.flow {
                while (true) {
                    emit(deviceConditions())
                    kotlinx.coroutines.delay(2_000)
                }
            }.distinctUntilChanged()
        lifecycleScope.launch {
            combine(downloadDao.observeActive(), downloadSettings.changes, conditions) { downloads, _, now ->
                val holds = DownloadHolds(downloadSettings.wifiOnly, downloadSettings.chargingOnly, downloadSettings.speedLimit)
                TransferText.rows(downloads, holds, now) to holds
            }.collect { (rows, holds) ->
                if (rows != store.state.value.downloads || holds != store.state.value.holds) {
                    store.dispatch(AppIntent.DownloadsChanged(rows, holds))
                }
            }
        }
    }

    /** The upload queue as Transfers shows it (D37), re-read when a row, a hold, the network or the library changes. */
    private fun observeUploads() {
        val conditions =
            kotlinx.coroutines.flow.flow {
                while (true) {
                    emit(deviceConditions())
                    kotlinx.coroutines.delay(2_000)
                }
            }.distinctUntilChanged()
        val onRomm = store.state.map { state -> state.library?.games?.filter { it.rommId != null }?.map { it.id }?.toSet().orEmpty() }
        lifecycleScope.launch {
            combine(uploadDao.observeAll(), uploadSettings.changes, conditions, onRomm.distinctUntilChanged()) { uploads, _, now, rommIds ->
                val holds = UploadHolds(uploadSettings.wifiOnly, uploadSettings.scanAfter)
                TransferText.uploadRows(uploads, holds, now) { it in rommIds } to holds
            }.collect { (rows, holds) ->
                if (rows != store.state.value.uploads || holds != store.state.value.uploadHolds) {
                    store.dispatch(AppIntent.UploadsChanged(rows, holds))
                }
            }
        }
    }

    private fun observeSaveConflicts() {
        lifecycleScope.launch {
            saveSyncDao.observeAll().collect { rows ->
                val conflicts =
                    rows.filter { it.state == SaveSyncState.CONFLICT }.associate {
                        it.gameId to
                            SaveConflictInfo(
                                deviceAt = it.conflictDeviceAt,
                                deviceSize = it.conflictDeviceSize,
                                serverAt = it.conflictServerAt,
                                serverSize = it.conflictServerSize,
                                serverFrom = it.conflictServerFrom,
                            )
                    }
                if (conflicts != store.state.value.saveConflicts) store.dispatch(AppIntent.SaveConflictsChanged(conflicts))
            }
        }
    }

    /**
     * Syncs [game]'s save with RomM right before it starts and waits up to a few seconds. Returns false when the
     * player has to choose between two saves first. Offline or slow, the game starts with the device's save and the
     * sync catches up after play.
     */
    private suspend fun savesReady(
        game: GameSummary,
        choice: SaveChoice? = null,
    ): Boolean {
        if (useFixtures || game.rommId == null || rommSettings.server() == null || !SaveLocator.supports(game.platformId)) return true
        // RomM is known to be down: start at once with the device's save; it goes up after play once RomM is back (D35).
        if (rommSettings.offline() != null) return true
        SaveSyncWorker.enqueue(this, game.id, choice, now = true)
        kotlinx.coroutines.withTimeoutOrNull(SAVE_CHECK_MILLIS) {
            WorkManager.getInstance(this@MainActivity).getWorkInfosForUniqueWorkFlow(SaveSyncWorker.workName(game.id))
                .first { items -> items.isNotEmpty() && items.all { it.state.isFinished } }
        } ?: diagnostics.record("Saves: check for ${game.id} timed out; starting with the device save")
        val sync = saveSyncDao.get(game.id)
        if (sync?.state == SaveSyncState.FAILED) diagnostics.record("Saves: ${sync.note}")
        return sync?.state != SaveSyncState.CONFLICT
    }

    private fun deviceConditions(): TransferText.Conditions {
        val connectivity = getSystemService(android.net.ConnectivityManager::class.java)
        val capabilities = connectivity.getNetworkCapabilities(connectivity.activeNetwork)
        val battery = getSystemService(android.os.BatteryManager::class.java)
        return TransferText.Conditions(
            online = capabilities?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true,
            unmetered = capabilities?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == true,
            charging = battery.isCharging,
        )
    }

    /** A folder whose persisted read grant is gone (SD card removed, grant revoked) is inaccessible. */
    private fun refreshFolderAccess() {
        if (useFixtures) return
        val folders = store.state.value.library?.folders ?: return
        lifecycleScope.launch {
            val missing =
                withContext(Dispatchers.IO) {
                    val granted = contentResolver.persistedUriPermissions.filter { it.isReadPermission }.map { it.uri.toString() }.toSet()
                    folders.filter { folder ->
                        folder !in granted ||
                            runCatching { DocumentFile.fromTreeUri(this@MainActivity, Uri.parse(folder))?.canRead() != true }
                                .getOrDefault(true)
                    }
                }
            if (missing != store.state.value.inaccessibleFolders) {
                store.dispatch(AppIntent.FolderAccessChanged(missing))
                if (missing.isNotEmpty()) diagnostics.record("Library: ${missing.size} folder(s) inaccessible")
            }
        }
    }

    /** The boards fill both panels edge to edge, so the Stage runs immersive; a swipe reveals the bars briefly. */
    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean = routeKey(event) || super.dispatchKeyEvent(event)

    /**
     * Keys from both windows. After a touch on the Deck its window holds key focus, so while a RomM field is being
     * typed in, text keys are handed to the Stage window, where the field is.
     */
    private fun routeKey(event: KeyEvent): Boolean {
        if (store.state.value.destination == Destination.MenuButton && recordMenuKeys(event)) return true
        if (rommForm.editing == null && serverForm.editing == null) return controller.onKey(event)
        return when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (event.action == KeyEvent.ACTION_DOWN) stopEditing()
                controller.onKey(event)
            }
            KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_DPAD_CENTER -> {
                if (event.action == KeyEvent.ACTION_DOWN) stopEditing()
                true
            }
            else -> window.superDispatchKeyEvent(event)
        }
    }

    private fun stopEditing() {
        rommForm.editing = null
        serverForm.editing = null
    }

    private fun observeUploadServers() {
        lifecycleScope.launch {
            uploadServerDao.observeAll().collect { servers ->
                val rows = servers.map(UploadServerText::row)
                store.dispatch(AppIntent.UploadServersChanged(rows))
                // The server just saved asks to trust its key: show it now instead of leaving a "Check key" to find.
                val waiting = awaitingTrust?.let { id -> rows.firstOrNull { it.id == id } }
                if (waiting != null && waiting.tested) {
                    awaitingTrust = null
                    if (waiting.offeredKey != null && store.state.value.destination == Destination.UploadServers) {
                        store.dispatch(AppIntent.Navigate(Destination.TrustServer(waiting.id)))
                    }
                }
            }
        }
    }

    override fun openServer(id: Long?) {
        lifecycleScope.launch {
            val server = id?.let { uploadServerDao.get(it) }
            serverForm.apply {
                this.id = server?.id
                sftp = server?.protocol == UploadProtocol.SFTP
                name = server?.name.orEmpty()
                host = server?.host.orEmpty()
                port = server?.port?.toString().orEmpty()
                username = server?.username.orEmpty()
                password = ""
                passwordSaved = server != null && uploadSecrets.has(server.id)
                folder = server?.folder.orEmpty()
                editing = null
                problem = null
            }
            store.dispatch(AppIntent.Navigate(Destination.UploadServerEdit(server?.id)))
        }
    }

    override fun editServerField(field: ServerField) {
        serverForm.editing = field
    }

    override fun setServerProtocol(sftp: Boolean) {
        serverForm.sftp = sftp
        serverForm.problem = null
    }

    override fun saveServer() {
        val form = serverForm
        form.editing = null
        val protocol = if (form.sftp) UploadProtocol.SFTP else UploadProtocol.SMB
        val host = form.host.trim().removePrefix("sftp://").removePrefix("smb://").trimEnd('/')
        val port = form.port.toIntOrNull() ?: protocol.defaultPort
        val folder = form.folder.trim()
        val password = form.password.takeIf { it.isNotEmpty() }
        form.problem =
            when {
                host.isEmpty() || host.any { it.isWhitespace() || it == '/' } -> "Type the server's name or address, like nas.local."
                port !in 1..65535 -> "The port is a number from 1 to 65535."
                form.username.isBlank() -> "Type the user name."
                password == null && !form.passwordSaved -> "Type the password."
                folder.isEmpty() && form.sftp -> "Type the RomM library folder, like /romm/library/roms."
                folder.isEmpty() -> "Type the share and folder, like roms/library."
                else -> null
            }
        if (form.problem != null) return
        lifecycleScope.launch(Dispatchers.IO) {
            val existing = form.id?.let { uploadServerDao.get(it) }
            val server =
                UploadServerEntity(
                    id = existing?.id ?: 0,
                    name = form.name.trim(),
                    protocol = protocol,
                    host = host,
                    port = port,
                    username = form.username.trim(),
                    folder = folder,
                    // Another host or protocol is another server: its key has to be trusted again.
                    trustedKey =
                        existing?.trustedKey?.takeIf { existing.host == host && existing.port == port && existing.protocol == protocol },
                )
            val id = if (existing == null) uploadServerDao.insert(server) else server.id.also { uploadServerDao.update(server) }
            if (password != null) uploadSecrets.save(id, password)
            awaitingTrust = id
            UploadServerCheckWorker.enqueue(this@MainActivity, uploadServerDao, id)
            launch(Dispatchers.Main) {
                form.password = ""
                form.passwordSaved = true
                diagnostics.record("Upload server: saved ${protocol.name}, testing")
                store.dispatch(AppIntent.Back)
            }
        }
    }

    override fun removeServer() {
        val id = serverForm.id ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            uploadServerDao.delete(id)
            uploadSecrets.remove(id)
            launch(Dispatchers.Main) {
                diagnostics.record("Upload server: removed")
                store.dispatch(AppIntent.Back)
            }
        }
    }

    override fun checkServer(id: Long) {
        val row = store.state.value.uploadServers.firstOrNull { it.id == id } ?: return
        if (row.checking) return
        if (row.offeredKey != null) return store.dispatch(AppIntent.Navigate(Destination.TrustServer(id)))
        lifecycleScope.launch {
            UploadServerCheckWorker.enqueue(
                this@MainActivity,
                uploadServerDao,
                id,
            )
        }
    }

    override fun trustServer(id: Long) {
        val key = store.state.value.uploadServers.firstOrNull { it.id == id }?.offeredKey ?: return
        lifecycleScope.launch {
            uploadServerDao.trust(id, key)
            UploadServerCheckWorker.enqueue(this@MainActivity, uploadServerDao, id)
            diagnostics.record("Upload server: host key trusted")
            store.dispatch(AppIntent.Back)
        }
    }

    override fun pickFolder() = chooseFolder.launch(null)

    override fun rescan() {
        if (useFixtures) return toast("Design fixtures are not scanned")
        LibraryScans.enqueue(this)
    }

    override fun saveScanReport() = exportScanReport.launch("ludoform-scan-report.txt")

    override fun exportLog() = exportLog.launch("ludoform-diagnostics.txt")

    override fun reattachDeck() = deckController.reattach()

    override fun openFiles() = startSafely(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_FILES), "No file manager found")

    override fun openNetwork() = startSafely(Intent(Settings.ACTION_WIFI_SETTINGS), "Network settings are unavailable")

    override fun record(message: String) = diagnostics.record(message)

    override fun play(request: LaunchRequest) {
        val game = store.state.value.library?.game(request.gameId) ?: return
        if (launching) return
        launching = true
        lifecycleScope.launch {
            try {
                if (!request.savesChecked && !savesReady(game)) {
                    diagnostics.record("Saves: conflict for ${game.id}")
                    store.dispatch(AppIntent.Navigate(Destination.SaveConflict(request)))
                    return@launch
                }
                val deckAvailable = store.state.value.deckAttached
                when (val outcome = gameLauncher.prepare(request, game, deckAvailable)) {
                    is GameLauncher.Outcome.Start -> start(outcome, game)
                    is GameLauncher.Outcome.ChooseScreen ->
                        store.dispatch(
                            AppIntent.Navigate(Destination.Screen(request, outcome.emulator, outcome.packageName)),
                        )
                    is GameLauncher.Outcome.ChooseDisc ->
                        store.dispatch(AppIntent.Navigate(Destination.Disc(game.id, outcome.discs, request)))
                    is GameLauncher.Outcome.ChooseEmulator -> showEmulatorSheet(game, outcome.message)
                    is GameLauncher.Outcome.Problem -> {
                        diagnostics.record("Launch: ${game.id} not started: ${outcome.message}")
                        store.dispatch(AppIntent.ShowNotice(outcome.message))
                    }
                }
            } finally {
                launching = false
            }
        }
    }

    override fun playWith(gameId: String) {
        val game = store.state.value.library?.game(gameId) ?: return
        lifecycleScope.launch { showEmulatorSheet(game, null) }
    }

    override fun useEmulator(
        gameId: String,
        choice: EmulatorChoice,
        scope: PreferenceScope,
    ) {
        val game = store.state.value.library?.game(gameId) ?: return
        gameLauncher.save(game, choice, scope)
        diagnostics.record("Launch: ${choice.packageName} saved for ${if (scope == PreferenceScope.GAME) game.id else game.platformId}")
        play(LaunchRequest(gameId, packageName = choice.packageName))
    }

    override fun getEmulator(packageName: String) {
        val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName"))
        try {
            startActivity(market.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            startSafely(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName")),
                "No app store or browser found",
            )
        }
    }

    /** Opening the sheet again (for example after a failed choice) only updates its message. */
    private suspend fun showEmulatorSheet(
        game: GameSummary,
        message: String?,
    ) {
        val current = store.state.value.destination
        if (current is Destination.Emulator && current.gameId == game.id) {
            store.dispatch(AppIntent.Replace(current.copy(message = message)))
        } else {
            store.dispatch(AppIntent.Navigate(gameLauncher.emulatorSheet(game, message)))
        }
    }

    override fun playOnScreen(action: UiAction.PlayOnScreen) {
        gameLauncher.saveScreen(action.request.gameId, action.emulatorPackage, action.screen, action.scope)
        play(action.request.copy(screen = action.screen))
    }

    override fun setGameScreen(setting: ScreenSetting) {
        gameLauncher.setGameScreenDefault(setting)
        store.dispatch(AppIntent.GameScreenChanged(setting))
    }

    private fun couchEnabled(): Boolean = getSharedPreferences(SCREENS_PREFS, MODE_PRIVATE).getBoolean(COUCH_MODE, false)

    override fun toggleCouch() {
        val enabled = !couchEnabled()
        getSharedPreferences(SCREENS_PREFS, MODE_PRIVATE).edit().putBoolean(COUCH_MODE, enabled).apply()
        diagnostics.record("Couch mode: ${if (enabled) "on" else "off"}")
        tvController.enabled = enabled
        store.dispatch(AppIntent.CouchChanged(enabled, tvController.tvDisplayId != null))
    }

    override fun randomGame() {
        val games = store.state.value.library?.games.orEmpty()
        val pick = games.filter { !it.remoteOnly }.ifEmpty { games }.randomOrNull() ?: return
        store.dispatch(AppIntent.Replace(Destination.Details(pick.id)))
    }

    override fun cycleGameScreen() {
        val next = ScreenSetting.entries[(store.state.value.gameScreen.ordinal + 1) % ScreenSetting.entries.size]
        gameLauncher.setGameScreenDefault(next)
        store.dispatch(AppIntent.GameScreenChanged(next))
    }

    override fun releaseDeck() {
        store.dispatch(AppIntent.GameOnDeck(null))
        deckController.release()
        tvController.release()
        finishPlaySessions()
    }

    /** Ludoform is shown again, so whatever was playing has ended (see [PlaySessions]). */
    private fun finishPlaySessions() {
        if (useFixtures) return
        lifecycleScope.launch {
            val kept = playSessions.finishOpen()
            if (kept.isNotEmpty()) diagnostics.record("Play: ${kept.size} session(s) recorded")
            // The game just closed: its save goes to RomM, now or as soon as there is a network.
            if (rommSettings.server() != null) kept.distinct().forEach { SaveSyncWorker.enqueue(this@MainActivity, it) }
        }
    }

    /**
     * The emulator opens in its own task. On the top screen it covers the Stage, which stops this activity, and
     * [onStop] dismisses the Deck until [onStart]. On the bottom screen the Deck is held off its display and the
     * Stage shows what is playing, until the player brings the Deck back or leaves and returns to Ludoform.
     */
    private fun start(
        outcome: GameLauncher.Outcome.Start,
        game: GameSummary,
    ) {
        val deckDisplay = deckController.deckDisplayId
        val tv = tvController.gameDisplayId.takeIf { store.state.value.couchActive }
        // In couch mode every game opens on the TV (D44); Ludoform steps off it like the Deck does for a game.
        val onDeck = tv == null && outcome.screen == GameScreen.BOTTOM && deckDisplay != null
        val target = tv ?: if (onDeck) deckDisplay else display?.displayId
        val options = ActivityOptions.makeBasic()
        target?.let { options.setLaunchDisplayId(it) }
        if (tv != null) {
            tvController.hold()
            store.dispatch(AppIntent.GameOnDeck(game.title))
        } else if (onDeck) {
            deckController.hold()
            store.dispatch(AppIntent.GameOnDeck(game.title))
        }
        try {
            startActivity(outcome.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), options.toBundle())
            diagnostics.record("Launch: started ${outcome.emulator} with ${outcome.file} on display $target")
            store.dispatch(AppIntent.CloseLaunchSheets)
            if (!useFixtures) lifecycleScope.launch { playSessions.start(game.id, outcome.packageName) }
            // Achievements and the manual for the in-game menu (D48), fetched while the game loads.
            if (!useFixtures && game.rommId != null && rommSettings.server() != null) {
                dev.lunikodevelopment.ludoform.romm.GameExtrasWorker.enqueue(this, game.id)
            }
        } catch (error: RuntimeException) {
            // ActivityNotFoundException or SecurityException: the emulator changed since the intent was checked.
            diagnostics.record("Launch: ${outcome.emulator} refused the game: ${error.javaClass.simpleName}")
            if (onDeck) releaseDeck()
            store.dispatch(AppIntent.ShowNotice("${outcome.emulator} could not open this game."))
        }
    }

    override fun openRomm() {
        val server = rommSettings.server()
        rommForm.address = server?.address.orEmpty()
        rommForm.username = server?.username.orEmpty()
        rommForm.password = ""
        rommForm.passwordSaved = rommSettings.hasPassword()
        rommForm.problem = null
        rommForm.editing = null
        store.dispatch(AppIntent.Navigate(Destination.Romm))
    }

    override fun editRommField(field: RommField) {
        rommForm.editing = field
    }

    override fun saveRomm() {
        rommForm.editing = null
        val url = RommAddress.parse(rommForm.address)
        val username = rommForm.username.trim()
        val password = rommForm.password.takeIf { it.isNotEmpty() }
        rommForm.problem =
            when {
                rommForm.address.trim().startsWith("http://", ignoreCase = true) ->
                    "Use an https address. Your password would travel unencrypted over http."
                url == null -> "Check the server address."
                username.isEmpty() -> "Type your RomM user name."
                password == null && !rommForm.passwordSaved -> "Type your RomM password."
                else -> null
            }
        if (rommForm.problem != null || url == null) return
        val address = RommAddress.display(url)
        lifecycleScope.launch(Dispatchers.IO) {
            rommSettings.save(address, username, password)
            RommHealthWorker.enqueue(this@MainActivity)
        }
        diagnostics.record("RomM: server saved, checking")
        rommForm.address = address
        rommForm.password = ""
        if (password != null) rommForm.passwordSaved = true
    }

    override fun syncRomm() {
        RommSyncWorker.enqueue(this, RommSyncWorker.Mode.FULL)
        diagnostics.record("RomM: sync requested")
    }

    override fun retryRomm() {
        if (rommSettings.server() == null) return
        RommHealthWorker.enqueue(this)
        diagnostics.record("RomM: retry requested")
    }

    override fun toggleRommPlatform(id: Int) {
        val row = store.state.value.romm.platforms.firstOrNull { it.id == id } ?: return
        lifecycleScope.launch {
            rommDao.setSync(id, !row.sync)
            RommSyncWorker.enqueue(this@MainActivity, RommSyncWorker.Mode.LOCAL)
        }
    }

    override fun download(gameId: String) {
        val game = store.state.value.library?.game(gameId)?.takeIf { it.remoteOnly } ?: return
        lifecycleScope.launch {
            val folder = downloadDao.folder(game.platformId)?.treeUri
            val writable =
                folder != null &&
                    contentResolver.persistedUriPermissions.any { it.uri.toString() == folder && it.isWritePermission }
            if (writable) {
                queueDownload(game, folder!!)
            } else {
                pendingDownload = game.id
                val platform = PlatformCatalog.info(game.platformId).shortName
                store.dispatch(AppIntent.ShowNotice("Choose the folder where $platform games go."))
                chooseDownloadFolder.launch(null)
            }
        }
    }

    private suspend fun queueDownload(
        game: GameSummary,
        folder: String,
    ) {
        val rom = game.rommId?.let { rommDao.getRom(it) } ?: return
        // RomM sends a game made of several files as one zip.
        val fileName = if ('.' in rom.fsName) rom.fsName else rom.fsName + ".zip"
        downloadDao.forRom(rom.id)?.takeIf { it.state == DownloadState.DONE }?.let { downloadDao.delete(it.id) }
        val added =
            downloadDao.insert(
                DownloadEntity(
                    rommId = rom.id,
                    gameId = game.id,
                    title = game.title,
                    platformId = game.platformId,
                    fileName = fileName,
                    folderUri = folder,
                    totalBytes = rom.sizeBytes,
                    createdAt = System.currentTimeMillis(),
                ),
            )
        DownloadWorker.enqueue(this)
        diagnostics.record("Download: ${if (added > 0) "queued" else "already queued"} RomM ${rom.id}")
        store.dispatch(AppIntent.ShowNotice("Added to Transfers."))
    }

    override fun transferPrimary(id: Long) {
        val row = store.state.value.downloads.firstOrNull { it.id == id } ?: return
        lifecycleScope.launch {
            val download = downloadDao.get(id) ?: return@launch
            if (row.state == DownloadRowState.ACTIVE) {
                downloadDao.setState(id, DownloadState.PAUSED)
            } else {
                downloadDao.update(download.copy(state = DownloadState.QUEUED, problem = null, attempts = 0))
                DownloadWorker.enqueue(this@MainActivity)
            }
        }
    }

    override fun cancelTransfer(id: Long) {
        lifecycleScope.launch { cancel(id) }
    }

    /** A running download's worker deletes its partial file when it sees the row gone; otherwise it goes here. */
    private suspend fun cancel(id: Long) {
        val download = downloadDao.get(id) ?: return
        downloadDao.delete(id)
        if (download.state != DownloadState.RUNNING) {
            download.partUri?.let { part ->
                withContext(Dispatchers.IO) {
                    runCatching { android.provider.DocumentsContract.deleteDocument(contentResolver, Uri.parse(part)) }
                }
            }
        }
        diagnostics.record("Download: cancelled RomM ${download.rommId}")
    }

    override fun cancelAllTransfers() {
        lifecycleScope.launch {
            downloadDao.active().forEach { cancel(it.id) }
            store.dispatch(AppIntent.Replace(Destination.Transfers()))
        }
    }

    override fun toggleHold(hold: TransferHold) {
        when (hold) {
            TransferHold.WIFI -> downloadSettings.wifiOnly = !downloadSettings.wifiOnly
            TransferHold.CHARGING -> downloadSettings.chargingOnly = !downloadSettings.chargingOnly
            TransferHold.SPEED -> {
                val steps = DownloadSettings.SPEED_STEPS
                downloadSettings.speedLimit = steps[(steps.indexOf(downloadSettings.speedLimit) + 1) % steps.size]
                // The running download reads the limit as it goes; no restart needed.
                return
            }
        }
        // New holds only apply to a newly enqueued worker; the partial file lets it carry on where it was.
        if (store.state.value.downloads.isNotEmpty()) DownloadWorker.enqueue(this, restart = true)
    }

    override fun toggleFavorite(gameId: String) {
        lifecycleScope.launch {
            val favorite = libraryDao.toggleFavorite(gameId, System.currentTimeMillis())
            diagnostics.record("Favorite: $gameId ${if (favorite) "added" else "removed"}")
        }
    }

    override fun upload(gameId: String) {
        lifecycleScope.launch {
            val title = store.state.value.library?.game(gameId)?.title ?: return@launch
            when (val added = UploadQueue.add(this@MainActivity, gameId)) {
                is UploadQueue.Added.Queued,
                is UploadQueue.Added.AlreadyQueued,
                -> {
                    diagnostics.record("Upload: queued $gameId")
                    store.dispatch(AppIntent.Navigate(Destination.Transfers(tab = TransferTab.UPLOAD)))
                }
                UploadQueue.Added.NoServer ->
                    store.dispatch(AppIntent.ShowNotice("Test an upload server in Settings first."))
                is UploadQueue.Added.Missing ->
                    store.dispatch(AppIntent.ShowNotice("Can't upload $title: missing ${added.names.joinToString(", ")}."))
                UploadQueue.Added.NotOnDevice ->
                    store.dispatch(AppIntent.ShowNotice("$title isn't on this device."))
            }
        }
    }

    override fun uploadPrimary(id: Long) {
        lifecycleScope.launch { UploadQueue.primary(this@MainActivity, id) }
    }

    override fun cancelUpload(id: Long) {
        lifecycleScope.launch { UploadQueue.cancel(this@MainActivity, id) }
    }

    override fun cancelAllUploads() {
        lifecycleScope.launch {
            UploadQueue.cancelAll(this@MainActivity)
            store.dispatch(AppIntent.Replace(Destination.Transfers(tab = TransferTab.UPLOAD)))
        }
    }

    override fun clearFinishedUploads() {
        lifecycleScope.launch { UploadQueue.clearFinished(this@MainActivity) }
    }

    override fun toggleUploadHold(hold: UploadHold) {
        when (hold) {
            UploadHold.WIFI -> {
                uploadSettings.wifiOnly = !uploadSettings.wifiOnly
                // The network hold only applies to a newly enqueued worker; part files let it carry on.
                if (store.state.value.uploads.any { it.state != UploadRowState.DONE }) {
                    UploadWorker.enqueue(this, restart = true)
                }
            }
            UploadHold.SCAN -> uploadSettings.scanAfter = !uploadSettings.scanAfter
        }
    }

    override fun reviewMissing() {
        val games =
            store.state.value.library?.games.orEmpty().filter { it.fileCount > 0 && it.rommId == null }
                .map { ImportScanner.LibraryGame(it.id, it.rawTitle, it.platformId) }
        openImportReview("your library") { ImportScanner.libraryGames(this, games) }
    }

    override fun importFolder() {
        val start = uploadSettings.quickFolder?.let { Uri.parse(it) }
        chooseImportFolder.launch(start)
    }

    private fun openImportReview(
        source: String,
        find: suspend () -> List<ImportPlan.Found>,
    ) {
        store.dispatch(AppIntent.ImportChanged(ImportReview(source, scanning = true)))
        store.dispatch(AppIntent.Navigate(Destination.ImportReview()))
        lifecycleScope.launch {
            val found = find()
            val server = ImportScanner.server(this@MainActivity)
            importServer = server
            val extensions =
                withContext(Dispatchers.IO) {
                    dev.lunikodevelopment.ludoform.library.PlatformRegistry(
                        this@MainActivity,
                    ).platforms.associate { it.id to it.extensions }
                }
            val items = ImportPlan.build(found, extensions, server)
            diagnostics.record("Import: ${found.size} found in $source, ${items.count { it.include }} ready")
            store.dispatch(AppIntent.ImportChanged(ImportReview(source, scanning = false, items = items)))
        }
    }

    override fun importRow(itemId: String) {
        val review = store.state.value.importReview ?: return
        val server = importServer ?: return
        val items = review.items.map { if (it.id == itemId) ImportPlan.act(it, server) else it }
        store.dispatch(AppIntent.ImportChanged(review.copy(items = items)))
    }

    override fun startImport() {
        val review = store.state.value.importReview ?: return
        val chosen = review.items.filter { it.include && it.platformId != null && it.fsSlug != null }
        lifecycleScope.launch {
            val missing = mutableListOf<String>()
            var queued = 0
            for (item in chosen) {
                val gameId = item.gameId ?: ("import:" + item.id.hashCode().toUInt().toString(16))
                val added =
                    UploadQueue.addImported(
                        this@MainActivity,
                        gameId,
                        item.title,
                        item.platformId!!,
                        item.fsSlug!!,
                        item.files.map { it.uri to it.name },
                    )
                when (added) {
                    is UploadQueue.Added.Queued, is UploadQueue.Added.AlreadyQueued -> queued++
                    is UploadQueue.Added.Missing -> missing.add(item.title)
                    else -> missing.add(item.title)
                }
            }
            diagnostics.record("Import: queued $queued, not queued ${missing.size}")
            importServer = null
            store.dispatch(AppIntent.ImportChanged(null))
            store.dispatch(AppIntent.Replace(Destination.Transfers(tab = TransferTab.UPLOAD)))
            if (missing.isNotEmpty()) store.dispatch(AppIntent.ShowNotice("Not queued, files missing: ${missing.joinToString(", ")}"))
        }
    }

    private fun refreshMenu() {
        val buttons = MenuButtons.describe(MenuButtons(this).pair)
        val menu = MenuState(InGameMenuService.isEnabled(this), buttons, menuCandidate?.let { MenuButtons.describe(it) })
        if (menu != store.state.value.menu) store.dispatch(AppIntent.MenuChanged(menu))
    }

    /** Pauses the in-game menu while its buttons are being chosen, so holding them there doesn't open it. */
    private fun observeMenuScreen() {
        lifecycleScope.launch {
            store.state.map { it.destination == Destination.MenuButton }.distinctUntilChanged().collect { choosing ->
                InGameMenuService.paused = choosing
                if (!choosing) {
                    menuCandidate = null
                    menuKeysDown.clear()
                    menuKeysPaired = false
                    refreshMenu()
                }
            }
        }
    }

    /**
     * On Choose the menu button, two keys held together become the candidate pair. A key pressed alone is replayed to
     * the screen on release, so A and B still work there.
     */
    private fun recordMenuKeys(event: KeyEvent): Boolean {
        val code = event.keyCode
        if (code == KeyEvent.KEYCODE_BACK) return false
        when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                if (event.repeatCount > 0) return true
                menuKeysDown.add(code)
                if (menuKeysDown.size == 2 && !menuKeysPaired) {
                    menuKeysPaired = true
                    val (first, second) = menuKeysDown.toList()
                    menuCandidate = first to second
                    diagnostics.record("Menu button: pair ${MenuButtons.describe(first to second)} pressed")
                    refreshMenu()
                }
            }
            KeyEvent.ACTION_UP -> {
                val alone = !menuKeysPaired && menuKeysDown == setOf(code)
                menuKeysDown.remove(code)
                if (menuKeysDown.isEmpty()) menuKeysPaired = false
                if (alone) {
                    controller.onKey(KeyEvent(event.downTime, event.eventTime, KeyEvent.ACTION_DOWN, code, 0))
                    controller.onKey(event)
                }
            }
        }
        return true
    }

    override fun openAccessibility() {
        try {
            startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS))
        } catch (_: ActivityNotFoundException) {
            toast("Accessibility settings are not available")
        }
    }

    override fun finishQuickstart() {
        getSharedPreferences(QUICKSTART_PREFS, MODE_PRIVATE).edit().putBoolean(QUICKSTART_DONE, true).apply()
        diagnostics.record("Quickstart: finished")
        store.dispatch(if (store.state.value.canGoBack) AppIntent.Back else AppIntent.Replace(Destination.Home))
    }

    override fun setColorSource(source: dev.lunikodevelopment.ludoform.data.ColorSource) {
        updateFeel { it.copy(colorSource = source, wallpaperColor = wallpaperColor()) }
    }

    override fun toggleFeel(toggle: dev.lunikodevelopment.ludoform.data.FeelToggle) {
        val feel = store.state.value.feel
        when (toggle) {
            dev.lunikodevelopment.ludoform.data.FeelToggle.HAPTICS -> {
                if (!feel.haptics && !uiFeedback.canVibrate) {
                    store.dispatch(AppIntent.ShowNotice("This device has no vibration motor Ludoform can use."))
                    return
                }
                updateFeel { it.copy(haptics = !it.haptics) }
            }
            dev.lunikodevelopment.ludoform.data.FeelToggle.SOUNDS -> updateFeel { it.copy(sounds = !it.sounds) }
            dev.lunikodevelopment.ludoform.data.FeelToggle.MUSIC ->
                if (!feel.music && feel.musicTrack == null) chooseMusic() else updateFeel { it.copy(music = !it.music) }
        }
    }

    override fun chooseMusic() {
        try {
            chooseMusicFile.launch(arrayOf("audio/*"))
        } catch (_: ActivityNotFoundException) {
            toast("No file picker is available")
        }
    }

    private fun updateFeel(change: (dev.lunikodevelopment.ludoform.data.FeelState) -> dev.lunikodevelopment.ludoform.data.FeelState) {
        val feel = change(store.state.value.feel)
        dev.lunikodevelopment.ludoform.feel.FeelStore.save(this, feel)
        store.dispatch(AppIntent.FeelChanged(feel))
    }

    /** Android's main wallpaper colour, for the Wallpaper colour source; null when Android gives none. */
    private fun wallpaperColor(): Int? =
        runCatching {
            android.app.WallpaperManager.getInstance(this)
                .getWallpaperColors(android.app.WallpaperManager.FLAG_SYSTEM)?.primaryColor?.toArgb()
        }.getOrNull()

    /**
     * Haptics and UI sounds follow focus and navigation (D47): a tick when focus moves, a click going forward and a
     * lower click going back. Music plays while Ludoform is in front and no game runs on the bottom screen.
     */
    private fun observeFeel() {
        lifecycleScope.launch {
            var last: Pair<FocusSpot?, Int>? = null
            store.state.map { it.focus.current to it.navigation.size }.distinctUntilChanged().collect { now ->
                val before = last
                last = now
                val feel = store.state.value.feel
                if (before == null || (!feel.haptics && !feel.sounds)) return@collect
                val tone =
                    when {
                        now.second > before.second -> dev.lunikodevelopment.ludoform.feel.UiTone.SELECT
                        now.second < before.second -> dev.lunikodevelopment.ludoform.feel.UiTone.BACK
                        now.first != null && now.first != before.first -> dev.lunikodevelopment.ludoform.feel.UiTone.MOVE
                        else -> return@collect
                    }
                uiFeedback.play(tone, feel.haptics, feel.sounds)
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) {
                try {
                    store.state.map { Triple(it.feel.musicTrack, it.feel.music, it.gameOnDeck == null) }.distinctUntilChanged()
                        .collect { (track, on, free) -> music.update(track, on && free) }
                } finally {
                    music.pause()
                }
            }
        }
    }

    override fun setTheme(theme: ThemeChoice) {
        ThemeStore.save(this, theme)
        store.dispatch(AppIntent.ThemeChanged(theme))
    }

    override fun useMenuButtons() {
        menuCandidate?.let { pair ->
            MenuButtons(this).pair = pair
            InGameMenuService.running?.reloadButtons()
            diagnostics.record("Menu button: now ${MenuButtons.describe(pair)}")
        }
        menuCandidate = null
        refreshMenu()
        store.dispatch(AppIntent.Back)
    }

    override fun resolveSave(
        request: LaunchRequest,
        resolution: SaveResolution,
    ) {
        val game = store.state.value.library?.game(request.gameId) ?: return
        val choice =
            when (resolution) {
                SaveResolution.DEVICE -> SaveChoice.DEVICE
                SaveResolution.SERVER -> SaveChoice.SERVER
                SaveResolution.BOTH -> SaveChoice.BOTH
            }
        diagnostics.record("Saves: ${game.id} settled with $choice")
        lifecycleScope.launch {
            if (savesReady(game, choice)) {
                store.dispatch(AppIntent.Back)
                play(request.copy(savesChecked = true))
            } else {
                store.dispatch(AppIntent.ShowNotice("The save could not be settled. Try again."))
            }
        }
    }

    override fun removeRomm() {
        WorkManager.getInstance(this).cancelUniqueWork(RommHealthWorker.WORK_NAME)
        RommSyncWorker.unschedule(this)
        // Takes the server's games out of the library; local games and play history stay.
        RommSyncWorker.enqueue(this, RommSyncWorker.Mode.CLEAR)
        lifecycleScope.launch(Dispatchers.IO) { rommSettings.remove() }
        diagnostics.record("RomM: server removed")
        rommForm.address = ""
        rommForm.username = ""
        rommForm.password = ""
        rommForm.passwordSaved = false
        rommForm.problem = null
        store.dispatch(AppIntent.SelectFocus(FocusSpot(Zones.ROMM_FIELDS, 0)))
    }

    private fun startSafely(
        intent: Intent,
        failure: String,
    ) {
        try {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            toast(failure)
        }
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    override fun onStart() {
        super.onStart()
        // Coming back from anywhere ends a bottom-screen session: start() shows the Deck again.
        if (store.state.value.gameOnDeck != null) store.dispatch(AppIntent.GameOnDeck(null))
        deckController.start()
        tvController.start()
        tvController.release()
        finishPlaySessions()
        refreshMenu()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        pendingDownload?.let { outState.putString(PENDING_DOWNLOAD, it) }
    }

    override fun onDestroy() {
        if (::music.isInitialized) music.stop()
        if (::uiFeedback.isInitialized) uiFeedback.release()
        super.onDestroy()
    }

    override fun onStop() {
        deckController.stop()
        tvController.stop()
        super.onStop()
    }

    private companion object {
        const val PENDING_DOWNLOAD = "pending-download"
        const val QUICKSTART_PREFS = "quickstart"
        const val SCREENS_PREFS = "screens"
        const val COUCH_MODE = "couch_mode"
        const val QUICKSTART_DONE = "done"
        const val SAVE_CHECK_MILLIS = 6_000L
        const val LIBRARY_REFRESH_MILLIS = 1_000L
    }
}
