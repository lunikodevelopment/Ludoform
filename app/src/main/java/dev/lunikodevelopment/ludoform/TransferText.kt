package dev.lunikodevelopment.ludoform

import dev.lunikodevelopment.ludoform.data.DownloadHolds
import dev.lunikodevelopment.ludoform.data.DownloadRow
import dev.lunikodevelopment.ludoform.data.DownloadRowState
import dev.lunikodevelopment.ludoform.data.UploadHolds
import dev.lunikodevelopment.ludoform.data.UploadRow
import dev.lunikodevelopment.ludoform.data.UploadRowState
import dev.lunikodevelopment.ludoform.data.library.DownloadEntity
import dev.lunikodevelopment.ludoform.data.library.DownloadProblem
import dev.lunikodevelopment.ludoform.data.library.DownloadState
import dev.lunikodevelopment.ludoform.data.library.UploadEntity
import dev.lunikodevelopment.ludoform.data.library.UploadProblem
import dev.lunikodevelopment.ludoform.data.library.UploadScan
import dev.lunikodevelopment.ludoform.data.library.UploadState
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

    /** The Stage line: "3 downloads · 1 upload · 2.4 GB left". */
    fun summary(
        rows: List<DownloadRow>,
        uploads: List<UploadRow> = emptyList(),
    ): String {
        val sending = uploads.filter { it.state != UploadRowState.DONE }
        if (rows.isEmpty() && sending.isEmpty()) {
            return if (uploads.isEmpty()) "Download games from your RomM server on their details page." else "All uploads are done."
        }
        val counts =
            listOfNotNull(
                rows.size.takeIf { it > 0 }?.let { "$it download${if (it == 1) "" else "s"}" },
                sending.size.takeIf { it > 0 }?.let { "$it upload${if (it == 1) "" else "s"}" },
            ).joinToString(" · ")
        val left = rows.sumOf { it.bytesLeft } + sending.sumOf { it.bytesLeft }
        return if (left > 0) "$counts · ${formatSize(left)} left" else counts
    }

    /** The upload queue in the player's words. [onRomm] says whether a game now shows as on RomM. */
    fun uploadRows(
        uploads: List<UploadEntity>,
        holds: UploadHolds,
        conditions: Conditions,
        onRomm: (String) -> Boolean,
    ): List<UploadRow> =
        uploads.map { upload ->
            val total = upload.totalBytes
            val percent = if (total > 0) (upload.bytesDone * 100 / total).toInt().coerceIn(0, 100) else 0
            val (state, status) =
                when (upload.state) {
                    UploadState.RUNNING ->
                        UploadRowState.ACTIVE to
                            listOfNotNull("$percent%", upload.bytesPerSecond.takeIf { it > 0 }?.let { formatSize(it) + "/s" })
                                .joinToString(" · ")
                    UploadState.QUEUED ->
                        UploadRowState.ACTIVE to
                            when {
                                !conditions.online -> "Waiting for a network"
                                holds.wifiOnly && !conditions.unmetered -> "Waiting for Wi-Fi"
                                upload.attempts > 0 -> "Trying again soon"
                                upload.bytesDone > 0 -> "Waiting · $percent%"
                                else -> "Waiting"
                            }
                    UploadState.PAUSED -> UploadRowState.PAUSED to "Paused at $percent%"
                    UploadState.FAILED -> UploadRowState.FAILED to uploadProblem(upload.problem, upload.detail)
                    UploadState.DONE -> UploadRowState.DONE to done(upload, onRomm(upload.gameId))
                }
            UploadRow(
                id = upload.id,
                gameId = upload.gameId,
                title = upload.title,
                platformId = upload.platformId,
                state = state,
                status = status,
                percent = if (upload.state == UploadState.DONE) 100 else percent,
                bytesLeft = if (upload.state == UploadState.DONE) 0 else (total - upload.bytesDone).coerceAtLeast(0),
            )
        }

    private fun done(
        upload: UploadEntity,
        onRomm: Boolean,
    ): String =
        when {
            onRomm -> "On RomM"
            upload.scan == UploadScan.WAITING -> "Uploaded · RomM scan next"
            upload.scan == UploadScan.RUNNING -> "Uploaded · RomM is scanning"
            upload.scan == UploadScan.DONE -> "Uploaded · RomM scanned"
            upload.scan == UploadScan.REFUSED -> "Uploaded · scan it in RomM"
            upload.scan == UploadScan.FAILED -> "Uploaded · RomM's scan failed"
            else -> "Uploaded"
        }

    fun uploadProblem(
        problem: UploadProblem?,
        detail: String?,
    ): String =
        when (problem) {
            UploadProblem.NO_SERVER -> "No upload server · add one in Settings"
            UploadProblem.SIGN_IN -> "The server refused the sign-in"
            UploadProblem.HOST_KEY -> "The server's key isn't trusted · test it in Settings"
            UploadProblem.UNREACHABLE -> "Can't reach the upload server"
            UploadProblem.NO_FOLDER -> "RomM's folder is gone from the server"
            UploadProblem.NOT_WRITABLE -> "Can't write to RomM's folder"
            UploadProblem.MISSING_FILES -> if (detail.isNullOrBlank()) "Files are missing" else "Missing $detail"
            UploadProblem.EXISTS -> "A different ${detail ?: "file"} is already there"
            UploadProblem.NETWORK -> "The connection keeps failing"
            UploadProblem.FAILED, null -> "The upload failed"
        }
}
