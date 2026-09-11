package app.dewey.widgets.bills

import app.dewey.domain.model.Document
import app.dewey.ui.bills.dueDateWords
import app.dewey.ui.bills.formatBillAmount
import app.dewey.ui.bills.groupBills
import java.time.LocalDate

/**
 * What the Bills widget draws, decided independently of Glance so it can be
 * unit-tested on the JVM without an AppWidgetManager.
 *
 * [Locked] when the paid tier is not active, [Empty] when it is but nothing is
 * due, [Rows] otherwise - the same three-way shape
 * [app.dewey.widgets.notes.NotesWidgetContent] uses, so both widgets fail the
 * same way when there is nothing to show.
 */
sealed interface BillsWidgetContent {
    data object Locked : BillsWidgetContent
    data object Empty : BillsWidgetContent
    data class Rows(val totalCount: Int, val bills: List<BillWidgetRow>) : BillsWidgetContent
}

/** One row's worth of a bill: who it is, what it costs, and "due in 5 days" style wording. */
data class BillWidgetRow(val vendor: String, val amount: String, val dueWords: String)

/** How many rows the widget ever shows at once - the header still reports the true total. */
private const val MAX_BILL_ROWS = 3

/**
 * Picks the bills the widget shows.
 *
 * Reuses [groupBills] rather than re-deriving its ordering: that function
 * already sorts Overdue before Due soon before Later, each bucket itself
 * soonest-due-date-first, and every overdue due date is necessarily earlier
 * than every not-yet-due one - so flattening its sections in order already
 * is "soonest due date first, overdue included", with no extra sort needed
 * here.
 */
fun billsWidgetContent(documents: List<Document>, isEntitled: Boolean, today: LocalDate): BillsWidgetContent {
    if (!isEntitled) return BillsWidgetContent.Locked

    val bills = groupBills(documents, today).flatMap { it.documents }
    if (bills.isEmpty()) return BillsWidgetContent.Empty

    val rows = bills.take(MAX_BILL_ROWS).map { it.toWidgetRow(today) }
    return BillsWidgetContent.Rows(totalCount = bills.size, bills = rows)
}

private fun Document.toWidgetRow(today: LocalDate): BillWidgetRow {
    // Same vendor-or-filename fallback as app.dewey.ui.notes.NoteFormatting
    // and app.dewey.ui.home.HomeGlanceSection - a bill with no vendor should
    // never render with a blank name.
    val vendorName = vendor?.trim()?.takeIf { it.isNotEmpty() } ?: displayName
    val due = requireNotNull(dueDate) { "groupBills only ever returns documents with a due date" }
    val amountText = amount?.let { formatBillAmount(it, currency) }.orEmpty()
    return BillWidgetRow(vendor = vendorName, amount = amountText, dueWords = dueDateWords(due, today))
}
