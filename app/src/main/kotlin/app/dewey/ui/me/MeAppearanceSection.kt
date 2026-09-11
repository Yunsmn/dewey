package app.dewey.ui.me

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.dewey.data.settings.ThemeMode
import app.dewey.ui.components.GlassCard
import app.dewey.ui.components.IconTile
import app.dewey.ui.theme.Dewey

/** System, Light or Dark — applied the moment it's picked, through [ThemeModeSegmentedControl]. */
@Composable
fun MeAppearanceSection(themeMode: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(Icons.Rounded.Palette, Dewey.colors.hues.mark, size = 40.dp)
            Spacer(Modifier.width(Dewey.spacing.row))
            Text("Appearance", style = Dewey.type.Title, color = Dewey.colors.ink)
        }
        Spacer(Modifier.height(Dewey.spacing.row))
        ThemeModeSegmentedControl(selected = themeMode, onSelect = onSelect)
    }
}
