package app.dewey.ui.notes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.Receipt
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.dewey.domain.model.Document
import app.dewey.ui.theme.Dewey

/**
 * "Attach to a bill", collapsed to one summary row and expanding in place to
 * a list of every bill plus "No bill" - no new navigation route or bottom
 * sheet, since the note editor this lives in is already a full screen.
 */
@Composable
fun BillAttachField(
    attachedBill: Document?,
    bills: List<Document>,
    expanded: Boolean,
    onToggle: () -> Unit,
    onPick: (Document?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Dewey.radii.small))
                .clickable(role = Role.Button, onClick = onToggle)
                .padding(vertical = Dewey.spacing.tight),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dewey.spacing.tight),
        ) {
            Icon(
                imageVector = if (attachedBill != null) Icons.Rounded.Receipt else Icons.Rounded.LinkOff,
                contentDescription = null,
                tint = if (attachedBill != null) Dewey.colors.hues.bills.strong else Dewey.colors.inkFaint,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = attachedBill?.let(::attachedBillLabel) ?: "No bill",
                style = Dewey.type.Body,
                color = Dewey.colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.Rounded.ExpandMore,
                contentDescription = if (expanded) "Collapse" else "Choose a bill",
                tint = Dewey.colors.inkFaint,
                modifier = Modifier.rotate(if (expanded) 180f else 0f),
            )
        }

        if (expanded) {
            Column(modifier = Modifier.padding(start = Dewey.spacing.gutter)) {
                BillOption(label = "No bill", selected = attachedBill == null, onClick = { onPick(null) })
                if (bills.isEmpty()) {
                    Text(
                        text = "Dewey hasn't found any bills yet.",
                        style = Dewey.type.Meta,
                        color = Dewey.colors.inkFaint,
                        modifier = Modifier.padding(vertical = Dewey.spacing.tight),
                    )
                }
                for (bill in bills) {
                    BillOption(
                        label = billPickerLabel(bill),
                        selected = attachedBill?.id == bill.id,
                        onClick = { onPick(bill) },
                    )
                }
            }
        }
    }
}

@Composable
private fun BillOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dewey.radii.small))
            .clickable(role = Role.RadioButton, onClick = onClick)
            .background(if (selected) Dewey.colors.accentSoft else Dewey.colors.paper)
            .padding(vertical = 10.dp, horizontal = Dewey.spacing.tight),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dewey.spacing.tight),
    ) {
        Text(
            text = label,
            style = Dewey.type.Body,
            color = if (selected) Dewey.colors.accent else Dewey.colors.inkMuted,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = Dewey.colors.accent,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}
