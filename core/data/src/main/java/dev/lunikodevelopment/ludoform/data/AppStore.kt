package dev.lunikodevelopment.ludoform.data

import dev.lunikodevelopment.ludoform.input.FocusDirection
import dev.lunikodevelopment.ludoform.input.FocusSpot
import dev.lunikodevelopment.ludoform.input.FocusState
import dev.lunikodevelopment.ludoform.input.Screen
import dev.lunikodevelopment.ludoform.input.move
import dev.lunikodevelopment.ludoform.input.normalize
import dev.lunikodevelopment.ludoform.input.select
import dev.lunikodevelopment.ludoform.input.touchScreen
import dev.lunikodevelopment.ludoform.model.LibrarySnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

enum class LibrarySort { TITLE, RECENT }

sealed interface Destination {
    /** The sheets between Play and a running game; a successful launch closes them. */
    val isLaunchSheet: Boolean get() = this is Emulator || this is Disc || this is Screen

    data object Home : Destination

    data class Consoles(val platformId: String? = null) : Destination

    /** [platformId] null shows every game. */
    data class Library(val platformId: String?, val sort: LibrarySort = LibrarySort.TITLE) : Destination

    data class Details(val gameId: String) : Destination

    /**
     * The Emulator sheet (board 15). [choices] are prepared by the launcher from the installed apps; [current] is the
     * row that is selected when the sheet opens. [message] explains why the sheet opened instead of the game.
     */
    data class Emulator(
        val gameId: String,
        val choices: List<EmulatorChoice>,
        val current: Int = 0,
        val scope: PreferenceScope = PreferenceScope.GAME,
        val message: String? = null,
    ) : Destination

    /** Choose disc (board 14): one row per playlist entry, launched with [request]. */
    data class Disc(val gameId: String, val discs: List<String>, val request: LaunchRequest) : Destination

    /** Asks which screen the game opens on, when the saved choice is "ask". */
    data class Screen(
        val request: LaunchRequest,
        val emulatorName: String,
        val emulatorPackage: String,
        val scope: ScreenScope = ScreenScope.ONCE,
    ) : Destination {
        val gameId: String get() = request.gameId
    }

    data object Settings : Destination

    /** Settings → RomM: the server form on the Stage, its status and actions on the Deck. */
    data object Romm : Destination

    /** The download queue (board 05). [confirmCancelAll] shows the "Cancel all transfers?" dialog. */
    data class Transfers(val confirmCancelAll: Boolean = false) : Destination
}

/** How the saved RomM server answered its last health check. */
enum class RommStatus { NOT_SET_UP, NOT_CHECKED, CHECKING, CONNECTED, PROBLEM }

/** The RomM server as Settings shows it. [title] and [detail] are written for the player. */
data class RommState(
    val address: String? = null,
    val username: String = "",
    val status: RommStatus = RommStatus.NOT_SET_UP,
    val title: String = "Not set up",
    val detail: String = "Add your server to see its games here.",
    /** The library sync in words: "1,204 games · synced 10:42", "Syncing…", or why it failed. Empty before any. */
    val syncLine: String = "",
    val syncing: Boolean = false,
    val platforms: List<RommPlatformRow> = emptyList(),
) {
    val configured: Boolean get() = address != null

    /** Platforms with a Ludoform platform; only these have a Sync switch. */
    val syncablePlatforms: List<RommPlatformRow> get() = platforms.filter { it.mappedTo != null && it.romCount > 0 }
}

/** One game in the download queue as the Transfers screen shows it. */
data class DownloadRow(
    val id: Long,
    val rommId: Int,
    val gameId: String,
    val title: String,
    val platformId: String,
    val state: DownloadRowState,
    /** Already in the player's words: "62% · 6.0 MB/s", "Paused at 31%", "Waiting for Wi-Fi"… */
    val status: String,
    val percent: Int,
    val bytesLeft: Long,
)

/** What a queue row offers: Pause while it runs or waits, Resume when paused, Retry when it failed. */
enum class DownloadRowState { ACTIVE, PAUSED, FAILED }

/** The Transfers holds as the screen shows them. */
data class DownloadHolds(val wifiOnly: Boolean = true, val chargingOnly: Boolean = false, val speedLimit: Long = 0)

/** A server platform in Settings → RomM. [mappedTo] is the Ludoform platform name, null when Ludoform has none. */
data class RommPlatformRow(val id: Int, val name: String, val romCount: Int, val mappedTo: String?, val sync: Boolean)

/** A destination on the back stack with the focus it had when the user left it. */
data class NavEntry(val destination: Destination, val focus: FocusState = FocusState())

data class ScanState(
    val running: Boolean = false,
    val finishedOnce: Boolean = false,
    val status: String = "No scan yet",
    val skipped: String = "",
)

