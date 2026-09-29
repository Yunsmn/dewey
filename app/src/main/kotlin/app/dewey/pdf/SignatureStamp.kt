package app.dewey.pdf

import android.content.ContentResolver
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.util.Matrix

/**
 * Drawing a signature bitmap onto one page of a PDF.
 *
 * The interesting part is not the drawing — a single [PDPageContentStream]
 * call, the same shape as [MarkTools]' stamping — it's [signaturePlacement]
 * below: the position and size a person chose by dragging on a *rendered
 * preview* of the page has to be converted into the page's own PDF user
 * space, which a page's `/Rotate` entry leaves completely alone. `/Rotate` is
 * a display instruction a viewer applies on top of the content stream, not a
 * change to the coordinates the content stream draws in, so a signature
 * dragged onto what the user *saw* lands in the wrong place — turned, and
 * often off the page entirely — unless that rotation is undone first. See
 * SignatureStampTest for the four cases (0/90/180/270) checked by hand.
 */

/** A rectangle in PDF user space: origin bottom-left, units are points. */
data class PdfRect(val x: Float, val y: Float, val width: Float, val height: Float)

/**
 * Converts a signature's position and size — normalized 0..1 against the
 * on-screen preview the user dragged it on, top-left origin, y increasing
 * downward like any bitmap — into the rectangle to draw it at in the page's
 * own box (its mediaBox or cropBox), origin at ([boxOriginX], [boxOriginY]),
 * x right, y up.
 *
 * Each of the rectangle's four corners is mapped independently by
 * [displayedToBoxFraction] and then the bounding box of the results is taken,
 * rather than assuming in advance which corner ends up top-left after
 * rotation — that assumption is exactly what would need to change for each
 * of the four rotations if it were baked in instead.
 */
fun signaturePlacement(
    normalizedX: Float,
    normalizedY: Float,
    normalizedWidth: Float,
    normalizedHeight: Float,
    boxWidth: Float,
    boxHeight: Float,
    boxOriginX: Float,
    boxOriginY: Float,
    rotationDegrees: Int,
): PdfRect {
    val rotation = PageOperations.normalizedRotationDegrees(0, rotationDegrees)
    val corners = listOf(
        normalizedX to normalizedY,
        normalizedX + normalizedWidth to normalizedY,
        normalizedX to normalizedY + normalizedHeight,
        normalizedX + normalizedWidth to normalizedY + normalizedHeight,
    ).map { (u, v) -> displayedToBoxFraction(u, v, rotation) }

    val sMin = corners.minOf { it.first }
    val sMax = corners.maxOf { it.first }
    val tMin = corners.minOf { it.second }
    val tMax = corners.maxOf { it.second }

    return PdfRect(
        x = boxOriginX + sMin * boxWidth,
        y = boxOriginY + tMin * boxHeight,
        width = (sMax - sMin) * boxWidth,
        height = (tMax - tMin) * boxHeight,
    )
}

/**
 * One point of the mapping [signaturePlacement] applies to all four corners:
 * a point at normalized ([u], [v]) on the displayed page (top-left origin, y
 * down) as a normalized fraction ([Pair.first], [Pair.second]) of the page's
 * own box (bottom-left origin, y up), given the page rotates
 * [rotationDegrees] clockwise for display (already normalized to 0/90/180/270).
 *
 * Derived by composing the standard "rotate a raster 90° clockwise" transform
 * — for a [w]x[h] source, `newX = h - y, newY = x` — with the y-flip that
 * gets from PDF's y-up convention to a bitmap's y-down one, once per 90° step
 * of rotation:
 *
 * ```
 * rotation   maps (u, v) displayed-normalized to (s, t) box-normalized
 * 0          s = u,       t = 1 - v
 * 90         s = v,       t = u
 * 180        s = 1 - u,   t = v
 * 270        s = 1 - v,   t = 1 - u
 * ```
 */
private fun displayedToBoxFraction(u: Float, v: Float, rotationDegrees: Int): Pair<Float, Float> =
    when (rotationDegrees) {
        90 -> v to u
        180 -> (1f - u) to v
        270 -> (1f - v) to (1f - u)
        else -> u to (1f - v)
    }

