package app.dewey.ui.tools.pages

import app.dewey.ui.tools.ToolTextField
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
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

private val QUARTER_TURNS = listOf(90, 180, 270)

@Composable
fun RotateToolScreen(toolkit: PdfToolkit, modifier: Modifier = Modifier) {
    val viewModel: RotateToolViewModel = viewModel(factory = RotateToolViewModel.factory(toolkit))
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pickFile = rememberPdfPicker(onPicked = viewModel::onFilePicked)
    val saveAs = rememberSaveAs(onCreated = viewModel::run)

    RotateToolContent(
        state = state,
        onPickFile = pickFile,
        onRangeChanged = viewModel::onRangeChanged,
        onDegreesChosen = viewModel::onDegreesChosen,
        onRun = { saveAs(derivedFileName(state.file?.name.orEmpty(), "rotated")) },
        onReset = viewModel::reset,
        modifier = modifier,
    )
}

@Composable
private fun RotateToolContent(
    state: RotateUiState,
    onPickFile: () -> Unit,
    onRangeChanged: (String) -> Unit,
    onDegreesChosen: (Int) -> Unit,
    onRun: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ToolScaffold(
        title = "Rotate",
        description = "Turn pages a quarter at a time. Leave the range blank to rotate the whole document.",
        state = state.runState,
        runLabel = "Rotate and save",
        canRun = state.canRun,
        onRun = onRun,
        onReset = onReset,
        modifier = modifier,
    ) {
        FieldLabel("Document")
        FileSlot(file = state.file, prompt = "Choose a PDF", onPick = onPickFile)

        if (state.file != null) {
            FieldLabel("Pages to rotate")
            PageCountHint(state.pageCount)
            ToolTextField(
                value = state.rangeText,
                onValueChange = onRangeChanged,
                placeholder = "e.g. 1-3, 7 — blank means every page",
            )

            FieldLabel("Direction")
            QuarterTurnChoice(selected = state.degrees, onSelect = onDegreesChosen)
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
    val shape = RoundedCornerShape(2.dp)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(if (isSelected) Dewey.colors.accentSoft else Color.Transparent, shape)
            .border(1.dp, if (isSelected) Dewey.colors.accent else Dewey.colors.rule, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = Dewey.spacing.gutter, vertical = Dewey.spacing.tight),
    ) {
        Text(
            text = "$degrees°",
            style = Dewey.type.Meta,
            color = if (isSelected) Dewey.colors.ink else Dewey.colors.inkMuted,
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B0F17, heightDp = 700, widthDp = 390)
@Composable
private fun RotateToolScreenPreview() {
    DeweyTheme {
        RotateToolContent(
            state = RotateUiState(
                file = PickedFile(Uri.EMPTY, "lease.pdf", 482_000),
                pageCount = 12,
                rangeText = "",
                degrees = 90,
            ),
            onPickFile = {},
            onRangeChanged = {},
            onDegreesChosen = {},
            onRun = {},
            onReset = {},
        )
    }
}
