package app.dewey.ui.tools.pages

import app.dewey.ui.tools.PickedFile

/** A document is chosen, its page count is known, and a range has been typed. */
fun canRunDelete(file: PickedFile?, pageCount: Int?, rangeText: String): Boolean =
    file != null && pageCount != null && rangeText.isNotBlank()

/** "Deleted 2 pages; 9 remain." */
fun deleteSummary(deletedCount: Int, remainingCount: Int): String {
    val remainsOrRemain = if (remainingCount == 1) "remains" else "remain"
    return "Deleted ${pageWord(deletedCount)}; ${pageWord(remainingCount)} $remainsOrRemain"
}
