package app.dewey.ui.me

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
import app.dewey.data.settings.ThemeMode
import app.dewey.ui.theme.Dewey

/**
 * System / Light / Dark, drawn as the same filled pill toggle as
 * [app.dewey.ui.notes.NotesSegmentedControl] — that one is fixed at two
 * options for Notes and Bills, so this is a sibling built for three rather
 * than a change to it.
 */
@Composable
fun ThemeModeSegmentedControl(
    selected: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
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
        ThemeModeOption(ThemeMode.SYSTEM, "System", selected == ThemeMode.SYSTEM, onSelect, Modifier.weight(1f))
        ThemeModeOption(ThemeMode.LIGHT, "Light", selected == ThemeMode.LIGHT, onSelect, Modifier.weight(1f))
        ThemeModeOption(ThemeMode.DARK, "Dark", selected == ThemeMode.DARK, onSelect, Modifier.weight(1f))
    }
}

@Composable
private fun ThemeModeOption(
    mode: ThemeMode,
    label: String,
    selected: Boolean,
    onSelect: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(999.dp)
    val background: Color by animateColorAsState(
        if (selected) Dewey.colors.glass else Color.Transparent,
        label = "themeModeBackground",
    )

    Box(
        modifier = modifier
            .clip(shape)
            .background(background)
            .clickable(role = Role.Tab, onClick = { onSelect(mode) })
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
