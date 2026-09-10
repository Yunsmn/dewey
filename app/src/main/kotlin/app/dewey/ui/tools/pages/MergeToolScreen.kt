package app.dewey.ui.tools.pages

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import app.dewey.ui.tools.ToolScaffold
import app.dewey.ui.tools.derivedFileName
import app.dewey.ui.tools.formatBytes
import app.dewey.ui.tools.rememberPdfsPicker
import app.dewey.ui.tools.rememberSaveAs

@Composable
fun MergeToolScreen(toolkit: PdfToolkit, modifier: Modifier = Modifier) {
    val viewModel: MergeToolViewModel = viewModel(factory = MergeToolViewModel.factory(toolkit))
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pickFiles = rememberPdfsPicker(onPicked = viewModel::addFiles)
    val saveAs = rememberSaveAs(onCreated = viewModel::run)

    MergeToolContent(
        state = state,
        onPickMore = pickFiles,
        onMoveUp = viewModel::moveUp,
        onMoveDown = viewModel::moveDown,
        onRemove = viewModel::remove,
        onRun = {
            // A merge has no one source to derive a name from; the first file
            // in the order is as good a stem as any.
            val stem = state.files.firstOrNull()?.name.orEmpty()
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
    onMoveUp: (Int) -> Unit,
    onMoveDown: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onRun: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ToolScaffold(
        title = "Merge",
        description = "Combine PDFs, in the order below, into one document. The originals stay as they are.",
        state = state.runState,
        runLabel = "Merge and save",
        canRun = state.canRun,
        onRun = onRun,
        onReset = onReset,
        modifier = modifier,
    ) {
        FieldLabel("Files, in order")
        state.files.forEachIndexed { index, file ->
            MergeFileRow(
                file = file,
                position = index + 1,
                canMoveUp = index > 0,
                canMoveDown = index < state.files.lastIndex,
                onMoveUp = { onMoveUp(index) },
                onMoveDown = { onMoveDown(index) },
                onRemove = { onRemove(index) },
            )
        }
        SecondaryAction(
            label = if (state.files.isEmpty()) "Choose PDFs" else "Add more",
            onClick = onPickMore,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun MergeFileRow(
    file: PickedFile,
    position: Int,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
) {
    GlassCard(modifier = Modifier.fillMaxWidth().padding(vertical = Dewey.spacing.hairline)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dewey.spacing.row),
            modifier = Modifier.fillMaxWidth(),
        ) {
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
            RowIconButton(icon = Icons.Outlined.ArrowUpward, enabled = canMoveUp, onClick = onMoveUp)
            RowIconButton(icon = Icons.Outlined.ArrowDownward, enabled = canMoveDown, onClick = onMoveDown)
            RowIconButton(icon = Icons.Outlined.Close, enabled = true, onClick = onRemove)
        }
    }
}

@Composable
private fun RowIconButton(icon: ImageVector, enabled: Boolean, onClick: () -> Unit) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = if (enabled) Dewey.colors.inkMuted else Dewey.colors.inkFaint,
        modifier = Modifier
            .background(Color.Transparent, RoundedCornerShape(2.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(Dewey.spacing.tight),
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF0B0F17, heightDp = 700, widthDp = 390)
@Composable
private fun MergeToolScreenPreview() {
    DeweyTheme {
        MergeToolContent(
            state = MergeUiState(
                files = listOf(
                    PickedFile(Uri.EMPTY, "lease.pdf", 482_000),
                    PickedFile(Uri.EMPTY, "addendum.pdf", 120_000),
                    PickedFile(Uri.EMPTY, "signature-page.pdf", 40_000),
                ),
            ),
            onPickMore = {},
            onMoveUp = {},
            onMoveDown = {},
            onRemove = {},
            onRun = {},
            onReset = {},
        )
    }
}
