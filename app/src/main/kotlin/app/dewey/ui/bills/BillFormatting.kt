package app.dewey.ui.bills

import app.dewey.index.DateExpander
import app.dewey.ui.library.formatAmount
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * The bill row's due-date subtitle and amount, as plain functions - same
 * reasoning as app.dewey.ui.library.DocumentTitle: the fallback matrix
 * (overdue by a day, overdue by a year, due today, due soon, due far enough
 * out to want a real date) is worth a JVM test, not just a glance at the
 * running app.
 */

/**
 * "Overdue by 3 days", "Due today", "Due in 5 days", or - once a bill is far
 * enough out that a relative count stops being useful - "Due 12 janvier
 * 2027". The cutoff matches [DUE_SOON_DAYS]: the same point where a bill
 * stops reading as "soon" is where the wording stops being relative too.
 */
fun dueDateWords(dueDate: LocalDate, today: LocalDate): String {
    val daysUntilDue = ChronoUnit.DAYS.between(today, dueDate)
    return when {
        daysUntilDue < 0 -> "Overdue by ${dayCount(-daysUntilDue)}"
        daysUntilDue == 0L -> "Due today"
        daysUntilDue == 1L -> "Due tomorrow"
        daysUntilDue <= DUE_SOON_DAYS -> "Due in ${dayCount(daysUntilDue)}"
        else -> "Due ${dueDate.toLongForm()}"
    }
}

/** The bill row's trailing figure: the amount, with its currency when extraction found one. */
fun formatBillAmount(amount: Double, currency: String?): String {
    val formatted = formatAmount(amount) ?: return ""
    return if (currency.isNullOrBlank()) formatted else "$formatted $currency"
}

private fun dayCount(days: Long): String = if (days == 1L) "1 day" else "$days days"

private fun LocalDate.toLongForm(): String =
    "$dayOfMonth ${DateExpander.MONTHS_FR[monthValue - 1]} $year"
