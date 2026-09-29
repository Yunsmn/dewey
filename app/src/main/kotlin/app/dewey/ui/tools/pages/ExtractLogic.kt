package app.dewey.ui.tools.pages

import app.dewey.ui.tools.PickedFile

/** A document is chosen, its page count is known, and at least one page is checked. */
fun canRunExtract(file: PickedFile?, pageCount: Int?, selected: Set<Int>): Boolean =
    file != null && pageCount != null && selected.isNotEmpty()

/** "Extracted 4 pages into a new document." */
fun extractSummary(extractedCount: Int): String =
    "Extracted ${pageWord(extractedCount)} into a new document"
