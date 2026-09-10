package app.dewey.ui.notes

import app.dewey.domain.model.Document
import app.dewey.domain.model.Note
import app.dewey.ui.bills.formatBillAmount
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The note card's body preview, its attached-bill chip label, and the per-bill
 * note count - as plain functions, same reasoning as
 * app.dewey.ui.bills.BillFormatting: worth a JVM test on their own, not just a
 * glance at the running app.
 */

/**
 * A one-line taste of [body]: newlines and runs of whitespace collapsed to a
 * single space, cut to [maxLength] with an ellipsis rather than left to wrap
 * a card into a wall of text.
 */
fun notePreview(body: String, maxLength: Int = 120): String {
    val collapsed = body.trim().replace(Regex("\\s+"), " ")
    if (collapsed.length <= maxLength) return collapsed
    return collapsed.take(maxLength).trimEnd() + "…"
}

/**
 * The attached-bill chip's text: the vendor (or the filename, when extraction
 * found no vendor) and the amount, in the same wording as the bills screen
 * itself - a note about a bill should never disagree with the bill on what
 * to call it.
 */
fun attachedBillLabel(document: Document): String {
    val name = document.vendor?.trim()?.takeIf { it.isNotEmpty() } ?: document.displayName
    val amount = document.amount?.let { formatBillAmount(it, document.currency) }?.takeIf { it.isNotEmpty() }
    return if (amount == null) name else "$name · $amount"
}

/** How many notes are attached to each bill, keyed by the bill's document id. */
fun noteCountsByBill(notes: List<Note>): Map<Long, Int> =
    notes.mapNotNull { it.billDocumentId }.groupingBy { it }.eachCount()

private val PICKER_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

/**
 * A bill picker row's label: vendor (or filename), amount and due date, each
 * omitted when the extractor found nothing for it - a bill missing every
 * field would otherwise read as a bare " · · " rather than just its name.
 */
fun billPickerLabel(document: Document): String {
    val name = document.vendor?.trim()?.takeIf { it.isNotEmpty() } ?: document.displayName
    val amount = document.amount?.let { formatBillAmount(it, document.currency) }?.takeIf { it.isNotEmpty() }
    val due = document.dueDate?.let { "due ${PICKER_DATE_FORMAT.format(it)}" }
    return listOfNotNull(name, amount, due).joinToString(" · ")
}
