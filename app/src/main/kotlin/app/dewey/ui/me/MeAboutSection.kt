package app.dewey.ui.me

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.dewey.ui.components.GlassCard
import app.dewey.ui.components.IconTile
import app.dewey.ui.components.SecondaryAction
import app.dewey.ui.theme.Dewey

/** Version, licence and the repo — a clone's whole reason to trust what the Privacy card just claimed. */
@Composable
fun MeAboutSection(versionName: String, onViewSource: () -> Unit) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(Icons.Rounded.Code, Dewey.colors.hues.scan, size = 40.dp)
            Spacer(Modifier.width(Dewey.spacing.row))
            Column {
                Text("Dewey $versionName", style = Dewey.type.Title, color = Dewey.colors.ink)
                Text("Open source under the MIT licence.", style = Dewey.type.Meta, color = Dewey.colors.inkMuted)
            }
        }
        Spacer(Modifier.height(Dewey.spacing.row))
        SecondaryAction(label = "View source on GitHub", onClick = onViewSource)
    }
}
