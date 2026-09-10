package app.dewey.ui.tools.secure

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.dewey.pdf.Corner
import app.dewey.ui.theme.Dewey
import app.dewey.ui.theme.DeweyTheme

/**
 * Where a page number lands, laid out as a page-shaped 3x2 grid rather than
 * six words in a row — a corner is a place on a rectangle, and a grid that
 * mirrors the page reads faster than six labels would.
 */
@Composable
fun CornerGrid(selected: Corner, onSelect: (Corner) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Dewey.spacing.tight)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Dewey.spacing.tight)) {
            CornerCell(Corner.TOP_LEFT, "Top left", selected, onSelect, Modifier.weight(1f))
            CornerCell(Corner.TOP_CENTER, "Top center", selected, onSelect, Modifier.weight(1f))
            CornerCell(Corner.TOP_RIGHT, "Top right", selected, onSelect, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Dewey.spacing.tight)) {
            CornerCell(Corner.BOTTOM_LEFT, "Bottom left", selected, onSelect, Modifier.weight(1f))
            CornerCell(Corner.BOTTOM_CENTER, "Bottom center", selected, onSelect, Modifier.weight(1f))
            CornerCell(Corner.BOTTOM_RIGHT, "Bottom right", selected, onSelect, Modifier.weight(1f))
        }
    }
}

@Composable
private fun CornerCell(
    corner: Corner,
    label: String,
    selected: Corner,
    onSelect: (Corner) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isSelected = corner == selected
    val shape = RoundedCornerShape(2.dp)

    Box(
        modifier = modifier
            .clip(shape)
            .background(if (isSelected) Dewey.colors.accentSoft else Color.Transparent, shape)
            .border(1.dp, if (isSelected) Dewey.colors.accent else Dewey.colors.rule, shape)
            .clickable(onClick = { onSelect(corner) })
            .padding(vertical = Dewey.spacing.tight),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = Dewey.type.Meta, color = if (isSelected) Dewey.colors.ink else Dewey.colors.inkMuted)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B0F17)
@Composable
private fun CornerGridPreview() {
    DeweyTheme {
        CornerGrid(selected = Corner.BOTTOM_CENTER, onSelect = {}, modifier = Modifier.padding(20.dp))
    }
}
