package app.dewey.ui.assistant

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.dewey.assistant.DocumentAssistant
import app.dewey.domain.model.Document
import app.dewey.ui.components.GlassCard
import app.dewey.ui.components.IconTile
import app.dewey.ui.components.SecondaryAction
import app.dewey.ui.documents.answerMarkdownToAnnotated
import app.dewey.ui.theme.Dewey
import app.dewey.ui.theme.Hue

/**
 * The transcript so far, auto-scrolling to the newest line — see
 * [LaunchedEffect] below, keyed on the message count so a new line, not just
 * any recomposition, is what triggers the scroll.
 */
@Composable
fun AssistantConversation(
    messages: List<AssistantMessage>,
    onOpenDocument: (Document) -> Unit,
    onRetry: (String) -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
) {
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = Dewey.spacing.gutter, vertical = Dewey.spacing.row),
        verticalArrangement = Arrangement.spacedBy(Dewey.spacing.row),
    ) {
        items(messages, key = { it.id }) { message ->
            MessageRow(message = message, onOpenDocument = onOpenDocument, onRetry = onRetry)
        }
    }
}

@Composable
private fun MessageRow(message: AssistantMessage, onOpenDocument: (Document) -> Unit, onRetry: (String) -> Unit) {
    when (message) {
        is AssistantMessage.FromUser -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            UserBubble(message.text)
        }

        is AssistantMessage.FromAssistant -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
            AnsweredBubble(message.text, message.sources, onOpenDocument)
        }

        is AssistantMessage.Pending -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
            PendingBubble(message.phase)
        }

        is AssistantMessage.Failure -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
            FailureBubble(message.message) { onRetry(message.question) }
        }

        is AssistantMessage.LimitReached -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
            LimitBubble()
        }
    }
}

private val BubbleMaxWidth = 300.dp

@Composable
private fun UserBubble(text: String) {
    val shape = RoundedCornerShape(Dewey.radii.medium)
    Box(
        modifier = Modifier
            .widthIn(max = BubbleMaxWidth)
            .clip(shape)
            .background(Dewey.colors.accent, shape)
            .padding(horizontal = Dewey.spacing.gutter, vertical = Dewey.spacing.row),
    ) {
        Text(text = text, style = Dewey.type.Body, color = Dewey.colors.onAccent)
    }
}

/** The shared shape of every assistant-side bubble: a small avatar tile beside whatever [content] says. */
@Composable
private fun AssistantAvatarRow(content: @Composable () -> Unit) {
    GlassCard(modifier = Modifier.widthIn(max = BubbleMaxWidth)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Dewey.spacing.row)) {
            IconTile(icon = Icons.Rounded.AutoAwesome, hue = Dewey.colors.hues.assistant, size = 32.dp)
            Column { content() }
        }
    }
}

@Composable
private fun AnsweredBubble(text: String, sources: List<Document>, onOpenDocument: (Document) -> Unit) {
    AssistantAvatarRow {
        Text(text = answerMarkdownToAnnotated(text), style = Dewey.type.Body, color = Dewey.colors.ink)
        if (sources.isNotEmpty()) {
            Spacer(Modifier.height(Dewey.spacing.row))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Dewey.spacing.tight),
            ) {
                for (document in sources) {
                    SourceChip(document = document, hue = Dewey.colors.hues.assistant, onClick = { onOpenDocument(document) })
                }
            }
        }
    }
}

@Composable
private fun PendingBubble(phase: AssistantMessage.Pending.Phase) {
    AssistantAvatarRow {
        val label = when (phase) {
            AssistantMessage.Pending.Phase.PREPARING -> "Getting ready…"
            AssistantMessage.Pending.Phase.THINKING -> "Thinking…"
        }
        Text(text = label, style = Dewey.type.Body, color = Dewey.colors.inkMuted)
    }
}

@Composable
private fun FailureBubble(message: String, onRetry: () -> Unit) {
    AssistantAvatarRow {
        Text(text = message, style = Dewey.type.Body, color = Dewey.colors.ink)
        Spacer(Modifier.height(Dewey.spacing.row))
        SecondaryAction(label = "Try again", onClick = onRetry)
    }
}

@Composable
private fun LimitBubble() {
    AssistantAvatarRow {
        Text(text = DocumentAssistant.LIMIT_REACHED_MESSAGE, style = Dewey.type.Body, color = Dewey.colors.inkMuted)
    }
}

@Composable
private fun SourceChip(document: Document, hue: Hue, onClick: () -> Unit) {
    val shape = RoundedCornerShape(999.dp)
    Row(
        modifier = Modifier
            .clip(shape)
            .background(hue.soft, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Dewey.spacing.row, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = Icons.Rounded.Description,
            contentDescription = null,
            tint = hue.strong,
            modifier = Modifier.size(13.dp),
        )
        Text(
            text = document.displayName,
            style = Dewey.type.Micro,
            color = hue.strong,
            maxLines = 1,
            overflow = TextOverflow.MiddleEllipsis,
        )
    }
}
