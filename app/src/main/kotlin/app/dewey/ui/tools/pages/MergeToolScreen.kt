package app.dewey.ui.tools.pages

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.dewey.pdf.PdfToolkit
import app.dewey.ui.components.GlassCard
import app.dewey.ui.components.SecondaryAction
import app.dewey.ui.theme.Dewey
import app.dewey.ui.theme.DeweyTheme
import app.dewey.ui.tools.FieldLabel
import app.dewey.ui.tools.PickedFile
import app.dewey.ui.tools.ToolDestination
import app.dewey.ui.tools.ToolScaffold
import app.dewey.ui.tools.derivedFileName
import app.dewey.ui.tools.formatBytes
import app.dewey.ui.tools.hue
import app.dewey.ui.tools.rememberPdfsPicker
import app.dewey.ui.tools.rememberSaveAs
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun MergeToolScreen(toolkit: PdfToolkit, modifier: Modifier = Modifier) {
    val viewModel: MergeToolViewModel = viewModel(factory = MergeToolViewModel.factory(toolkit))
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pickFiles = rememberPdfsPicker(onPicked = viewModel::addFiles)
    val saveAs = rememberSaveAs(onCreated = viewModel::run)

    MergeToolContent(
        state = state,
        onPickMore = pickFiles,
        onMove = viewModel::onMove,
        onRemove = viewModel::remove,
        onRun = {
            // A merge has no one source to derive a name from; the first file
            // in the order is as good a stem as any.
            val stem = state.files.firstOrNull()?.file?.name.orEmpty()
            saveAs(derivedFileName(stem, "merged"))
        },
        onReset = viewModel::reset,
        modifier = modifier,
    )
}

@Composable
private fun MergeToolContent(
    state: MergeUiState,
    onPickMore: () -> Unit,
    onMove: (from: Int, to: Int) -> Unit,
    onRemove: (Int) -> Unit,
    onRun: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ToolScaffold(
        title = "Merge",
        description = "Combine PDFs into one document. Long-press a file and drag it to reorder the list.",
        state = state.runState,
        runLabel = "Merge and save",
        canRun = state.canRun,
        onRun = onRun,
        onReset = onReset,
        modifier = modifier,
        hue = ToolDestination.MERGE.group.hue,
        icon = ToolDestination.MERGE.icon,
    ) {
        FieldLabel("Files, in order")
        if (state.files.isNotEmpty()) {
            MergeFileList(files = state.files, onMove = onMove, onRemove = onRemove)
        }
        SecondaryAction(
            label = if (state.files.isEmpty()) "Choose PDFs" else "Add more",
            onClick = onPickMore,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * A fixed [height] rather than [Modifier.fillMaxSize] on purpose: this list
 * sits inside ToolScaffold's own scrolling column, and a lazy list measured
 * with no height bound inside a scrolling column throws — see
 * [app.dewey.ui.tools.thumbnails.PageGrid]'s doc for the same fix on the same
 * class of crash. Bounding it lets a long merge list scroll internally
 * instead.
 */
@Composable
private fun MergeFileList(
    files: List<MergeFile>,
    onMove: (from: Int, to: Int) -> Unit,
    onRemove: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to -> onMove(from.index, to.index) }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth().height((files.size.coerceAtMost(4) * MERGE_ROW_HEIGHT_DP + 8).dp),
        contentPadding = PaddingValues(vertical = Dewey.spacing.hairline),
    ) {
        itemsIndexed(files, key = { _, entry -> entry.id }) { position, entry ->
            ReorderableItem(reorderState, key = entry.id) { isDragging ->
                MergeFileRow(
                    file = entry.file,
                    position = position + 1,
                    isDragging = isDragging,
                    onRemove = { onRemove(position) },
                    dragHandleModifier = Modifier.longPressDraggableHandle(
                        onDragStarted = { haptics.performHapticFeedback(HapticFeedbackType.LongPress) },
                    ),
                )
            }
        }
    }
}

@Composable
private fun MergeFileRow(
    file: PickedFile,
    position: Int,
    isDragging: Boolean,
    onRemove: () -> Unit,
    dragHandleModifier: Modifier,
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth().padding(vertical = Dewey.spacing.hairline),
        accent = if (isDragging) Dewey.colors.accent else null,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dewey.spacing.row),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                imageVector = Icons.Rounded.DragHandle,
                contentDescription = "Drag to reorder",
                tint = Dewey.colors.inkFaint,
                modifier = dragHandleModifier,
            )
            Text("$position", style = Dewey.type.Label, color = Dewey.colors.inkMuted)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name.ifEmpty { "Chosen file" },
                    style = Dewey.type.Mono,
                    color = Dewey.colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(formatBytes(file.sizeBytes), style = Dewey.type.Meta, color = Dewey.colors.inkMuted)
            }
            RowIconButton(icon = Icons.Outlined.Close, enabled = true, onClick = onRemove)
        }
    }
}

@Composable
private fun RowIconButton(icon: ImageVector, enabled: Boolean, onClick: () -> Unit) {
    Icon(
        imageVector = icon,
        contentDescription = "Remove this file",
        tint = if (enabled) Dewey.colors.inkMuted else Dewey.colors.inkFaint,
        modifier = Modifier
            .background(Color.Transparent, RoundedCornerShape(2.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(Dewey.spacing.tight),
    )
}

private const val MERGE_ROW_HEIGHT_DP = 64

@Preview(showBackground = true, backgroundColor = 0xFF0B0F17, heightDp = 700, widthDp = 390)
@Composable
private fun MergeToolScreenPreview() {
    DeweyTheme {
        MergeToolContent(
            state = MergeUiState(
                files = listOf(
                    MergeFile(1L, PickedFile(Uri.EMPTY, "lease.pdf", 482_000)),
                    MergeFile(2L, PickedFile(Uri.EMPTY, "addendum.pdf", 120_000)),
                    MergeFile(3L, PickedFile(Uri.EMPTY, "signature-page.pdf", 40_000)),
                ),
            ),
            onPickMore = {},
            onMove = { _, _ -> },
            onRemove = {},
            onRun = {},
            onReset = {},
        )
    }
}
