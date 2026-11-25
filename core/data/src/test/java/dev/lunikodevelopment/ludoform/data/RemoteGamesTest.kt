package dev.lunikodevelopment.ludoform.data

import dev.lunikodevelopment.ludoform.input.FocusSpot
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
    fun remoteOnlyGamesOfferDownloadInsteadOfPlay() {
        val store = AppStore()
        store.dispatch(AppIntent.LibraryChanged(library))
        store.dispatch(AppIntent.Navigate(Destination.Details("a-remote")))
        assertEquals(1, store.state.value.layout.zone(Zones.DETAILS_PLAY)?.size)
        assertEquals(UiAction.Download("a-remote"), Screens.actionFor(store.state.value, FocusSpot(Zones.DETAILS_PLAY, 0)))
        // Once queued, the same button opens Transfers.
        val row = DownloadRow(1, 7, "a-remote", "A", "nds", DownloadRowState.ACTIVE, "Waiting", 0, 10)
        store.dispatch(AppIntent.DownloadsChanged(listOf(row), DownloadHolds()))
        assertEquals(UiAction.Navigate(Destination.Transfers()), Screens.actionFor(store.state.value, FocusSpot(Zones.DETAILS_PLAY, 0)))
        assertTrue(library.game("a-remote")!!.remoteOnly)
        assertFalse(library.game("z-local")!!.remoteOnly)
    }

    @Test
    fun transfersRowsOfferTheirActionAndCancelAllAsksFirst() {
        val store = AppStore()
        store.dispatch(AppIntent.LibraryChanged(library))
        val rows =
            listOf(
                DownloadRow(1, 7, "a-remote", "A", "nds", DownloadRowState.ACTIVE, "62%", 62, 10),
                DownloadRow(2, 8, "b-remote", "B", "nds", DownloadRowState.FAILED, "Sign-in failed", 0, 10),
            )
        store.dispatch(AppIntent.DownloadsChanged(rows, DownloadHolds()))
        store.dispatch(AppIntent.Navigate(Destination.Transfers()))
        assertEquals(FocusSpot(Zones.TRANSFER_ROWS, 0), store.state.value.focus.current)
        assertEquals(UiAction.TransferPrimary(1), Screens.actionFor(store.state.value, FocusSpot(Zones.TRANSFER_ROWS, 0)))
        assertEquals(UiAction.CancelTransfer(2), Screens.actionFor(store.state.value, FocusSpot(Zones.TRANSFER_ROWS, 3)))
        val ask = Screens.actionFor(store.state.value, FocusSpot(Zones.TRANSFER_TOP, 0))
        assertEquals(UiAction.Replace(Destination.Transfers(confirmCancelAll = true)), ask)
        store.dispatch(AppIntent.Replace(Destination.Transfers(confirmCancelAll = true)))
        assertEquals(FocusSpot(Zones.TRANSFER_CONFIRM, 0), store.state.value.focus.current)
        assertEquals(UiAction.Replace(Destination.Transfers()), Screens.actionFor(store.state.value, FocusSpot(Zones.TRANSFER_CONFIRM, 0)))
        assertEquals(UiAction.CancelAllTransfers, Screens.actionFor(store.state.value, FocusSpot(Zones.TRANSFER_CONFIRM, 1)))
    }
}
