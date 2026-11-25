package dev.lunikodevelopment.ludoform

import dev.lunikodevelopment.ludoform.data.DownloadHolds
import dev.lunikodevelopment.ludoform.data.DownloadRow
import dev.lunikodevelopment.ludoform.data.DownloadRowState
import dev.lunikodevelopment.ludoform.data.library.DownloadEntity
import dev.lunikodevelopment.ludoform.data.library.DownloadProblem
import dev.lunikodevelopment.ludoform.data.library.DownloadState
import dev.lunikodevelopment.ludoform.ui.formatSize

/** Turns queue rows into what the Transfers screen says, in the player's words. */
object TransferText {
    /** What the device can offer right now, for "Waiting for Wi-Fi" and "Waiting to charge". */
    data class Conditions(val online: Boolean, val unmetered: Boolean, val charging: Boolean)

    fun rows(
        downloads: List<DownloadEntity>,
        holds: DownloadHolds,
        conditions: Conditions,
    ): List<DownloadRow> =
        downloads.filter { it.state != DownloadState.DONE }.map { download ->
            val total = download.totalBytes
            val percent = if (total > 0) (download.bytesDone * 100 / total).toInt().coerceIn(0, 100) else 0
            val (state, status) =
                when (download.state) {
                    DownloadState.RUNNING ->
                        DownloadRowState.ACTIVE to
                            listOfNotNull("$percent%", download.bytesPerSecond.takeIf { it > 0 }?.let { formatSize(it) + "/s" })
                                .joinToString(" · ")
                    DownloadState.QUEUED -> DownloadRowState.ACTIVE to waiting(download, holds, conditions)
                    DownloadState.PAUSED -> DownloadRowState.PAUSED to "Paused at $percent%"
                    DownloadState.FAILED -> DownloadRowState.FAILED to problem(download.problem)
                    DownloadState.DONE -> DownloadRowState.ACTIVE to "Done"
                }
            DownloadRow(
                id = download.id,
                rommId = download.rommId,
                gameId = download.gameId,
                title = download.title,
                platformId = download.platformId,
                state = state,
                status = status,
                percent = percent,
                bytesLeft = (total - download.bytesDone).coerceAtLeast(0),
            )
        }

    private fun waiting(
        download: DownloadEntity,
        holds: DownloadHolds,
        conditions: Conditions,
    ): String =
        when {
            !conditions.online -> "Waiting for a network"
            holds.wifiOnly && !conditions.unmetered -> "Waiting for Wi-Fi"
            holds.chargingOnly && !conditions.charging -> "Waiting to charge"
            download.attempts > 0 -> "Trying again soon"
            download.bytesDone > 0 && download.totalBytes > 0 -> "Waiting · ${download.bytesDone * 100 / download.totalBytes}%"
            else -> "Waiting"
        }

    fun problem(problem: DownloadProblem?): String =
        when (problem) {
            DownloadProblem.SIGN_IN -> "RomM refused the sign-in"
            DownloadProblem.NOT_FOUND -> "No longer on the server"
            DownloadProblem.SERVER -> "The server refused it"
            DownloadProblem.NO_SPACE -> "Not enough space on the SD card"
            DownloadProblem.FOLDER -> "Can't write to the games folder"
            DownloadProblem.NETWORK, null -> "The connection keeps failing"
        }

    /** The Stage line: "3 downloads · 2.4 GB left". */
    fun summary(rows: List<DownloadRow>): String {
        if (rows.isEmpty()) return "Download games from your RomM server on their details page."
        val count = "${rows.size} download${if (rows.size == 1) "" else "s"}"
        val left = rows.sumOf { it.bytesLeft }
        return if (left > 0) "$count · ${formatSize(left)} left" else count
    }
}
