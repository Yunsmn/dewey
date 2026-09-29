package app.dewey.ui.tools.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.dewey.ui.components.SecondaryAction
import app.dewey.ui.theme.Dewey

/**
 * The row above a select-mode [app.dewey.ui.tools.thumbnails.PageGrid]:
 * Select all / Clear, and a live count of how many pages are checked.
 *
 * Shared across Extract, Delete and Rotate rather than each screen laying
 * out its own — the three differ only in what running does with the
 * selection, not in how someone builds it.
 */
@Composable
fun SelectionSummaryRow(
    selectedCount: Int,
    onSelectAll: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dewey.spacing.row),
    ) {
        SecondaryAction(label = "Select all", onClick = onSelectAll)
        SecondaryAction(label = "Clear", onClick = onClear)
        Text(
            text = "$selectedCount selected",
            style = Dewey.type.Meta,
            color = Dewey.colors.inkMuted,
            modifier = Modifier.weight(1f),
        )
    }
}
