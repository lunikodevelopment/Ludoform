package dev.lunikodevelopment.ludoform

import dev.lunikodevelopment.ludoform.data.RommStatus
import dev.lunikodevelopment.ludoform.romm.RommCheck
import dev.lunikodevelopment.ludoform.romm.RommHealth
import dev.lunikodevelopment.ludoform.romm.RommServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class RommStatusTextTest {
    private val server = RommServer("romm.example.org", "player")

    private fun state(
        health: RommHealth?,
        checking: Boolean = false,
    ) = RommStatusText.state(server, health?.let { RommCheck(it, 1L) }, checking)

    @Test
    fun describesEachStepOfSetup() {
        assertEquals(RommStatus.NOT_SET_UP, RommStatusText.state(null, null, false).status)
        assertFalse(RommStatusText.state(null, null, false).configured)
        assertEquals(RommStatus.NOT_CHECKED, state(null).status)
        assertEquals(RommStatus.CHECKING, state(RommHealth.Connected("5.4.1", "player"), checking = true).status)
        val connected = state(RommHealth.Connected("5.4.1", "player"))
        assertEquals(RommStatus.CONNECTED, connected.status)
        assertEquals("RomM 5.4.1 · signed in as player", connected.detail)
    }

    @Test
    fun problemsAreWrittenForThePlayer() {
        assertEquals("Sign-in failed", state(RommHealth.SignInFailed("5.4.1")).title)
        assertEquals("Sign in again", state(RommHealth.SignInFailed("")).title)
        assertEquals("RomM 4.8.0 is too old", state(RommHealth.TooOld("4.8.0")).title)
        val error = state(RommHealth.ServerError("5.4.1", 502))
        assertEquals(RommStatus.PROBLEM, error.status)
        assertFalse("No status codes on consumer screens", "502" in error.title + error.detail)
    }
}
