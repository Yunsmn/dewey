package app.dewey.extract

import java.time.LocalDate

/** The two dates a bills dashboard cares about - see [DateFieldExtractor]. */
data class DocumentDates(val issueDate: LocalDate? = null, val dueDate: LocalDate? = null)

/**
 * Separates a document's issue date from its due date.
 *
 * A Lydec bill states only a due date and never repeats when it was issued;
 * an insurance policy states both, a paragraph apart, under different
 * labels. Conflating them is worse than missing one - an issue date shown as
 * a due date tells someone their bill is already late.
 *
 * Two tiers. Tier one is a fixed vocabulary of labels that unambiguously mean
 * "this is the issue date" or "this is the due date" in French, Arabic or
 * English - the same invoice2data-style approach as [AmountExtractor]. Tier
 * two is a narrow fallback for the one shape in the test corpus with no
 * explicit date label at all: a French "Periode : du X au Y" line, where the
 * period's start (X) stands in for the issue date. Nothing is guessed beyond
 * that - a document with an unlabelled date and no range phrasing reports no
 * issue date rather than a coin flip.
 */
internal object DateFieldExtractor {

    private val DUE_LABELS = listOf(
        "date limite de paiement", "date d'echeance", "date d'échéance",
        "echeance de paiement", "échéance de paiement",
        "آخر أجل للأداء", "تاريخ الاستحقاق",
        "due date", "payment due",
    )

    private val ISSUE_LABELS = listOf(
        "date d'achat", "date de consultation", "date de delivrance", "date de délivrance",
        "date d'effet", "date de depot", "date de dépôt", "solde final",
        "fait a", "fait à",
        "date d'emission", "date d'émission", "date d'edition", "date d'édition",
        "emise le", "émise le",
        "تاريخ الفحص", "تاريخ تسليم النسخة", "بتاريخ",
        "date of purchase", "issued on", "date issued", "date of issue",
        // "fait le" alongside "fait a": French documents close with either, and
        // an attestation using the wrong one of the pair was simply unreadable.
        "fait le",
        "تاريخ الإصدار",
    )

    private val PERIOD_LINE = listOf("periode", "période")
    private val RANGE_START = Regex("""\bdu\b""", RegexOption.IGNORE_CASE)

    fun extract(text: String): DocumentDates {
        val lines = TextLines.nonBlank(text)
        var issueDate: LocalDate? = null
        var dueDate: LocalDate? = null
        var rangeFallback: LocalDate? = null

        for (index in lines.indices) {
            val line = lines[index]
            val folded = line.lowercase()

            // The label is matched on this single line only - never a joined
            // window - so a match here is never actually a neighbouring
            // field's label bleeding in. Only once this line is confirmed to
            // anchor a label does the value search widen forward.
            if (dueDate == null && DUE_LABELS.any { folded.contains(it.lowercase()) }) {
                DateTokenParsing.firstDate(TextLines.forwardWindow(lines, index))?.let { dueDate = it }
                continue // a due-labelled line is never also the issue date
            }

            if (issueDate == null && ISSUE_LABELS.any { folded.contains(it.lowercase()) }) {
                DateTokenParsing.firstDate(TextLines.forwardWindow(lines, index))?.let { issueDate = it }
                continue
            }

            if (rangeFallback == null &&
                PERIOD_LINE.any { folded.contains(it) } &&
                RANGE_START.containsMatchIn(line)
            ) {
                DateTokenParsing.firstDate(TextLines.forwardWindow(lines, index))?.let { rangeFallback = it }
            }
        }

        return DocumentDates(issueDate ?: rangeFallback, dueDate)
    }
}
