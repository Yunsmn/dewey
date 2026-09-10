package app.dewey.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.dewey.ui.theme.Hue

/**
 * An icon on a rounded square of its own colour's wash.
 *
 * The one way this app shows what kind of thing something is at a glance: a
 * tool, a category, a recent file. The rounding scales with [size] so a small
 * tile in a list row and a large one on the Home grid read as the same shape.
 *
 * @param contentDescription null when a label beside the tile already says
 *   what it is, so a screen reader does not announce it twice.
 */
@Composable
fun IconTile(
    icon: ImageVector,
    hue: Hue,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    contentDescription: String? = null,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.32f))
            .background(hue.soft),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = hue.strong,
            modifier = Modifier.size(size * 0.52f),
        )
    }
}
