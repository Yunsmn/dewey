package app.dewey.extract

import java.time.LocalDate

/**
 * What the field extractor could read out of a document's plain text.
 *
 * Every field is independently nullable. A document that yields an amount but
 * no vendor is a valid, honest result, not a partial failure - the bills
 * dashboard is expected to render a missing field as blank rather than as
 * zero or "unknown". Guessing wrong is worse than admitting the text did not
 * say: a fabricated due date tells someone their bill is later, or earlier,
 * than it really is.
 */
data class ExtractedFields(
    val vendor: String? = null,
    val amount: Double? = null,
    val currency: String? = null,
    val issueDate: LocalDate? = null,
    val dueDate: LocalDate? = null,
)
