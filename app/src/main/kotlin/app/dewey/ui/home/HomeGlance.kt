package app.dewey.ui.home

import app.dewey.domain.model.Document
import app.dewey.ui.library.readable
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * The Home "At a glance" figures, as plain functions - same reasoning as
 * app.dewey.ui.bills.BillGrouping: the windowing and ranking rules are worth
 * a JVM test, not just a glance at the running app.
 */

/**
 * How many days out still counts as "due soon" for this card.
 *
 * Wider than app.dewey.ui.bills.BillGrouping's DUE_SOON_DAYS on purpose: that
 * screen is a worklist somebody scrolls through bucket by bucket, and this is
 * a single summary figure meant to answer "is anything coming up" at a
 * glance, which wants a longer horizon.
 */
internal const val GLANCE_WINDOW_DAYS = 30L

/** The nearest bill due within the glance window, and how many share it. */
data class BillsGlance(val dueSoonCount: Int, val soonest: Document)

/**
 * Bills due between today and [GLANCE_WINDOW_DAYS] days out, today included.
 *
 * An overdue bill does not count here - it is a different situation from one
 * still coming due, and Home is not the screen that sorts between the two
 * (see app.dewey.ui.bills.BillGrouping for that). A document only counts as a
 * bill at all when it has both a due date and an amount, the same rule
 * groupBills applies - restated here rather than reused because the two
 * screens' windows differ.
 */
fun billsGlance(documents: List<Document>, today: LocalDate): BillsGlance? {
    val dueSoon = documents
        .filter { it.dueDate != null && it.amount != null }
        .filter { document ->
            val daysUntilDue = ChronoUnit.DAYS.between(today, document.dueDate)
            daysUntilDue in 0..GLANCE_WINDOW_DAYS
        }
        .sortedBy { it.dueDate }

    val soonest = dueSoon.firstOrNull() ?: return null
    return BillsGlance(dueSoonCount = dueSoon.size, soonest = soonest)
}

/** One category and how many documents are filed under it. */
data class CategoryCount(val label: String, val count: Int)

/** How many categories the Home glance shows by default. */
internal const val TOP_CATEGORIES_LIMIT = 4

/**
 * The busiest categories, most documents first and alphabetical on a tie -
 * the same tiebreak app.dewey.ui.library.LibraryViewModel uses for its own
 * sections, so a category's rank never disagrees between the two screens.
 */
fun topCategories(documents: List<Document>, limit: Int = TOP_CATEGORIES_LIMIT): List<CategoryCount> =
    documents
        .groupBy { it.categoryLabel(unfiled = it.docType.readable()) }
        .map { (label, docs) -> CategoryCount(label, docs.size) }
        .sortedWith(compareByDescending<CategoryCount> { it.count }.thenBy { it.label })
        .take(limit)
