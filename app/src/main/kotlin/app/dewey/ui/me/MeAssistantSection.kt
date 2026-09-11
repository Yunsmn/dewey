package app.dewey.ui.me

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.dewey.ui.components.GlassCard
import app.dewey.ui.components.IconTile
import app.dewey.ui.theme.Dewey

/** How many of today's cloud questions are left — the same daily budget the Documents ask bar and the full assistant spend from. */
@Composable
fun MeAssistantSection(questionsLeft: Int, dailyLimit: Int) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(Icons.Rounded.Chat, Dewey.colors.hues.assistant, size = 40.dp)
            Spacer(Modifier.width(Dewey.spacing.row))
            Column {
                Text("Assistant", style = Dewey.type.Title, color = Dewey.colors.ink)
                Text(
                    "$questionsLeft of $dailyLimit questions left today",
                    style = Dewey.type.Meta,
                    color = Dewey.colors.inkMuted,
                )
            }
        }
    }
}
