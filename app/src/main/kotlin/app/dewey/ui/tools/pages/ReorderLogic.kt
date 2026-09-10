package app.dewey.ui.tools.pages

import app.dewey.ui.tools.PickedFile

/**
 * A document is chosen, its page count is known, and both positions the user
 * typed are whole numbers within it. Checked here rather than left entirely
 * to [app.dewey.pdf.PageOperations.parsePageMove] so the run button can stay
 * disabled while someone is still typing a number, instead of only failing
 * once they press it.
 */
fun canRunReorder(file: PickedFile?, pageCount: Int?, fromText: String, toText: String): Boolean {
    if (file == null) return false
    val count = pageCount ?: return false
    val from = fromText.toIntOrNull() ?: return false
    val to = toText.toIntOrNull() ?: return false
    return from in 1..count && to in 1..count
}

/** "Moved page 5 to position 1." */
fun reorderSummary(from: Int, to: Int): String = "Moved page $from to position $to"
