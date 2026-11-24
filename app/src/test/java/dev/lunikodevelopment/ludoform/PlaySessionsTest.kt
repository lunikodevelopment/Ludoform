package dev.lunikodevelopment.ludoform

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaySessionsTest {
    private val start = 1_000_000L

    @Test
    fun shortSessionsAreDroppedAndLongOnesCapped() {
        assertNull(PlaySessions.endFor(start, start + 5_000))
        assertEquals(start + 60_000, PlaySessions.endFor(start, start + 60_000))
        assertEquals(start + PlaySessions.MAX_SESSION_MILLIS, PlaySessions.endFor(start, start + 3 * PlaySessions.MAX_SESSION_MILLIS))
    }
}
