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
    const val ROMM_FIELDS = "romm.fields"
    const val ROMM_ACTIONS = "romm.actions"
    const val ROMM_PLATFORMS = "romm.platforms"
    const val TRANSFER_TOP = "transfer.top"
    const val TRANSFER_ROWS = "transfer.rows"
    const val TRANSFER_HOLDS = "transfer.holds"
    const val TRANSFER_CONFIRM = "transfer.confirm"
    const val SAVE_ACTIONS = "save.actions"
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
    /** The save was synced (or a conflict settled) just before this launch. */
    val savesChecked: Boolean = false,
)

/** How the player settles a save conflict. */
enum class SaveResolution { DEVICE, SERVER, BOTH }

/** The two saves of a conflict as the Save conflict screen compares them (board 13). Times are epoch milliseconds. */
data class SaveConflictInfo(
    val deviceAt: Long?,
    val deviceSize: Long?,
    val serverAt: Long?,
    val serverSize: Long?,
    val serverFrom: String?,
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

enum class HomeApp { SETTINGS, TRANSFERS, FILES, NETWORK }

/** The Transfers screen's hold chips. */
enum class TransferHold { WIFI, CHARGING, SPEED }

enum class StageTab { HOME, CONSOLES, LIBRARY, SETTINGS }

enum class SettingsItem { ROMM, GAME_SCREEN, ADD_FOLDER, RESCAN, SAVE_REPORT, BUTTON_TESTER, EXPORT_LOG, REATTACH }

enum class DetailsAction { BACK, MORE_FROM_PLATFORM, RESCAN }

/** The RomM form's fields on the Stage, top to bottom. */
enum class RommField { ADDRESS, USERNAME, PASSWORD }

/** The RomM screen's Deck buttons; Remove only exists while a server is saved (D21: no dead controls). */
enum class RommAction { SAVE, SYNC, REMOVE, BACK }

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

    /** Opens Settings → RomM with the form filled from the saved server. */
    data object OpenRomm : UiAction

    /** Puts the text cursor in a RomM field and shows the keyboard. */
    data class EditRommField(val field: RommField) : UiAction

    /** Saves the RomM form and runs the health check. */
    data object SaveRomm : UiAction

    data object RemoveRomm : UiAction

    /** Fetches the RomM library now. */
    data object SyncRomm : UiAction

    /** Turns a server platform's Sync switch (D30: "don't sync" keeps its games out of the library). */
    data class ToggleRommPlatform(val id: Int) : UiAction

    /** Queues a remote-only game for download, asking for its platform's folder the first time. */
    data class Download(val gameId: String) : UiAction

    /** Pause, Resume or Retry, by the row's state. */
    data class TransferPrimary(val id: Long) : UiAction

    data class CancelTransfer(val id: Long) : UiAction

    data object CancelAllTransfers : UiAction

    data class ToggleHold(val hold: TransferHold) : UiAction

    /** Settles a save conflict, then starts [request]. */
    data class ResolveSave(val request: LaunchRequest, val resolution: SaveResolution) : UiAction

    data object None : UiAction
}

data class HomeModel(
    val featured: List<GameSummary>,
    val consoles: List<PlatformSummary>,
    val recent: List<GameSummary>,
    val apps: List<HomeApp> = HomeApp.entries,
    /** True once a game has been played: the first rail becomes "Continue playing". */
    val continuing: Boolean = false,
)

