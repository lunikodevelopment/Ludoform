package dev.lunikodevelopment.ludoform.data

import dev.lunikodevelopment.ludoform.input.FocusSpot
import dev.lunikodevelopment.ludoform.model.GameSummary
import dev.lunikodevelopment.ludoform.model.LibrarySnapshot
import dev.lunikodevelopment.ludoform.model.PlatformSummary
import org.junit.Assert.assertEquals
import org.junit.Test

/** The Upload button on Details and the Upload tab in Transfers (D37). */
class UploadScreensTest {
    private val library =
        LibrarySnapshot(
            folders = listOf("content://roms"),
            platforms = listOf(PlatformSummary("nds", "Nintendo DS", 3)),
            games =
                listOf(
                    GameSummary("local", "nds", "Local", 1, 1, "Local.nds"),
                    GameSummary("both", "nds", "Both", 1, 1, "Both.nds", rommId = 4),
                    GameSummary("remote", "nds", "Remote", 1, 0, "Remote.nds", rommId = 5),
                ),
        )
    private val ready = UploadServerRow(1, "nas", "SMB", "nas", "roms", "Ready", problem = false, checking = false, ready = true)

    private fun store(servers: List<UploadServerRow> = listOf(ready)) =
        AppStore().apply {
            dispatch(AppIntent.LibraryChanged(library))
            dispatch(AppIntent.UploadServersChanged(servers))
        }

    @Test
    fun uploadShowsOnlyForAGameOnlyOnTheDeviceWithAReadyServer() {
        val state = store().state.value
        assertEquals(DetailsAction.UPLOAD, Screens.detailsActions(state, "local").first())
        assertEquals(DetailsAction.BACK, Screens.detailsActions(state, "both").first())
        assertEquals(DetailsAction.BACK, Screens.detailsActions(state, "remote").first())
        val untested = store(listOf(ready.copy(ready = false))).state.value
        assertEquals(DetailsAction.BACK, Screens.detailsActions(untested, "local").first())
    }

    @Test
    fun uploadQueuesTheGameAndAQueuedOneOpensTheUploadTab() {
        val store = store()
        store.dispatch(AppIntent.Navigate(Destination.Details("local")))
        assertEquals(UiAction.Upload("local"), Screens.actionFor(store.state.value, FocusSpot(Zones.DETAILS_ACTIONS, 0)))
        val row = UploadRow(9, "local", "Local", UploadRowState.ACTIVE, "Waiting", 0, 10)
        store.dispatch(AppIntent.UploadsChanged(listOf(row), UploadHolds()))
        assertEquals(
            UiAction.Navigate(Destination.Transfers(tab = TransferTab.UPLOAD)),
            Screens.actionFor(store.state.value, FocusSpot(Zones.DETAILS_ACTIONS, 0)),
        )
    }

    @Test
    fun theUploadTabListsUploadsWithTheirOwnButtonsAndHolds() {
        val store = store()
        val active = UploadRow(9, "local", "Local", UploadRowState.ACTIVE, "Waiting", 0, 10)
        val done = UploadRow(10, "both", "Both", UploadRowState.DONE, "On RomM", 100, 0)
        store.dispatch(AppIntent.UploadsChanged(listOf(active, done), UploadHolds()))
        store.dispatch(AppIntent.Navigate(Destination.Transfers(tab = TransferTab.UPLOAD)))
        val state = store.state.value
        assertEquals(FocusSpot(Zones.TRANSFER_ROWS, 0), state.focus.current)
        assertEquals(
            listOf(TransferTopAction.CLEAR_FINISHED, TransferTopAction.CANCEL_ALL),
            Screens.transferTopActions(state, TransferTab.UPLOAD),
        )
        assertEquals(UiAction.UploadPrimary(9), Screens.actionFor(state, FocusSpot(Zones.TRANSFER_ROWS, 0)))
        assertEquals(UiAction.CancelUpload(9), Screens.actionFor(state, FocusSpot(Zones.TRANSFER_ROWS, 1)))
        assertEquals(UiAction.Navigate(Destination.Details("both")), Screens.actionFor(state, FocusSpot(Zones.TRANSFER_ROWS, 2)))
        assertEquals(UiAction.ClearFinishedUploads, Screens.actionFor(state, FocusSpot(Zones.TRANSFER_TOP, 0)))
        assertEquals(UiAction.ToggleUploadHold(UploadHold.SCAN), Screens.actionFor(state, FocusSpot(Zones.TRANSFER_HOLDS, 1)))
        assertEquals(
            UiAction.Replace(Destination.Transfers(tab = TransferTab.DOWNLOAD)),
            Screens.actionFor(state, FocusSpot(Zones.TRANSFER_TABS, 0)),
        )
    }

    @Test
    fun withoutServersOrUploadsTransfersHasNoUploadTab() {
        val store = store(emptyList())
        store.dispatch(AppIntent.Navigate(Destination.Transfers(tab = TransferTab.UPLOAD)))
        val state = store.state.value
        assertEquals(TransferTab.DOWNLOAD, Screens.transferTab(state, Destination.Transfers(tab = TransferTab.UPLOAD)))
        assertEquals(0, state.layout.deck.first { it.id == Zones.TRANSFER_TABS }.size)
    }
}
