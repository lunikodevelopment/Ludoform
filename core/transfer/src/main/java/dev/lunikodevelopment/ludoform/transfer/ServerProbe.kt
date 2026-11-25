package dev.lunikodevelopment.ludoform.transfer

import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.SmbConfig
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.share.DiskShare
import dev.lunikodevelopment.ludoform.data.library.ServerCheck
import dev.lunikodevelopment.ludoform.data.library.UploadProtocol
import net.schmizz.sshj.DefaultConfig
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.common.Buffer
import net.schmizz.sshj.common.KeyType
import net.schmizz.sshj.sftp.FileMode
import net.schmizz.sshj.transport.TransportException
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import net.schmizz.sshj.userauth.UserAuthException
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.MessageDigest
import java.security.PublicKey
import java.security.Security
import java.util.Base64
import java.util.EnumSet
import java.util.concurrent.TimeUnit

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

/** The SFTP host-key rule (D36): nothing is trusted until the player confirms it, and a changed key is refused. */
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

    /** The fingerprint in groups of four for reading aloud: `p3Vx 9kQa Lw2H …` (board 08). */
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
    private const val TIMEOUT_MS = 10_000

    init {
        // Android's own "BC" provider is a stripped copy without the curves and ciphers SSH servers use.
        Security.removeProvider("BC")
        Security.insertProviderAt(BouncyCastleProvider(), 1)
    }

    fun test(access: ServerAccess): ProbeResult =
        try {
            when (access.protocol) {
                UploadProtocol.SFTP -> sftp(access)
                UploadProtocol.SMB -> smb(access)
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

    private fun sftp(access: ServerAccess): ProbeResult {
        var offered: String? = null
        val verifier =
            object : HostKeyVerifier {
                override fun verify(
                    hostname: String,
                    port: Int,
                    key: PublicKey,
                ): Boolean {
                    val description = HostKeys.describe(KeyType.fromKey(key).toString(), Buffer.PlainBuffer().putPublicKey(key).compactData)
                    offered = description
                    return HostKeys.refusal(access.trustedKey, description) == null
                }

                override fun findExistingAlgorithms(
                    hostname: String,
                    port: Int,
                ): List<String> = emptyList()
            }
        SSHClient(DefaultConfig()).use { ssh ->
            ssh.connectTimeout = TIMEOUT_MS
            ssh.timeout = TIMEOUT_MS
            ssh.addHostKeyVerifier(verifier)
            try {
                ssh.connect(access.host, access.port)
            } catch (error: TransportException) {
                val key = offered ?: return ProbeResult(ServerCheck.FAILED, "The secure connection failed.")
                return ProbeResult(HostKeys.refusal(access.trustedKey, key) ?: ServerCheck.FAILED, null, key)
            }
            try {
                ssh.authPassword(access.username, access.password)
            } catch (error: UserAuthException) {
                return ProbeResult(ServerCheck.SIGN_IN, null, offered)
            }
            ssh.newSFTPClient().use { sftp ->
                val attributes = sftp.statExistence(access.folder)
                if (attributes == null || attributes.type != FileMode.Type.DIRECTORY) {
                    return ProbeResult(ServerCheck.NO_FOLDER, null, offered)
                }
                val path = access.folder.trimEnd('/') + "/" + TEST_FILE
                return try {
                    sftp.open(path, EnumSet.of(net.schmizz.sshj.sftp.OpenMode.WRITE, net.schmizz.sshj.sftp.OpenMode.CREAT)).close()
                    sftp.rm(path)
                    ProbeResult(ServerCheck.OK, null, offered)
                } catch (error: IOException) {
                    ProbeResult(ServerCheck.NOT_WRITABLE, null, offered)
                }
            }
        }
    }

    private fun smb(access: ServerAccess): ProbeResult {
        val parts = access.folder.replace('\\', '/').trim('/').split('/').filter { it.isNotEmpty() }
        val share = parts.firstOrNull() ?: return ProbeResult(ServerCheck.NO_FOLDER, "Type the share and folder, like roms/library.")
        val path = parts.drop(1).joinToString("\\")
        val config =
            SmbConfig.builder().withTimeout(
                TIMEOUT_MS.toLong(),
                TimeUnit.MILLISECONDS,
            ).withSoTimeout(TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS).build()
        SMBClient(config).use { client ->
            client.connect(access.host, access.port).use { connection ->
                val session =
                    try {
                        val (domain, user) =
                            access.username.split(
                                '\\',
                                limit = 2,
                            ).let { if (it.size == 2) it[0] to it[1] else null to it[0] }
                        connection.authenticate(AuthenticationContext(user, access.password.toCharArray(), domain))
                    } catch (error: com.hierynomus.mssmb2.SMBApiException) {
                        return ProbeResult(ServerCheck.SIGN_IN)
                    }
                session.use {
                    val disk =
                        try {
                            session.connectShare(share) as? DiskShare
                                ?: return ProbeResult(ServerCheck.NO_FOLDER, "\"$share\" is not a folder share.")
                        } catch (error: com.hierynomus.mssmb2.SMBApiException) {
                            return ProbeResult(ServerCheck.NO_FOLDER, "The share \"$share\" was not found or is not allowed.")
                        }
                    disk.use {
                        if (path.isNotEmpty() && !disk.folderExists(path)) return ProbeResult(ServerCheck.NO_FOLDER)
                        val test = if (path.isEmpty()) TEST_FILE else "$path\\$TEST_FILE"
                        return try {
                            disk.openFile(
                                test,
                                EnumSet.of(AccessMask.GENERIC_WRITE, AccessMask.DELETE),
                                null,
                                SMB2ShareAccess.ALL,
                                SMB2CreateDisposition.FILE_OVERWRITE_IF,
                                null,
                            ).close()
                            disk.rm(test)
                            ProbeResult(ServerCheck.OK)
                        } catch (error: com.hierynomus.mssmb2.SMBApiException) {
                            ProbeResult(ServerCheck.NOT_WRITABLE)
                        }
                    }
                }
            }
        }
    }
}
