package app.dewey.ui.tools.pages

import app.dewey.ui.tools.PickedFile

/** Whether there is enough chosen to merge — the same floor `PageOperations.merge` enforces. */
fun canRunMerge(files: List<PickedFile>): Boolean = files.size >= 2

/** "Merged 3 PDFs into one 41-page document." */
fun mergeSummary(fileCount: Int, resultPageCount: Int): String =
    "Merged $fileCount PDFs into one $resultPageCount-page document"

/** [items] with the entry at [index] moved one position earlier; a no-op at the top. */
fun <T> moveUp(items: List<T>, index: Int): List<T> = move(items, index, index - 1)

/** [items] with the entry at [index] moved one position later; a no-op at the bottom. */
fun <T> moveDown(items: List<T>, index: Int): List<T> = move(items, index, index + 1)

/**
 * [items] with the entry at [index] removed.
 *
 * Immutable per house style: this returns a new list rather than mutating
 * [items], the same as [moveUp] and [moveDown] below it.
 */
fun <T> removeAt(items: List<T>, index: Int): List<T> =
    items.filterIndexed { i, _ -> i != index }

/**
 * The shared arithmetic behind [moveUp] and [moveDown]: swap the entry at
 * [from] into position [to], leaving everything else in its relative order.
 * Out-of-bounds is a no-op rather than a thrown exception — a screen calls
 * this from a button that should already be disabled at the edges, but a
 * stale click landing a frame late shouldn't crash the tool.
 */
private fun <T> move(items: List<T>, from: Int, to: Int): List<T> {
    if (from !in items.indices || to !in items.indices) return items
    return items.toMutableList().apply { add(to, removeAt(from)) }
}
