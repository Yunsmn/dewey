package app.dewey.ui.tools.pages

import app.dewey.ui.tools.PickedFile

/** A document is chosen, its page count is known, and a range has been typed. */
fun canRunExtract(file: PickedFile?, pageCount: Int?, rangeText: String): Boolean =
    file != null && pageCount != null && rangeText.isNotBlank()

/** "Extracted 4 pages into a new document." */
fun extractSummary(extractedCount: Int): String =
    "Extracted ${pageWord(extractedCount)} into a new document"
