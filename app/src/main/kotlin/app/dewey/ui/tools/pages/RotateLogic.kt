package app.dewey.ui.tools.pages

import app.dewey.ui.tools.PickedFile

/** A document is chosen, its page count is known, at least one page is checked, and a quarter-turn is picked. */
fun canRunRotate(file: PickedFile?, pageCount: Int?, selected: Set<Int>, degrees: Int?): Boolean =
    file != null && pageCount != null && selected.isNotEmpty() && degrees != null

/** "Rotated 4 pages 90° clockwise." */
fun rotateSummary(rotatedCount: Int, degrees: Int): String =
    "Rotated ${pageWord(rotatedCount)} $degrees° clockwise"