data class PickerModel(
    val platforms: List<PlatformSummary>,
    val selected: PlatformSummary?,
    val games: List<GameSummary>,
    /** The games rail shows "Recently played" once a game of the platform was played, otherwise "Recently added". */
    val recentlyPlayed: Boolean = false,
    val playTimeMillis: Long = 0,
    val lastPlayedAt: Long? = null,
)

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

    fun rommActions(state: AppState): List<RommAction> =
        when {
            state.romm.status == RommStatus.CONNECTED -> RommAction.entries
            state.romm.configured -> listOf(RommAction.SAVE, RommAction.REMOVE, RommAction.BACK)
            else -> listOf(RommAction.SAVE, RommAction.BACK)
        }

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
        // Played games come first, most recent first. The rest of the row features one game from each of the largest
        // platforms, then fills alphabetically.
        // Games on the device come before games only on the RomM server, which fill the rows when there are too few.
        val onDevice = compareBy<GameSummary> { it.remoteOnly }
        val played = playedFirst(library.games.filter { it.lastPlayedAt != null })
        val firstPerPlatform = consoles.mapNotNull { platform -> library.gamesFor(platform.id).minWithOrNull(onDevice.then(byTitle)) }
        val featured =
            (played + firstPerPlatform.sortedWith(onDevice) + library.games.sortedWith(onDevice.then(byTitle)))
                .distinctBy { it.id }.take(HOME_ROW)
        val recent = library.games.sortedWith(onDevice.thenByDescending { it.addedAt }.then(byTitle)).take(HOME_ROW)
        return HomeModel(featured, consoles.take(HOME_ROW), recent, continuing = played.isNotEmpty())
    }

    /** Most recently played first; unplayed games after them, newest first. */
    private fun playedFirst(games: List<GameSummary>): List<GameSummary> =
        games.sortedWith(
            compareByDescending<GameSummary> { it.lastPlayedAt ?: Long.MIN_VALUE }.thenByDescending { it.addedAt }.thenBy { it.title },
        )

    fun picker(
        library: LibrarySnapshot,
        selectedIndex: Int,
    ): PickerModel {
        val platforms =
            library.platforms.sortedWith(
                compareBy<PlatformSummary> { PlatformCatalog.info(it.id, it.name).year ?: Int.MAX_VALUE }.thenBy { it.name },
            )
        val selected = platforms.getOrNull(selectedIndex.coerceIn(0, maxOf(platforms.size - 1, 0)))
        val platformGames = selected?.let { library.gamesFor(it.id) }.orEmpty()
        val played = platformGames.any { it.lastPlayedAt != null }
        val games =
            if (played) {
                playedFirst(platformGames)
            } else {
                platformGames.sortedWith(compareBy<GameSummary> { it.remoteOnly }.thenByDescending { it.addedAt }.thenBy { it.title })
            }.take(HOME_ROW)
        return PickerModel(
            platforms,
            selected,
            games,
            recentlyPlayed = played,
            playTimeMillis = platformGames.sumOf { it.playTimeMillis },
            lastPlayedAt = platformGames.mapNotNull { it.lastPlayedAt }.maxOrNull(),
        )
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
                val game = library?.game(destination.gameId)
                val exists = game != null
                FocusLayout(
                    stage = listOf(tabs),
                    deck =
                        listOf(
                            // Play and Play with…, or a single Download for a game only on the RomM server.
                            FocusZone(
                                Zones.DETAILS_PLAY,
                                Screen.DECK,
                                if (game == null) {
                                    0
                                } else if (game.remoteOnly) {
                                    1
                                } else {
                                    2
                                },
                            ),
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
            is Destination.SaveConflict ->
                FocusLayout(
                    emptyList(),
                    listOf(FocusZone(Zones.SAVE_ACTIONS, Screen.DECK, 4, columns = 2)),
                    FocusSpot(Zones.SAVE_ACTIONS, 0),
                )
            is Destination.Transfers ->
                if (destination.confirmCancelAll) {
                    FocusLayout(
                        emptyList(),
                        listOf(FocusZone(Zones.TRANSFER_CONFIRM, Screen.DECK, 2)),
                        FocusSpot(Zones.TRANSFER_CONFIRM, 0),
                    )
                } else {
                    FocusLayout(
                        stage = listOf(tabs),
                        deck =
                            listOf(
                                FocusZone(Zones.TRANSFER_TOP, Screen.DECK, if (state.downloads.isEmpty()) 0 else 1),
                                FocusZone(Zones.TRANSFER_ROWS, Screen.DECK, state.downloads.size * 2, columns = 2),
                                FocusZone(Zones.TRANSFER_HOLDS, Screen.DECK, TransferHold.entries.size),
                            ),
                        initial =
                            if (state.downloads.isEmpty()) FocusSpot(Zones.TRANSFER_HOLDS, 0) else FocusSpot(Zones.TRANSFER_ROWS, 0),
                    )
                }
            Destination.Romm ->
                FocusLayout(
                    stage = listOf(tabs, FocusZone(Zones.ROMM_FIELDS, Screen.STAGE, RommField.entries.size, columns = 1)),
                    deck =
                        listOf(
                            FocusZone(Zones.ROMM_PLATFORMS, Screen.DECK, state.romm.syncablePlatforms.size, columns = 2),
                            FocusZone(Zones.ROMM_ACTIONS, Screen.DECK, rommActions(state).size),
                        ),
                    initial = if (state.romm.configured) FocusSpot(Zones.ROMM_ACTIONS, 0) else FocusSpot(Zones.ROMM_FIELDS, 0),
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
            Destination.Settings, Destination.Romm, is Destination.Transfers -> null
            is Destination.SaveConflict -> library.game(destination.request.gameId)
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
            is Destination.Library, is Destination.Details, is Destination.Emulator, is Destination.Disc, is Destination.Screen,
            is Destination.SaveConflict,
            ->
                StageTab.LIBRARY
            Destination.Settings, Destination.Romm -> StageTab.SETTINGS
            is Destination.Transfers -> StageTab.HOME
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
                    HomeApp.TRANSFERS -> UiAction.Navigate(Destination.Transfers())
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
                if (library.game(details.gameId)?.remoteOnly == true) {
                    // Already queued: the button opens Transfers instead of queueing it twice.
                    return if (state.downloads.any { it.gameId == details.gameId }) {
                        UiAction.Navigate(Destination.Transfers())
                    } else {
                        UiAction.Download(details.gameId)
                    }
                }
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
                    SettingsItem.ROMM -> UiAction.OpenRomm
                    SettingsItem.GAME_SCREEN -> UiAction.CycleGameScreen
                    SettingsItem.ADD_FOLDER -> UiAction.PickFolder
                    SettingsItem.RESCAN -> UiAction.Rescan
                    SettingsItem.SAVE_REPORT -> UiAction.SaveScanReport
                    SettingsItem.BUTTON_TESTER -> UiAction.ButtonTester
                    SettingsItem.EXPORT_LOG -> UiAction.ExportLog
                    SettingsItem.REATTACH -> UiAction.Reattach
                    null -> UiAction.None
                }
            Zones.ROMM_FIELDS -> RommField.entries.getOrNull(spot.index)?.let { UiAction.EditRommField(it) } ?: UiAction.None
            Zones.SAVE_ACTIONS -> {
                val sheet = destination as? Destination.SaveConflict ?: return UiAction.None
                when (spot.index) {
                    0 -> UiAction.ResolveSave(sheet.request, SaveResolution.DEVICE)
                    1 -> UiAction.ResolveSave(sheet.request, SaveResolution.SERVER)
                    2 -> UiAction.ResolveSave(sheet.request, SaveResolution.BOTH)
                    else -> UiAction.Back
                }
            }
            Zones.TRANSFER_TOP -> UiAction.Replace(Destination.Transfers(confirmCancelAll = true))
            Zones.TRANSFER_CONFIRM ->
                if (spot.index == 0) UiAction.Replace(Destination.Transfers()) else UiAction.CancelAllTransfers
            Zones.TRANSFER_ROWS -> {
                val row = state.downloads.getOrNull(spot.index / 2) ?: return UiAction.None
                if (spot.index % 2 == 0) UiAction.TransferPrimary(row.id) else UiAction.CancelTransfer(row.id)
            }
            Zones.TRANSFER_HOLDS -> TransferHold.entries.getOrNull(spot.index)?.let { UiAction.ToggleHold(it) } ?: UiAction.None
            Zones.ROMM_PLATFORMS ->
                state.romm.syncablePlatforms.getOrNull(spot.index)?.let { UiAction.ToggleRommPlatform(it.id) } ?: UiAction.None
            Zones.ROMM_ACTIONS ->
                when (rommActions(state).getOrNull(spot.index)) {
                    RommAction.SAVE -> UiAction.SaveRomm
                    RommAction.SYNC -> UiAction.SyncRomm
                    RommAction.REMOVE -> UiAction.RemoveRomm
                    RommAction.BACK -> UiAction.Back
                    null -> UiAction.None
                }
            else -> UiAction.None
        }
    }
}
