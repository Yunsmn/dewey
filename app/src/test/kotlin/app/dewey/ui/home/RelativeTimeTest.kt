package app.dewey.ui.home

import com.google.common.truth.Truth.assertThat
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Test

/**
 * relativeTime's wording at each boundary - see the function doc for why the
 * zone is a parameter rather than the system default, and why calendar day
 * rather than raw elapsed time decides which family of wording applies.
 */
class RelativeTimeTest {

    private val zone = ZoneId.of("UTC")
    private val now = at(2026, 9, 10, 12, 0)

    @Test
    fun `under a minute reads as just now`() {
        val createdAt = at(2026, 9, 10, 11, 59, 35)

        assertThat(relativeTime(createdAt, now, zone)).isEqualTo("Just now")
    }

    @Test
    fun `a few minutes reads in minutes`() {
        val createdAt = at(2026, 9, 10, 11, 55)

        assertThat(relativeTime(createdAt, now, zone)).isEqualTo("5 min ago")
    }

    @Test
    fun `an hour and a half, same day, reads in whole hours`() {
        val createdAt = at(2026, 9, 10, 10, 30)

        assertThat(relativeTime(createdAt, now, zone)).isEqualTo("1 h ago")
    }

    @Test
    fun `just before midnight reads as yesterday, not as an hour count`() {
        val createdAt = at(2026, 9, 9, 23, 50)
        val justAfterMidnight = at(2026, 9, 10, 0, 20)

        assertThat(relativeTime(createdAt, justAfterMidnight, zone)).isEqualTo("Yesterday")
    }

    @Test
    fun `two calendar days back reads in days`() {
        val createdAt = at(2026, 9, 8, 9, 0)

        assertThat(relativeTime(createdAt, now, zone)).isEqualTo("2 d ago")
    }

    @Test
    fun `six calendar days back is still a day count`() {
        val createdAt = at(2026, 9, 4, 9, 0)

        assertThat(relativeTime(createdAt, now, zone)).isEqualTo("6 d ago")
    }

    @Test
    fun `a week or more falls back to a short date`() {
        val createdAt = at(2026, 9, 1, 9, 0)

        assertThat(relativeTime(createdAt, now, zone)).isEqualTo("1 Sep")
    }

    @Test
    fun `a file from a previous year still reads as a short date, with no year`() {
        val createdAt = at(2025, 12, 25, 9, 0)

        assertThat(relativeTime(createdAt, now, zone)).isEqualTo("25 Dec")
    }

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int = 0): Long =
        ZonedDateTime.of(year, month, day, hour, minute, second, 0, zone).toInstant().toEpochMilli()
}
