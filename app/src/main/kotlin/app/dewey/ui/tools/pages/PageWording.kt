package app.dewey.ui.tools.pages

/**
 * "1 page" or "4 pages" — the plural every page-tool summary reads naturally
 * with. Shared rather than repeated because five tools all end in a sentence
 * that needs it.
 */
fun pageWord(count: Int): String = if (count == 1) "1 page" else "$count pages"
