package app.dewey.ui.me

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.dewey.ui.components.GlassCard
import app.dewey.ui.components.IconTile
import app.dewey.ui.theme.Dewey

/**
 * What actually leaves the device, in the same terms as the README's section
 * of that name — stated rather than implied, since a vague privacy claim is
 * worse than none.
 */
@Composable
fun MePrivacySection() {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(Icons.Rounded.Shield, Dewey.colors.hues.protect, size = 40.dp)
            Spacer(Modifier.width(Dewey.spacing.row))
            Text("Privacy", style = Dewey.type.Title, color = Dewey.colors.ink)
        }
        Spacer(Modifier.height(Dewey.spacing.row))
        Text(
            "Your documents are read, indexed and sorted on this phone.",
            style = Dewey.type.Meta,
            color = Dewey.colors.inkMuted,
        )
        Spacer(Modifier.height(Dewey.spacing.tight))
        Text(
            "Only when you ask the assistant, your question and the few passages it found are sent to Google's Gemini.",
            style = Dewey.type.Meta,
            color = Dewey.colors.inkMuted,
        )
        Spacer(Modifier.height(Dewey.spacing.tight))
        Text("No account, and no ads.", style = Dewey.type.Meta, color = Dewey.colors.inkMuted)
    }
}
