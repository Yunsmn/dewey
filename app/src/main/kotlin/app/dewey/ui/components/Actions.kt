package app.dewey.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.dewey.ui.theme.Dewey
import app.dewey.ui.theme.DeweyTheme

/**
 * The one filled action on a screen: a full pill in [Dewey.colors.accent],
 * rounded all the way rather than the sharp corners this app used to draw its
 * buttons with. Pressing scales it slightly rather than rippling — on a soft
 * pill a ripple looks like a mistake, and the scale reads as the pill being
 * pressed.
 *
 * @param icon shown before the label when the action benefits from one —
 *   most don't, so it stays null by default.
 */
@Composable
fun PrimaryAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, label = "press")
    val shape = RoundedCornerShape(PILL_CORNER)

    Row(
        modifier = modifier
            .scale(scale)
            .clip(shape)
            .background(Dewey.colors.accent, shape)
            .clickable(interactionSource = interactions, indication = null, onClick = onClick)
            .padding(horizontal = Dewey.spacing.block, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(Dewey.spacing.tight, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Dewey.colors.onAccent,
                modifier = Modifier.size(18.dp),
            )
        }
        Text(text = label, style = Dewey.type.Button, color = Dewey.colors.onAccent)
    }
}

/**
 * The quieter sibling: tonal rather than outlined — [Dewey.colors.accentSoft]
 * behind [Dewey.colors.accent] text — so it still reads as coloured and alive
 * rather than as a bare outline waiting for something to happen.
 */
@Composable
fun SecondaryAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    val interactions = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(PILL_CORNER)

    Row(
        modifier = modifier
            .clip(shape)
            .background(Dewey.colors.accentSoft, shape)
            .clickable(interactionSource = interactions, indication = null, onClick = onClick)
            .padding(horizontal = Dewey.spacing.gutter, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(Dewey.spacing.tight, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Dewey.colors.accent,
                modifier = Modifier.size(16.dp),
            )
        }
        Text(text = label, style = Dewey.type.Meta.copy(fontWeight = FontWeight.SemiBold), color = Dewey.colors.accent)
    }
}

private val PILL_CORNER = 999.dp

@Preview(showBackground = true, backgroundColor = 0xFFFAF7F2)
@Composable
private fun ActionsPreview() {
    DeweyTheme {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PrimaryAction("Choose a folder", {})
            SecondaryAction("Cancel", {})
        }
    }
}
