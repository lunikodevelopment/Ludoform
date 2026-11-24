package dev.lunikodevelopment.ludoform.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

class PlayHistoryTest {
    private val minute = 60_000L

    @Test
    fun playTimeMatchesTheBoards() {
        assertEquals("Under a minute", PlayHistory.playTime(59_000))
        assertEquals("12m", PlayHistory.playTime(12 * minute))
        assertEquals("4h 32m", PlayHistory.playTime((4 * 60 + 32) * minute))
        assertEquals("2h", PlayHistory.playTime(120 * minute))
        assertEquals("61 hours", PlayHistory.playTime(61 * 60 * minute + 5 * minute))
    }

    @Test
    fun lastPlayedCountsCalendarDays() {
        val zone = ZoneOffset.UTC

        fun at(text: String) = LocalDateTime.parse(text).toInstant(zone).toEpochMilli()
        val now = at("2026-11-24T09:00:00")
        assertEquals("today", PlayHistory.lastPlayed(at("2026-11-24T00:10:00"), now, zone))
        assertEquals("yesterday", PlayHistory.lastPlayed(at("2026-11-23T23:50:00"), now, zone))
        assertEquals("3 days ago", PlayHistory.lastPlayed(at("2026-11-21T12:00:00"), now, zone))
        assertEquals("2 Mar", PlayHistory.lastPlayed(at("2026-03-02T12:00:00"), now, zone))
        assertEquals("2 Mar 2025", PlayHistory.lastPlayed(at("2025-03-02T12:00:00"), now, zone))
    }
}
