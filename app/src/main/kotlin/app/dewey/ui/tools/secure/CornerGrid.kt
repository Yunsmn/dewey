package app.dewey.ui.tools.secure

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.dewey.pdf.Corner
import app.dewey.ui.theme.Dewey
import app.dewey.ui.theme.DeweyTheme
import app.dewey.ui.theme.Hue

/**
 * Where a page number lands, laid out as a page-shaped 3x2 grid rather than
 * six words in a row — a corner is a place on a rectangle, and a grid that
 * mirrors the page reads faster than six labels would.
 *
 * @param hue tints the chosen cell with the mark tool's own colour rather
 *   than a house-wide accent. Falls back to [Dewey.colors.accent] when null.
 */
@Composable
fun CornerGrid(selected: Corner, onSelect: (Corner) -> Unit, modifier: Modifier = Modifier, hue: Hue? = null) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Dewey.spacing.tight)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Dewey.spacing.tight)) {
            CornerCell(Corner.TOP_LEFT, "Top left", selected, hue, onSelect, Modifier.weight(1f))
            CornerCell(Corner.TOP_CENTER, "Top center", selected, hue, onSelect, Modifier.weight(1f))
            CornerCell(Corner.TOP_RIGHT, "Top right", selected, hue, onSelect, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Dewey.spacing.tight)) {
            CornerCell(Corner.BOTTOM_LEFT, "Bottom left", selected, hue, onSelect, Modifier.weight(1f))
            CornerCell(Corner.BOTTOM_CENTER, "Bottom center", selected, hue, onSelect, Modifier.weight(1f))
            CornerCell(Corner.BOTTOM_RIGHT, "Bottom right", selected, hue, onSelect, Modifier.weight(1f))
        }
    }
}

@Composable
private fun CornerCell(
    corner: Corner,
    label: String,
    selected: Corner,
    hue: Hue?,
    onSelect: (Corner) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isSelected = corner == selected
    val shape = RoundedCornerShape(Dewey.radii.small)
    val tint = hue?.strong ?: Dewey.colors.accent
    val soft = hue?.soft ?: Dewey.colors.accentSoft

    Box(
        modifier = modifier
            .clip(shape)
            .background(if (isSelected) soft else Dewey.colors.paperSunken, shape)
            .clickable(onClick = { onSelect(corner) })
            .padding(vertical = Dewey.spacing.row),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = Dewey.type.Meta.copy(fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal),
            color = if (isSelected) tint else Dewey.colors.inkMuted,
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B0F17)
@Composable
private fun CornerGridPreview() {
    DeweyTheme {
        CornerGrid(selected = Corner.BOTTOM_CENTER, onSelect = {}, modifier = Modifier.padding(20.dp))
    }
}
