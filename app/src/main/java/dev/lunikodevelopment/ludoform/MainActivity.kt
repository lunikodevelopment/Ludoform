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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import dev.lunikodevelopment.ludoform.data.EmulatorChoice
import dev.lunikodevelopment.ludoform.data.LaunchRequest
import dev.lunikodevelopment.ludoform.data.PreferenceScope
import dev.lunikodevelopment.ludoform.data.ScanState
import dev.lunikodevelopment.ludoform.data.library.LibraryRepository
import dev.lunikodevelopment.ludoform.display.DeckController
import dev.lunikodevelopment.ludoform.library.LibraryScans
import dev.lunikodevelopment.ludoform.model.GameSummary
import dev.lunikodevelopment.ludoform.theme.LudoformTheme
import dev.lunikodevelopment.ludoform.ui.DeckRoot
import dev.lunikodevelopment.ludoform.ui.DesignFixtures
import dev.lunikodevelopment.ludoform.ui.LudoController
import dev.lunikodevelopment.ludoform.ui.PlatformActions
import dev.lunikodevelopment.ludoform.ui.SingleScreenRoot
import dev.lunikodevelopment.ludoform.ui.StageRoot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity(), PlatformActions {
    @Inject lateinit var store: AppStore

    @Inject lateinit var diagnostics: Diagnostics

    @Inject lateinit var libraryRepository: LibraryRepository

    @Inject lateinit var gameLauncher: GameLauncher

    /** One launch at a time: a second Play press while the first is being prepared is ignored. */
    private var launching = false

    private lateinit var controller: LudoController
    private lateinit var deckController: DeckController

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
                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    LibraryScans.enqueue(this, uri.toString())
                    refreshFolderAccess()
                } catch (_: SecurityException) {
                    toast("Folder access was not granted")
                }
            }
        }
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
        hideSystemBars()
        controller = LudoController(store, this)
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
                            LudoformTheme { DeckRoot(state, controller.events, store::dispatch) }
                        }
                    }
                },
                onAttachedChanged = { store.dispatch(AppIntent.DeckConnectionChanged(it)) },
                onKeyEvent = controller::onKey,
                onDiagnostic = { message ->
                    store.dispatch(AppIntent.DisplayDiagnostic(message))
                    diagnostics.record("Display: $message")
                },
            )
        setContent {
            val state by store.state.collectAsState()
            BackHandler(enabled = state.canGoBack) { store.dispatch(AppIntent.Back) }
            LudoformTheme {
                if (state.deckAttached) {
                    StageRoot(state, controller.events, store::dispatch)
                } else {
                    SingleScreenRoot(state, controller.events, store::dispatch)
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
            libraryRepository.snapshots.collect { snapshot ->
                store.dispatch(AppIntent.LibraryChanged(snapshot))
                refreshFolderAccess()
            }
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

    override fun dispatchKeyEvent(event: KeyEvent): Boolean = controller.onKey(event) || super.dispatchKeyEvent(event)

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
                when (val outcome = gameLauncher.prepare(request, game)) {
                    is GameLauncher.Outcome.Start -> start(outcome)
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

    /**
     * The emulator opens in its own task on the Stage's display. Covering the Stage stops this activity, and
     * [onStop] then dismisses the Deck; [onStart] brings it back when the player returns.
     */
    private fun start(outcome: GameLauncher.Outcome.Start) {
        val options = ActivityOptions.makeBasic()
        display?.displayId?.let { options.setLaunchDisplayId(it) }
        try {
            startActivity(outcome.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), options.toBundle())
            diagnostics.record("Launch: started ${outcome.emulator} with ${outcome.file}")
            store.dispatch(AppIntent.CloseLaunchSheets)
        } catch (error: RuntimeException) {
            // ActivityNotFoundException or SecurityException: the emulator changed since the intent was checked.
            diagnostics.record("Launch: ${outcome.emulator} refused the game: ${error.javaClass.simpleName}")
            store.dispatch(AppIntent.ShowNotice("${outcome.emulator} could not open this game."))
        }
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
        deckController.start()
    }

    override fun onStop() {
        deckController.stop()
        super.onStop()
    }
}
