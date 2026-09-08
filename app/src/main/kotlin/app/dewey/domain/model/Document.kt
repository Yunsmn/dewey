package app.dewey.domain.model

import java.time.LocalDate

/**
 * A document Dewey knows about.
 *
 * [uri] is a SAF content URI string, never a filesystem path. The Storage Access
 * Framework does not hand out paths, and a document the user granted access to
 * may live on a provider that has no path at all — Drive, or a USB volume. Code
 * that wants bytes goes through ContentResolver.
 */
data class Document(
    val id: Long = 0,
    val uri: String,
    val displayName: String,
    val sizeBytes: Long,
    val lastModified: Long,
    val pageCount: Int = 0,
    val docType: DocType = DocType.UNKNOWN,
    val language: String? = null,
    val textSource: TextSource = TextSource.NONE,
    val indexedAt: Long? = null,
    /**
     * See app.dewey.extract.FieldExtractor. Every field is independently
     * nullable - a document with an amount but no vendor is a valid result.
     */
    val vendor: String? = null,
    val amount: Double? = null,
    val currency: String? = null,
    val issueDate: LocalDate? = null,
    val dueDate: LocalDate? = null,
) {
    val isIndexed: Boolean get() = indexedAt != null
}

/**
 * Where a document's text came from. Worth recording rather than inferring:
 * OCR'd text is materially less reliable than a PDF's own text layer, and
 * extraction should be able to say so instead of pretending both are equal.
 */
enum class TextSource {
    /** Not extracted yet. */
    NONE,

    /** The PDF carried its own text layer. */
    EMBEDDED,

    /** Rendered to a bitmap and read by ML Kit. */
    OCR,

    /** Tried both and got nothing usable. */
    FAILED,
}

enum class DocType {
    UNKNOWN,
    UTILITY_BILL,
    BANK_STATEMENT,
    INVOICE,
    RENTAL_CONTRACT,
    MEDICAL,
    UNIVERSITY,
    INSURANCE,
    EMPLOYMENT,
    TAX,
    WARRANTY,
    ADMIN,
    TRAVEL,
    PAPER,
}
