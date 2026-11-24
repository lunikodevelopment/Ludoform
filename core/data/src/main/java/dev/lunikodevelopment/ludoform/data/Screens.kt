package dev.lunikodevelopment.ludoform.data

import dev.lunikodevelopment.ludoform.input.FocusLayout
import dev.lunikodevelopment.ludoform.input.FocusSpot
import dev.lunikodevelopment.ludoform.input.FocusZone
import dev.lunikodevelopment.ludoform.input.Screen
import dev.lunikodevelopment.ludoform.input.select
import dev.lunikodevelopment.ludoform.model.GameSummary
import dev.lunikodevelopment.ludoform.model.LibrarySnapshot
import dev.lunikodevelopment.ludoform.model.PlatformCatalog
import dev.lunikodevelopment.ludoform.model.PlatformSummary
import java.util.Locale

/** Zone ids shared by the focus map and the composables that draw each zone. */
object Zones {
    const val TABS = "stage.tabs"
    const val HOME_PLAY = "home.play"
    const val HOME_NOTICE = "home.notice"
    const val HOME_GAMES = "home.games"
    const val HOME_CONSOLES = "home.consoles"
    const val HOME_RECENT = "home.recent"
    const val HOME_APPS = "home.apps"
    const val STATE_ACTIONS = "state.actions"
    const val PICKER_CONSOLES = "picker.consoles"
    const val PICKER_GAMES = "picker.games"
    const val PICKER_ACTIONS = "picker.actions"
    const val LIBRARY_SORT = "library.sort"
    const val LIBRARY_GRID = "library.grid"
    const val LIBRARY_OPEN = "library.open"
    const val DETAILS_PLAY = "details.play"
    const val DETAILS_ACTIONS = "details.actions"
    const val SETTINGS_LIST = "settings.list"
    const val EMULATOR_SCOPE = "emulator.scope"
    const val EMULATOR_LIST = "emulator.list"
    const val EMULATOR_ACTIONS = "emulator.actions"
    const val DISC_LIST = "disc.list"
    const val DISC_ACTIONS = "disc.actions"
    const val SCREEN_SCOPE = "screen.scope"
    const val SCREEN_LIST = "screen.list"
    const val SCREEN_ACTIONS = "screen.actions"
    const val PLAYING_ACTIONS = "playing.actions"
}

/** The screen a game opens on. */
enum class GameScreen { TOP, BOTTOM }

/** The global Game screen setting; ASK opens the screen sheet before each launch. */
enum class ScreenSetting { TOP, BOTTOM, ASK }

/** How long a choice from the screen sheet is kept: this launch only, for the game, or for its emulator. */
enum class ScreenScope { ONCE, GAME, EMULATOR }

/** A request to start a game. Null fields mean "use the saved preference" and "ask when there are several discs". */
data class LaunchRequest(
    val gameId: String,
    val packageName: String? = null,
    val disc: Int? = null,
    val screen: GameScreen? = null,
)

/** Whether a choice made in the Emulator sheet is saved for this game or for every game of its platform. */
enum class PreferenceScope { GAME, PLATFORM }

/**
 * One row of the Emulator sheet. [packageName] is the exact installed package, or the package to look up in a store
 * when [installed] is false. [detail] is the row's second line and [method] how the game is handed over.
 */
data class EmulatorChoice(
    val packageName: String,
    val name: String,
    val detail: String,
    val method: String,
    val installed: Boolean,
)

/** What the Home and empty-library screens show. */
enum class LibraryStatus { LOADING, NO_FOLDERS, SCANNING, NO_GAMES, NO_ACCESS, READY }

enum class StateAction { ADD_FOLDER, CHOOSE_AGAIN, RESCAN, SAVE_REPORT, SETTINGS }

enum class HomeApp { SETTINGS, FILES, NETWORK }

enum class StageTab { HOME, CONSOLES, LIBRARY, SETTINGS }

enum class SettingsItem { GAME_SCREEN, ADD_FOLDER, RESCAN, SAVE_REPORT, BUTTON_TESTER, EXPORT_LOG, REATTACH }

enum class DetailsAction { BACK, MORE_FROM_PLATFORM, RESCAN }

sealed interface UiAction {
    data class Navigate(val destination: Destination) : UiAction

    data class Replace(val destination: Destination) : UiAction

    data object Back : UiAction

    data object PickFolder : UiAction

    data object Rescan : UiAction

    data object SaveScanReport : UiAction

    data object ButtonTester : UiAction

