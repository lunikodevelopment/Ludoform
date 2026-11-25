package dev.lunikodevelopment.ludoform

import dev.lunikodevelopment.ludoform.data.DownloadHolds
import dev.lunikodevelopment.ludoform.data.DownloadRowState
import dev.lunikodevelopment.ludoform.data.library.DownloadEntity
import dev.lunikodevelopment.ludoform.data.library.DownloadProblem
import dev.lunikodevelopment.ludoform.data.library.DownloadState
import org.junit.Assert.assertEquals
import org.junit.Test

class TransferTextTest {
    private val mb = 1024L * 1024
    private val base = DownloadEntity(1, 7, "romm:7", "Ember Vale", "nds", "Ember Vale.nds", "content://x", totalBytes = 100 * mb)
    private val online = TransferText.Conditions(online = true, unmetered = true, charging = false)

    private fun status(
        download: DownloadEntity,
        holds: DownloadHolds = DownloadHolds(),
        conditions: TransferText.Conditions = online,
    ) = TransferText.rows(listOf(download), holds, conditions).single()

    @Test
    fun runningShowsPercentAndSpeed() {
        val row = status(base.copy(state = DownloadState.RUNNING, bytesDone = 62 * mb, bytesPerSecond = 6 * mb))
        assertEquals("62% · 6.0 MB/s", row.status)
        assertEquals(DownloadRowState.ACTIVE, row.state)
        assertEquals(38 * mb, row.bytesLeft)
    }

    @Test
    fun holdsExplainTheWait() {
        val queued = base.copy(state = DownloadState.QUEUED)
        assertEquals("Waiting for Wi-Fi", status(queued, conditions = online.copy(unmetered = false)).status)
        assertEquals("Waiting", status(queued, DownloadHolds(wifiOnly = false), online.copy(unmetered = false)).status)
        assertEquals("Waiting to charge", status(queued, DownloadHolds(chargingOnly = true)).status)
        assertEquals("Waiting for a network", status(queued, conditions = online.copy(online = false)).status)
        assertEquals("Trying again soon", status(queued.copy(attempts = 2)).status)
    }

    @Test
    fun pausedAndFailedRowsSayWhy() {
        assertEquals("Paused at 31%", status(base.copy(state = DownloadState.PAUSED, bytesDone = 31 * mb)).status)
        val failed = status(base.copy(state = DownloadState.FAILED, problem = DownloadProblem.NO_SPACE))
        assertEquals("Not enough space on the SD card", failed.status)
        assertEquals(DownloadRowState.FAILED, failed.state)
    }

    @Test
    fun summaryCountsWhatIsLeft() {
        val rows = TransferText.rows(listOf(base, base.copy(id = 2, rommId = 8)), DownloadHolds(), online)
        assertEquals("2 downloads · 200.0 MB left", TransferText.summary(rows))
    }
}