/**
 * Draws [signature] onto 0-based page [pageIndex] of [source], sized and
 * positioned by [normalizedX]/[normalizedY]/[normalizedWidth]/[normalizedHeight]
 * — see [signaturePlacement] for how those, given relative to the *displayed*
 * page, become a rectangle in the page's own space.
 *
 * [LosslessFactory.createFromImage] rather than the JPEG path
 * [RasterTools] uses for photos: a signature is drawn with a transparent
 * background, and JPEG has no alpha channel to keep it with.
 */
suspend fun stampSignature(
    workspace: PdfWorkspace,
    resolver: ContentResolver,
    source: Uri,
    target: Uri,
    sizeBytes: Long,
    pageIndex: Int,
    signature: Bitmap,
    normalizedX: Float,
    normalizedY: Float,
    normalizedWidth: Float,
    normalizedHeight: Float,
): Result<Unit> = workspace.read(source, sizeBytes) { document ->
    runSigning(source) {
        stampOntoPage(document, pageIndex, signature, normalizedX, normalizedY, normalizedWidth, normalizedHeight)
    } ?: saveOpenDocument(document, resolver, target)
}.flatten()

/**
 * Runs the drawing step, returning a named [PdfWorkspace.Failure] instead of
 * letting a drawing failure escape to [PdfWorkspace.read]'s own catch, which
 * would report it as an unreadable *source* — wrong for a failure that
 * happened while writing to a document that opened just fine. Mirrors
 * [MarkTools]' own `runStamping`.
 */
private fun runSigning(source: Uri, block: () -> Unit): Result<Unit>? =
    try {
        block()
        null
    } catch (e: Exception) {
        Log.w(TAG, "Could not sign $source", e)
        Result.failure(PdfToolException(PdfWorkspace.Failure.CouldNotWrite(e.message ?: "could not sign")))
    }

private fun stampOntoPage(
    document: PDDocument,
    pageIndex: Int,
    signature: Bitmap,
    normalizedX: Float,
    normalizedY: Float,
    normalizedWidth: Float,
    normalizedHeight: Float,
) {
    val page = document.getPage(pageIndex)
    // cropBox, not mediaBox: PdfRenderer draws the crop box — the region a
    // viewer actually shows — and that is what the user dragged the
    // signature onto. PDFBox's getCropBox() already falls back to the
    // mediaBox when no crop box is set, so this covers both.
    val box = page.cropBox
    val placement = signaturePlacement(
        normalizedX = normalizedX,
        normalizedY = normalizedY,
        normalizedWidth = normalizedWidth,
        normalizedHeight = normalizedHeight,
        boxWidth = box.width,
        boxHeight = box.height,
        boxOriginX = box.lowerLeftX,
        boxOriginY = box.lowerLeftY,
        rotationDegrees = page.rotation,
    )

    val image = LosslessFactory.createFromImage(document, signature)
    PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true).use { stream ->
        stream.drawImage(image, uprightOnScreen(placement.x, placement.y, placement.width, placement.height, page.rotation))
    }
}

/**
 * The transform that draws the signature into its page-space rectangle so it
 * reads upright once the viewer applies the page's /Rotate.
 *
 * A plain drawImage(x, y, w, h) is always axis-aligned in unrotated page
 * space, so on a page stored sideways the signature would come out sideways
 * and stretched. /Rotate turns the page clockwise for display, so the image is
 * turned the same amount counter-clockwise here; the rectangle's width and
 * height are already the page-space ones, swapped for 90 and 270.
 */
internal fun uprightOnScreen(x: Float, y: Float, w: Float, h: Float, rotationDegrees: Int): Matrix =
    when (((rotationDegrees % 360) + 360) % 360) {
        90 -> Matrix(0f, h, -w, 0f, x + w, y)
        180 -> Matrix(-w, 0f, 0f, -h, x + w, y + h)
        270 -> Matrix(0f, -h, w, 0f, x, y + h)
        else -> Matrix(w, 0f, 0f, h, x, y)
    }

private const val TAG = "SignatureStamp"
