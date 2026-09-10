package app.dewey.ui.tools.pages

import app.dewey.ui.tools.ToolTextField
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.dewey.pdf.PdfToolkit
import app.dewey.ui.theme.Dewey
import app.dewey.ui.theme.DeweyTheme
import app.dewey.ui.tools.FieldLabel
import app.dewey.ui.tools.FileSlot
import app.dewey.ui.tools.PickedFile
import app.dewey.ui.tools.ToolScaffold
import app.dewey.ui.tools.derivedFileName
import app.dewey.ui.tools.rememberPdfPicker
import app.dewey.ui.tools.rememberSaveAs

@Composable
fun ReorderToolScreen(toolkit: PdfToolkit, modifier: Modifier = Modifier) {
    val viewModel: ReorderToolViewModel = viewModel(factory = ReorderToolViewModel.factory(toolkit))
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pickFile = rememberPdfPicker(onPicked = viewModel::onFilePicked)
    val saveAs = rememberSaveAs(onCreated = viewModel::run)

    ReorderToolContent(
        state = state,
        onPickFile = pickFile,
        onFromChanged = viewModel::onFromChanged,
        onToChanged = viewModel::onToChanged,
        onRun = { saveAs(derivedFileName(state.file?.name.orEmpty(), "reordered")) },
        onReset = viewModel::reset,
        modifier = modifier,
    )
}

@Composable
private fun ReorderToolContent(
    state: ReorderUiState,
    onPickFile: () -> Unit,
    onFromChanged: (String) -> Unit,
    onToChanged: (String) -> Unit,
    onRun: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ToolScaffold(
        title = "Reorder",
        description = "Move one page to a new position. Everything between shifts to make room.",
        state = state.runState,
        runLabel = "Reorder and save",
        canRun = state.canRun,
        onRun = onRun,
        onReset = onReset,
        modifier = modifier,
    ) {
        FieldLabel("Document")
        FileSlot(file = state.file, prompt = "Choose a PDF", onPick = onPickFile)

        if (state.file != null) {
            PageCountHint(state.pageCount)
            Row(horizontalArrangement = Arrangement.spacedBy(Dewey.spacing.gutter)) {
                Column(modifier = Modifier.weight(1f)) {
                    FieldLabel("Move page")
                    ToolTextField(
                        value = state.fromText,
                        onValueChange = onFromChanged,
                        placeholder = "e.g. 5",
                        keyboardType = KeyboardType.Number,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    FieldLabel("To position")
                    ToolTextField(
                        value = state.toText,
                        onValueChange = onToChanged,
                        placeholder = "e.g. 1",
                        keyboardType = KeyboardType.Number,
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B0F17, heightDp = 700, widthDp = 390)
@Composable
private fun ReorderToolScreenPreview() {
    DeweyTheme {
        ReorderToolContent(
            state = ReorderUiState(
                file = PickedFile(Uri.EMPTY, "lease.pdf", 482_000),
                pageCount = 12,
                fromText = "5",
                toText = "1",
            ),
            onPickFile = {},
            onFromChanged = {},
            onToChanged = {},
            onRun = {},
            onReset = {},
        )
    }
}
