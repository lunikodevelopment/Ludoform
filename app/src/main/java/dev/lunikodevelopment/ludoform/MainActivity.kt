package dev.lunikodevelopment.ludoform

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.work.WorkInfo
import androidx.work.WorkManager
import dagger.hilt.android.AndroidEntryPoint
import dev.lunikodevelopment.ludoform.data.AppIntent
import dev.lunikodevelopment.ludoform.data.AppStore
import dev.lunikodevelopment.ludoform.data.Destination
import dev.lunikodevelopment.ludoform.display.DeckController
import dev.lunikodevelopment.ludoform.input.FocusDirection
import dev.lunikodevelopment.ludoform.input.FocusTarget
import dev.lunikodevelopment.ludoform.input.Screen
import dev.lunikodevelopment.ludoform.library.LibraryScans
import dev.lunikodevelopment.ludoform.theme.LudoformTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var store: AppStore

    @Inject lateinit var diagnostics: Diagnostics
    private val exportScanReport =
        registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
            if (uri != null) {
                lifecycleScope.launch {
                    val success =
                        withContext(Dispatchers.IO) {
                            runCatching {
                                val report = filesDir.resolve("library-scan-report.txt")
                                check(report.exists())
                                checkNotNull(contentResolver.openOutputStream(uri, "w")).use {
                                        output ->
                                    report.inputStream().use { it.copyTo(output) }
                                }
                            }.isSuccess
                        }
                    Toast.makeText(
                        this@MainActivity,
                        if (success) "Scan report saved" else "Could not save scan report",
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        }
    private val chooseFolder =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) {
                try {
                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    LibraryScans.enqueue(this, uri.toString())
                } catch (_: SecurityException) {
                    Toast.makeText(this, "Folder access was not granted", Toast.LENGTH_LONG).show()
                }
            }
        }
    private val exportLog =
        registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
            if (uri != null) {
                diagnostics.export(uri) { success ->
                    runOnUiThread {
                        Toast.makeText(
                            this,
                            if (success) "Diagnostics saved" else "Could not save diagnostics",
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }
            }
        }
    private lateinit var deckController: DeckController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
                store.dispatch(AppIntent.LibraryScanUpdated(status, current?.outputData?.getString("skipped").orEmpty()))
            }
        }
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
                            LudoformTheme {
                                DeckPlaceholder(
                                    state = state,
                                    onIntent = store::dispatch,
                                    onExport = ::exportDiagnostics,
                                    onPickFolder = ::pickFolder,
                                    onRescan = ::rescanLibrary,
                                    onExportScanReport = ::saveScanReport,
                                )
                            }
                        }
                    }
                },
                onAttachedChanged = { store.dispatch(AppIntent.DeckConnectionChanged(it)) },
                onKeyEvent = ::handleKey,
                onDiagnostic = { message ->
                    store.dispatch(AppIntent.DisplayDiagnostic(message))
                    diagnostics.record("Display: $message")
                },
            )
        setContent {
            val state by store.state.collectAsState()
            BackHandler(enabled = state.canGoBack) { store.dispatch(AppIntent.Back) }
            LudoformTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.onBackground,
                ) {
                    Column {
                        Column(
                            modifier = Modifier.weight(1f).touchFocus(Screen.STAGE, store::dispatch),
                            verticalArrangement = Arrangement.SpaceEvenly,
                        ) {
                            Text("Ludoform · Stage")
                            Text("Current screen: ${state.destination.label}")
                            Text(if (state.deckAttached) "Deck connected" else "Deck unavailable · single-screen mode")
                            DestinationButtons(state = state, onIntent = store::dispatch, screen = Screen.STAGE)
                            FocusButton(
                                FocusTarget.STAGE_REATTACH,
                                state,
                                store::dispatch,
                                { deckController.reattach() },
                            ) { Text("Re-attach the Deck") }
                        }
                        if (!state.deckAttached) {
                            Column(modifier = Modifier.weight(1f)) {
                                DeckPlaceholder(
                                    state = state,
                                    onIntent = store::dispatch,
                                    onExport = ::exportDiagnostics,
                                    onPickFolder = ::pickFolder,
                                    onRescan = ::rescanLibrary,
                                    onExportScanReport = ::saveScanReport,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean = handleKey(event) || super.dispatchKeyEvent(event)

    private fun saveScanReport() {
        exportScanReport.launch("ludoform-scan-report.txt")
    }

    private fun pickFolder() {
        chooseFolder.launch(null)
    }

    private fun rescanLibrary() {
        LibraryScans.enqueue(this)
    }

    private fun exportDiagnostics() {
        exportLog.launch("ludoform-diagnostics.txt")
    }

    private fun handleKey(event: KeyEvent): Boolean {
        if (store.state.value.buttonTesterActive) {
            if (event.action == KeyEvent.ACTION_DOWN || event.action == KeyEvent.ACTION_UP) {
                val action = if (event.action == KeyEvent.ACTION_DOWN) "DOWN" else "UP"
                val description = "$action ${KeyEvent.keyCodeToString(event.keyCode)} (${event.keyCode}), scan ${event.scanCode}"
                store.dispatch(AppIntent.RecordButtonEvent(description))
                diagnostics.record("Button tester: $description")
                if (event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                    store.dispatch(AppIntent.SetButtonTester(false))
                }
            }
            return true
        }
        val direction =
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_UP -> FocusDirection.UP
                KeyEvent.KEYCODE_DPAD_DOWN -> FocusDirection.DOWN
                KeyEvent.KEYCODE_DPAD_LEFT -> FocusDirection.LEFT
                KeyEvent.KEYCODE_DPAD_RIGHT -> FocusDirection.RIGHT
                else -> null
            }
        val select =
            event.keyCode == KeyEvent.KEYCODE_DPAD_CENTER || event.keyCode == KeyEvent.KEYCODE_BUTTON_A ||
                event.keyCode == KeyEvent.KEYCODE_ENTER
        val back = event.keyCode == KeyEvent.KEYCODE_BUTTON_B
        if (direction == null && !select && !back) return false
        if (event.action != KeyEvent.ACTION_DOWN) return true
        if (direction != null) store.dispatch(AppIntent.MoveFocus(direction))
        if (event.repeatCount == 0 && back) store.dispatch(AppIntent.Back)
        if (event.repeatCount == 0 && select) {
            when (store.state.value.focus.current) {
                FocusTarget.STAGE_HOME, FocusTarget.DECK_HOME -> store.dispatch(AppIntent.Navigate(Destination.HOME))
                FocusTarget.STAGE_LIBRARY, FocusTarget.DECK_LIBRARY -> store.dispatch(AppIntent.Navigate(Destination.LIBRARY))
                FocusTarget.STAGE_SETTINGS, FocusTarget.DECK_SETTINGS -> store.dispatch(AppIntent.Navigate(Destination.SETTINGS))
                FocusTarget.DECK_BACK -> store.dispatch(AppIntent.Back)
                FocusTarget.DECK_TESTER -> store.dispatch(AppIntent.SetButtonTester(true))
                FocusTarget.DECK_EXPORT -> exportDiagnostics()
                FocusTarget.DECK_ADD_FOLDER -> pickFolder()
                FocusTarget.DECK_RESCAN -> rescanLibrary()
                FocusTarget.DECK_SCAN_REPORT -> saveScanReport()
                FocusTarget.STAGE_REATTACH -> deckController.reattach()
            }
        }
        return true
    }

    override fun onStart() {
        super.onStart()
        deckController.start()
    }

    override fun onStop() {
        deckController.stop()
        super.onStop()
    }
}
