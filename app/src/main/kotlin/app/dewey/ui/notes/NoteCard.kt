package app.dewey.ui.notes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.dewey.domain.model.Document
import app.dewey.domain.model.Note
import app.dewey.ui.components.GlassCard
import app.dewey.ui.theme.Dewey

/**
 * One note, as a soft card - the same shape language as [app.dewey.ui.bills.BillCard],
 * so the two segments of this tab read as one screen rather than two glued
 * together. [attachedBill] is looked up by the caller rather than carried on
 * [Note] itself, since the note only knows the bill's id.
 */
@Composable
fun NoteCard(note: Note, attachedBill: Document?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    GlassCard(modifier = modifier.fillMaxWidth(), onClick = onClick) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = note.title.ifBlank { "Untitled note" },
                    style = Dewey.type.Title,
                    color = Dewey.colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val preview = notePreview(note.body)
                if (preview.isNotEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = preview,
                        style = Dewey.type.Body,
                        color = Dewey.colors.inkMuted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (note.pinned) {
                Icon(
                    imageVector = Icons.Rounded.PushPin,
                    contentDescription = "Pinned",
                    tint = Dewey.colors.accent,
                    modifier = Modifier.size(16.dp).padding(start = Dewey.spacing.tight),
                )
            }
        }
        Spacer(Modifier.height(Dewey.spacing.tight))
        NoteChip(attachedBill = attachedBill)
    }
}
