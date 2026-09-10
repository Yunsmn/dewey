package app.dewey.ui.tools.secure

import app.dewey.ui.tools.ToolTextField
import app.dewey.ui.tools.OptionRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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
import app.dewey.ui.tools.rememberPdfPicker
import app.dewey.ui.tools.rememberSaveAs

@Composable
fun WatermarkToolScreen(toolkit: PdfToolkit, modifier: Modifier = Modifier) {
    val viewModel: WatermarkViewModel = viewModel(factory = WatermarkViewModel.factory(toolkit))
    val state by viewModel.state.collectAsStateWithLifecycle()

    val pickSource = rememberPdfPicker(onPicked = viewModel::onSourcePicked)
    val saveAs = rememberSaveAs(onCreated = viewModel::onDestinationChosen)

    WatermarkContent(
        state = state,
        onPickSource = pickSource,
        onTextChanged = viewModel::onTextChanged,
        onOpacityChosen = viewModel::onOpacityChosen,
        onAngleChosen = viewModel::onAngleChosen,
        onRun = { saveAs(state.suggestedFileName) },
        onReset = viewModel::reset,
        modifier = modifier,
    )
}

@Composable
private fun WatermarkContent(
    state: WatermarkUiState,
    onPickSource: () -> Unit,
    onTextChanged: (String) -> Unit,
    onOpacityChosen: (WatermarkOpacity) -> Unit,
    onAngleChosen: (WatermarkAngle) -> Unit,
    onRun: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ToolScaffold(
        title = "Watermark",
        description = "Stamp a line of text across every page, faint enough that the page stays legible underneath it.",
        state = state.run,
        runLabel = "Watermark and save",
        canRun = state.canRun,
        onRun = onRun,
        onReset = onReset,
        modifier = modifier,
    ) {
        FieldLabel("Document")
        FileSlot(file = state.source, prompt = "Choose a PDF", onPick = onPickSource)

        Spacer(Modifier.height(Dewey.spacing.row))
        FieldLabel("Watermark text")
        ToolTextField(
            value = state.text,
            onValueChange = onTextChanged,
            placeholder = "e.g. CONFIDENTIAL",
        )

        Spacer(Modifier.height(Dewey.spacing.row))
        FieldLabel("Opacity")
        OptionRow(
            options = WatermarkOpacity.entries,
            selected = state.opacity,
            label = { it.label },
            onSelected = onOpacityChosen,
        )

        Spacer(Modifier.height(Dewey.spacing.row))
        FieldLabel("Angle")
        OptionRow(
            options = WatermarkAngle.entries,
            selected = state.angle,
            label = { it.label },
            onSelected = onAngleChosen,
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B0F17, heightDp = 780, widthDp = 390)
@Composable
private fun WatermarkPreview() {
    DeweyTheme {
        WatermarkContent(
            state = WatermarkUiState(
                source = PickedFile(android.net.Uri.EMPTY, "contract.pdf", 240_000),
                text = "DRAFT",
            ),
            onPickSource = {},
            onTextChanged = {},
            onOpacityChosen = {},
            onAngleChosen = {},
            onRun = {},
            onReset = {},
        )
    }
}
