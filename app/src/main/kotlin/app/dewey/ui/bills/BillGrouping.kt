package app.dewey.ui.bills

import app.dewey.domain.model.Document
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * How pressing a bill is, relative to today.
 *
 * Declared in display order - [groupBills] walks these entries in order, so
 * this enum's own ordering is what puts Overdue above Due soon above Later.
 */
enum class BillUrgency {
    OVERDUE,
    DUE_SOON,
    LATER,
}

/** A bill is a document with both a due date and an amount - anything else is not one. */
data class BillSection(val urgency: BillUrgency, val documents: List<Document>)

/** A due date this many days out or closer counts as "due soon" rather than "later". */
internal const val DUE_SOON_DAYS = 14L

/**
 * Groups bills into Overdue / Due soon / Later against [today], soonest due
 * date first throughout.
 *
 * [today] is a parameter rather than `LocalDate.now()` called in here so the
 * three-way boundary - a bill due in exactly 14 days is "due soon", one due
 * in 15 is "later" - is a fact this function can be asked about directly in
 * a JVM test, and so a screen that stays open across midnight does not
 * silently reclassify a bill out from under the person reading it.
 *
 * A document missing either field is dropped rather than trusted to have
 * been filtered upstream (see DocumentDao.observeBills) - this function is
 * the one place "is this even a bill" is decided, and it should give the
 * same answer whether its input came from the DAO or a test's own list.
 */
fun groupBills(documents: List<Document>, today: LocalDate): List<BillSection> {
    val bills = documents
        .filter { it.dueDate != null && it.amount != null }
        .sortedBy { it.dueDate }

    return BillUrgency.entries
        .map { urgency -> BillSection(urgency, bills.filter { urgencyOf(it.dueDate!!, today) == urgency }) }
        .filter { it.documents.isNotEmpty() }
}

private fun urgencyOf(dueDate: LocalDate, today: LocalDate): BillUrgency {
    val daysUntilDue = ChronoUnit.DAYS.between(today, dueDate)
    return when {
        daysUntilDue < 0 -> BillUrgency.OVERDUE
        daysUntilDue <= DUE_SOON_DAYS -> BillUrgency.DUE_SOON
        else -> BillUrgency.LATER
    }
}

/** The section heading's label - see SectionHeading. */
fun BillUrgency.label(): String = when (this) {
    BillUrgency.OVERDUE -> "Overdue"
    BillUrgency.DUE_SOON -> "Due soon"
    BillUrgency.LATER -> "Later"
}
