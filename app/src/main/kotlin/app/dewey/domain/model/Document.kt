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
    /**
     * The folder this document was filed into, if it was.
     *
     * Carried alongside [docType] rather than derived from it because the two
     * can disagree, and when they do the folder wins. A category learned from
     * the user's own filing — "Voiture", "Immigration" — has a folder and no
     * [DocType] at all, and it is still the truthful answer to what the
     * document is. See app.dewey.classify.CategoryLearner.
     */
    val sortedFolder: String? = null,
    /**
     * Why this document is waiting, as a
     * [app.dewey.classify.DocumentClassifier.Verdict.Reason] name, or the
     * mover's own words when a move was refused. Null when nothing is wrong.
     */
    val reviewReason: String? = null,
) {
    val isIndexed: Boolean get() = indexedAt != null

    /**
     * What to call this document's category.
     *
     * The folder first: it is either the user's own word for it or the app's,
     * and either way it is what they will see in their file manager.
     */
    fun categoryLabel(unfiled: String): String = sortedFolder ?: unfiled
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
