package app.dewey.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.dewey.ui.theme.Dewey

/**
 * The surface everything in this app sits on.
 *
 * Translucent over the ground rather than a flat panel, with a hairline that is
 * white at low alpha so it catches an edge instead of drawing a grey box. That
 * is where the depth comes from — not from a shadow, which on a dark ground
 * reads as a smudge, and not from a real backdrop blur, which Compose cannot do
 * below API 31 and which costs more than it returns behind a scrolling list.
 *
 * @param accent an optional colour for the left edge. Used to carry meaning a
 *   reader can take in without reading — amber for a bill due soon, red for one
 *   already late — and left null everywhere it would only be decoration.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    accent: Color? = null,
    corner: Dp = 20.dp,
    padding: Dp = 14.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(corner)

    Column(
        modifier = modifier
            .clip(shape)
            .background(Dewey.colors.glass)
            .border(1.dp, Dewey.colors.glassBorder, shape)
            .then(
                // Drawn inside the clip so the bar takes the card's rounding at
                // the corners rather than sticking out as a rectangle.
                if (accent != null) Modifier.accentEdge(accent) else Modifier
            )
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(padding),
        content = content,
    )
}

/** A narrow bar down the leading edge, in [color]. */
private fun Modifier.accentEdge(color: Color): Modifier = drawBehind {
    drawRect(color = color, size = Size(EDGE_WIDTH.toPx(), size.height))
}

private val EDGE_WIDTH = 3.dp
