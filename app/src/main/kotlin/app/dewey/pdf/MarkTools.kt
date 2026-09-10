package app.dewey.pdf

import android.content.ContentResolver
import android.net.Uri
import android.util.Log
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import com.tom_roush.pdfbox.util.Matrix

/**
 * Stamping text onto every page of a PDF: a diagonal watermark, or a running
 * page number.
 *
 * Pages are handled one at a time — opened, drawn on, closed, moved past —
 * and never collected into a list. A [PDPageContentStream] left open holds a
 * buffer of pending drawing operators, and [PDDocument.getPage] pulls a page
 * into memory whether or not the caller keeps a reference to it; a 300-page
 * statement processed by accumulating either would hold the better part of
 * the whole document's drawn state on the heap at once for no reason, right
 * up until a single `document.save()` at the end wants all of it flushed
 * together. Writing each page immediately keeps the working set to one page.
 *
 * The actual placement math lives in MarkGeometry.kt as plain arithmetic, so
 * it can be checked against page sizes and rotations without PDFBox.
 */

private const val TAG = "MarkTools"

private const val DEFAULT_WATERMARK_OPACITY = 0.15f
private const val DEFAULT_WATERMARK_ANGLE_DEGREES = 45f
private const val DEFAULT_WATERMARK_FONT_SIZE = 48f

// Mid-gray rather than black: at DEFAULT_WATERMARK_OPACITY a black watermark
// still reads as a dark smudge over light content, where a mid-gray one at
// the same opacity sits closer to a true wash and is easier to see through.
private const val WATERMARK_GRAY = 0.6f

private const val DEFAULT_PAGE_NUMBER_FONT_SIZE = 10f

// Half an inch, the common margin for a running header/footer in print.
private const val DEFAULT_MARGIN = 36f

/**
 * Draws [text] once across every page, rotated by [angleDegrees] and centred,
 * at [opacity] so the page's own content stays legible underneath it.
 *
 * The content stream is opened in append mode with the page's existing
 * graphics context reset first — see the constructor call below — so the
 * watermark cannot inherit a leftover fill colour, clip path, or unbalanced
 * `q`/`Q` nesting from whatever produced the original page, and painting the
 * watermark cannot corrupt that original content either.
 */
suspend fun watermark(
    workspace: PdfWorkspace,
    resolver: ContentResolver,
    source: Uri,
    target: Uri,
    sizeBytes: Long,
    text: String,
    opacity: Float = DEFAULT_WATERMARK_OPACITY,
    angleDegrees: Float = DEFAULT_WATERMARK_ANGLE_DEGREES,
    fontSize: Float = DEFAULT_WATERMARK_FONT_SIZE,
): Result<Unit> = workspace.read(source, sizeBytes) { document ->
    // Caught here, not left to PdfWorkspace.read's own catch: that one reports
    // every failure inside the block as Unreadable, which would misdescribe a
    // document that opened fine and failed while being drawn on.
    runStamping(source) { stampWatermark(document, text, opacity, angleDegrees, fontSize) }
        ?: saveOpenDocument(document, resolver, target)
}.flatten()

/**
 * Stamps a page number on every page in [corner], starting the count at
 * [startingNumber]. With [showTotal], each stamp reads "N / total" where
 * total is the document's actual page count — independent of
 * [startingNumber], so numbering a document that starts at, say, 5 still
 * reports the real total rather than total + 4.
 */
suspend fun pageNumbers(
    workspace: PdfWorkspace,
    resolver: ContentResolver,
    source: Uri,
    target: Uri,
    sizeBytes: Long,
    corner: Corner = Corner.BOTTOM_CENTER,
    startingNumber: Int = 1,
    showTotal: Boolean = false,
    fontSize: Float = DEFAULT_PAGE_NUMBER_FONT_SIZE,
    margin: Float = DEFAULT_MARGIN,
): Result<Unit> = workspace.read(source, sizeBytes) { document ->
    runStamping(source) { stampPageNumbers(document, corner, startingNumber, showTotal, fontSize, margin) }
        ?: saveOpenDocument(document, resolver, target)
}.flatten()

/**
 * Runs a stamping step, returning a named [PdfWorkspace.Failure] instead of
 * letting a drawing failure escape to PdfWorkspace.read's own catch, which
 * would report it as an unreadable *source* document — wrong for a failure
 * that happened while writing to an already-successfully-opened one.
 */
private fun runStamping(source: Uri, block: () -> Unit): Result<Unit>? =
    try {
        block()
        null
    } catch (e: Exception) {
        Log.w(TAG, "Could not stamp $source", e)
        Result.failure(PdfToolException(PdfWorkspace.Failure.CouldNotWrite(e.message ?: "could not stamp")))
    }

private fun stampWatermark(
    document: PDDocument,
    text: String,
    opacity: Float,
    angleDegrees: Float,
    fontSize: Float,
) {
    val font = PDType1Font.HELVETICA_BOLD
    val textWidth = font.getStringWidth(text) / 1000f * fontSize
    // PDFBox has no cheap measured height for an arbitrary string — only the
    // font's own metrics per glyph — so the font size itself stands in for
    // text height. Close enough for centring a watermark; a few points of
    // slack from ascenders/descenders is not visible at this opacity.
    val textHeight = fontSize

    for (page in document.pages) {
        // mediaBox, not cropBox: a watermark should cover the full physical
        // page a printer or PDF viewer renders, not just whatever narrower
        // region a scanner or prior tool left as the visible crop.
        val box = page.mediaBox
        val placement = watermarkPlacement(box.width, box.height, textWidth, textHeight, angleDegrees)

        PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true).use { stream ->
            stream.saveGraphicsState()

            val transparency = PDExtendedGraphicsState()
            transparency.setNonStrokingAlphaConstant(opacity)
            stream.setGraphicsStateParameters(transparency)

            stream.beginText()
            stream.setFont(font, fontSize)
            stream.setNonStrokingColor(WATERMARK_GRAY, WATERMARK_GRAY, WATERMARK_GRAY)
            stream.setTextMatrix(
                Matrix.getRotateInstance(Math.toRadians(angleDegrees.toDouble()), placement.x, placement.y)
            )
            stream.showText(text)
            stream.endText()

            stream.restoreGraphicsState()
        }
    }
}

private fun stampPageNumbers(
    document: PDDocument,
    corner: Corner,
    startingNumber: Int,
    showTotal: Boolean,
    fontSize: Float,
    margin: Float,
) {
    val font = PDType1Font.HELVETICA
    val total = document.numberOfPages

    for (index in 0 until total) {
        val page = document.getPage(index)
        val label = formatPageNumber(startingNumber + index, total, showTotal)
        val textWidth = font.getStringWidth(label) / 1000f * fontSize
        val box = page.mediaBox
        val position = pageNumberPosition(corner, box.width, box.height, margin, textWidth)

        PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true).use { stream ->
            stream.beginText()
            stream.setFont(font, fontSize)
            stream.setNonStrokingColor(0f, 0f, 0f)
            stream.newLineAtOffset(position.x, position.y)
            stream.showText(label)
            stream.endText()
        }
    }
}

