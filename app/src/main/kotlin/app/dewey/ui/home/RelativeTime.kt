package app.dewey.ui.home

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * "2 h ago", "Yesterday", or a short date once a file is old enough that a
 * day count stops being useful.
 *
 * Calendar day decides which family of wording applies, not raw elapsed
 * time - a file from 23:58 read at 00:02 the next day is "Yesterday", not
 * "4 min ago", because the day it was made and the day it is being looked at
 * are not the same one. Within a single calendar day the reverse is true:
 * elapsed time is what reads naturally ("Just now", "5 min ago", "2 h ago").
 *
 * [zone] and both timestamps are parameters rather than the system clock and
 * default zone read in here, so a JVM test can pin every boundary - a
 * minute, an hour, midnight, a week - without depending on where or when it
 * runs. The month name is fixed to English rather than read from the
 * device's locale: this row's other wording ("Just now", "min ago") is
 * English regardless of what a bill's own vendor text is written in, and the
 * date should not be the one word that disagrees.
 */
fun relativeTime(createdAt: Long, now: Long, zone: ZoneId = ZoneId.systemDefault()): String {
    val elapsed = (now - createdAt).coerceAtLeast(0)
    val createdDate = Instant.ofEpochMilli(createdAt).atZone(zone).toLocalDate()
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()

    if (createdDate == today) {
        return when {
            elapsed < MINUTE_MILLIS -> "Just now"
            elapsed < HOUR_MILLIS -> "${elapsed / MINUTE_MILLIS} min ago"
            else -> "${elapsed / HOUR_MILLIS} h ago"
        }
    }

    return when (val daysAgo = ChronoUnit.DAYS.between(createdDate, today)) {
        1L -> "Yesterday"
        in 2L..RECENT_DAYS_WINDOW -> "$daysAgo d ago"
        else -> createdDate.toShortDate()
    }
}

private const val MINUTE_MILLIS = 60_000L
private const val HOUR_MILLIS = 60 * MINUTE_MILLIS

/** A day count past this many reads as a date instead - a week's worth of "N d ago" is as far as a count stays useful. */
private const val RECENT_DAYS_WINDOW = 6L

private fun LocalDate.toShortDate(): String =
    "$dayOfMonth ${month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)}"
