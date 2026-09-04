package app.dewey.extract

/**
 * Line splitting shared by [AmountExtractor] and [DateFieldExtractor].
 *
 * A label is always matched against a single line, never a joined window - a
 * long wrapped French sentence is enough to make a label two paragraphs away
 * read as though it were next to an unrelated figure (see [AmountExtractor]'s
 * class doc). Only once a specific line is confirmed to anchor a label does
 * the search ever widen, and only forward, to reach a value the renderer put
 * on the next line or two: every Arabic document in the test corpus renders
 * "label : value" as separate extracted lines, because pdf_render.py draws
 * each direction run as its own text-showing operation for mixed
 * Arabic/Latin content.
 */
internal object TextLines {

    fun nonBlank(text: String): List<String> =
        text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()

    /** [start] joined with up to [size] - 1 following lines. */
    fun forwardWindow(lines: List<String>, start: Int, size: Int = 3): String {
        val end = minOf(start + size, lines.size)
        return lines.subList(start, end).joinToString(" ")
    }
}
