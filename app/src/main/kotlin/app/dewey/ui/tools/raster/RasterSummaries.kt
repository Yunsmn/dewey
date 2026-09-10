package app.dewey.ui.tools.raster

import app.dewey.pdf.CompressionResult
import app.dewey.pdf.PageExport
import app.dewey.ui.tools.formatBytes
import kotlin.math.roundToInt

/**
 * What to tell the user after exporting a PDF's pages to images.
 *
 * [PageExport] exists precisely so a partial export is never reported as a
 * clean one — see its own doc — so this always names which pages (1-based,
 * the way a person counts them) were skipped rather than only saying how many.
 */
fun pdfToImagesSummary(export: PageExport): String {
    if (export.isComplete) {
        return "Saved ${pageCountWords(export.rendered)}."
    }
    val total = export.rendered + export.skipped.size
    val skippedPages = export.skipped.map { it + 1 }.sorted()
    return "Saved ${export.rendered} of $total pages. ${pageListSentence(skippedPages)} couldn't be saved."
}

/**
 * What to tell the user after assembling images into a PDF.
 *
 * [RasterTools.imagesToPdf][app.dewey.pdf.RasterTools.imagesToPdf] skips an
 * image it cannot read rather than failing the whole document, so "placed"
 * can be less than "chosen" on a run that still succeeded.
 */
fun imagesToPdfSummary(placed: Int, chosen: Int): String {
    if (placed >= chosen) {
        return "Placed ${pageCountWords(placed, noun = "image")} into the PDF."
    }
    val skipped = chosen - placed
    return "Placed $placed of $chosen images into the PDF. " +
        "${pageCountWords(skipped, noun = "image")} couldn't be read."
}

/**
 * What to tell the user after compressing a PDF.
 *
 * [CompressionResult] carries the possibility that the result is unverifiable
 * or actually larger than the source — a raster-heavy scan shrinks, a
 * text-heavy PDF does not — and both are said plainly rather than folded into
 * a generic "Done".
 */
fun compressionSummary(result: CompressionResult): String {
    val resultBytes = result.resultBytes
        ?: return "Compressed, but the new size couldn't be checked."

    if (result.isSmaller == false) {
        return "The result came out larger than the original — this PDF is probably already compact."
    }

    val saved = result.originalBytes - resultBytes
    val percent = percentSmaller(result.originalBytes, resultBytes)
    return "Saved ${formatBytes(saved)} ($percent% smaller)."
}

/** Pure so the rounding rule — half up, clamped to a sane range — can be tested without a device. */
internal fun percentSmaller(originalBytes: Long, resultBytes: Long): Int {
    if (originalBytes <= 0) return 0
    val fraction = (originalBytes - resultBytes).toDouble() / originalBytes.toDouble()
    return (fraction * 100).roundToInt().coerceIn(0, 100)
}

private fun pageCountWords(count: Int, noun: String = "page"): String =
    if (count == 1) "1 $noun" else "$count ${noun}s"

/** "Page 7", "Pages 1 and 5", or "Pages 1, 3 and 5" — never a bare list of numbers. */
private fun pageListSentence(pages: List<Int>): String {
    val label = if (pages.size == 1) "Page" else "Pages"
    val list = when (pages.size) {
        1 -> "${pages[0]}"
        else -> "${pages.dropLast(1).joinToString(", ")} and ${pages.last()}"
    }
    return "$label $list"
}
