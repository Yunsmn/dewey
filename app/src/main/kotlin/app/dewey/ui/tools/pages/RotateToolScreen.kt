package app.dewey.ui.tools.pages

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.dewey.pdf.PageThumbnailSource
import app.dewey.pdf.PdfToolkit
import app.dewey.ui.theme.Dewey
import app.dewey.ui.theme.DeweyTheme
import app.dewey.ui.tools.FieldLabel
import app.dewey.ui.tools.FileSlot
import app.dewey.ui.tools.PickedFile
import app.dewey.ui.tools.ToolDestination
import app.dewey.ui.tools.ToolScaffold
import app.dewey.ui.tools.derivedFileName
import app.dewey.ui.tools.hue
import app.dewey.ui.tools.rememberPdfPicker
import app.dewey.ui.tools.rememberSaveAs
import app.dewey.ui.tools.thumbnails.PageGrid
import app.dewey.ui.tools.thumbnails.PageGridMode

private val QUARTER_TURNS = listOf(90, 180, 270)

@Composable
fun RotateToolScreen(toolkit: PdfToolkit, modifier: Modifier = Modifier) {
    val viewModel: RotateToolViewModel = viewModel(factory = RotateToolViewModel.factory(toolkit))
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pickFile = rememberPdfPicker(onPicked = viewModel::onFilePicked)
    val saveAs = rememberSaveAs(onCreated = viewModel::run)

    RotateToolContent(
        state = state,
        thumbnailSource = viewModel.thumbnails,
        onPickFile = pickFile,
        onToggle = viewModel::onToggle,
        onSelectAll = viewModel::onSelectAll,
        onClear = viewModel::onClearSelection,
        onDegreesChosen = viewModel::onDegreesChosen,
        onRun = { saveAs(derivedFileName(state.file?.name.orEmpty(), "rotated")) },
        onReset = viewModel::reset,
        modifier = modifier,
    )
}

@Composable
private fun RotateToolContent(
    state: RotateUiState,
    thumbnailSource: PageThumbnailSource,
    onPickFile: () -> Unit,
    onToggle: (Int) -> Unit,
    onSelectAll: () -> Unit,
    onClear: () -> Unit,
    onDegreesChosen: (Int) -> Unit,
    onRun: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hue = ToolDestination.ROTATE.group.hue

    ToolScaffold(
        title = "Rotate",
        description = "Turn pages a quarter at a time. Every page is selected to start — tap to turn only some.",
        state = state.runState,
        runLabel = "Rotate and save",
        canRun = state.canRun,
        onRun = onRun,
        onReset = onReset,
        modifier = modifier,
        hue = hue,
        icon = ToolDestination.ROTATE.icon,
    ) {
        FieldLabel("Document")
        FileSlot(file = state.file, prompt = "Choose a PDF", onPick = onPickFile, hue = hue)

        if (state.file != null) {
            FieldLabel("Direction")
            QuarterTurnChoice(selected = state.degrees, onSelect = onDegreesChosen)

            PageCountHint(state.pageCount)
            val pageCount = state.pageCount
            if (pageCount != null) {
                SelectionSummaryRow(selectedCount = state.selected.size, onSelectAll = onSelectAll, onClear = onClear)
                PageGrid(
                    mode = PageGridMode.Select(
                        pageCount = pageCount,
                        selected = state.selected,
                        onToggle = onToggle,
                        previewRotationDegrees = state.degrees ?: 0,
                    ),
                    thumbnailSource = thumbnailSource,
                    file = state.file,
                    hue = hue,
                )
            }
        }
    }
}

@Composable
private fun QuarterTurnChoice(selected: Int?, onSelect: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(Dewey.spacing.row)) {
        QUARTER_TURNS.forEach { degrees ->
            QuarterTurnChip(degrees = degrees, isSelected = degrees == selected, onClick = { onSelect(degrees) })
        }
    }
}

@Composable
private fun QuarterTurnChip(degrees: Int, isSelected: Boolean, onClick: () -> Unit) {
    val hue = ToolDestination.ROTATE.group.hue
    val shape = RoundedCornerShape(Dewey.radii.small)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(if (isSelected) hue.strong else Dewey.colors.paperSunken, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = Dewey.spacing.gutter, vertical = Dewey.spacing.row),
    ) {
        Text(
            text = "$degrees°",
            style = Dewey.type.Meta.copy(fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal),
            color = if (isSelected) Dewey.colors.onAccent else Dewey.colors.inkMuted,
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B0F17, heightDp = 700, widthDp = 390)
@Composable
private fun RotateToolScreenPreview() {
    DeweyTheme {
        val context = LocalContext.current
        RotateToolContent(
            // pageCount left null — see ExtractToolScreenPreview's comment.
            state = RotateUiState(file = PickedFile(Uri.EMPTY, "lease.pdf", 482_000), degrees = 90),
            thumbnailSource = PageThumbnailSource(context.contentResolver, context.cacheDir),
            onPickFile = {},
            onToggle = {},
            onSelectAll = {},
            onClear = {},
            onDegreesChosen = {},
            onRun = {},
            onReset = {},
        )
    }
}
