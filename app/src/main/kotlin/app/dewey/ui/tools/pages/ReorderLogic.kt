package app.dewey.ui.tools.pages

import app.dewey.ui.tools.PickedFile

/** Every page, 0-based, in its original order — [PageGrid][app.dewey.ui.tools.thumbnails.PageGrid]'s starting arrangement and what "Reset order" returns to. */
fun identityOrder(pageCount: Int): List<Int> = (0 until pageCount).toList()

/**
 * A document is chosen, a page order is known, and it differs from
 * [identityOrder] — dragging pages back to where they started shouldn't
 * leave the run button enabled for a save that would change nothing.
 */
fun canRunReorder(file: PickedFile?, order: List<Int>?, pageCount: Int?): Boolean {
    if (file == null || order == null || pageCount == null) return false
    return order != identityOrder(pageCount)
}

/** What a Reorder run reports — the details already showed up live as the pages moved, so there is nothing left to count. */
fun reorderSummary(): String = "Saved the new page order."
