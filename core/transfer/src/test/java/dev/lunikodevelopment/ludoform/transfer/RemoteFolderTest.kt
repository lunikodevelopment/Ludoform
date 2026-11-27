package dev.lunikodevelopment.ludoform.transfer

import dev.lunikodevelopment.ludoform.data.library.UploadProtocol
import org.apache.sshd.common.file.virtualfs.VirtualFileSystemFactory
import org.apache.sshd.server.SshServer
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider
import org.apache.sshd.sftp.server.SftpSubsystemFactory
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import kotlin.random.Random

/** An upload over SFTP that stops halfway and resumes from the part file, against an embedded server. */
class RemoteFolderTest {
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

    private fun open(): RemoteFolder {
        val access = ServerAccess(UploadProtocol.SFTP, "127.0.0.1", server.port, "luna", "secret", "/roms", null)
        val key = (RemoteFolders.open(access) as RemoteOpen.Refused).result.offeredKey
        return (RemoteFolders.open(access.copy(trustedKey = key)) as RemoteOpen.Opened).folder
    }

    @Test
    fun aStoppedUploadResumesFromItsPartFile() {
        val content = Random(7).nextBytes(1_500_000)
        val part = "psx/Game/" + UploadPlan.partName("Game.bin")
        open().use { folder ->
            assertFalse(folder.isFolder("psx"))
            folder.makeFolders("psx/Game")
            assertTrue(folder.isFolder("psx/Game"))
            assertNull(folder.size("psx/Game/Game.bin"))
            // Stops after the first buffer, as a pause or a dropped connection would.
            val completed = folder.write(part, 0, ByteArrayInputStream(content)) { false }
            assertFalse(completed)
        }
        open().use { folder ->
            val offset = folder.size(part)!!
            assertTrue("Something was sent before the stop", offset in 1 until content.size)
            val rest = ByteArrayInputStream(content, offset.toInt(), content.size - offset.toInt())
            assertTrue(folder.write(part, offset, rest) { true })
            folder.rename(part, "psx/Game/Game.bin")
            assertEquals(content.size.toLong(), folder.size("psx/Game/Game.bin"))
        }
        assertArrayEquals(content, root.resolve("roms/psx/Game/Game.bin").readBytes())
        assertFalse(root.resolve("roms/$part").exists())
    }
}