data class AppState(
    val navigation: List<NavEntry> = listOf(NavEntry(Destination.Home)),
    val focus: FocusState = FocusState(),
    /** Null until the database first answers. */
    val library: LibrarySnapshot? = null,
    val inaccessibleFolders: List<String> = emptyList(),
    val scan: ScanState = ScanState(),
    val deckAttached: Boolean = false,
    val displayDiagnostic: String = "Display discovery not started",
    val buttonTesterActive: Boolean = false,
    val recentButtonEvents: List<String> = emptyList(),
    val notice: String? = null,
    /** Where games open unless a game or emulator has its own choice (Settings → Game screen). */
    val gameScreen: ScreenSetting = ScreenSetting.TOP,
    /** Title of the game running on the bottom screen; the Deck stays hidden until the player brings it back. */
    val gameOnDeck: String? = null,
    val romm: RommState = RommState(),
    val downloads: List<DownloadRow> = emptyList(),
    val holds: DownloadHolds = DownloadHolds(),
) {
    val destination: Destination get() = navigation.last().destination
    val canGoBack: Boolean get() = navigation.size > 1
    val layout get() = Screens.layoutFor(this)
}

sealed interface AppIntent {
    data class Navigate(val destination: Destination) : AppIntent

    /** Swaps the current destination without adding a back step, as the sort chips do. */
    data class Replace(val destination: Destination) : AppIntent

    data object Back : AppIntent

    data class LibraryChanged(val snapshot: LibrarySnapshot) : AppIntent

    data class FolderAccessChanged(val inaccessible: List<String>) : AppIntent

    data class ScanChanged(val scan: ScanState) : AppIntent

    data class DisplayDiagnostic(val message: String) : AppIntent

    data class SetButtonTester(val active: Boolean) : AppIntent

    data class RecordButtonEvent(val description: String) : AppIntent

    data class MoveFocus(val direction: FocusDirection) : AppIntent

    data class SelectFocus(val spot: FocusSpot) : AppIntent

    data class TouchScreen(val screen: Screen) : AppIntent

    /** L1/R1: steps the console carousel or jumps between letters in the library. */
    data class Shoulder(val step: Int) : AppIntent

    data class DeckConnectionChanged(val attached: Boolean) : AppIntent

    data class ShowNotice(val message: String?) : AppIntent

    /** A game started: leave the Emulator and Choose disc sheets so the player returns to the game's details. */
    data object CloseLaunchSheets : AppIntent

    data class GameScreenChanged(val setting: ScreenSetting) : AppIntent

    data class GameOnDeck(val title: String?) : AppIntent

    data class RommChanged(val romm: RommState) : AppIntent

    data class DownloadsChanged(val downloads: List<DownloadRow>, val holds: DownloadHolds) : AppIntent
}

@Singleton
class AppStore
    @Inject
    constructor() {
        private val mutableState = MutableStateFlow(AppState().let { it.copy(focus = it.focus.normalize(it.layout)) })
        val state: StateFlow<AppState> = mutableState.asStateFlow()

        fun dispatch(intent: AppIntent) {
            mutableState.update { current -> reduce(current, intent).let { it.copy(focus = it.focus.normalize(it.layout)) } }
        }

        private fun reduce(
            current: AppState,
            intent: AppIntent,
        ): AppState =
            when (intent) {
                is AppIntent.Navigate ->
                    if (intent.destination == current.destination) {
                        current
                    } else {
                        val saved = current.navigation.dropLast(1) + current.navigation.last().copy(focus = current.focus)
                        current.copy(navigation = saved + NavEntry(intent.destination), focus = FocusState(), notice = null)
                    }
                is AppIntent.Replace -> current.copy(navigation = current.navigation.dropLast(1) + NavEntry(intent.destination))
                AppIntent.Back ->
                    if (current.canGoBack) {
                        val remaining = current.navigation.dropLast(1)
                        current.copy(navigation = remaining, focus = remaining.last().focus, notice = null)
                    } else {
                        current
                    }
                is AppIntent.LibraryChanged -> current.copy(library = intent.snapshot)
                is AppIntent.FolderAccessChanged -> current.copy(inaccessibleFolders = intent.inaccessible)
                is AppIntent.ScanChanged -> current.copy(scan = intent.scan)
                is AppIntent.DeckConnectionChanged -> current.copy(deckAttached = intent.attached)
                is AppIntent.MoveFocus -> current.copy(focus = current.focus.move(intent.direction, current.layout), notice = null)
                is AppIntent.SelectFocus -> current.copy(focus = current.focus.select(intent.spot, current.layout))
                is AppIntent.TouchScreen -> current.copy(focus = current.focus.touchScreen(intent.screen, current.layout))
                is AppIntent.Shoulder -> Screens.shoulder(current, intent.step)
                is AppIntent.DisplayDiagnostic -> current.copy(displayDiagnostic = intent.message)
                is AppIntent.SetButtonTester -> current.copy(buttonTesterActive = intent.active)
                is AppIntent.RecordButtonEvent ->
                    current.copy(recentButtonEvents = (current.recentButtonEvents + intent.description).takeLast(8))
                is AppIntent.ShowNotice -> current.copy(notice = intent.message)
                is AppIntent.GameScreenChanged -> current.copy(gameScreen = intent.setting)
                is AppIntent.GameOnDeck -> current.copy(gameOnDeck = intent.title)
                is AppIntent.RommChanged -> current.copy(romm = intent.romm)
                is AppIntent.DownloadsChanged -> current.copy(downloads = intent.downloads, holds = intent.holds)
                AppIntent.CloseLaunchSheets -> {
                    val remaining =
                        current.navigation.dropLastWhile { it.destination.isLaunchSheet }
                    if (remaining.size == current.navigation.size || remaining.isEmpty()) {
                        current
                    } else {
                        current.copy(navigation = remaining, focus = remaining.last().focus, notice = null)
                    }
                }
            }
    }
