package app.dewey.ui.notes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Receipt
import androidx.compose.material.icons.rounded.StickyNote2
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.dewey.domain.model.Document
import app.dewey.ui.theme.Dewey
import app.dewey.ui.theme.Hue

/**
 * A note's small provenance chip: the bill it's about, in
 * [Dewey.colors.hues.bills], or "Standalone" in [Dewey.colors.hues.mark] -
 * one glance says whether a note stands on its own before anyone reads it.
 */
@Composable
fun NoteChip(attachedBill: Document?, modifier: Modifier = Modifier) {
    val hue = if (attachedBill != null) Dewey.colors.hues.bills else Dewey.colors.hues.mark
    val label = attachedBill?.let(::attachedBillLabel) ?: "Standalone"
    val icon = if (attachedBill != null) Icons.Rounded.Receipt else Icons.Rounded.StickyNote2

    Chip(icon = icon, label = label, hue = hue, modifier = modifier)
}

@Composable
private fun Chip(icon: ImageVector, label: String, hue: Hue, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(999.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .background(hue.soft, shape)
            .padding(horizontal = Dewey.spacing.tight, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = hue.strong, modifier = Modifier.size(13.dp))
        Text(
            text = label,
            style = Dewey.type.Micro,
            color = hue.strong,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
