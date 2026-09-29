package app.dewey.ui.tools.pages

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.dewey.pdf.PageThumbnailSource
import app.dewey.pdf.PdfToolkit
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

@Composable
fun DeletePagesToolScreen(toolkit: PdfToolkit, modifier: Modifier = Modifier) {
    val viewModel: DeletePagesToolViewModel = viewModel(factory = DeletePagesToolViewModel.factory(toolkit))
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pickFile = rememberPdfPicker(onPicked = viewModel::onFilePicked)
    val saveAs = rememberSaveAs(onCreated = viewModel::run)

    DeletePagesToolContent(
        state = state,
        thumbnailSource = viewModel.thumbnails,
        onPickFile = pickFile,
        onToggle = viewModel::onToggle,
        onSelectAll = viewModel::onSelectAll,
        onClear = viewModel::onClearSelection,
        onRun = { saveAs(derivedFileName(state.file?.name.orEmpty(), "edited")) },
        onReset = viewModel::reset,
        modifier = modifier,
    )
}

@Composable
private fun DeletePagesToolContent(
    state: DeleteUiState,
    thumbnailSource: PageThumbnailSource,
    onPickFile: () -> Unit,
    onToggle: (Int) -> Unit,
    onSelectAll: () -> Unit,
    onClear: () -> Unit,
    onRun: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hue = ToolDestination.DELETE_PAGES.group.hue

    ToolScaffold(
        title = "Delete pages",
        description = "Tap the pages you don't need, then remove them. The original stays as it is.",
        state = state.runState,
        runLabel = "Delete and save",
        canRun = state.canRun,
        onRun = onRun,
        onReset = onReset,
        modifier = modifier,
        hue = hue,
        icon = ToolDestination.DELETE_PAGES.icon,
    ) {
        FieldLabel("Document")
        FileSlot(file = state.file, prompt = "Choose a PDF", onPick = onPickFile, hue = hue)

        if (state.file != null) {
            PageCountHint(state.pageCount)
            val pageCount = state.pageCount
            if (pageCount != null) {
                SelectionSummaryRow(selectedCount = state.selected.size, onSelectAll = onSelectAll, onClear = onClear)
                PageGrid(
                    mode = PageGridMode.Select(pageCount = pageCount, selected = state.selected, onToggle = onToggle),
                    thumbnailSource = thumbnailSource,
                    file = state.file,
                    hue = hue,
                )
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B0F17, heightDp = 700, widthDp = 390)
@Composable
private fun DeletePagesToolScreenPreview() {
    DeweyTheme {
        val context = LocalContext.current
        DeletePagesToolContent(
            // pageCount left null — see ExtractToolScreenPreview's comment.
            state = DeleteUiState(file = PickedFile(Uri.EMPTY, "lease.pdf", 482_000)),
            thumbnailSource = PageThumbnailSource(context.contentResolver, context.cacheDir),
            onPickFile = {},
            onToggle = {},
            onSelectAll = {},
            onClear = {},
            onRun = {},
            onReset = {},
        )
    }
}
