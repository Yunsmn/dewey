package app.dewey.ui.tools.raster

/**
 * Moves the item at [from] to [to], leaving the relative order of everything
 * else intact. Out-of-range indices are a no-op rather than a crash: a screen
 * driving this from a tap on an up/down affordance can end up calling it right
 * at either edge of the list.
 *
 * Returns a new list, never mutates [images] — the order a user has already
 * chosen is never something to silently change out from under them.
 */
fun <T> reorderImage(images: List<T>, from: Int, to: Int): List<T> {
    if (from == to || from !in images.indices || to !in images.indices) return images
    val reordered = images.toMutableList()
    val moved = reordered.removeAt(from)
    reordered.add(to, moved)
    return reordered
}

/** Drops the item at [index]. Out-of-range is a no-op, matching [reorderImage]. */
fun <T> removeImage(images: List<T>, index: Int): List<T> {
    if (index !in images.indices) return images
    val remaining = images.toMutableList()
    remaining.removeAt(index)
    return remaining
}
