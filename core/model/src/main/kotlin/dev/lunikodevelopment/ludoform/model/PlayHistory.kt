package dev.lunikodevelopment.ludoform.model

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Wording for play time and last-played dates, as the Home, Console picker and Details boards show them. */
object PlayHistory {
    private const val MINUTE = 60_000L
    private const val HOUR = 60 * MINUTE

    /** "4h 32m", "12m", "61 hours" past 24 hours, "Under a minute" for anything shorter than a minute. */
    fun playTime(millis: Long): String {
        val hours = millis / HOUR
        val minutes = (millis % HOUR) / MINUTE
        return when {
            millis < MINUTE -> "Under a minute"
            hours >= 24 -> "$hours hours"
            hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
            hours > 0 -> "${hours}h"
            else -> "${minutes}m"
        }
    }

    /** "today", "yesterday", "3 days ago" within a week, then "12 Mar" this year or "12 Mar 2025" before. */
    fun lastPlayed(
        at: Long,
        now: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): String {
        val day = Instant.ofEpochMilli(at).atZone(zone).toLocalDate()
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val days = ChronoUnit.DAYS.between(day, today)
        return when {
            days <= 0 -> "today"
            days == 1L -> "yesterday"
            days < 7 -> "$days days ago"
            day.year == today.year -> day.format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH))
            else -> day.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH))
        }
    }
}
