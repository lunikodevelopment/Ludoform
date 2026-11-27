package dev.lunikodevelopment.ludoform.transfer

import com.hierynomus.mssmb2.SMBApiException
import dev.lunikodevelopment.ludoform.data.library.ServerCheck
import dev.lunikodevelopment.ludoform.data.library.UploadProtocol
import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.MessageDigest
import java.util.Base64

/** What Ludoform needs to reach an upload server once; [trustedKey] is the SFTP host key the player trusted. */
data class ServerAccess(
    val protocol: UploadProtocol,
    val host: String,
    val port: Int,
    val username: String,
    val password: String,
    val folder: String,
    val trustedKey: String?,
)

/** A connection test's outcome. [offeredKey] is the SFTP host key the server showed, when it showed one. */
data class ProbeResult(val check: ServerCheck, val detail: String? = null, val offeredKey: String? = null)

/** The SFTP host-key rule: nothing is trusted until the player confirms it, and a changed key is refused. */
object HostKeys {
    /** `ssh-ed25519 SHA256:p3Vx9kQa…`, like OpenSSH prints it. */
    fun describe(
        type: String,
        blob: ByteArray,
    ): String = type + " SHA256:" + Base64.getEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(blob))

    /** Null when [offered] may be used; otherwise why not. */
    fun refusal(
        trusted: String?,
        offered: String,
    ): ServerCheck? =
        when (trusted) {
            null -> ServerCheck.HOST_KEY_UNKNOWN
            offered -> null
            else -> ServerCheck.HOST_KEY_CHANGED
        }

    /** The fingerprint in groups of four for reading aloud: `p3Vx 9kQa Lw2H …`. */
    fun groups(key: String): List<String> = key.substringAfter("SHA256:").chunked(4)

    /** The key type as the board shows it: `ED25519`, `RSA`, `ECDSA`. */
    fun typeLabel(key: String): String =
        key.substringBefore(' ').removePrefix("ssh-").removePrefix("ecdsa-sha2-").substringBefore('-').uppercase().let {
            if (it.startsWith("NISTP")) "ECDSA" else it
        }
}

/**
 * Connects to an upload server, signs in and checks the RomM folder exists and can be written to, by creating and
 * deleting a small file. Blocking; runs in a WorkManager job (only `core:romm` and `core:transfer` use the network).
 */
object ServerProbe {
    private const val TEST_FILE = ".ludoform-write-test"

    fun test(access: ServerAccess): ProbeResult =
        try {
            when (val open = RemoteFolders.open(access)) {
                is RemoteOpen.Refused -> open.result
                is RemoteOpen.Opened ->
                    open.folder.use { folder ->
                        try {
                            folder.write(TEST_FILE, 0, ByteArrayInputStream(ByteArray(0))) { true }
                            folder.delete(TEST_FILE)
                            ProbeResult(ServerCheck.OK, null, open.offeredKey)
                        } catch (error: IOException) {
                            ProbeResult(ServerCheck.NOT_WRITABLE, null, open.offeredKey)
                        } catch (error: SMBApiException) {
                            ProbeResult(ServerCheck.NOT_WRITABLE, null, open.offeredKey)
                        }
                    }
            }
        } catch (error: UnknownHostException) {
            ProbeResult(ServerCheck.UNREACHABLE, "The server name was not found.")
        } catch (error: ConnectException) {
            ProbeResult(ServerCheck.UNREACHABLE, "The server refused the connection.")
        } catch (error: SocketTimeoutException) {
            ProbeResult(ServerCheck.UNREACHABLE, "The server did not answer in time.")
        } catch (error: IOException) {
            ProbeResult(ServerCheck.FAILED, error.javaClass.simpleName)
        }
}
