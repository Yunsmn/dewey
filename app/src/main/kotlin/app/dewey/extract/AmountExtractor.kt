package app.dewey.extract

/**
 * The amount, picked from every currency-tagged number in the text rather
 * than "the biggest one" or "the last one".
 *
 * Neither naive rule survives the second most common category in the test
 * corpus: a rental contract states the security deposit right after the
 * rent, and the deposit is always the larger figure and always comes last.
 * So candidates are scored instead - a fixed vocabulary of total/due phrases
 * (French, Arabic, English) scores positive, a fixed vocabulary of
 * subtotal/component/deposit phrases scores negative, and an unlabelled
 * figure scores neutral. The highest-scoring candidate wins; ties go to
 * whichever appears last, since a grand total is conventionally the final
 * figure on a bill. If nothing scores non-negative, nothing is returned - a
 * subtotal offered up as "the amount" is worse than no amount at all.
 *
 * A candidate's score comes first from its own line (or the small pair-window
 * it was found in), and only falls back to the one or two lines *before* it
 * when that line names nothing at all. Own-line evidence has to win first:
 * scoring from a wider window directly is what let a long wrapped French
 * sentence ("... 5000 dirhams, payable d'avance avant le cinq de\nchaque
 * mois... ARTICLE 4 - DEPOT DE GARANTIE...") make the deposit two lines later
 * read as if the rent's own label applied to it. The backward-only fallback
 * exists for Arabic, where the renderer splits "label : value" onto separate
 * lines and the label ends up one or two lines *before* its figure, never
 * after - see [TextLines].
 *
 * This is the invoice2data approach: a small, per-phrase vocabulary tried in
 * priority order, not a model.
 */
internal object AmountExtractor {

    data class Result(val value: Double, val currency: String)

    // A grouped number needs at least one thousands separator to take this
    // branch ("1 234,56", "1,234.56"); plain "281.26" or "15023.26" fall
    // through to the second alternative untouched. Requiring a group (`+`,
    // not `*`) is what keeps the two alternatives from fighting each other.
    private const val NUMBER = """-?\d{1,3}(?:[   ,.]\d{3})+(?:[.,]\d+)?|-?\d+(?:[.,]\d+)?"""

    private const val CURRENCY = """(?:\bMAD\b|\bDH\b|\bdirhams?\b|درهم|دراهم)"""

    private val NUMBER_THEN_CURRENCY = Regex("""($NUMBER)\s*($CURRENCY)""", RegexOption.IGNORE_CASE)
    private val CURRENCY_THEN_NUMBER = Regex("""($CURRENCY)\s*($NUMBER)""", RegexOption.IGNORE_CASE)

    private val POSITIVE_PHRASES = listOf(
        "total a payer", "total à payer", "total a regler", "total à régler",
        "montant total regle", "montant total réglé", "montant total",
        "total amount paid", "solde final", "prime annuelle",
        "salaire mensuel brut", "gratification mensuelle",
        "impot sur le revenu du", "impôt sur le revenu du", "loyer mensuel",
        "المبلغ الإجمالي المستحق", "الوجيبة الشهرية", "الوجيبة الكرائية",
    )

    private val NEGATIVE_PHRASES = listOf(
        "depot de garantie", "dépôt de garantie", "garantie", "franchise",
        "subtotal", "total hors taxe", "value added tax", "tva",
        "consommation hors forfait", "abonnement", "revenus fonciers",
        "revenus salariaux", "deductions", "déductions", "solde initial",
        "الضمانة",
    )

    /** How many lines a candidate is allowed to look backward for a label. */
    private const val BACKWARD_LINES = 2

    /** How many lines a number/currency pair may span (the Arabic split). */
    private const val PAIR_SPAN = 2

    fun extract(text: String): Result? {
        val lines = TextLines.nonBlank(text)
        var best: Candidate? = null
        var bestScore = Int.MIN_VALUE

        for (index in lines.indices) {
            val pairWindow = TextLines.forwardWindow(lines, index, PAIR_SPAN)
            val candidates = candidatesOn(pairWindow)
            if (candidates.isEmpty()) continue

            val score = score(lines, index)
            for (candidate in candidates) {
                // >= so a later tie wins - see the class doc on why "last" is
                // the right tiebreaker for a bill's grand total.
                if (score >= bestScore) {
                    bestScore = score
                    best = candidate
                }
            }
        }

        if (best == null || bestScore < 0) return null
        return Result(best.value, normaliseCurrency(best.currencyToken))
    }

    private data class Candidate(val value: Double, val currencyToken: String)

    private fun candidatesOn(window: String): List<Candidate> {
        val found = mutableListOf<Candidate>()
        for (match in NUMBER_THEN_CURRENCY.findAll(window)) {
            toCandidate(match.groupValues[1], match.groupValues[2])?.let(found::add)
        }
        for (match in CURRENCY_THEN_NUMBER.findAll(window)) {
            toCandidate(match.groupValues[2], match.groupValues[1])?.let(found::add)
        }
        return found
    }

    private fun toCandidate(rawNumber: String, currencyToken: String): Candidate? =
        AmountParsing.parse(rawNumber)?.let { Candidate(it, currencyToken) }

    /** 1, -1, or null - no phrase found at all, distinct from a neutral one. */
    private fun phraseScore(text: String): Int? {
        val folded = text.lowercase()
        return when {
            POSITIVE_PHRASES.any { folded.contains(it.lowercase()) } -> 1
            NEGATIVE_PHRASES.any { folded.contains(it.lowercase()) } -> -1
            else -> null
        }
    }

    private fun score(lines: List<String>, index: Int): Int {
        phraseScore(TextLines.forwardWindow(lines, index, PAIR_SPAN))?.let { return it }
        val behindStart = maxOf(0, index - BACKWARD_LINES)
        val behind = lines.subList(behindStart, index + 1).joinToString(" ")
        return phraseScore(behind) ?: 0
    }

    // Every spelling in CURRENCY names the same coin. Moroccan bills mix
    // "MAD", "DH" and "dirhams" freely (even within one document type - see
    // rental_contract in the test corpus), and a dashboard showing three
    // different currency strings for one currency would be its own bug.
    private fun normaliseCurrency(token: String): String = "MAD"
}
