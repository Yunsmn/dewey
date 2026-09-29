package app.dewey.ui.tools.thumbnails

/**
 * [items] with the entry at [from] moved to [to], leaving everything else in
 * its relative order.
 *
 * Shared by [PageGrid]'s drag-and-drop reorder mode and Merge's file-list
 * reordering — both are "pick this up, drop it there" over a plain list, and
 * the arithmetic does not care whether the items are page indices or picked
 * files.
 *
 * Out-of-bounds is a no-op rather than a thrown exception: a drag gesture and
 * a stale accessibility action can both report an index a frame after it
 * stopped being valid, and that should do nothing rather than crash the tool.
 */
fun <T> moveItem(items: List<T>, from: Int, to: Int): List<T> {
    if (from !in items.indices || to !in items.indices) return items
    return items.toMutableList().apply { add(to, removeAt(from)) }
}
