package app.dewey.ui.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.dewey.ui.theme.Dewey
import app.dewey.ui.theme.DeweyTheme

/**
 * A row of words to pick exactly one from — quality presets, export format,
 * watermark opacity — rather than a slider or a dropdown. There are never more
 * than three options for anything this renders, so a row of taps beats a menu.
 *
 * Lives in the shared layer, not with any one tool. It started in the raster
 * tools, and the protect and mark screens then imported it from there, which
 * made one tool package depend on another for a widget that has nothing to do
 * with either.
 */
@Composable
fun <T> OptionRow(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dewey.spacing.tight)) {
        for (option in options) {
            OptionChip(
                text = label(option),
                selected = option == selected,
                onClick = { onSelected(option) },
            )
        }
    }
}

@Composable
private fun OptionChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(PILL_CORNER)
    val background = if (selected) Dewey.colors.accent else Color.Transparent
    val border = if (selected) Dewey.colors.accent else Dewey.colors.rule
    val ink = if (selected) Dewey.colors.onAccent else Dewey.colors.ink

    Box(
        modifier = Modifier
            .clip(shape)
            .background(background)
            .border(1.dp, border, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = Dewey.spacing.row, vertical = Dewey.spacing.tight),
    ) {
        Text(text, style = Dewey.type.Meta, color = ink)
    }
}

private val PILL_CORNER = 999.dp

@Preview(showBackground = true, backgroundColor = 0xFF0B0F17)
@Composable
private fun OptionRowPreview() {
    DeweyTheme {
        OptionRow(
            options = listOf("Small", "Balanced", "Sharp"),
            selected = "Balanced",
            label = { it },
            onSelected = {},
            modifier = Modifier.padding(20.dp),
        )
    }
}
