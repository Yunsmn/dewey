package app.dewey.ui.tools.pages

import app.dewey.ui.tools.ToolTextField
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.dewey.pdf.PdfToolkit
import app.dewey.ui.theme.DeweyTheme
import app.dewey.ui.tools.FieldLabel
import app.dewey.ui.tools.FileSlot
import app.dewey.ui.tools.PickedFile
import app.dewey.ui.tools.ToolScaffold
import app.dewey.ui.tools.derivedFileName
import app.dewey.ui.tools.rememberPdfPicker
import app.dewey.ui.tools.rememberSaveAs

@Composable
fun DeletePagesToolScreen(toolkit: PdfToolkit, modifier: Modifier = Modifier) {
    val viewModel: DeletePagesToolViewModel = viewModel(factory = DeletePagesToolViewModel.factory(toolkit))
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pickFile = rememberPdfPicker(onPicked = viewModel::onFilePicked)
    val saveAs = rememberSaveAs(onCreated = viewModel::run)

    DeletePagesToolContent(
        state = state,
        onPickFile = pickFile,
        onRangeChanged = viewModel::onRangeChanged,
        onRun = { saveAs(derivedFileName(state.file?.name.orEmpty(), "edited")) },
        onReset = viewModel::reset,
        modifier = modifier,
    )
}

@Composable
private fun DeletePagesToolContent(
    state: DeleteUiState,
    onPickFile: () -> Unit,
    onRangeChanged: (String) -> Unit,
    onRun: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ToolScaffold(
        title = "Delete pages",
        description = "Remove specific pages from a document. The original stays as it is.",
        state = state.runState,
        runLabel = "Delete and save",
        canRun = state.canRun,
        onRun = onRun,
        onReset = onReset,
        modifier = modifier,
    ) {
        FieldLabel("Document")
        FileSlot(file = state.file, prompt = "Choose a PDF", onPick = onPickFile)

        if (state.file != null) {
            FieldLabel("Pages to delete")
            PageCountHint(state.pageCount)
            ToolTextField(
                value = state.rangeText,
                onValueChange = onRangeChanged,
                placeholder = "e.g. 2, 5-6",
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B0F17, heightDp = 700, widthDp = 390)
@Composable
private fun DeletePagesToolScreenPreview() {
    DeweyTheme {
        DeletePagesToolContent(
            state = DeleteUiState(
                file = PickedFile(Uri.EMPTY, "lease.pdf", 482_000),
                pageCount = 12,
                rangeText = "2,5-6",
            ),
            onPickFile = {},
            onRangeChanged = {},
            onRun = {},
            onReset = {},
        )
    }
}
