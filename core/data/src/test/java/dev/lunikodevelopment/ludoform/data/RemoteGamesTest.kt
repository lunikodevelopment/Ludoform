package dev.lunikodevelopment.ludoform.data

import dev.lunikodevelopment.ludoform.model.GameSummary
import dev.lunikodevelopment.ludoform.model.LibrarySnapshot
import dev.lunikodevelopment.ludoform.model.PlatformSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteGamesTest {
    private fun game(
        id: String,
        platform: String,
        remote: Boolean,
        addedAt: Long = 1,
    ) = GameSummary(id, platform, id, addedAt, if (remote) 0 else 1, id, rommId = if (remote) id.hashCode() else null)

    private val library =
        LibrarySnapshot(
            folders = listOf("content://3ds"),
            platforms = listOf(PlatformSummary("nds", "Nintendo DS", 3), PlatformSummary("3ds", "Nintendo 3DS", 1)),
            games =
                listOf(
                    game("a-remote", "nds", true, addedAt = 99),
                    game("b-remote", "nds", true, addedAt = 98),
                    game("c-remote", "nds", true, addedAt = 97),
                    game("z-local", "3ds", false, addedAt = 1),
                ),
        )

    @Test
    fun gamesOnTheDeviceLeadHomeAndServerGamesFill() {
        val home = Screens.home(library)
        assertEquals("z-local", home.featured.first().id)
        assertEquals("z-local", home.recent.first().id)
        assertEquals(3, home.featured.size)
    }

    @Test
    fun remoteOnlyGamesHaveNoPlayButton() {
        val store = AppStore()
        store.dispatch(AppIntent.LibraryChanged(library))
        store.dispatch(AppIntent.Navigate(Destination.Details("a-remote")))
        assertEquals(0, store.state.value.layout.zone(Zones.DETAILS_PLAY)?.size)
        assertEquals(Zones.DETAILS_ACTIONS, store.state.value.focus.current?.zone)
        assertTrue(library.game("a-remote")!!.remoteOnly)
        assertFalse(library.game("z-local")!!.remoteOnly)
    }
}