    data object ExportLog : UiAction

    data object Reattach : UiAction

    data object OpenFiles : UiAction

    data object OpenNetwork : UiAction

    data class Play(val request: LaunchRequest) : UiAction

    /** Opens the Emulator sheet for a game. */
    data class PlayWith(val gameId: String) : UiAction

    /** Saves [choice] for the game or its platform, then starts the game with it. */
    data class UseEmulator(val gameId: String, val choice: EmulatorChoice, val scope: PreferenceScope) : UiAction

    /** Opens the store page of an emulator that is not installed. */
    data class GetEmulator(val packageName: String) : UiAction

    /** Starts [request] on [screen] and, unless [scope] is ONCE, remembers the screen for the game or emulator. */
    data class PlayOnScreen(
        val request: LaunchRequest,
        val screen: GameScreen,
        val scope: ScreenScope,
        val emulatorPackage: String,
    ) : UiAction

    /** Settings → Game screen: Top, then Bottom, then Ask. */
    data object CycleGameScreen : UiAction

    /** Shows the launcher on the bottom screen again while a game runs there. */
    data object ReleaseDeck : UiAction

    data object None : UiAction
}

data class HomeModel(
    val featured: List<GameSummary>,
    val consoles: List<PlatformSummary>,
    val recent: List<GameSummary>,
    val apps: List<HomeApp> = HomeApp.entries,
)

data class PickerModel(val platforms: List<PlatformSummary>, val selected: PlatformSummary?, val games: List<GameSummary>)

data class LibraryModel(val platform: PlatformSummary?, val games: List<GameSummary>, val sort: LibrarySort) {
    /** First grid index of every initial letter, for the letter rail and L1/R1 jumps. */
    val letters: List<Pair<Char, Int>> by lazy {
        if (sort != LibrarySort.TITLE) {
            emptyList()
        } else {
            games.mapIndexed { index, game -> initial(game.title) to index }.distinctBy { it.first }
        }
    }

    companion object {
        fun initial(title: String): Char =
            title.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.let { if (it.isDigit()) '#' else it } ?: '#'
    }
}

object Screens {
    const val HOME_ROW = 3
    const val GRID_COLUMNS = 4
    val stageTabs = StageTab.entries
    val settingsItems = SettingsItem.entries
    val detailsActions = DetailsAction.entries

    fun status(state: AppState): LibraryStatus {
        val library = state.library ?: return LibraryStatus.LOADING
        return when {
            !library.isEmpty -> LibraryStatus.READY
            library.folders.isEmpty() -> LibraryStatus.NO_FOLDERS
            library.folders.all { it in state.inaccessibleFolders } -> LibraryStatus.NO_ACCESS
            state.scan.running || !state.scan.finishedOnce -> LibraryStatus.SCANNING
            else -> LibraryStatus.NO_GAMES
        }
    }

    fun stateActions(status: LibraryStatus): List<StateAction> =
        when (status) {
            LibraryStatus.NO_FOLDERS -> listOf(StateAction.ADD_FOLDER, StateAction.SETTINGS)
            LibraryStatus.NO_ACCESS -> listOf(StateAction.CHOOSE_AGAIN, StateAction.SETTINGS)
            LibraryStatus.NO_GAMES -> listOf(StateAction.ADD_FOLDER, StateAction.RESCAN, StateAction.SAVE_REPORT)
            LibraryStatus.SCANNING -> listOf(StateAction.SETTINGS)
            LibraryStatus.LOADING, LibraryStatus.READY -> emptyList()
        }

    fun home(library: LibrarySnapshot): HomeModel {
        val byTitle = compareBy<GameSummary, String>(String.CASE_INSENSITIVE_ORDER) { it.title }
        val consoles = library.platforms.sortedWith(compareByDescending<PlatformSummary> { it.gameCount }.thenBy { it.name })
        // Without play history yet, feature one game from each of the largest platforms, then fill alphabetically.
        val firstPerPlatform = consoles.mapNotNull { platform -> library.gamesFor(platform.id).minWithOrNull(byTitle) }
        val featured = (firstPerPlatform + library.games.sortedWith(byTitle)).distinctBy { it.id }.take(HOME_ROW)
        val recent = library.games.sortedWith(compareByDescending<GameSummary> { it.addedAt }.then(byTitle)).take(HOME_ROW)
        return HomeModel(featured, consoles.take(HOME_ROW), recent)
    }

