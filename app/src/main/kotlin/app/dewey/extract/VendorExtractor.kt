package app.dewey.extract

/**
 * The vendor, taken from the document's own letterhead - the first line of
 * real content, which is where every template in the test corpus (and every
 * Moroccan bill outside it) puts the issuing organisation's name.
 *
 * One layout wrinkle earns a special case: several Moroccan administrative
 * documents open with a "Royaume du Maroc - Ministere de l'Interieur"
 * masthead that is identical across residence certificates, birth
 * certificates and more - the office that actually issued this particular
 * document is the line underneath. That masthead (and its Arabic form) is
 * the only line this ever skips; every other document is taken at its first
 * line, unprocessed.
 */
internal object VendorExtractor {

    private val GENERIC_MASTHEADS = setOf(
        "royaume du maroc - ministere de l'interieur",
        "royaume du maroc - ministère de l'intérieur",
        "المملكة المغربية - وزارة الداخلية",
    )

    fun extract(text: String): String? {
        val lines = text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .iterator()

        if (!lines.hasNext()) return null
        var candidate = lines.next()

        if (candidate.lowercase() in GENERIC_MASTHEADS && lines.hasNext()) {
            candidate = lines.next()
        }

        return candidate.ifBlank { null }
    }
}
