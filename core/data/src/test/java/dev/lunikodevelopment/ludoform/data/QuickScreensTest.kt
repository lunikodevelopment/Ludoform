package dev.lunikodevelopment.ludoform.data

import dev.lunikodevelopment.ludoform.input.FocusSpot
import dev.lunikodevelopment.ludoform.model.GameSummary
import dev.lunikodevelopment.ludoform.model.LibrarySnapshot
import dev.lunikodevelopment.ludoform.model.PlatformSummary
import org.junit.Assert.assertEquals
import org.junit.Test

/** Quick Menu, Search and Quick Settings (D43). */
class QuickScreensTest {
    private fun game(
        id: String,
        title: String,
        platform: String = "gc",
        played: Long = 0,
        lastPlayed: Long? = null,
        added: Long = 1,
    ) = GameSummary(id, platform, title, added, 1, "$title.iso", playTimeMillis = played, lastPlayedAt = lastPlayed)

    private val library =
        LibrarySnapshot(
            folders = listOf("content://roms"),
            platforms = listOf(PlatformSummary("gc", "GameCube", 3), PlatformSummary("gba", "Game Boy Advance", 1)),
            games =
                listOf(
                    game("orbital", "Orbital Garden", played = 10, lastPlayed = 5),
                    game("ember", "Ember Vale: Origins", platform = "gba", played = 50, lastPlayed = 9),
                    game("frost", "Frostline Record", added = 7),
                    game("tide", "Tidefield Harbor", added = 3),
                ),
        )

    private fun store() = AppStore().apply { dispatch(AppIntent.LibraryChanged(library)) }

    @Test
    fun searchPutsTitlesStartingWithTheQueryFirstThenWordsThenTheRest() {
        val results = Screens.search(library, Destination.Search("or"))
        assertEquals(listOf("orbital", "ember", "frost", "tide"), results.map { it.id })
        assertEquals(listOf("orbital", "frost", "tide"), Screens.search(library, Destination.Search("or", "gc")).map { it.id })
        assertEquals(emptyList<GameSummary>(), Screens.search(library, Destination.Search("  ")))
        assertEquals(listOf("ember"), Screens.search(library, Destination.Search("ÉMBER")).map { it.id })
    }

    @Test
    fun keysTypeDeleteAndTheFilterCyclesSystems() {
        val store = store()
        store.dispatch(AppIntent.Navigate(Destination.Search("o")))
        val state = store.state.value
        assertEquals(FocusSpot(Zones.SEARCH_KEYS, 0), state.focus.current)
        val r = Screens.searchKeys.indexOf("R")
        assertEquals(UiAction.Replace(Destination.Search("or")), Screens.actionFor(state, FocusSpot(Zones.SEARCH_KEYS, r)))
        assertEquals(
            UiAction.Replace(Destination.Search("")),
            Screens.actionFor(state, FocusSpot(Zones.SEARCH_KEYS, Screens.searchKeys.lastIndex)),
        )
        assertEquals(UiAction.Replace(Destination.Search("o", "gc")), Screens.actionFor(state, FocusSpot(Zones.SEARCH_FILTER, 0)))
        assertEquals("gba", Screens.nextSearchPlatform(library, "gc"))
        assertEquals(null, Screens.nextSearchPlatform(library, "gba"))
    }

    @Test
    fun l3OpensTheQuickMenuFromMainScreensAndClosesIt() {
        val store = store()
        assertEquals(UiAction.Navigate(Destination.QuickMenu), Screens.shortcut(store.state.value, Destination.QuickMenu))
        store.dispatch(AppIntent.Navigate(Destination.QuickMenu))
        assertEquals(UiAction.Back, Screens.shortcut(store.state.value, Destination.QuickMenu))
        assertEquals(UiAction.None, Screens.shortcut(store.state.value, Destination.QuickSettings))
        val state = store.state.value
        assertEquals(UiAction.Replace(Destination.Search()), Screens.actionFor(state, FocusSpot(Zones.QUICK_MENU, 0)))
        assertEquals(UiAction.RandomGame, Screens.actionFor(state, FocusSpot(Zones.QUICK_MENU, 1)))
        assertEquals(
            UiAction.Replace(Destination.Library(null, LibrarySort.MOST_PLAYED)),
            Screens.actionFor(state, FocusSpot(Zones.QUICK_MENU, 2)),
        )
    }

    @Test
    fun theQuickMenuListsOrderTheLibrary() {
        fun ids(sort: LibrarySort) = Screens.library(library, Destination.Library(null, sort)).games.map { it.id }
        assertEquals(listOf("ember", "orbital"), ids(LibrarySort.MOST_PLAYED))
        assertEquals(listOf("ember", "orbital"), ids(LibrarySort.LAST_PLAYED))
        assertEquals(listOf("frost", "tide"), ids(LibrarySort.UNPLAYED))
    }

    @Test
    fun quickSettingsSetsTheThemeAndGameScreen() {
        val store = store()
        store.dispatch(AppIntent.Navigate(Destination.QuickSettings))
        val state = store.state.value
        assertEquals(FocusSpot(Zones.QS_THEME, 0), state.focus.current)
        assertEquals(UiAction.SetTheme(ThemeChoice.BLACK), Screens.actionFor(state, FocusSpot(Zones.QS_THEME, 3)))
        assertEquals(UiAction.SetGameScreen(ScreenSetting.ASK), Screens.actionFor(state, FocusSpot(Zones.QS_SCREEN, 2)))
    }
}
