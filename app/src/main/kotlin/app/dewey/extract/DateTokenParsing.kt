package app.dewey.extract

import app.dewey.index.DateExpander
import java.time.DateTimeException
import java.time.LocalDate

/**
 * Finds a single calendar date within a line of text and parses it.
 *
 * Three shapes are recognised. ISO (`2023-08-14`) is unambiguous. Slash and
 * dot forms (`03/04/2024`, `15.11.2022`) are not - this corpus is Moroccan,
 * so day comes first, the same call DateExpander already makes and for the
 * same reason: guessing wrong here costs a wrong due date, not just a
 * slightly worse search. A spelled-out month (`14 aout 2023`, `14 غشت 2023`)
 * is also recognised, reusing DateExpander's Moroccan month vocabulary so the
 * two lists stay identical by construction instead of by comment.
 *
 * A line can hold more than one date - "Periode : du 2024-01-01 au
 * 2024-01-28" has two - so [firstDate] returns the leftmost, which for a
 * French date range is the start.
 */
internal object DateTokenParsing {

    private val ISO = Regex("""\b(\d{4})-(\d{1,2})-(\d{1,2})\b""")
    private val SLASH = Regex("""\b(\d{1,2})/(\d{1,2})/(\d{4})\b""")
    private val DOT = Regex("""\b(\d{1,2})\.(\d{1,2})\.(\d{4})\b""")

    private val monthLookup: Map<String, Int> = buildMap {
        for (months in listOf(DateExpander.MONTHS_FR, DateExpander.MONTHS_EN, DateExpander.MONTHS_AR)) {
            months.forEachIndexed { index, name -> put(name.lowercase(), index + 1) }
        }
    }

    private val SPELLED = Regex(
        """\b(\d{1,2})\s+(${monthLookup.keys.joinToString("|") { Regex.escape(it) }})\s+(\d{4})""",
        RegexOption.IGNORE_CASE,
    )

    /** The leftmost date in [line], if any. Never throws on a malformed one. */
    fun firstDate(line: String): LocalDate? {
        isoDate(line)?.let { return it }
        slashDate(line)?.let { return it }
        dotDate(line)?.let { return it }
        spelledDate(line)?.let { return it }
        return null
    }

    private fun isoDate(line: String): LocalDate? {
        val match = ISO.find(line) ?: return null
        val (year, month, day) = match.destructured
        return toDate(year.toInt(), month.toIntOrNull(), day.toIntOrNull())
    }

    private fun slashDate(line: String): LocalDate? {
        val match = SLASH.find(line) ?: return null
        val (day, month, year) = match.destructured
        return toDate(year.toInt(), month.toIntOrNull(), day.toIntOrNull())
    }

    private fun dotDate(line: String): LocalDate? {
        val match = DOT.find(line) ?: return null
        val (day, month, year) = match.destructured
        return toDate(year.toInt(), month.toIntOrNull(), day.toIntOrNull())
    }

    private fun spelledDate(line: String): LocalDate? {
        val match = SPELLED.find(line) ?: return null
        val (day, monthName, year) = match.destructured
        val month = monthLookup[monthName.lowercase()]
        return toDate(year.toInt(), month, day.toIntOrNull())
    }

    private fun toDate(year: Int, month: Int?, day: Int?): LocalDate? {
        if (month == null || day == null) return null
        return try {
            LocalDate.of(year, month, day)
        } catch (e: DateTimeException) {
            null // 31 April, month 13, year 0000 - a malformed date, not a crash.
        }
    }
}
