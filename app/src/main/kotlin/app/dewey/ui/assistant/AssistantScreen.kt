package app.dewey.ui.assistant

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.dewey.di.AppContainer
import app.dewey.domain.model.Document
import app.dewey.ui.theme.Dewey
import app.dewey.ui.theme.DeweyTheme

/**
 * The full-screen assistant, opened from Home once the paid tier is
 * entitled — the lead wraps this in [app.dewey.ui.billing.LibrarianGate] when
 * wiring navigation, so this screen never has to ask.
 *
 * No bottom nav here; the lead hides it for this route the way a full-screen
 * flow expects. [AssistantViewModel] owns the conversation and survives
 * rotation on its own; this composable only ever reads it.
 */
@Composable
fun AssistantScreen(
    container: AppContainer,
    onOpenDocument: (Document) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: AssistantViewModel = viewModel(factory = AssistantViewModel.factory(container))
    val state by viewModel.state.collectAsStateWithLifecycle()
    val input by viewModel.input.collectAsStateWithLifecycle()

    AssistantContent(
        state = state,
        input = input,
        onInputChanged = viewModel::onInputChanged,
        onSend = viewModel::onSend,
        onRetry = viewModel::onAsk,
        onSuggestionTapped = viewModel::onAsk,
        onOpenDocument = onOpenDocument,
        onBack = onBack,
        modifier = modifier,
    )
}

/** Stateless so it can be driven directly from a fixed [AssistantUiState] in the previews below. */
@Composable
private fun AssistantContent(
    state: AssistantUiState,
    input: String,
    onInputChanged: (String) -> Unit,
    onSend: () -> Unit,
    onRetry: (String) -> Unit,
    onSuggestionTapped: (String) -> Unit,
    onOpenDocument: (Document) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Dewey.colors.paper)
            .statusBarsPadding(),
    ) {
        AssistantTopBar(remaining = state.remainingToday, onBack = onBack)

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (state.messages.isEmpty()) {
                AssistantEmptyState(onSuggestionTapped = onSuggestionTapped)
            } else {
                AssistantConversation(messages = state.messages, onOpenDocument = onOpenDocument, onRetry = onRetry)
            }
        }

        AssistantInputBar(input = input, onInputChanged = onInputChanged, onSend = onSend, canSend = state.canSend)
    }
}

@Composable
private fun AssistantTopBar(remaining: Int, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Dewey.spacing.gutter, vertical = Dewey.spacing.row),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(imageVector = Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = Dewey.colors.ink)
        }
        Text(
            text = "Assistant",
            style = Dewey.type.Title,
            color = Dewey.colors.ink,
            modifier = Modifier.weight(1f).padding(start = Dewey.spacing.tight),
        )
        RemainingChip(remaining)
    }
}

@Composable
private fun RemainingChip(remaining: Int, modifier: Modifier = Modifier) {
    val hue = Dewey.colors.hues.assistant
    val shape = RoundedCornerShape(999.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(hue.soft, shape)
            .padding(horizontal = Dewey.spacing.row, vertical = 6.dp),
    ) {
        Text(text = "$remaining left today", style = Dewey.type.Micro, color = hue.strong)
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 700)
@Composable
private fun AssistantScreenLightPreview() {
    DeweyTheme(dark = false) {
        AssistantContent(
            state = PreviewState,
            input = "",
            onInputChanged = {},
            onSend = {},
            onRetry = {},
            onSuggestionTapped = {},
            onOpenDocument = {},
            onBack = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 700)
@Composable
private fun AssistantScreenDarkPreview() {
    DeweyTheme(dark = true) {
        AssistantContent(
            state = PreviewState,
            input = "",
            onInputChanged = {},
            onSend = {},
            onRetry = {},
            onSuggestionTapped = {},
            onOpenDocument = {},
            onBack = {},
        )
    }
}

private val PreviewDocument = Document(1, "u1", "electricity-january.pdf", 0, 0)

private val PreviewState = AssistantUiState(
    messages = listOf(
        AssistantMessage.FromUser(1, "What did my last electricity bill cost?"),
        AssistantMessage.FromAssistant(2, "Your last electricity bill was **62.40 EUR**, due 2026-09-28.", listOf(PreviewDocument)),
        AssistantMessage.FromUser(3, "When does my insurance renew?"),
        AssistantMessage.Pending(4, "When does my insurance renew?", AssistantMessage.Pending.Phase.THINKING),
    ),
    remainingToday = 47,
)
