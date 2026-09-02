package app.dewey.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.dewey.ui.theme.Dewey
import app.dewey.ui.theme.DeweyTheme

/**
 * The one filled action on a screen.
 *
 * Pressing scales it slightly rather than rippling. On a paper surface a ripple
 * looks like a mistake, and the scale reads as the page being pressed.
 */
@Composable
fun PrimaryAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, label = "press")

    Box(
        modifier = modifier
            .scale(scale)
            .background(Dewey.colors.accent, RoundedCornerShape(2.dp))
            .clickable(interactionSource = interactions, indication = null, onClick = onClick)
            .padding(horizontal = Dewey.spacing.block, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, style = Dewey.type.Button, color = Dewey.colors.onAccent)
    }
}

/** The quieter sibling: outlined, for anything that is not the main action. */
@Composable
fun SecondaryAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()

    Box(
        modifier = modifier
            .border(1.dp, if (pressed) Dewey.colors.ink else Dewey.colors.rule, RoundedCornerShape(2.dp))
            .clickable(interactionSource = interactions, indication = null, onClick = onClick)
            .padding(horizontal = Dewey.spacing.gutter, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, style = Dewey.type.Meta, color = Dewey.colors.ink)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFAF7F2)
@Composable
private fun ActionsPreview() {
    DeweyTheme {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
        ) {
            PrimaryAction("Choose a folder", {})
            SecondaryAction("Cancel", {})
        }
    }
}
