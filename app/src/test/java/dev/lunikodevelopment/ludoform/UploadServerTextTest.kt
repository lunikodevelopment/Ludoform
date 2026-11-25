package dev.lunikodevelopment.ludoform

import dev.lunikodevelopment.ludoform.data.library.ServerCheck
import dev.lunikodevelopment.ludoform.data.library.UploadProtocol
import dev.lunikodevelopment.ludoform.data.library.UploadServerEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UploadServerTextTest {
    private val nas = UploadServerEntity(7, "", UploadProtocol.SMB, "nas.local", 445, "luna", "roms/library")

    @Test
    fun rowsNameTheServerAndOnlyShowPortsThatDiffer() {
        val row = UploadServerText.row(nas)
        assertEquals("nas.local", row.name)
        assertEquals("nas.local", row.address)
        assertEquals("SMB", row.protocol)
        assertEquals("Not tested yet", row.status)
        assertFalse(row.tested)
        assertEquals("homelab:2222", UploadServerText.row(nas.copy(protocol = UploadProtocol.SFTP, host = "homelab", port = 2222)).address)
    }

    @Test
    fun aKeyWaitingToBeTrustedTurnsTheButtonIntoCheckKey() {
        val waiting =
            UploadServerText.row(
                nas.copy(protocol = UploadProtocol.SFTP, check = ServerCheck.HOST_KEY_UNKNOWN, offeredKey = "ssh-ed25519 SHA256:abc"),
            )
        assertEquals("ssh-ed25519 SHA256:abc", waiting.offeredKey)
        assertEquals("Check key", UploadServerText.action(waiting))
        assertTrue(waiting.tested)
        // Once trusted and working, the key is no longer offered for trust.
        val ok =
            UploadServerText.row(
                nas.copy(check = ServerCheck.OK, offeredKey = "ssh-ed25519 SHA256:abc", trustedKey = "ssh-ed25519 SHA256:abc"),
            )
        assertNull(ok.offeredKey)
        assertEquals("Test", UploadServerText.action(ok))
        assertFalse(ok.problem)
    }

    @Test
    fun problemsAreSaidPlainly() {
        assertEquals(
            "Sign-in failed · check the user name and password",
            UploadServerText.row(nas.copy(check = ServerCheck.SIGN_IN)).status,
        )
        assertEquals("The folder can't be written to", UploadServerText.row(nas.copy(check = ServerCheck.NOT_WRITABLE)).status)
        assertTrue(UploadServerText.row(nas.copy(check = ServerCheck.HOST_KEY_CHANGED, offeredKey = "k")).keyChanged)
    }
}
