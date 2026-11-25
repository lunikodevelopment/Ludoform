package dev.lunikodevelopment.ludoform.transfer

import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.mssmb2.SMBApiException
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.SmbConfig
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import dev.lunikodevelopment.ludoform.data.library.ServerCheck
import dev.lunikodevelopment.ludoform.data.library.UploadProtocol
import net.schmizz.sshj.DefaultConfig
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.common.Buffer
import net.schmizz.sshj.common.KeyType
import net.schmizz.sshj.sftp.FileMode
import net.schmizz.sshj.sftp.OpenMode
import net.schmizz.sshj.sftp.SFTPClient
import net.schmizz.sshj.transport.TransportException
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import net.schmizz.sshj.userauth.UserAuthException
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.security.PublicKey
import java.security.Security
import java.util.EnumSet
import java.util.concurrent.TimeUnit

/**
 * A signed-in connection to an upload server's RomM folder (D36, D37). Paths are relative to that folder and
 * `/`-separated. Blocking; used from WorkManager jobs only.
 */
interface RemoteFolder : Closeable {
    /** The file's size, or null when nothing is there. */
    fun size(path: String): Long?

    fun isFolder(path: String): Boolean

    /** Creates [path] and any missing folders above it. */
    fun makeFolders(path: String)

    /**
     * Writes [input] into [path] from [offset] (0 starts the file afresh). [onWritten] gets the bytes written so far
     * in this call and returns false to stop. Returns false when stopped.
     */
    fun write(
        path: String,
        offset: Long,
        input: InputStream,
        onWritten: (Long) -> Boolean,
    ): Boolean

    fun rename(
        from: String,
        to: String,
    )

    fun delete(path: String)
}

/** Opening a [RemoteFolder]: either it is open, or the server refused in a way the player has to fix. */
sealed interface RemoteOpen {
    data class Opened(val folder: RemoteFolder, val offeredKey: String?) : RemoteOpen

    data class Refused(val result: ProbeResult) : RemoteOpen
}

object RemoteFolders {
    private const val TIMEOUT_MS = 10_000
    private const val BUFFER = 256 * 1024

    init {
        // Android's own "BC" provider is a stripped copy without the curves and ciphers SSH servers use.
        Security.removeProvider("BC")
        Security.insertProviderAt(BouncyCastleProvider(), 1)
    }

    /** Connects, signs in and checks the RomM folder exists. Network failures are thrown as [IOException]. */
    fun open(access: ServerAccess): RemoteOpen =
        when (access.protocol) {
            UploadProtocol.SFTP -> sftp(access)
            UploadProtocol.SMB -> smb(access)
        }

    private fun sftp(access: ServerAccess): RemoteOpen {
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
        val ssh = SSHClient(DefaultConfig())
        var opened = false
        try {
            ssh.connectTimeout = TIMEOUT_MS
            ssh.timeout = TIMEOUT_MS
            ssh.addHostKeyVerifier(verifier)
            try {
                ssh.connect(access.host, access.port)
            } catch (error: TransportException) {
                val key = offered ?: return RemoteOpen.Refused(ProbeResult(ServerCheck.FAILED, "The secure connection failed."))
                return RemoteOpen.Refused(ProbeResult(HostKeys.refusal(access.trustedKey, key) ?: ServerCheck.FAILED, null, key))
            }
            try {
                ssh.authPassword(access.username, access.password)
            } catch (error: UserAuthException) {
                return RemoteOpen.Refused(ProbeResult(ServerCheck.SIGN_IN, null, offered))
            }
            val sftp = ssh.newSFTPClient()
            val root = access.folder.trimEnd('/').ifEmpty { "/" }
            val attributes = sftp.statExistence(root)
            if (attributes == null || attributes.type != FileMode.Type.DIRECTORY) {
                sftp.close()
                return RemoteOpen.Refused(ProbeResult(ServerCheck.NO_FOLDER, null, offered))
            }
            opened = true
            return RemoteOpen.Opened(SftpFolder(ssh, sftp, root), offered)
        } finally {
            if (!opened) runCatching { ssh.close() }
        }
    }

