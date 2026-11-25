package dev.lunikodevelopment.ludoform.transfer

import dev.lunikodevelopment.ludoform.data.library.ServerCheck
import dev.lunikodevelopment.ludoform.data.library.UploadProtocol
import org.apache.sshd.common.file.virtualfs.VirtualFileSystemFactory
import org.apache.sshd.server.SshServer
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider
import org.apache.sshd.sftp.server.SftpSubsystemFactory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** A real SFTP round trip against an embedded Apache MINA SSHD server. */
class ServerProbeTest {
    @get:Rule val temp = TemporaryFolder()
    private lateinit var server: SshServer
    private lateinit var root: File

    @Before
    fun start() {
        root = temp.newFolder("root").apply { resolve("roms").mkdirs() }
        server =
            SshServer.setUpDefaultServer().apply {
                port = 0
                keyPairProvider = SimpleGeneratorHostKeyProvider(temp.newFile("host.ser").toPath())
                setPasswordAuthenticator { user, password, _ -> user == "luna" && password == "secret" }
                subsystemFactories = listOf(SftpSubsystemFactory())
                fileSystemFactory = VirtualFileSystemFactory(root.toPath())
                start()
            }
    }

    @After
    fun stop() = server.stop(true)

    private fun access(
        password: String = "secret",
        folder: String = "/roms",
        trusted: String? = null,
    ) = ServerAccess(UploadProtocol.SFTP, "127.0.0.1", server.port, "luna", password, folder, trusted)

    @Test
    fun firstConnectStopsAtTheHostKeyThenWorksOnceTrusted() {
        val first = ServerProbe.test(access())
        assertEquals(ServerCheck.HOST_KEY_UNKNOWN, first.check)
        val key = assertNotNull(first.offeredKey).let { first.offeredKey!! }
        assertTrue(key, key.contains(" SHA256:"))
        val trusted = ServerProbe.test(access(trusted = key))
        assertEquals(ServerCheck.OK, trusted.check)
        assertFalse("The write test file is removed", root.resolve("roms/.ludoform-write-test").exists())
    }

    @Test
    fun aChangedKeyIsRefused() {
        val result = ServerProbe.test(access(trusted = "ssh-ed25519 SHA256:someOtherKey"))
        assertEquals(ServerCheck.HOST_KEY_CHANGED, result.check)
    }

    @Test
    fun wrongPasswordAndMissingFolderAreNamed() {
        val key = ServerProbe.test(access()).offeredKey
        assertEquals(ServerCheck.SIGN_IN, ServerProbe.test(access(password = "nope", trusted = key)).check)
        assertEquals(ServerCheck.NO_FOLDER, ServerProbe.test(access(folder = "/missing", trusted = key)).check)
    }

    @Test
    fun nothingListeningIsUnreachable() {
        val closed = access().copy(port = 1)
        assertEquals(ServerCheck.UNREACHABLE, ServerProbe.test(closed).check)
    }

    @Test
    fun fingerprintsReadInGroupsOfFour() {
        val key = "ssh-ed25519 SHA256:p3Vx9kQaLw2HtY7cRm0B"
        assertEquals(listOf("p3Vx", "9kQa", "Lw2H", "tY7c", "Rm0B"), HostKeys.groups(key))
        assertEquals("ED25519", HostKeys.typeLabel(key))
        assertEquals("RSA", HostKeys.typeLabel("ssh-rsa SHA256:x"))
        assertEquals("ECDSA", HostKeys.typeLabel("ecdsa-sha2-nistp256 SHA256:x"))
        assertNull(HostKeys.refusal(key, key))
    }
}
