package app.dewey.ui.tools.sign

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import app.dewey.ui.theme.Dewey

/**
 * Everything drawn on a [SignaturePad] so far: one stroke per finger-down to
 * finger-up, each a list of the raw points it passed through.
 *
 * A plain class rather than a ViewModel — the strokes never outlive the
 * composable that draws them, and nothing outside this screen needs to
 * observe them mid-draw, only the finished bitmap once "Done" is pressed. See
 * [version]: mutating [strokes] in place, as every point added does, would
 * otherwise never trigger a redraw — Compose reacts to a *state* value
 * actually changing, not to a mutation buried inside a plain list it merely
 * holds a reference to.
 */
class SignaturePadState {
    private val strokes = mutableListOf<MutableList<Offset>>()

    var version by mutableStateOf(0)
        private set

    /** False for an empty pad, or one that only ever saw taps too short to leave a line. */
    val hasInk: Boolean get() = strokes.any { it.isNotEmpty() }

    fun beginStroke(point: Offset) {
        strokes += mutableListOf(point)
        version++
    }

    fun extendStroke(point: Offset) {
        strokes.lastOrNull()?.add(point)
        version++
    }

    fun clear() {
        strokes.clear()
        version++
    }

    /** One smoothed [Path] per stroke, for [SignaturePad] to draw. */
    fun paths(): List<Path> = strokes.map { it.toSmoothedPath() }

    /**
     * Renders the strokes onto a transparent [width]x[height] bitmap, ink in
     * [inkColor] at [strokeWidthPx] — the signature this pad is for, ready to
     * hand to [app.dewey.pdf.stampSignature] or [saveSignature].
     */
    fun toBitmap(width: Int, height: Int, inkColor: Color, strokeWidthPx: Float): Bitmap {
        val bitmap = Bitmap.createBitmap(width.coerceAtLeast(1), height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val canvas = AndroidCanvas(bitmap)
        val paint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            strokeWidth = strokeWidthPx
            color = inkColor.toArgb()
        }
        for (path in paths()) {
            canvas.drawPath(path.asAndroidPath(), paint)
        }
        return bitmap.croppedToInk(strokeWidthPx)
    }

    /**
     * This bitmap trimmed to the drawn strokes plus a stroke's width of margin.
     *
     * The pad is far wider than most signatures; left uncropped, its empty
     * margins would be placed and scaled along with the ink, so the signature
     * lands smaller than it looks and off-centre from where it was dropped.
     */
    private fun Bitmap.croppedToInk(strokeWidthPx: Float): Bitmap {
        val points = strokes.flatten()
        if (points.isEmpty()) return this
        val margin = strokeWidthPx
        val left = (points.minOf { it.x } - margin).toInt().coerceIn(0, width - 1)
        val top = (points.minOf { it.y } - margin).toInt().coerceIn(0, height - 1)
        val right = (points.maxOf { it.x } + margin).toInt().coerceIn(left + 1, width)
        val bottom = (points.maxOf { it.y } + margin).toInt().coerceIn(top + 1, height)
        return Bitmap.createBitmap(this, left, top, right - left, bottom - top)
    }
}

/**
 * A single stroke's points, smoothed into one continuous [Path] by drawing a
 * quadratic curve through the midpoint of each consecutive pair, rather than
 * connecting the raw points with straight segments — the standard fix for
 * finger input's own jitter, which a polyline through raw touch samples shows
 * as a visibly faceted line rather than a signature's usual smooth stroke.
 */
private fun List<Offset>.toSmoothedPath(): Path {
    val path = Path()
    if (isEmpty()) return path

    if (size == 1) {
        // A tap rather than a drag: draw a dot no straight line would leave a
        // mark for, exploiting the pad's round stroke cap.
        val point = this[0]
        path.moveTo(point.x, point.y)
        path.lineTo(point.x, point.y)
        return path
    }

    path.moveTo(this[0].x, this[0].y)
    for (i in 1 until size - 1) {
        val current = this[i]
        val next = this[i + 1]
        val midpoint = Offset((current.x + next.x) / 2f, (current.y + next.y) / 2f)
        path.quadraticTo(current.x, current.y, midpoint.x, midpoint.y)
    }
    path.lineTo(this[size - 1].x, this[size - 1].y)
    return path
}

/** Also used by [SignToolScreen] to compute the same width when freezing the drawn strokes into a bitmap. */
const val SIGNATURE_STROKE_WIDTH_FRACTION = 0.012f

/**
 * A pad to draw a signature on with a finger: ink in [Dewey.colors.ink] on
 * [Dewey.colors.paperRaised], stroke width scaled to the pad's own size so it
 * reads as the same pen thickness on a phone or a tablet.
 */
@Composable
fun SignaturePad(state: SignaturePadState, modifier: Modifier = Modifier) {
    val ink = Dewey.colors.ink
    val paper = Dewey.colors.paperRaised

    Canvas(
        modifier = modifier
            .clip(RoundedCornerShape(Dewey.radii.medium))
            .background(paper)
            .pointerInput(state) {
                detectDragGestures(
                    onDragStart = { offset -> state.beginStroke(offset) },
                    onDrag = { change, _ ->
                        change.consume()
                        state.extendStroke(change.position)
                    },
                )
            },
    ) {
        // Read explicitly so the draw phase redraws when a stroke changes —
        // paths() alone reads through a plain list Compose cannot see into.
        @Suppress("UNUSED_VARIABLE") val redrawOn = state.version

        val strokeWidthPx = size.minDimension * SIGNATURE_STROKE_WIDTH_FRACTION
        for (path in state.paths()) {
            drawPath(
                path = path,
                color = ink,
                style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
    }
}
