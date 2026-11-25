package dev.lunikodevelopment.ludoform

import dev.lunikodevelopment.ludoform.data.RommOffline
import dev.lunikodevelopment.ludoform.data.RommOutage
import dev.lunikodevelopment.ludoform.ui.OfflineText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OfflineTextTest {
    private val down = RommOffline(RommOutage.UNREACHABLE, since = 0, nextTryAt = 102_000)

    @Test
    fun countdownCheckingAndNoNetwork() {
        assertEquals("Next try in 1:42", OfflineText.nextTry(down, online = true, checking = false, now = 0))
        assertEquals("Checking…", OfflineText.nextTry(down, online = true, checking = true, now = 0))
        assertEquals("Checking…", OfflineText.nextTry(down, online = true, checking = false, now = 200_000))
        assertEquals("Waiting for a network", OfflineText.nextTry(down, online = false, checking = false, now = 0))
        assertNull(OfflineText.nextTry(null, online = true, checking = false, now = 0))
    }

    @Test
    fun titlesSayWhatWentWrong() {
        assertEquals("Can't reach your server", OfflineText.title(down, online = true))
        assertEquals("RomM refused the sign-in", OfflineText.title(down.copy(outage = RommOutage.SIGN_IN), online = true))
        assertEquals("You're offline", OfflineText.title(down, online = false))
        assertEquals(
            "romm.example.org didn't answer. Installed games still play, and saves sync when it's back.",
            OfflineText.detail(down, "romm.example.org", online = true),
        )
        assertEquals("3 saves waiting", OfflineText.savesWaiting(3))
        assertNull(OfflineText.savesWaiting(0))
    }
}
