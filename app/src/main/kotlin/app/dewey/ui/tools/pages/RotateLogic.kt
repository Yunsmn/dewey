package app.dewey.ui.tools.pages

import app.dewey.ui.tools.PickedFile

/**
 * Blank input means "every page" — a deliberate UX choice: the common case is
 * rotating a whole scanned document, and leaving the range field empty reads
 * better than making someone type "1-N" for that.
 */
fun effectiveRotateRange(rangeText: String, pageCount: Int): String =
    rangeText.ifBlank { "1-$pageCount" }

/** A document is chosen, its page count is known, and a quarter-turn is picked. */
fun canRunRotate(file: PickedFile?, pageCount: Int?, degrees: Int?): Boolean =
    file != null && pageCount != null && degrees != null

/** "Rotated 4 pages 90° clockwise." */
fun rotateSummary(rotatedCount: Int, degrees: Int): String =
    "Rotated ${pageWord(rotatedCount)} $degrees° clockwise"
