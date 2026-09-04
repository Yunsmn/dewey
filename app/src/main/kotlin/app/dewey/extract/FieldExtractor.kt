package app.dewey.extract

import java.text.Normalizer

/**
 * Pulls vendor, amount, currency and dates out of a document's already
 * extracted plain text - no network call, no model, a handful of regexes and
 * label lists. See tools/eval/extract_bench.py for the measured numbers: the
 * brief called this a cloud job, but it is rule-based here for the same
 * reason classification is (see [app.dewey.classify.DocumentClassifier]) -
 * the failures a rule set has are legible (a missing label) rather than
 * opaque (a wrong model output), and reading invoice2data's approach - a
 * small vocabulary of phrases tried in priority order - teaches more about
 * real layout chaos than reaching for a model would.
 */
object FieldExtractor {

    fun extract(text: String): ExtractedFields {
        if (text.isBlank()) return ExtractedFields()

        // PDF text with shaped Arabic glyphs comes back as presentation-form
        // codepoints (U+FB50-FEFF) rather than the base letters "غشت" or
        // "آخر أجل للأداء" are written with - pypdf and PDFBox both hand this
        // back unnormalised. NFKC folds the glyphs to their base letters,
        // without which none of the Arabic label lists below would ever
        // match. Latin and digit text passes through unchanged.
        val normalised = Normalizer.normalize(text, Normalizer.Form.NFKC)

        val amount = AmountExtractor.extract(normalised)
        val dates = DateFieldExtractor.extract(normalised)

        return ExtractedFields(
            vendor = VendorExtractor.extract(normalised),
            amount = amount?.value,
            currency = amount?.currency,
            issueDate = dates.issueDate,
            dueDate = dates.dueDate,
        )
    }
}
