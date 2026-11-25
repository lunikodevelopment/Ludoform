package dev.lunikodevelopment.ludoform.data

import dev.lunikodevelopment.ludoform.input.FocusSpot
import dev.lunikodevelopment.ludoform.model.GameSummary
import dev.lunikodevelopment.ludoform.model.LibrarySnapshot
import dev.lunikodevelopment.ludoform.model.PlatformSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ServerUnreachableTest {
    private fun game(
        id: String,
        platform: String,
        remote: Boolean,
        playedAt: Long? = null,
    ) = GameSummary(id, platform, id, 1, if (remote) 0 else 1, id, lastPlayedAt = playedAt, rommId = if (remote) id.hashCode() else 7)

    private val library =
        LibrarySnapshot(
            folders = listOf("content://roms"),
            platforms = listOf(PlatformSummary("nds", "Nintendo DS", 3), PlatformSummary("gba", "Game Boy Advance", 4)),
            games =
                listOf(
                    game("Zelda", "nds", remote = false, playedAt = 50),
                    game("Animal", "nds", remote = true),
                    game("Kirby", "nds", remote = false),
                    game("Metroid", "gba", remote = false),
                    game("A", "gba", remote = true),
                    game("B", "gba", remote = true),
                    game("C", "gba", remote = true),
                ),
        )
    private val down = RommOffline(RommOutage.UNREACHABLE, since = 1, nextTryAt = 30_000, savesWaiting = 2)

    private fun store(offline: RommOffline?) =
        AppStore().apply {
            dispatch(AppIntent.LibraryChanged(library))
            dispatch(AppIntent.RommChanged(RommState(address = "romm.example.org", status = RommStatus.CONNECTED, offline = offline)))
        }

    @Test
    fun showsTheLastPlayedConsoleWithInstalledGamesFirst() {
        val model = Screens.offline(library, Destination.ServerUnreachable())
        assertEquals("nds", model.platform?.id)
        assertEquals(listOf("Kirby", "Zelda", "Animal"), model.games.map { it.id })
        assertEquals(2, model.installed)
        val installedOnly = Screens.offline(library, Destination.ServerUnreachable("gba", installedOnly = true))
        assertEquals(listOf("Metroid"), installedOnly.games.map { it.id })
    }

    @Test
    fun deckActionsOpenSettingsRetryAndFilter() {
        val store = store(down)
        store.dispatch(AppIntent.Navigate(Destination.ServerUnreachable("nds")))
        val state = store.state.value
        assertEquals(FocusSpot(Zones.OFFLINE_GRID, 0), state.focus.current)
        assertEquals(UiAction.OpenRomm, Screens.actionFor(state, FocusSpot(Zones.OFFLINE_ACTIONS, 0)))
        assertEquals(UiAction.RetryRomm, Screens.actionFor(state, FocusSpot(Zones.OFFLINE_ACTIONS, 1)))
        assertEquals(
            UiAction.Replace(Destination.ServerUnreachable("nds", installedOnly = true)),
            Screens.actionFor(state, FocusSpot(Zones.OFFLINE_FILTER, 0)),
        )
        assertEquals(UiAction.Navigate(Destination.Details("Kirby")), Screens.actionFor(state, FocusSpot(Zones.OFFLINE_GRID, 0)))
    }

    @Test
    fun homeNoticeLeadsToTheScreenWhileRommIsDown() {
        assertNull(Screens.homeNotice(store(null).state.value))
        val state = store(down).state.value
        assertEquals(HomeNotice.ROMM_OFFLINE, Screens.homeNotice(state))
        assertEquals(1, state.layout.zone(Zones.HOME_NOTICE)?.size)
        assertEquals(UiAction.Navigate(Destination.ServerUnreachable()), Screens.actionFor(state, FocusSpot(Zones.HOME_NOTICE, 0)))
        // Folders that need access again are the more urgent notice.
        val store = store(down)
        store.dispatch(AppIntent.FolderAccessChanged(listOf("content://roms")))
        assertEquals(HomeNotice.FOLDERS, Screens.homeNotice(store.state.value))
    }

    @Test
    fun remoteGamesExplainInsteadOfQueueingWhileRommIsDown() {
        val store = store(down)
        store.dispatch(AppIntent.Navigate(Destination.Details("Animal")))
        assertEquals(
            UiAction.Navigate(Destination.ServerUnreachable("nds")),
            Screens.actionFor(store.state.value, FocusSpot(Zones.DETAILS_PLAY, 0)),
        )
        // Installed games still play.
        store.dispatch(AppIntent.Navigate(Destination.Details("Kirby")))
        assertEquals(UiAction.Play(LaunchRequest("Kirby")), Screens.actionFor(store.state.value, FocusSpot(Zones.DETAILS_PLAY, 0)))
    }

    @Test
    fun upFromTheFirstGameReachesTheNotice() {
        val store = store(down)
        assertEquals(FocusSpot(Zones.HOME_GAMES, 0), store.state.value.focus.current)
        store.dispatch(AppIntent.MoveFocus(dev.lunikodevelopment.ludoform.input.FocusDirection.UP))
        assertEquals(FocusSpot(Zones.HOME_NOTICE, 0), store.state.value.focus.current)
    }
}
