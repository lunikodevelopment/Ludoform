package dev.lunikodevelopment.ludoform

import dev.lunikodevelopment.ludoform.data.library.PlaySessionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaySessionsTest {
    private val start = 1_000_000L
    private val minute = 60_000L
    private val hour = 60 * minute

    private fun session(
        bootCount: Int? = 7,
        uptime: Long? = 50_000L,
    ) = PlaySessionEntity(gameId = "g", emulatorPackage = "e", startedAt = start, bootCount = bootCount, startedUptime = uptime)

    private fun at(
        wall: Long,
        uptime: Long = 50_000L + wall,
        boot: Int? = 7,
    ) = DeviceTime(start + wall, uptime, boot)

    @Test
    fun shortSessionsAreDroppedAndLongOnesCapped() {
        assertNull(PlaySessions.endFor(session(), at(5_000)))
        assertEquals(start + minute, PlaySessions.endFor(session(), at(minute)))
        assertEquals(start + PlaySessions.MAX_SESSION_MILLIS, PlaySessions.endFor(session(), at(3 * PlaySessions.MAX_SESSION_MILLIS)))
    }

    @Test
    fun timeAsleepDoesNotCount() {
        // Two hours on the wall clock, 20 minutes awake.
        assertEquals(start + 20 * minute, PlaySessions.endFor(session(), at(2 * hour, uptime = 50_000L + 20 * minute)))
    }

    @Test
    fun mostlyAsleepIsDropped() {
        assertNull(PlaySessions.endFor(session(), at(2 * hour, uptime = 50_000L + 3_000)))
    }

    @Test
    fun aRestartKeepsTheSessionWithoutTime() {
        // The battery ran out mid-game: 149 minutes later Ludoform is shown after a new boot.
        assertEquals(start, PlaySessions.endFor(session(), at(149 * minute, uptime = 10 * minute, boot = 8)))
    }

    @Test
    fun sessionsFromBeforeTheClocksUseTheWallClock() {
        val old = session(bootCount = null, uptime = null)
        assertEquals(start + 30 * minute, PlaySessions.endFor(old, at(30 * minute, uptime = 1, boot = 8)))
    }

    @Test
    fun unknownBootCountFallsBackToTheWallClock() {
        assertEquals(start + 30 * minute, PlaySessions.endFor(session(), at(30 * minute, uptime = 1, boot = null)))
    }
}
