package app.dewey.ui.documents

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.dewey.domain.model.Document
import app.dewey.ui.components.PrimaryAction
import app.dewey.ui.components.SecondaryAction
import app.dewey.ui.components.SectionHeading
import app.dewey.ui.components.TaskBanner
import app.dewey.ui.library.LibraryUiState
import app.dewey.ui.theme.Dewey
import app.dewey.work.TaskState

/**
 * The indexing/sort banner, the Sort and Undo actions, and the documents the
 * classifier would not file on its own — the exact three things
 * `LibraryScreen` shows above its own list, reused here rather than
 * reinvented since both screens are driven by the same
 * [app.dewey.ui.library.LibraryViewModel].
 */
fun LazyListScope.documentsTaskSection(
    state: LibraryUiState,
    onCancelIndexing: () -> Unit,
    onSort: () -> Unit,
    onUndo: () -> Unit,
    onOpenDocument: (Document) -> Unit,
) {
    item(key = "documents-task") {
        val live = state.banner
        AnimatedVisibility(visible = live !is TaskState.Idle) {
            Column {
                TaskBanner(state = live, onCancel = onCancelIndexing)
                Spacer(Modifier.height(Dewey.spacing.gutter))
            }
        }
    }

    item(key = "documents-actions") {
        DocumentsTaskActions(state = state, onSort = onSort, onUndo = onUndo)
    }

    if (state.needsReview.isNotEmpty()) {
        item(key = "documents-review-heading") {
            Spacer(Modifier.height(Dewey.spacing.block))
            SectionHeading(label = "Needs a look", count = state.needsReview.size)
            Text(
                text = "Dewey wasn't sure about these, so it left them where they were.",
                style = Dewey.type.Meta,
                color = Dewey.colors.inkMuted,
                modifier = Modifier.padding(bottom = Dewey.spacing.tight),
            )
        }
        items(state.needsReview, key = { "documents-review-${it.id}" }) { document ->
            NeedsReviewRow(document = document, onOpenDocument = onOpenDocument)
            HorizontalDivider(color = Dewey.colors.rule, thickness = 1.dp)
        }
    }
}

/** Sorting and undo — see `LibraryScreen`'s own `LibraryActions` for why undo sits in the open. */
@Composable
private fun DocumentsTaskActions(state: LibraryUiState, onSort: () -> Unit, onUndo: () -> Unit) {
    if (!state.canSort && !state.canUndo) return

    Row(horizontalArrangement = Arrangement.spacedBy(Dewey.spacing.row)) {
        if (state.canSort) {
            PrimaryAction(label = "Sort into folders", onClick = onSort)
        }
        if (state.canUndo && !state.isBusy) {
            SecondaryAction(label = "Undo the sort", onClick = onUndo)
        }
    }
}
