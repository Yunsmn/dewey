package app.dewey.ui.tools.pages

import app.dewey.ui.tools.PickedFile

/**
 * A document is chosen, its page count is known, at least one page is
 * checked, and it is not every page — deleting all of them is refused here,
 * before a run, the same way [app.dewey.pdf.PageOperations.parsePageDeletion]
 * refuses it as [app.dewey.pdf.PageOperations.Issue.WouldEmptyDocument] if
 * this check is ever bypassed.
 */
fun canRunDelete(file: PickedFile?, pageCount: Int?, selected: Set<Int>): Boolean {
    if (file == null || pageCount == null) return false
    return selected.isNotEmpty() && selected.size < pageCount
}

/** "Deleted 2 pages; 9 remain." */
fun deleteSummary(deletedCount: Int, remainingCount: Int): String {
    val remainsOrRemain = if (remainingCount == 1) "remains" else "remain"
    return "Deleted ${pageWord(deletedCount)}; ${pageWord(remainingCount)} $remainsOrRemain"
}
