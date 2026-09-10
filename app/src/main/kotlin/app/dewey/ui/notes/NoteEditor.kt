package app.dewey.ui.notes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.dewey.domain.model.Document
import app.dewey.ui.components.PrimaryAction
import app.dewey.ui.components.SecondaryAction
import app.dewey.ui.theme.Dewey
import app.dewey.ui.theme.DeweyTheme

/**
 * A full-screen editor rather than a route or a bottom sheet - see the class
 * doc on NotesScreen for why. Closing it always saves: a note editor that
 * needs an explicit save action is one more way to lose a paragraph someone
 * just wrote, so the only way a draft is discarded is by never having typed
 * anything into it - see [NotesViewModel.saveAndClose].
 */
@Composable
fun NoteEditor(
    state: NoteEditorState,
    bills: List<Document>,
    onTitleChange: (String) -> Unit,
    onBodyChange: (String) -> Unit,
    onTogglePin: () -> Unit,
    onToggleBillPicker: () -> Unit,
    onPickBill: (Document?) -> Unit,
    onRequestDelete: () -> Unit,
    onCancelDelete: () -> Unit,
    onConfirmDelete: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val attachedBill = bills.firstOrNull { it.id == state.billDocumentId }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Dewey.colors.paper)
            .statusBarsPadding()
            .imePadding(),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            EditorTopBar(
                canDelete = !state.isNew,
                pinned = state.pinned,
                onTogglePin = onTogglePin,
                onRequestDelete = onRequestDelete,
                onClose = onClose,
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Dewey.spacing.gutter),
            ) {
                BasicTextField(
                    value = state.title,
                    onValueChange = onTitleChange,
                    singleLine = true,
                    textStyle = LocalTextStyle.current.merge(Dewey.type.Headline.copy(color = Dewey.colors.ink)),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    cursorBrush = SolidColor(Dewey.colors.accent),
                    decorationBox = { field ->
                        Box {
                            if (state.title.isEmpty()) {
                                Text("Title", style = Dewey.type.Headline, color = Dewey.colors.inkFaint)
                            }
                            field()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(Dewey.spacing.row))
                HorizontalDivider(color = Dewey.colors.rule)
                Spacer(Modifier.height(Dewey.spacing.tight))

                BillAttachField(
                    attachedBill = attachedBill,
                    bills = bills,
                    expanded = state.pickingBill,
                    onToggle = onToggleBillPicker,
                    onPick = onPickBill,
                )

                Spacer(Modifier.height(Dewey.spacing.tight))
                HorizontalDivider(color = Dewey.colors.rule)
                Spacer(Modifier.height(Dewey.spacing.row))

                BasicTextField(
                    value = state.body,
                    onValueChange = onBodyChange,
                    textStyle = LocalTextStyle.current.merge(Dewey.type.Body.copy(color = Dewey.colors.ink)),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    cursorBrush = SolidColor(Dewey.colors.accent),
                    decorationBox = { field ->
                        Box {
                            if (state.body.isEmpty()) {
                                Text("Write a note…", style = Dewey.type.Body, color = Dewey.colors.inkFaint)
                            }
                            field()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(bottom = Dewey.spacing.section),
                )
            }
        }

        if (state.confirmingDelete) {
            DeleteConfirmation(
                onCancel = onCancelDelete,
                onConfirm = onConfirmDelete,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(Dewey.spacing.gutter),
            )
        }
    }
}

@Composable
private fun EditorTopBar(
    canDelete: Boolean,
    pinned: Boolean,
    onTogglePin: () -> Unit,
    onRequestDelete: () -> Unit,
    onClose: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dewey.spacing.row, vertical = Dewey.spacing.tight),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EditorIconButton(icon = Icons.Rounded.Close, contentDescription = "Save and close", onClick = onClose)
        Row(horizontalArrangement = Arrangement.spacedBy(Dewey.spacing.tight)) {
            EditorIconButton(
                icon = Icons.Rounded.PushPin,
                contentDescription = if (pinned) "Unpin note" else "Pin note",
                tint = if (pinned) Dewey.colors.accent else Dewey.colors.inkFaint,
                onClick = onTogglePin,
            )
            if (canDelete) {
                EditorIconButton(
                    icon = Icons.Rounded.Delete,
                    contentDescription = "Delete note",
                    tint = Dewey.colors.danger,
                    onClick = onRequestDelete,
                )
            }
        }
    }
}

@Composable
private fun EditorIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    tint: Color = Dewey.colors.ink,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun DeleteConfirmation(onCancel: () -> Unit, onConfirm: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dewey.radii.medium))
            .background(Dewey.colors.glass)
            .padding(Dewey.spacing.gutter),
    ) {
        Text("Delete this note?", style = Dewey.type.Title, color = Dewey.colors.ink)
        Spacer(Modifier.height(Dewey.spacing.tight))
        Text(
            text = "This can't be undone.",
            style = Dewey.type.Meta,
            color = Dewey.colors.inkMuted,
        )
        Spacer(Modifier.height(Dewey.spacing.row))
        Row(horizontalArrangement = Arrangement.spacedBy(Dewey.spacing.tight)) {
            SecondaryAction(label = "Keep it", onClick = onCancel)
            PrimaryAction(label = "Delete", onClick = onConfirm, modifier = Modifier)
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B0F17, heightDp = 780, widthDp = 390)
@Composable
private fun NoteEditorPreview() {
    val bills = listOf(
        Document(id = 1, uri = "u1", displayName = "bill.pdf", sizeBytes = 0, lastModified = 0, vendor = "Lydec", amount = 281.26, currency = "MAD"),
    )
    DeweyTheme {
        NoteEditor(
            state = NoteEditorState(
                noteId = 1,
                title = "Call the landlord",
                body = "Ask about the water heater before the next visit. Mention the receipt from March too.",
                billDocumentId = 1,
            ),
            bills = bills,
            onTitleChange = {},
            onBodyChange = {},
            onTogglePin = {},
            onToggleBillPicker = {},
            onPickBill = {},
            onRequestDelete = {},
            onCancelDelete = {},
            onConfirmDelete = {},
            onClose = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF5F6FA, heightDp = 780, widthDp = 390)
@Composable
private fun NoteEditorNewLightPreview() {
    DeweyTheme(dark = false) {
        NoteEditor(
            state = NoteEditorState(),
            bills = emptyList(),
            onTitleChange = {},
            onBodyChange = {},
            onTogglePin = {},
            onToggleBillPicker = {},
            onPickBill = {},
            onRequestDelete = {},
            onCancelDelete = {},
            onConfirmDelete = {},
            onClose = {},
        )
    }
}
