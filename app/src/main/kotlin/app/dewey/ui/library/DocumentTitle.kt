package app.dewey.ui.library

import app.dewey.index.DateExpander
import java.time.LocalDate
import java.util.Locale

/**
 * Builds the library row's title from what FieldExtractor found, and formats
 * the amount for the row's trailing slot.
 *
 * Pulled out as plain functions - no Compose, no Android - so the fallback
 * matrix (no vendor, no date, no amount, vendor with no date) is a JVM test
 * rather than something only visible by eyeballing a running app.
 */

/**
 * "Lydec · janvier 2023", or just "Lydec" with no date, or null with no
 * vendor at all - the same "leave it honest" rule [Document.title] already
 * used for an unclassified document, extended to a classified one that the
 * extractor still found nothing to name in the text.
 */
fun documentTitle(vendor: String?, issueDate: LocalDate?, dueDate: LocalDate?): String? {
    val name = vendor?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val date = issueDate ?: dueDate
    return if (date != null) "$name · ${monthYear(date, name)}" else name
}

/** The row's trailing figure, or null when there is nothing to show. */
fun formatAmount(amount: Double?): String? =
    amount?.let { String.format(Locale.US, "%,.2f", it) }

/**
 * Picks the month name table by the vendor string's own script rather than
 * the document's (unpopulated - see Document.language) language field: a
 * Moroccan Arabic bill's vendor line is itself written in Arabic, so it is
 * the one signal actually available at this point. Anything else - French,
 * English, a vendor OCR mangled into gibberish - defaults to French, which
 * is what the large majority of this corpus is.
 */
private fun monthYear(date: LocalDate, vendor: String): String {
    val months = if (containsArabicScript(vendor)) DateExpander.MONTHS_AR else DateExpander.MONTHS_FR
    return "${months[date.monthValue - 1]} ${date.year}"
}

private fun containsArabicScript(text: String): Boolean =
    text.any { it.code in 0x0600..0x06FF || it.code in 0xFB50..0xFEFF }
