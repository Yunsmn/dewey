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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.dewey.ui.theme.Dewey

/**
 * The surface everything in this app sits on.
 *
 * The two palettes earn depth differently, because a shadow and a hairline do
 * not survive the same trip. In light mode a soft, low-alpha shadow lifts the
 * card off the paper the way a real card would — a border there only draws a
 * grey outline nothing is actually shaped by. In dark mode a shadow against a
 * dark ground is invisible or reads as a smudge, so a hairline border — white
 * at low alpha — catches the edge instead. Neither is a real backdrop blur,
 * which Compose cannot do below API 31 and which costs more than it returns
 * behind a scrolling list.
 *
 * @param accent an optional colour for the left edge. Used to carry meaning a
 *   reader can take in without reading — amber for a bill due soon, red for one
 *   already late — and left null everywhere it would only be decoration.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    accent: Color? = null,
    corner: Dp = Dewey.radii.medium,
    padding: Dp = 14.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(corner)
    // Color.luminance() rather than a theme flag: the palette exposes colour
    // roles, not "which mode is this", and a card that only reads its own
    // colours stays correct if a third palette ever joins light and dark.
    val isLight = Dewey.colors.paper.luminance() > 0.5f
    val shadowColor = Dewey.colors.ink.copy(alpha = 0.12f)

    Column(
        modifier = modifier
            .then(
                if (isLight) {
                    Modifier.shadow(
                        elevation = 5.dp,
                        shape = shape,
                        clip = false,
                        ambientColor = shadowColor,
                        spotColor = shadowColor,
                    )
                } else {
                    Modifier
                }
            )
            .clip(shape)
            .background(Dewey.colors.glass)
            .then(if (isLight) Modifier else Modifier.border(1.dp, Dewey.colors.glassBorder, shape))
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
