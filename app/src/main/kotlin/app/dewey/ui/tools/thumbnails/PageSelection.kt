package app.dewey.ui.tools.thumbnails

/**
 * The pure logic behind [PageGrid]'s select mode: which 0-based page indices
 * are checked, independent of how the grid draws them. Kept as functions over
 * a plain [Set] rather than a small state class so a ViewModel's state stays
 * one field — `selected: Set<Int>` — instead of another object threaded
 * through every `copy()`.
 */
fun toggleSelection(selected: Set<Int>, index: Int): Set<Int> =
    if (index in selected) selected - index else selected + index

/** Every page, 0-based — what "Select all" sets [PageGrid]'s selection to. */
fun selectAll(pageCount: Int): Set<Int> = (0 until pageCount).toSet()

/**
 * The comma-separated 1-based spec [app.dewey.pdf.PageOperations] already
 * parses, built from a 0-based selection.
 *
 * Sorted ascending regardless of tap order: a [Set] carries no order of its
 * own, and every select-mode tool (Extract, Delete, Rotate) promises its
 * output keeps page order, not the order pages happened to be tapped in.
 */
fun specFromSelection(selected: Set<Int>): String =
    selected.sorted().joinToString(",") { (it + 1).toString() }
