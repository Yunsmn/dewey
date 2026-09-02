package app.dewey.index

/**
 * Makes a document findable by how people say its dates, not how it prints them.
 *
 * Measured on the test corpus, a quarter of documents print only `2023-08-14`
 * while their natural description is "the receipt from aout". Neither embeddings
 * nor keyword search can bridge that, because the word is not in the document.
 * So the spoken forms are appended to the text that gets indexed. The file is
 * never modified - only the index grows.
 *
 * Worth 5 to 7 points of recall@1 in tools/eval/retrieval_bench.py.
 */
object DateExpander {

    private val MONTHS_FR = arrayOf(
        "janvier", "fevrier", "mars", "avril", "mai", "juin",
        "juillet", "aout", "septembre", "octobre", "novembre", "decembre",
    )

    private val MONTHS_EN = arrayOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December",
    )

    /**
     * Moroccan month names, which are not the Levantine ones. A Moroccan bill
     * uses the Maghrebi set, and indexing the wrong one would help nobody.
     */
    private val MONTHS_AR = arrayOf(
        "يناير", "فبراير", "مارس", "أبريل", "ماي", "يونيو",
        "يوليوز", "غشت", "شتنبر", "أكتوبر", "نونبر", "دجنبر",
    )

    private val ISO_DATE = Regex("""\b(\d{4})-(\d{1,2})-(\d{1,2})\b""")
    private val SLASH_DATE = Regex("""\b(\d{1,2})/(\d{1,2})/(\d{4})\b""")
    private val DOT_DATE = Regex("""\b(\d{1,2})\.(\d{1,2})\.(\d{4})\b""")

    /**
     * A bank statement is hundreds of transaction dates. Expanding every one
     * would bury the document's actual content under generated filler, so the
     * distinct months are capped.
     */
    private const val MAX_EXPANSIONS = 45

    /** Every spoken form of every date found, in order, without duplicates. */
    fun expansions(text: String): List<String> {
        val seen = LinkedHashSet<String>()

        for (match in ISO_DATE.findAll(text)) {
            val (year, month, _) = match.destructured
            add(seen, year, month.toIntOrNull())
            if (seen.size >= MAX_EXPANSIONS) return seen.toList()
        }

        // Day first, because this corpus is Moroccan: 03/04/2024 is April.
        // Guessing wrong here costs a slightly worse search, whereas guessing
        // wrong in the extraction layer costs a wrong due date - which is why
        // that decision lives there and not here.
        for (regex in arrayOf(SLASH_DATE, DOT_DATE)) {
            for (match in regex.findAll(text)) {
                val (_, month, year) = match.destructured
                add(seen, year, month.toIntOrNull())
                if (seen.size >= MAX_EXPANSIONS) return seen.toList()
            }
        }

        return seen.toList()
    }

    /** The text as it should be indexed: the original, plus the spoken dates. */
    fun augment(text: String): String {
        val extra = expansions(text)
        return if (extra.isEmpty()) text else buildString {
            append(text)
            append('\n')
            extra.joinTo(this, " ")
        }
    }

    private fun add(into: MutableSet<String>, year: String, month: Int?) {
        if (month == null || month !in 1..12) return
        val index = month - 1
        into += "${MONTHS_FR[index]} $year"
        into += "${MONTHS_EN[index]} $year"
        into += "${MONTHS_AR[index]} $year"
    }
}
