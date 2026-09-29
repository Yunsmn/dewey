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
import app.dewey.ui.components.SecondaryAction
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
fun ReorderToolScreen(toolkit: PdfToolkit, modifier: Modifier = Modifier) {
    val viewModel: ReorderToolViewModel = viewModel(factory = ReorderToolViewModel.factory(toolkit))
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pickFile = rememberPdfPicker(onPicked = viewModel::onFilePicked)
    val saveAs = rememberSaveAs(onCreated = viewModel::run)

    ReorderToolContent(
        state = state,
        thumbnailSource = viewModel.thumbnails,
        onPickFile = pickFile,
        onMove = viewModel::onMove,
        onResetOrder = viewModel::resetOrder,
        onRun = { saveAs(derivedFileName(state.file?.name.orEmpty(), "reordered")) },
        onReset = viewModel::reset,
        modifier = modifier,
    )
}

@Composable
private fun ReorderToolContent(
    state: ReorderUiState,
    thumbnailSource: PageThumbnailSource,
    onPickFile: () -> Unit,
    onMove: (from: Int, to: Int) -> Unit,
    onResetOrder: () -> Unit,
    onRun: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hue = ToolDestination.REORDER.group.hue

    ToolScaffold(
        title = "Reorder",
        description = "Long-press a page and drag it where it belongs. Everything between shifts to make room.",
        state = state.runState,
        runLabel = "Reorder and save",
        canRun = state.canRun,
        onRun = onRun,
        onReset = onReset,
        modifier = modifier,
        hue = hue,
        icon = ToolDestination.REORDER.icon,
    ) {
        FieldLabel("Document")
        FileSlot(file = state.file, prompt = "Choose a PDF", onPick = onPickFile, hue = hue)

        if (state.file != null) {
            PageCountHint(state.pageCount)
            val order = state.order
            if (order != null) {
                SecondaryAction(label = "Reset order", onClick = onResetOrder)
                PageGrid(
                    mode = PageGridMode.Reorder(order = order, onMove = onMove),
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
private fun ReorderToolScreenPreview() {
    DeweyTheme {
        val context = LocalContext.current
        ReorderToolContent(
            // order left null — see ExtractToolScreenPreview's comment; the
            // grid needs a real PageThumbnailSource to mount.
            state = ReorderUiState(file = PickedFile(Uri.EMPTY, "lease.pdf", 482_000)),
            thumbnailSource = PageThumbnailSource(context.contentResolver, context.cacheDir),
            onPickFile = {},
            onMove = { _, _ -> },
            onResetOrder = {},
            onRun = {},
            onReset = {},
        )
    }
}
