package app.dewey.ui.tools.sign

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import app.dewey.pdf.Corner
import app.dewey.ui.theme.Dewey
import app.dewey.ui.theme.Hue
import app.dewey.ui.tools.secure.CornerGrid
import kotlin.math.roundToInt

private const val MIN_SIZE_FRACTION = 0.12f
private const val MAX_SIZE_FRACTION = 0.75f

/**
 * The rendered page with the signature dragged onto it.
 *
 * A [BoxWithConstraints] rather than a fixed size: the overlay's pixel math
 * and the drag gesture's own delta both need the box's *actual* on-screen
 * size, known only once it's actually laid out, not a value assumed ahead of
 * that. [placement] itself stays purely normalized (0..1) — see
 * [SignPlacement] — so none of this screen-pixel arithmetic leaks into what
 * is finally handed to [app.dewey.pdf.stampSignature].
 */
@Composable
fun PlacementPreview(
    preview: Bitmap,
    signature: Bitmap,
    placement: SignPlacement,
    lastCorner: Corner,
    onDrag: (deltaXFraction: Float, deltaYFraction: Float) -> Unit,
    onCornerChosen: (Corner) -> Unit,
    onSizeChosen: (Float) -> Unit,
    hue: Hue,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val aspect = preview.width.toFloat() / preview.height.toFloat()

    Column(modifier = modifier) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(aspect)
                .clip(RoundedCornerShape(Dewey.radii.medium))
                .background(Dewey.colors.paperSunken),
        ) {
            val boxWidthPx = with(density) { maxWidth.toPx() }
            val boxHeightPx = with(density) { maxHeight.toPx() }

            Image(bitmap = preview.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize())

            Image(
                bitmap = signature.asImageBitmap(),
                contentDescription = "Your signature",
                modifier = Modifier
                    .offset {
                        IntOffset((placement.x * boxWidthPx).roundToInt(), (placement.y * boxHeightPx).roundToInt())
                    }
                    .size(
                        width = with(density) { (placement.width * boxWidthPx).toDp() },
                        height = with(density) { (placement.height * boxHeightPx).toDp() },
                    )
                    .pointerInput(boxWidthPx, boxHeightPx) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            if (boxWidthPx > 0f && boxHeightPx > 0f) {
                                onDrag(dragAmount.x / boxWidthPx, dragAmount.y / boxHeightPx)
                            }
                        }
                    },
            )
        }

        Spacer(Modifier.height(Dewey.spacing.row))
        Slider(
            value = placement.width,
            onValueChange = onSizeChosen,
            valueRange = MIN_SIZE_FRACTION..MAX_SIZE_FRACTION,
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = hue.strong,
                activeTrackColor = hue.strong,
                inactiveTrackColor = hue.soft,
            ),
        )

        Spacer(Modifier.height(Dewey.spacing.tight))
        CornerGrid(selected = lastCorner, onSelect = onCornerChosen, hue = hue)
    }
}
