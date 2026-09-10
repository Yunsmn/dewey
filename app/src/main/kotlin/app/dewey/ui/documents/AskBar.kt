package app.dewey.ui.documents

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dewey.domain.model.Document
import app.dewey.search.SearchResult
import app.dewey.ui.components.GlassCard
import app.dewey.ui.components.IconTile
import app.dewey.ui.library.readable
import app.dewey.ui.theme.Dewey
import app.dewey.ui.theme.DeweyTheme
import app.dewey.ui.theme.Hue

/**
 * "Search or ask your documents" — one field, two things it can do.
 *
 * Typing alone already searches (see [AskViewModel.onQueryChanged]); the Ask
 * pill only appears once there is a question worth sending further, and is
 * the same action as the field's own IME button, so a person who never
 * notices the pill can still just press enter.
 *
 * Asking hides the keyboard, because the answer arrives directly beneath the
 * field and an open keyboard is exactly where it would land. The clear button
 * is the way back to the library: a non-blank field replaces the list, and
 * deleting a whole question a character at a time is not a way back.
 */
@Composable
fun AskBar(
    query: String,
    onQueryChanged: (String) -> Unit,
    onAsk: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val ask = {
        keyboard?.hide()
        onAsk()
    }
    val shape = RoundedCornerShape(999.dp)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Dewey.colors.paperSunken, shape)
            .padding(start = Dewey.spacing.gutter, end = Dewey.spacing.tight, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dewey.spacing.row),
    ) {
        Icon(
            imageVector = Icons.Rounded.Search,
            contentDescription = null,
            tint = Dewey.colors.inkFaint,
            modifier = Modifier.size(20.dp),
        )
        Box(modifier = Modifier.weight(1f).padding(vertical = 8.dp)) {
            if (query.isEmpty()) {
                Text(
                    text = "Search or ask your documents",
                    style = Dewey.type.Body,
                    color = Dewey.colors.inkFaint,
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChanged,
                singleLine = true,
                textStyle = LocalTextStyle.current.merge(Dewey.type.Body.copy(color = Dewey.colors.ink)),
                // BasicTextField's default cursor is black, which vanishes on
                // the dark palette's sunken field.
                cursorBrush = SolidColor(Dewey.colors.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { ask() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (query.isNotEmpty()) {
            ClearButton(onClick = { onQueryChanged("") })
        }
        if (query.isNotBlank()) {
            AskPill(onClick = ask)
        }
    }
}

@Composable
private fun ClearButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.Close,
            contentDescription = "Clear",
            tint = Dewey.colors.inkMuted,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun AskPill(onClick: () -> Unit) {
    val hue = Dewey.colors.hues.assistant
    val shape = RoundedCornerShape(999.dp)
    Row(
        modifier = Modifier
            .clip(shape)
            .background(hue.strong, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Dewey.spacing.row, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = Icons.Rounded.AutoAwesome,
            contentDescription = null,
            tint = Dewey.colors.onAccent,
            modifier = Modifier.size(15.dp),
        )
        Text(text = "Ask", style = Dewey.type.Label, color = Dewey.colors.onAccent)
    }
}

/**
 * The answer to a question just asked, or what is standing in for one:
 * loading the encoder, thinking, or a plain-language reason there is nothing
 * to show — see [AnswerFailureMessages] for why every failure gets its own
 * sentence rather than one generic "something went wrong".
 */
@Composable
fun AnswerCard(state: AnswerUiState, onOpenDocument: (Document) -> Unit, modifier: Modifier = Modifier) {
    when (state) {
        AnswerUiState.Idle -> Unit
        AnswerUiState.Preparing -> AnswerStatus("Waking the assistant…", modifier)
        AnswerUiState.Thinking -> AnswerStatus("Thinking…", modifier)
        is AnswerUiState.Failed -> AnswerStatus(state.message, modifier, isError = true)
        is AnswerUiState.Answered -> Answered(state, onOpenDocument, modifier)
    }
}

@Composable
private fun Answered(state: AnswerUiState.Answered, onOpenDocument: (Document) -> Unit, modifier: Modifier = Modifier) {
    val hue = Dewey.colors.hues.assistant
    GlassCard(modifier = modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(Dewey.spacing.row)) {
            IconTile(icon = Icons.Rounded.AutoAwesome, hue = hue, size = 32.dp)
            Column {
                Text(text = state.text, style = Dewey.type.Body, color = Dewey.colors.ink)
                if (state.sources.isNotEmpty()) {
                    Spacer(Modifier.height(Dewey.spacing.row))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(Dewey.spacing.tight),
                    ) {
                        for (document in state.sources) {
                            SourceChip(document = document, hue = hue, onClick = { onOpenDocument(document) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceChip(document: Document, hue: Hue, onClick: () -> Unit) {
    val shape = RoundedCornerShape(999.dp)
    Row(
        modifier = Modifier
            .clip(shape)
            .background(hue.soft, shape)
            .clickable(onClick = onClick)
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

@Composable
private fun AnswerStatus(text: String, modifier: Modifier = Modifier, isError: Boolean = false) {
    Text(
        text = text,
        style = Dewey.type.Body,
        color = if (isError) Dewey.colors.danger else Dewey.colors.inkMuted,
        modifier = modifier,
    )
}

/** One search-as-you-type match, with the passage that made it match. The whole row opens it, icon included. */
@Composable
fun AskSearchResultRow(result: SearchResult, onOpenDocument: (Document) -> Unit, modifier: Modifier = Modifier) {
    val category = result.document.categoryLabel(unfiled = result.document.docType.readable())
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dewey.radii.small))
            .clickable(onClick = { onOpenDocument(result.document) })
            .padding(vertical = Dewey.spacing.row),
        horizontalArrangement = Arrangement.spacedBy(Dewey.spacing.row),
    ) {
        IconTile(icon = Icons.Rounded.Description, hue = Dewey.colors.hues.forCategory(category))
        Column {
            Text(
                text = result.document.displayName,
                style = Dewey.type.Mono.copy(fontSize = 13.sp),
                color = Dewey.colors.ink,
                maxLines = 1,
                overflow = TextOverflow.MiddleEllipsis,
            )
            Text(
                text = result.snippet.take(SNIPPET_CHARS),
                style = Dewey.type.Meta,
                color = Dewey.colors.inkMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

private const val SNIPPET_CHARS = 200

@Preview(widthDp = 380)
@Composable
private fun AskBarLightPreview() {
    DeweyTheme(dark = false) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            AskBar(query = "", onQueryChanged = {}, onAsk = {})
            AskBar(query = "electricity bill january", onQueryChanged = {}, onAsk = {})
        }
    }
}

@Preview(widthDp = 380)
@Composable
private fun AskBarDarkPreview() {
    DeweyTheme(dark = true) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            AskBar(query = "", onQueryChanged = {}, onAsk = {})
            AskBar(query = "electricity bill january", onQueryChanged = {}, onAsk = {})
        }
    }
}
