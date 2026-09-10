package app.dewey.ui.notes

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected as semanticsSelected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.dewey.ui.theme.Dewey

/** The tab's two views - notes of your own, and the bills Dewey found. */
enum class NotesSegment {
    NOTES,
    BILLS,
}

/**
 * A two-option pill toggle, filled all the way rather than left as bare tabs -
 * this screen has no other chrome to say which of two things it's showing.
 */
@Composable
fun NotesSegmentedControl(
    selected: NotesSegment,
    onSelect: (NotesSegment) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(999.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Dewey.colors.paperSunken)
            .padding(4.dp),
    ) {
        SegmentOption(
            label = "Notes",
            selected = selected == NotesSegment.NOTES,
            onClick = { onSelect(NotesSegment.NOTES) },
            modifier = Modifier.weight(1f),
        )
        SegmentOption(
            label = "Bills",
            selected = selected == NotesSegment.BILLS,
            onClick = { onSelect(NotesSegment.BILLS) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SegmentOption(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(999.dp)
    val background: Color by animateColorAsState(
        if (selected) Dewey.colors.glass else Color.Transparent,
        label = "segmentBackground",
    )

    Box(
        modifier = modifier
            .clip(shape)
            .background(background)
            .clickable(role = Role.Tab, onClick = onClick)
            .semantics { semanticsSelected = selected }
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = Dewey.type.Label,
            color = if (selected) Dewey.colors.ink else Dewey.colors.inkMuted,
        )
    }
}
