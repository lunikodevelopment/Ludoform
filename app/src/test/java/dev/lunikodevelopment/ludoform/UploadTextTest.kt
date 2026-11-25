package dev.lunikodevelopment.ludoform

import dev.lunikodevelopment.ludoform.data.UploadHolds
import dev.lunikodevelopment.ludoform.data.UploadRowState
import dev.lunikodevelopment.ludoform.data.library.UploadEntity
import dev.lunikodevelopment.ludoform.data.library.UploadProblem
import dev.lunikodevelopment.ludoform.data.library.UploadScan
import dev.lunikodevelopment.ludoform.data.library.UploadState
import org.junit.Assert.assertEquals
import org.junit.Test

class UploadTextTest {
    private val mb = 1024L * 1024
    private val base =
        UploadEntity(1, "g1", "Ember Vale (Europe)", "nds", 1, "nds", "", "", totalBytes = 100 * mb)
    private val online = TransferText.Conditions(online = true, unmetered = true, charging = false)

    private fun row(
        upload: UploadEntity,
        conditions: TransferText.Conditions = online,
        onRomm: Boolean = false,
    ) = TransferText.uploadRows(listOf(upload), UploadHolds(), conditions) { onRomm }.single()

    @Test
    fun runningAndWaitingRowsSayWhatIsHappening() {
        assertEquals("38% · 4.0 MB/s", row(base.copy(state = UploadState.RUNNING, bytesDone = 38 * mb, bytesPerSecond = 4 * mb)).status)
        assertEquals("Waiting for Wi-Fi", row(base, online.copy(unmetered = false)).status)
        assertEquals("Waiting · 20%", row(base.copy(bytesDone = 20 * mb)).status)
        assertEquals("Paused at 20%", row(base.copy(state = UploadState.PAUSED, bytesDone = 20 * mb)).status)
    }

    @Test
    fun failuresNameTheProblem() {
        val failed = base.copy(state = UploadState.FAILED)
        assertEquals(UploadRowState.FAILED, row(failed).state)
        assertEquals(
            "Missing Game (Disc 2).cue",
            row(failed.copy(problem = UploadProblem.MISSING_FILES, detail = "Game (Disc 2).cue")).status,
        )
        assertEquals(
            "A different Ember.nds is already there",
            row(failed.copy(problem = UploadProblem.EXISTS, detail = "Ember.nds")).status,
        )
    }

    @Test
    fun finishedRowsFollowTheRommScan() {
        val done = base.copy(state = UploadState.DONE, bytesDone = 100 * mb)
        assertEquals("Uploaded · RomM is scanning", row(done.copy(scan = UploadScan.RUNNING)).status)
        assertEquals("Uploaded · scan it in RomM", row(done.copy(scan = UploadScan.REFUSED)).status)
        assertEquals("On RomM", row(done.copy(scan = UploadScan.DONE), onRomm = true).status)
        assertEquals(0, row(done).bytesLeft)
    }

    @Test
    fun theStageCountsDownloadsAndUploads() {
        val uploading = row(base.copy(state = UploadState.RUNNING, bytesDone = 50 * mb))
        assertEquals("1 upload · 50.0 MB left", TransferText.summary(emptyList(), listOf(uploading)))
    }
}
