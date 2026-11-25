package dev.lunikodevelopment.ludoform

import dev.lunikodevelopment.ludoform.data.UploadServerRow
import dev.lunikodevelopment.ludoform.data.library.ServerCheck
import dev.lunikodevelopment.ludoform.data.library.UploadProtocol
import dev.lunikodevelopment.ludoform.data.library.UploadServerEntity

/** Upload servers as the screens show them (D36), in the player's words. */
object UploadServerText {
    fun row(server: UploadServerEntity): UploadServerRow {
        val keyWaiting = server.check == ServerCheck.HOST_KEY_UNKNOWN || server.check == ServerCheck.HOST_KEY_CHANGED
        return UploadServerRow(
            id = server.id,
            name = server.name.ifBlank { server.host },
            protocol = if (server.protocol == UploadProtocol.SFTP) "SFTP" else "SMB",
            address = if (server.port == server.protocol.defaultPort) server.host else "${server.host}:${server.port}",
            folder = server.folder,
            status = status(server),
            problem = server.check !in setOf(ServerCheck.OK, ServerCheck.NOT_CHECKED, ServerCheck.CHECKING),
            checking = server.check == ServerCheck.CHECKING,
            tested = server.check != ServerCheck.CHECKING && server.check != ServerCheck.NOT_CHECKED,
            offeredKey = server.offeredKey.takeIf { keyWaiting },
            keyChanged = server.check == ServerCheck.HOST_KEY_CHANGED,
            ready = server.check == ServerCheck.OK,
        )
    }

    fun status(server: UploadServerEntity): String =
        when (server.check) {
            ServerCheck.NOT_CHECKED -> "Not tested yet"
            ServerCheck.CHECKING -> "Testing…"
            ServerCheck.OK -> "Ready · folder can be written to"
            ServerCheck.HOST_KEY_UNKNOWN -> "Key not trusted yet"
            ServerCheck.HOST_KEY_CHANGED -> "The server's key changed · check it"
            ServerCheck.UNREACHABLE -> server.checkDetail ?: "Can't reach the server"
            ServerCheck.SIGN_IN -> server.checkDetail ?: "Sign-in failed · check the user name and password"
            ServerCheck.NO_FOLDER -> server.checkDetail ?: "That folder doesn't exist"
            ServerCheck.NOT_WRITABLE -> "The folder can't be written to"
            ServerCheck.FAILED -> "The connection failed" + (server.checkDetail?.let { " ($it)" } ?: "")
        }

    /** The row's button: trust a waiting key, or test again. */
    fun action(row: UploadServerRow): String =
        when {
            row.checking -> "Testing…"
            row.offeredKey != null -> "Check key"
            else -> "Test"
        }
}
