package app.dewey.ui.tools.split

import app.dewey.ui.tools.ToolTextField
import app.dewey.ui.tools.OptionRow
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.dewey.pdf.PdfToolkit
import app.dewey.ui.components.IconTile
import app.dewey.ui.theme.Dewey
import app.dewey.ui.theme.DeweyTheme
import app.dewey.ui.theme.Hue
import app.dewey.ui.tools.FieldLabel
import app.dewey.ui.tools.FileSlot
import app.dewey.ui.tools.PickedFile
import app.dewey.ui.tools.ToolDestination
import app.dewey.ui.tools.ToolRunState
import app.dewey.ui.tools.ToolScaffold
import app.dewey.ui.tools.hue
import app.dewey.ui.tools.rememberFolderPicker
import app.dewey.ui.tools.rememberPdfPicker

@Composable
fun SplitToolScreen(toolkit: PdfToolkit, modifier: Modifier = Modifier) {
    val viewModel: SplitToolViewModel = viewModel(factory = SplitToolViewModel.factory(toolkit))
    val state by viewModel.state.collectAsStateWithLifecycle()

    val pickSource = rememberPdfPicker(onPicked = viewModel::onSourcePicked)
    val pickDestination = rememberFolderPicker(onPicked = viewModel::onDestinationPicked)

    SplitContent(
        state = state,
        onPickSource = pickSource,
        onPickDestination = pickDestination,
        onModeChosen = viewModel::onModeChosen,
        onEveryNChanged = viewModel::onEveryNChanged,
        onRangesChanged = viewModel::onRangesChanged,
        onRun = viewModel::run,
        onReset = viewModel::reset,
        modifier = modifier,
    )
}

@Composable
private fun SplitContent(
    state: SplitUiState,
    onPickSource: () -> Unit,
    onPickDestination: () -> Unit,
    onModeChosen: (SplitMode) -> Unit,
    onEveryNChanged: (String) -> Unit,
    onRangesChanged: (String) -> Unit,
    onRun: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hue = ToolDestination.SPLIT.group.hue

    ToolScaffold(
        title = "Split",
        description = "Break one PDF into several smaller ones, saved into a folder you choose.",
        state = state.run,
        runLabel = "Split and save",
        canRun = state.canRun,
        onRun = onRun,
        onReset = onReset,
        modifier = modifier,
        hue = hue,
        icon = ToolDestination.SPLIT.icon,
    ) {
        FieldLabel("Document")
        FileSlot(file = state.source, prompt = "Choose a PDF", onPick = onPickSource, hue = hue)

        if (state.pageCount != null) {
            Spacer(Modifier.height(Dewey.spacing.tight))
            Text("${pageWord(state.pageCount)} in this document", style = Dewey.type.Meta, color = Dewey.colors.inkMuted)
        }

        Spacer(Modifier.height(Dewey.spacing.row))
        FieldLabel("Split by")
        OptionRow(options = SplitMode.entries, selected = state.mode, label = { it.label }, onSelected = onModeChosen, hue = hue)

        Spacer(Modifier.height(Dewey.spacing.row))
        when (state.mode) {
            SplitMode.EVERY_N_PAGES -> {
                FieldLabel("Pages per file")
                ToolTextField(
                    value = state.everyNText,
                    onValueChange = onEveryNChanged,
                    placeholder = "1",
                    keyboardType = KeyboardType.Number,
                    hue = hue,
                )
            }

            SplitMode.BY_RANGES -> {
                FieldLabel("Page ranges")
                ToolTextField(
                    value = state.rangesText,
                    onValueChange = onRangesChanged,
                    placeholder = "e.g. 1-3, 4-7, 8-11",
                    hue = hue,
                )
            }
        }

        Spacer(Modifier.height(Dewey.spacing.row))
        FieldLabel("Save to")
        FolderSlot(folderName = state.destinationName, prompt = "Choose a folder", onPick = onPickDestination, hue = hue)
    }
}

/**
 * [FileSlot]'s sibling for a folder — the same sunken slot with a folder
 * glyph — kept local to this screen the way
 * [app.dewey.ui.tools.raster.PdfToImagesToolScreen] keeps its own: the shared
 * layer only has room for one file-shaped slot, and duplicating this small a
 * composable costs less than reaching into another tool package for it.
 */
@Composable
private fun FolderSlot(
    folderName: String?,
    prompt: String,
    onPick: () -> Unit,
    hue: Hue,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(Dewey.radii.medium)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Dewey.colors.paperSunken)
            .clickable(onClick = onPick, role = Role.Button)
            .padding(Dewey.spacing.gutter),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dewey.spacing.row),
    ) {
        IconTile(icon = Icons.Rounded.Folder, hue = hue, size = 40.dp)
        if (folderName == null) {
            Text(prompt, style = Dewey.type.Body, color = Dewey.colors.ink)
        } else {
            Column {
                Text(
                    text = folderName,
                    style = Dewey.type.Mono,
                    color = Dewey.colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(Dewey.spacing.hairline))
                Text("tap to change", style = Dewey.type.Meta, color = Dewey.colors.inkMuted)
            }
        }
    }
}

private fun pageWord(count: Int): String = if (count == 1) "1 page" else "$count pages"

@Preview(showBackground = true, backgroundColor = 0xFF0B0F17, heightDp = 820, widthDp = 390)
@Composable
private fun SplitToolScreenDarkPreview() {
    DeweyTheme(dark = true) {
        SplitContent(
            state = SplitUiState(
                source = PickedFile(Uri.EMPTY, "statement.pdf", 900_000),
                pageCount = 24,
                mode = SplitMode.EVERY_N_PAGES,
                everyNText = "5",
                destinationName = "Statements",
            ),
            onPickSource = {},
            onPickDestination = {},
            onModeChosen = {},
            onEveryNChanged = {},
            onRangesChanged = {},
            onRun = {},
            onReset = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF5F6FA, heightDp = 820, widthDp = 390)
@Composable
private fun SplitToolScreenLightPreview() {
    DeweyTheme(dark = false) {
        SplitContent(
            state = SplitUiState(
                source = PickedFile(Uri.EMPTY, "contract.pdf", 482_000),
                pageCount = 11,
                mode = SplitMode.BY_RANGES,
                rangesText = "1-3, 4-7, 8-11",
                destinationName = "Contracts",
                run = ToolRunState.Done("Split into 3 of 4 PDFs. Part 2 couldn't be saved."),
            ),
            onPickSource = {},
            onPickDestination = {},
            onModeChosen = {},
            onEveryNChanged = {},
            onRangesChanged = {},
            onRun = {},
            onReset = {},
        )
    }
}
