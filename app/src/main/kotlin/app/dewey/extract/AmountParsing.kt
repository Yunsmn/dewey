package app.dewey.extract

/**
 * Turns a matched number token into a [Double], without assuming which
 * character means "thousands" and which means "decimal".
 *
 * A Moroccan French bill writes `1 234,56`; an English-styled receipt writes
 * `1,234.56`. Assuming one gets the other wrong by three orders of magnitude
 * rather than merely imprecise, so the two separators are read off the string
 * itself rather than off a locale guess.
 */
internal object AmountParsing {

    // Space, no-break space and narrow no-break space: the three characters a
    // French-formatted amount uses for thousands grouping.
    private val GROUPING_CHARS = charArrayOf(' ', ' ', ' ')

    /** Null on anything that is not a parseable number - never throws. */
    fun parse(raw: String): Double? {
        var text = raw.trim()
        if (text.isEmpty()) return null

        val negative = text.startsWith("-")
        if (negative) text = text.substring(1)
        if (text.isEmpty()) return null

        for (char in GROUPING_CHARS) {
            text = text.replace(char.toString(), "")
        }

        val lastComma = text.lastIndexOf(',')
        val lastDot = text.lastIndexOf('.')

        val normalised = when {
            lastComma == -1 && lastDot == -1 -> text

            // Both present: whichever comes last is the decimal mark
            // ("1,234.56" -> dot decides; "1.234,56" -> comma decides) and
            // the other is thousands grouping, dropped rather than kept.
            lastComma != -1 && lastDot != -1 -> if (lastComma > lastDot) {
                text.replace(".", "").replace(',', '.')
            } else {
                text.replace(",", "")
            }

            // Only a comma: the Moroccan French decimal mark.
            lastComma != -1 -> text.replace(',', '.')

            // Only a dot: already the shape Kotlin's parser expects.
            else -> text
        }

        val value = normalised.toDoubleOrNull() ?: return null
        return if (negative) -value else value
    }
}