    fun picker(
        library: LibrarySnapshot,
        selectedIndex: Int,
    ): PickerModel {
        val platforms =
            library.platforms.sortedWith(
                compareBy<PlatformSummary> { PlatformCatalog.info(it.id, it.name).year ?: Int.MAX_VALUE }.thenBy { it.name },
            )
        val selected = platforms.getOrNull(selectedIndex.coerceIn(0, maxOf(platforms.size - 1, 0)))
        val games =
            selected?.let { platform ->
                library.gamesFor(platform.id).sortedWith(compareByDescending<GameSummary> { it.addedAt }.thenBy { it.title }).take(HOME_ROW)
            }.orEmpty()
        return PickerModel(platforms, selected, games)
    }

    fun library(
        library: LibrarySnapshot,
        destination: Destination.Library,
    ): LibraryModel {
        val games = library.gamesFor(destination.platformId)
        val sorted =
            when (destination.sort) {
                LibrarySort.TITLE -> games.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title.lowercase(Locale.ROOT) })
                LibrarySort.RECENT -> games.sortedWith(compareByDescending<GameSummary> { it.addedAt }.thenBy { it.title })
            }
        return LibraryModel(destination.platformId?.let { library.platform(it) }, sorted, destination.sort)
    }

    fun layoutFor(state: AppState): FocusLayout {
        if (state.gameOnDeck != null) {
            return FocusLayout(listOf(FocusZone(Zones.PLAYING_ACTIONS, Screen.STAGE, 1)), emptyList(), FocusSpot(Zones.PLAYING_ACTIONS, 0))
        }
        val library = state.library
        val tabs = FocusZone(Zones.TABS, Screen.STAGE, stageTabs.size)
        return when (val destination = state.destination) {
            Destination.Home -> {
                val status = status(state)
                if (status != LibraryStatus.READY || library == null) {
                    FocusLayout(emptyList(), listOf(FocusZone(Zones.STATE_ACTIONS, Screen.DECK, stateActions(status).size)))
                } else {
                    val home = home(library)
                    FocusLayout(
                        stage = listOf(FocusZone(Zones.HOME_PLAY, Screen.STAGE, if (home.featured.isEmpty()) 0 else 1)),
                        deck =
                            listOf(
                                FocusZone(Zones.HOME_NOTICE, Screen.DECK, if (state.inaccessibleFolders.isEmpty()) 0 else 1),
                                FocusZone(Zones.HOME_GAMES, Screen.DECK, home.featured.size),
                                FocusZone(Zones.HOME_CONSOLES, Screen.DECK, home.consoles.size + 1),
                                FocusZone(Zones.HOME_RECENT, Screen.DECK, home.recent.size + 1),
                                FocusZone(Zones.HOME_APPS, Screen.DECK, HomeApp.entries.size),
                            ),
                        initial = FocusSpot(Zones.HOME_GAMES, 0),
                    )
                }
            }
            is Destination.Consoles -> {
                val platforms = library?.let { picker(it, 0).platforms }.orEmpty()
                val requested = platforms.indexOfFirst { it.id == destination.platformId }.coerceAtLeast(0)
                val selected = picker(library ?: LibrarySnapshot.EMPTY, state.focus.indexIn(Zones.PICKER_CONSOLES, requested))
                FocusLayout(
                    stage = emptyList(),
                    deck =
                        listOf(
                            FocusZone(Zones.PICKER_CONSOLES, Screen.DECK, platforms.size, wrap = true),
                            FocusZone(Zones.PICKER_GAMES, Screen.DECK, selected.games.size),
                            FocusZone(Zones.PICKER_ACTIONS, Screen.DECK, if (platforms.isEmpty()) 1 else 2),
                        ),
                    initial = if (platforms.isEmpty()) FocusSpot(Zones.PICKER_ACTIONS, 0) else FocusSpot(Zones.PICKER_CONSOLES, requested),
                )
            }
            is Destination.Library -> {
                val model = library(library ?: LibrarySnapshot.EMPTY, destination)
                FocusLayout(
                    stage = listOf(tabs),
                    deck =
                        listOf(
                            FocusZone(Zones.LIBRARY_SORT, Screen.DECK, LibrarySort.entries.size),
                            FocusZone(Zones.LIBRARY_GRID, Screen.DECK, model.games.size, columns = GRID_COLUMNS),
                            FocusZone(Zones.LIBRARY_OPEN, Screen.DECK, if (model.games.isEmpty()) 0 else 1),
                        ),
                    initial = FocusSpot(Zones.LIBRARY_GRID, 0).takeIf { model.games.isNotEmpty() },
                )
            }
            is Destination.Details -> {
                val exists = library?.game(destination.gameId) != null
                FocusLayout(
                    stage = listOf(tabs),
                    deck =
                        listOf(
                            FocusZone(Zones.DETAILS_PLAY, Screen.DECK, if (exists) 2 else 0),
                            FocusZone(Zones.DETAILS_ACTIONS, Screen.DECK, detailsActions.size),
                        ),
                    initial = FocusSpot(if (exists) Zones.DETAILS_PLAY else Zones.DETAILS_ACTIONS, 0),
                )
            }
            is Destination.Emulator ->
                FocusLayout(
                    stage = emptyList(),
                    deck =
                        listOf(
                            FocusZone(Zones.EMULATOR_SCOPE, Screen.DECK, PreferenceScope.entries.size),
                            FocusZone(Zones.EMULATOR_LIST, Screen.DECK, destination.choices.size, columns = 1),
                            FocusZone(Zones.EMULATOR_ACTIONS, Screen.DECK, 2),
                        ),
                    initial =
                        if (destination.choices.isEmpty()) {
                            FocusSpot(Zones.EMULATOR_ACTIONS, 0)
                        } else {
                            FocusSpot(Zones.EMULATOR_LIST, destination.current.coerceIn(0, destination.choices.lastIndex))
                        },
                )
            is Destination.Disc ->
                FocusLayout(
                    stage = emptyList(),
                    deck =
                        listOf(
                            FocusZone(Zones.DISC_LIST, Screen.DECK, destination.discs.size, columns = 1),
                            FocusZone(Zones.DISC_ACTIONS, Screen.DECK, if (destination.discs.isEmpty()) 1 else 2),
                        ),
                    initial = FocusSpot(if (destination.discs.isEmpty()) Zones.DISC_ACTIONS else Zones.DISC_LIST, 0),
                )
            is Destination.Screen ->
                FocusLayout(
                    stage = emptyList(),
                    deck =
                        listOf(
                            FocusZone(Zones.SCREEN_SCOPE, Screen.DECK, ScreenScope.entries.size),
                            FocusZone(Zones.SCREEN_LIST, Screen.DECK, GameScreen.entries.size, columns = 1),
                            FocusZone(Zones.SCREEN_ACTIONS, Screen.DECK, 2),
                        ),
                    initial = FocusSpot(Zones.SCREEN_LIST, 0),
                )
            Destination.Settings ->
                FocusLayout(
                    stage = listOf(tabs),
                    deck = listOf(FocusZone(Zones.SETTINGS_LIST, Screen.DECK, settingsItems.size, columns = 2)),
                )
        }
    }

    fun shoulder(
        state: AppState,
        step: Int,
    ): AppState {
        val layout = state.layout
        return when (val destination = state.destination) {
            is Destination.Consoles -> {
                val size = layout.zone(Zones.PICKER_CONSOLES)?.size ?: 0
                if (size == 0) return state
                val index = Math.floorMod(state.focus.indexIn(Zones.PICKER_CONSOLES) + step, size)
                state.copy(focus = state.focus.select(FocusSpot(Zones.PICKER_CONSOLES, index), layout))
            }
            is Destination.Library -> {
                val letters = library(state.library ?: return state, destination).letters
                if (letters.isEmpty()) return state
                val index = state.focus.indexIn(Zones.LIBRARY_GRID)
                val currentLetter = letters.indexOfLast { it.second <= index }.coerceAtLeast(0)
                val target = letters[(currentLetter + step).coerceIn(0, letters.lastIndex)].second
                state.copy(focus = state.focus.select(FocusSpot(Zones.LIBRARY_GRID, target), layout))
            }
            else -> state
        }
    }

    /** The game the screens are centred on, or null when focus is elsewhere. */
    fun focusedGame(state: AppState): GameSummary? {
        val library = state.library ?: return null
        return when (val destination = state.destination) {
            Destination.Home -> {
                val home = home(library)
                val zone = state.focus.current?.zone
                when (zone) {
                    Zones.HOME_RECENT -> home.recent.getOrNull(state.focus.indexIn(Zones.HOME_RECENT))
                    else -> home.featured.getOrNull(state.focus.indexIn(Zones.HOME_GAMES))
                } ?: home.featured.firstOrNull()
            }
            is Destination.Consoles ->
                picker(
                    library,
                    state.focus.indexIn(Zones.PICKER_CONSOLES),
                ).games.getOrNull(state.focus.indexIn(Zones.PICKER_GAMES))
            is Destination.Library -> library(library, destination).games.getOrNull(state.focus.indexIn(Zones.LIBRARY_GRID))
            is Destination.Details -> library.game(destination.gameId)
            is Destination.Emulator -> library.game(destination.gameId)
            is Destination.Disc -> library.game(destination.gameId)
            is Destination.Screen -> library.game(destination.gameId)
            Destination.Settings -> null
        }
    }

    /** The Emulator sheet's selected row: the focused one, or the last one focus left. */
    fun selectedChoice(
        state: AppState,
        sheet: Destination.Emulator,
    ): EmulatorChoice? = sheet.choices.getOrNull(state.focus.indexIn(Zones.EMULATOR_LIST, sheet.current))

    fun selectedDisc(
        state: AppState,
        sheet: Destination.Disc,
    ): Int = state.focus.indexIn(Zones.DISC_LIST).coerceIn(0, maxOf(sheet.discs.size - 1, 0))

    fun selectedScreen(state: AppState): GameScreen =
        GameScreen.entries[state.focus.indexIn(Zones.SCREEN_LIST).coerceIn(0, GameScreen.entries.lastIndex)]

    fun tabFor(destination: Destination): StageTab =
        when (destination) {
            Destination.Home -> StageTab.HOME
            is Destination.Consoles -> StageTab.CONSOLES
            is Destination.Library, is Destination.Details, is Destination.Emulator, is Destination.Disc, is Destination.Screen ->
                StageTab.LIBRARY
            Destination.Settings -> StageTab.SETTINGS
        }

    fun actionFor(
        state: AppState,
        spot: FocusSpot,
    ): UiAction {
        val library = state.library ?: LibrarySnapshot.EMPTY
        val destination = state.destination
        return when (spot.zone) {
            Zones.TABS ->
                when (stageTabs.getOrNull(spot.index)) {
                    StageTab.HOME -> UiAction.Navigate(Destination.Home)
                    StageTab.CONSOLES -> UiAction.Navigate(Destination.Consoles())
                    StageTab.LIBRARY -> UiAction.Navigate(Destination.Library(null))
                    StageTab.SETTINGS -> UiAction.Navigate(Destination.Settings)
                    null -> UiAction.None
                }
            Zones.HOME_PLAY -> focusedGame(state)?.let { UiAction.Navigate(Destination.Details(it.id)) } ?: UiAction.None
            Zones.HOME_NOTICE -> UiAction.PickFolder
            Zones.HOME_GAMES ->
                home(library).featured.getOrNull(spot.index)?.let { UiAction.Navigate(Destination.Details(it.id)) } ?: UiAction.None
            Zones.HOME_CONSOLES -> {
                val consoles = home(library).consoles
                consoles.getOrNull(spot.index)?.let { UiAction.Navigate(Destination.Library(it.id)) }
                    ?: UiAction.Navigate(Destination.Consoles())
            }
            Zones.HOME_RECENT ->
                home(library).recent.getOrNull(spot.index)?.let { UiAction.Navigate(Destination.Details(it.id)) }
                    ?: UiAction.Navigate(Destination.Library(null, LibrarySort.RECENT))
            Zones.HOME_APPS ->
                when (HomeApp.entries.getOrNull(spot.index)) {
                    HomeApp.SETTINGS -> UiAction.Navigate(Destination.Settings)
                    HomeApp.FILES -> UiAction.OpenFiles
                    HomeApp.NETWORK -> UiAction.OpenNetwork
                    null -> UiAction.None
                }
            Zones.STATE_ACTIONS ->
                when (stateActions(status(state)).getOrNull(spot.index)) {
                    StateAction.ADD_FOLDER, StateAction.CHOOSE_AGAIN -> UiAction.PickFolder
                    StateAction.RESCAN -> UiAction.Rescan
                    StateAction.SAVE_REPORT -> UiAction.SaveScanReport
                    StateAction.SETTINGS -> UiAction.Navigate(Destination.Settings)
                    null -> UiAction.None
                }
            Zones.PICKER_CONSOLES, Zones.PICKER_ACTIONS -> {
                val selected = picker(library, state.focus.indexIn(Zones.PICKER_CONSOLES)).selected
                if (spot.zone == Zones.PICKER_ACTIONS && (spot.index == 1 || selected == null)) {
                    UiAction.Back
                } else {
                    selected?.let { UiAction.Navigate(Destination.Library(it.id)) } ?: UiAction.None
                }
            }
            Zones.PICKER_GAMES ->
                picker(library, state.focus.indexIn(Zones.PICKER_CONSOLES)).games.getOrNull(spot.index)
                    ?.let { UiAction.Navigate(Destination.Details(it.id)) } ?: UiAction.None
            Zones.LIBRARY_SORT ->
                (destination as? Destination.Library)
                    ?.let { UiAction.Replace(it.copy(sort = LibrarySort.entries[spot.index])) } ?: UiAction.None
            Zones.LIBRARY_GRID, Zones.LIBRARY_OPEN -> {
                val model = (destination as? Destination.Library)?.let { library(library, it) } ?: return UiAction.None
                val index = if (spot.zone == Zones.LIBRARY_GRID) spot.index else state.focus.indexIn(Zones.LIBRARY_GRID)
                model.games.getOrNull(index)?.let { UiAction.Navigate(Destination.Details(it.id)) } ?: UiAction.None
            }
            Zones.DETAILS_PLAY -> {
                val details = destination as? Destination.Details ?: return UiAction.None
                if (spot.index == 0) UiAction.Play(LaunchRequest(details.gameId)) else UiAction.PlayWith(details.gameId)
            }
            Zones.EMULATOR_SCOPE ->
                (destination as? Destination.Emulator)
                    ?.let { UiAction.Replace(it.copy(scope = PreferenceScope.entries[spot.index])) } ?: UiAction.None
            Zones.EMULATOR_LIST, Zones.EMULATOR_ACTIONS -> {
                val sheet = destination as? Destination.Emulator ?: return UiAction.None
                if (spot.zone == Zones.EMULATOR_ACTIONS && spot.index == 0) return UiAction.Back
                val choice = selectedChoice(state, sheet) ?: return UiAction.None
                if (choice.installed) UiAction.UseEmulator(sheet.gameId, choice, sheet.scope) else UiAction.GetEmulator(choice.packageName)
            }
            Zones.DISC_LIST, Zones.DISC_ACTIONS -> {
                val sheet = destination as? Destination.Disc ?: return UiAction.None
                if (spot.zone == Zones.DISC_ACTIONS && (spot.index == 0 || sheet.discs.isEmpty())) return UiAction.Back
                val disc = if (spot.zone == Zones.DISC_LIST) spot.index else selectedDisc(state, sheet)
                UiAction.Play(sheet.request.copy(disc = disc))
            }
            Zones.DETAILS_ACTIONS -> {
                val game = (destination as? Destination.Details)?.let { library.game(it.gameId) }
                when (detailsActions.getOrNull(spot.index)) {
                    DetailsAction.BACK -> UiAction.Back
                    DetailsAction.MORE_FROM_PLATFORM -> UiAction.Navigate(Destination.Library(game?.platformId))
                    DetailsAction.RESCAN -> UiAction.Rescan
                    null -> UiAction.None
                }
            }
            Zones.SCREEN_SCOPE ->
                (destination as? Destination.Screen)
                    ?.let { UiAction.Replace(it.copy(scope = ScreenScope.entries[spot.index])) } ?: UiAction.None
            Zones.SCREEN_LIST, Zones.SCREEN_ACTIONS -> {
                val sheet = destination as? Destination.Screen ?: return UiAction.None
                if (spot.zone == Zones.SCREEN_ACTIONS && spot.index == 0) return UiAction.Back
                val screen = if (spot.zone == Zones.SCREEN_LIST) GameScreen.entries[spot.index] else selectedScreen(state)
                UiAction.PlayOnScreen(sheet.request, screen, sheet.scope, sheet.emulatorPackage)
            }
            Zones.PLAYING_ACTIONS -> UiAction.ReleaseDeck
            Zones.SETTINGS_LIST ->
                when (settingsItems.getOrNull(spot.index)) {
                    SettingsItem.GAME_SCREEN -> UiAction.CycleGameScreen
                    SettingsItem.ADD_FOLDER -> UiAction.PickFolder
                    SettingsItem.RESCAN -> UiAction.Rescan
                    SettingsItem.SAVE_REPORT -> UiAction.SaveScanReport
                    SettingsItem.BUTTON_TESTER -> UiAction.ButtonTester
                    SettingsItem.EXPORT_LOG -> UiAction.ExportLog
                    SettingsItem.REATTACH -> UiAction.Reattach
                    null -> UiAction.None
                }
            else -> UiAction.None
        }
    }
}
