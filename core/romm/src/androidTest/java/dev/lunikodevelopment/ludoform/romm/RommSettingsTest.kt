package dev.lunikodevelopment.ludoform.romm

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.lunikodevelopment.ludoform.data.RommOutage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RommSettingsTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun passwordIsEncryptedAtRestAndKeptWhenEditing() {
        // A file of its own: the test must never touch the server the player saved.
        val settings = RommSettings(context, "romm-test")
        try {
            settings.save("romm.example.org", "player", "correct horse")
            val raw = context.getSharedPreferences("romm-test", 0).all.values.joinToString()
            assertFalse("Password stored in the clear", "correct horse" in raw)
            assertEquals("correct horse", settings.password())
            settings.saveCheck(RommCheck(RommHealth.Connected("5.4.1", "player"), 1L))
            assertEquals(RommHealth.Connected("5.4.1", "player"), settings.lastCheck()?.health)
            settings.save("romm2.example.org", "player", null)
            assertEquals("correct horse", settings.password())
            assertNull("An edit clears the last check", settings.lastCheck())
        } finally {
            settings.remove()
        }
        assertNull(settings.server())
    }

    @Test
    fun outagesBackOffAndClear() {
        val settings = RommSettings(context, "romm-test-outage")
        try {
            assertNull(settings.offline())
            assertEquals(30_000L, settings.reportOutage(RommOutage.UNREACHABLE, 1_000))
            assertEquals(60_000L, settings.reportOutage(RommOutage.UNREACHABLE, 31_000))
            val offline = settings.offline()!!
            assertEquals(RommOutage.UNREACHABLE, offline.outage)
            assertEquals("Down since the first failure", 1_000L, offline.since)
            assertEquals(91_000L, offline.nextTryAt)
            repeat(10) { settings.reportOutage(RommOutage.SIGN_IN, 100_000) }
            assertEquals(ROMM_RETRY_STEPS.last(), settings.offline()!!.nextTryAt - 100_000)
            assertEquals(RommOutage.SIGN_IN, settings.offline()!!.outage)
            settings.reportReachable()
            assertNull(settings.offline())
            assertEquals("A new outage starts over", 30_000L, settings.reportOutage(RommOutage.SERVER_ERROR, 5))
        } finally {
            settings.remove()
        }
    }
}
