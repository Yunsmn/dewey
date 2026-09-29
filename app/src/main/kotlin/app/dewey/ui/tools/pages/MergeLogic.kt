package app.dewey.ui.tools.pages

import app.dewey.ui.tools.PickedFile

/** Whether there is enough chosen to merge — the same floor `PageOperations.merge` enforces. */
fun canRunMerge(files: List<PickedFile>): Boolean = files.size >= 2

/** "Merged 3 PDFs into one 41-page document." */
fun mergeSummary(fileCount: Int, resultPageCount: Int): String =
    "Merged $fileCount PDFs into one $resultPageCount-page document"

/**
 * [items] with the entry at [index] removed.
 *
 * Immutable per house style: this returns a new list rather than mutating
 * [items]. Dragging to reorder the list itself is
 * [app.dewey.ui.tools.thumbnails.moveItem] — shared with
 * [app.dewey.ui.tools.thumbnails.PageGrid]'s reorder mode rather than
 * duplicated here, since both are "pick this up, drop it there" over a plain
 * list.
 */
fun <T> removeAt(items: List<T>, index: Int): List<T> =
    items.filterIndexed { i, _ -> i != index }