    private class SftpFolder(
        private val ssh: SSHClient,
        private val sftp: SFTPClient,
        private val root: String,
    ) : RemoteFolder {
        private fun full(path: String) = if (path.isEmpty()) root else root.trimEnd('/') + "/" + path

        override fun size(path: String): Long? = sftp.statExistence(full(path))?.takeIf { it.type == FileMode.Type.REGULAR }?.size

        override fun isFolder(path: String): Boolean = sftp.statExistence(full(path))?.type == FileMode.Type.DIRECTORY

        override fun makeFolders(path: String) = sftp.mkdirs(full(path))

        override fun write(
            path: String,
            offset: Long,
            input: InputStream,
            onWritten: (Long) -> Boolean,
        ): Boolean {
            val modes =
                if (offset == 0L) EnumSet.of(OpenMode.WRITE, OpenMode.CREAT, OpenMode.TRUNC) else EnumSet.of(OpenMode.WRITE, OpenMode.CREAT)
            sftp.open(full(path), modes).use { file ->
                file.RemoteFileOutputStream(offset, 16).use { output ->
                    val buffer = ByteArray(BUFFER)
                    var written = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) return true
                        output.write(buffer, 0, read)
                        written += read
                        if (!onWritten(written)) return false
                    }
                }
            }
            @Suppress("UNREACHABLE_CODE")
            return true
        }

        override fun rename(
            from: String,
            to: String,
        ) = sftp.rename(full(from), full(to))

        override fun delete(path: String) = sftp.rm(full(path))

        override fun close() {
            runCatching { sftp.close() }
            runCatching { ssh.close() }
        }
    }

    private fun smb(access: ServerAccess): RemoteOpen {
        val parts = access.folder.replace('\\', '/').trim('/').split('/').filter { it.isNotEmpty() }
        val share =
            parts.firstOrNull()
                ?: return RemoteOpen.Refused(ProbeResult(ServerCheck.NO_FOLDER, "Type the share and folder, like roms/library."))
        val root = parts.drop(1).joinToString("\\")
        val config =
            SmbConfig.builder()
                .withTimeout(TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
                .withSoTimeout(TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
                .build()
        val client = SMBClient(config)
        var opened = false
        try {
            val connection = client.connect(access.host, access.port)
            val session =
                try {
                    val (domain, user) =
                        access.username.split('\\', limit = 2).let { if (it.size == 2) it[0] to it[1] else null to it[0] }
                    connection.authenticate(AuthenticationContext(user, access.password.toCharArray(), domain))
                } catch (error: SMBApiException) {
                    return RemoteOpen.Refused(ProbeResult(ServerCheck.SIGN_IN))
                }
            val disk =
                try {
                    session.connectShare(share) as? DiskShare
                        ?: return RemoteOpen.Refused(ProbeResult(ServerCheck.NO_FOLDER, "\"$share\" is not a folder share."))
                } catch (error: SMBApiException) {
                    return RemoteOpen.Refused(ProbeResult(ServerCheck.NO_FOLDER, "The share \"$share\" was not found or is not allowed."))
                }
            if (root.isNotEmpty() && !disk.folderExists(root)) {
                disk.close()
                return RemoteOpen.Refused(ProbeResult(ServerCheck.NO_FOLDER))
            }
            opened = true
            return RemoteOpen.Opened(SmbFolder(client, connection, session, disk, root), null)
        } finally {
            if (!opened) runCatching { client.close() }
        }
    }

    private class SmbFolder(
        private val client: SMBClient,
        private val connection: Connection,
        private val session: Session,
        private val disk: DiskShare,
        private val root: String,
    ) : RemoteFolder {
        private fun full(path: String): String {
            val relative = path.replace('/', '\\').trim('\\')
            return listOf(root, relative).filter { it.isNotEmpty() }.joinToString("\\")
        }

        override fun size(path: String): Long? {
            val target = full(path)
            return try {
                if (!disk.fileExists(target)) null else disk.getFileInformation(target).standardInformation.endOfFile
            } catch (error: SMBApiException) {
                null
            }
        }

        override fun isFolder(path: String): Boolean =
            try {
                disk.folderExists(full(path))
            } catch (error: SMBApiException) {
                false
            }

        override fun makeFolders(path: String) {
            var current = root
            for (segment in path.split('/').filter { it.isNotEmpty() }) {
                current = if (current.isEmpty()) segment else "$current\\$segment"
                if (!disk.folderExists(current)) disk.mkdir(current)
            }
        }

        override fun write(
            path: String,
            offset: Long,
            input: InputStream,
            onWritten: (Long) -> Boolean,
        ): Boolean {
            val disposition = if (offset == 0L) SMB2CreateDisposition.FILE_OVERWRITE_IF else SMB2CreateDisposition.FILE_OPEN_IF
            disk.openFile(full(path), EnumSet.of(AccessMask.GENERIC_WRITE), null, SMB2ShareAccess.ALL, disposition, null).use { file ->
                val buffer = ByteArray(BUFFER)
                var written = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) return true
                    var sent = 0
                    while (sent < read) sent += file.write(buffer, offset + written + sent, sent, read - sent).toInt()
                    written += read
                    if (!onWritten(written)) return false
                }
            }
            @Suppress("UNREACHABLE_CODE")
            return true
        }

        override fun rename(
            from: String,
            to: String,
        ) {
            disk.openFile(
                full(from),
                EnumSet.of(AccessMask.DELETE, AccessMask.GENERIC_READ),
                null,
                SMB2ShareAccess.ALL,
                SMB2CreateDisposition.FILE_OPEN,
                null,
            ).use { it.rename(full(to), false) }
        }

        override fun delete(path: String) = disk.rm(full(path))

        override fun close() {
            runCatching { disk.close() }
            runCatching { session.close() }
            runCatching { connection.close() }
            runCatching { client.close() }
        }
    }
}
