package dev.lunikodevelopment.ludoform.data

import dev.lunikodevelopment.ludoform.input.FocusDirection
import dev.lunikodevelopment.ludoform.input.FocusSpot
import dev.lunikodevelopment.ludoform.input.Screen
import dev.lunikodevelopment.ludoform.model.GameSummary
import dev.lunikodevelopment.ludoform.model.LibrarySnapshot
import dev.lunikodevelopment.ludoform.model.PlatformSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppStoreTest {
    private fun game(
        id: String,
        platform: String,
        title: String,
        added: Long = 0,
    ) = GameSummary(id, platform, title, added, 1, "$title.bin")

    private val library =
        LibrarySnapshot(
            folders = listOf("content://roms"),
            platforms = listOf(PlatformSummary("gc", "GameCube", 6), PlatformSummary("dreamcast", "Dreamcast", 1)),
            games =
                listOf(
                    game("a", "gc", "Ember Vale", 1),
                    game("b", "gc", "Frostline", 6),
                    game("c", "gc", "Mossreach", 3),
                    game("d", "gc", "Tidefield", 4),
                    game("e", "gc", "Lumen Rally", 5),
                    game("f", "gc", "Iron Grove", 2),
                    game("g", "dreamcast", "Soft Circuit", 7),
                ),
        )

    private fun ready() = AppStore().apply { dispatch(AppIntent.LibraryChanged(library)) }

    @Test
    fun homeStartsOnTheFirstFeaturedGameAndStageFollowsIt() {
        val store = ready()
        assertEquals(FocusSpot(Zones.HOME_GAMES, 0), store.state.value.focus.current)
        assertEquals("Ember Vale", Screens.focusedGame(store.state.value)?.title)
        store.dispatch(AppIntent.MoveFocus(FocusDirection.RIGHT))
        assertEquals("Soft Circuit", Screens.focusedGame(store.state.value)?.title)
    }

    @Test
    fun upFromTheDeckReachesStagePlayAndDownRestoresTheGame() {
        val store = ready()
        store.dispatch(AppIntent.MoveFocus(FocusDirection.RIGHT))
        store.dispatch(AppIntent.MoveFocus(FocusDirection.UP))
        assertEquals(FocusSpot(Zones.HOME_PLAY, 0), store.state.value.focus.current)
        assertEquals("Soft Circuit", Screens.focusedGame(store.state.value)?.title)
        store.dispatch(AppIntent.MoveFocus(FocusDirection.DOWN))
        assertEquals(FocusSpot(Zones.HOME_GAMES, 1), store.state.value.focus.current)
    }

    @Test
    fun navigationRestoresTheFocusEachScreenHadOnBack() {
        val store = ready()
        store.dispatch(AppIntent.MoveFocus(FocusDirection.DOWN))
        val onConsoles = store.state.value.focus.current
        store.dispatch(AppIntent.Navigate(Destination.Library("gc")))
        assertEquals(FocusSpot(Zones.LIBRARY_GRID, 0), store.state.value.focus.current)
        store.dispatch(AppIntent.MoveFocus(FocusDirection.DOWN))
        store.dispatch(AppIntent.Navigate(Destination.Details("f")))
        assertEquals(FocusSpot(Zones.DETAILS_PLAY, 0), store.state.value.focus.current)
        store.dispatch(AppIntent.Back)
        assertEquals(FocusSpot(Zones.LIBRARY_GRID, 4), store.state.value.focus.current)
        store.dispatch(AppIntent.Back)
        assertEquals(onConsoles, store.state.value.focus.current)
        store.dispatch(AppIntent.Back)
        assertFalse(store.state.value.canGoBack)
    }

    @Test
    fun consolePickerOpensOnTheRequestedPlatformAndShouldersCycle() {
        val store = ready()
        store.dispatch(AppIntent.Navigate(Destination.Consoles("gc")))
        val picker = { Screens.picker(library, store.state.value.focus.indexIn(Zones.PICKER_CONSOLES)) }
        assertEquals(listOf("dreamcast", "gc"), picker().platforms.map { it.id })
        assertEquals("gc", picker().selected?.id)
        store.dispatch(AppIntent.Shoulder(1))
        assertEquals("dreamcast", picker().selected?.id)
        store.dispatch(AppIntent.MoveFocus(FocusDirection.LEFT))
        assertEquals("gc", picker().selected?.id)
        assertEquals(
            UiAction.Navigate(Destination.Library("gc")),
            Screens.actionFor(store.state.value, store.state.value.focus.current!!),
        )
    }

    @Test
    fun libraryShouldersJumpBetweenLetters() {
        val store = ready()
        store.dispatch(AppIntent.Navigate(Destination.Library("gc")))
        store.dispatch(AppIntent.Shoulder(1))
        val model = Screens.library(library, Destination.Library("gc"))
        assertEquals('F', LibraryModel.initial(model.games[store.state.value.focus.current!!.index].title))
        store.dispatch(AppIntent.Shoulder(-1))
        assertEquals(0, store.state.value.focus.current!!.index)
    }

    @Test
    fun sortChipReplacesTheDestinationWithoutABackStep() {
        val store = ready()
        store.dispatch(AppIntent.Navigate(Destination.Library(null)))
        store.dispatch(AppIntent.SelectFocus(FocusSpot(Zones.LIBRARY_SORT, 1)))
        val action = Screens.actionFor(store.state.value, store.state.value.focus.current!!)
        assertEquals(UiAction.Replace(Destination.Library(null, LibrarySort.RECENT)), action)
        store.dispatch(AppIntent.Replace((action as UiAction.Replace).destination))
        assertEquals(2, store.state.value.navigation.size)
        assertEquals("Soft Circuit", Screens.library(library, store.state.value.destination as Destination.Library).games.first().title)
        assertEquals(FocusSpot(Zones.LIBRARY_SORT, 1), store.state.value.focus.current)
    }

    @Test
    fun libraryStatusCoversLoadingEmptyScanningAndLostAccess() {
        val store = AppStore()
        assertEquals(LibraryStatus.LOADING, Screens.status(store.state.value))
        store.dispatch(AppIntent.LibraryChanged(LibrarySnapshot.EMPTY))
        assertEquals(LibraryStatus.NO_FOLDERS, Screens.status(store.state.value))
        assertEquals(FocusSpot(Zones.STATE_ACTIONS, 0), store.state.value.focus.current)
        val folderOnly = LibrarySnapshot(listOf("content://roms"), emptyList(), emptyList())
        store.dispatch(AppIntent.LibraryChanged(folderOnly))
        store.dispatch(AppIntent.ScanChanged(ScanState(running = true)))
        assertEquals(LibraryStatus.SCANNING, Screens.status(store.state.value))
        store.dispatch(AppIntent.ScanChanged(ScanState(finishedOnce = true)))
        assertEquals(LibraryStatus.NO_GAMES, Screens.status(store.state.value))
        store.dispatch(AppIntent.FolderAccessChanged(listOf("content://roms")))
        assertEquals(LibraryStatus.NO_ACCESS, Screens.status(store.state.value))
        assertEquals(UiAction.PickFolder, Screens.actionFor(store.state.value, FocusSpot(Zones.STATE_ACTIONS, 0)))
    }

    @Test
    fun lostFolderOnAFilledLibraryAddsAFocusableNotice() {
        val store = ready()
        store.dispatch(AppIntent.FolderAccessChanged(listOf("content://roms")))
        assertEquals(LibraryStatus.READY, Screens.status(store.state.value))
        store.dispatch(AppIntent.MoveFocus(FocusDirection.UP))
        assertEquals(FocusSpot(Zones.HOME_NOTICE, 0), store.state.value.focus.current)
        assertEquals(UiAction.PickFolder, Screens.actionFor(store.state.value, store.state.value.focus.current!!))
        store.dispatch(AppIntent.TouchScreen(Screen.STAGE))
        assertEquals(FocusSpot(Zones.HOME_PLAY, 0), store.state.value.focus.current)
    }

    @Test
    fun playExplainsThatLaunchingIsNotAvailableYet() {
        val store = ready()
        store.dispatch(AppIntent.Navigate(Destination.Details("b")))
        assertEquals(UiAction.LaunchUnavailable("b"), Screens.actionFor(store.state.value, store.state.value.focus.current!!))
        store.dispatch(AppIntent.ShowNotice("Launching arrives with emulator setup"))
        store.dispatch(AppIntent.MoveFocus(FocusDirection.RIGHT))
        assertNull(store.state.value.notice)
    }

    @Test
    fun connectionAndTesterUpdatesKeepNavigation() {
        val store = ready()
        store.dispatch(AppIntent.Navigate(Destination.Settings))
        store.dispatch(AppIntent.DeckConnectionChanged(true))
        assertTrue(store.state.value.deckAttached)
        store.dispatch(AppIntent.SetButtonTester(true))
        repeat(10) { store.dispatch(AppIntent.RecordButtonEvent("event $it")) }
        assertEquals((2..9).map { "event $it" }, store.state.value.recentButtonEvents)
        assertEquals(Destination.Settings, store.state.value.destination)
    }
}
