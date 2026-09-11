package app.dewey.ui.assistant

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.dewey.ui.components.IconTile
import app.dewey.ui.theme.Dewey

/** Before the first question of a session: what the assistant is for, and a few it can already answer. */
@Composable
fun AssistantEmptyState(onSuggestionTapped: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(Dewey.spacing.gutter),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        IconTile(icon = Icons.Rounded.AutoAwesome, hue = Dewey.colors.hues.assistant, size = 64.dp)
        Text(
            text = "Ask anything about your documents — bills, insurance, dates, amounts.",
            style = Dewey.type.Body,
            color = Dewey.colors.inkMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Dewey.spacing.row, bottom = Dewey.spacing.block),
        )
        Column(verticalArrangement = Arrangement.spacedBy(Dewey.spacing.row), modifier = Modifier.fillMaxWidth()) {
            for (question in SuggestedQuestions) {
                SuggestionChip(text = question, onClick = { onSuggestionTapped(question) })
            }
        }
    }
}

@Composable
private fun SuggestionChip(text: String, onClick: () -> Unit) {
    val hue = Dewey.colors.hues.assistant
    val shape = RoundedCornerShape(Dewey.radii.medium)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(hue.soft, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Dewey.spacing.gutter, vertical = Dewey.spacing.row),
    ) {
        Text(text = text, style = Dewey.type.Body, color = hue.strong)
    }
}

private val SuggestedQuestions = listOf(
    "Which bills are due soon?",
    "When does my insurance renew?",
    "What did my last electricity bill cost?",
    "Summarise my medical certificates",
)
