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
    data object Home : Destination

    data class Consoles(val platformId: String? = null) : Destination

    /** [platformId] null shows every game. */
    data class Library(val platformId: String?, val sort: LibrarySort = LibrarySort.TITLE) : Destination

    data class Details(val gameId: String) : Destination

    data object Settings : Destination
}

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
            }
    }
