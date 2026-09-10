package app.dewey.ui.tools.secure

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
import app.dewey.ui.tools.ToolDestination
import app.dewey.ui.tools.ToolRunState
import app.dewey.ui.tools.ToolScaffold
import app.dewey.ui.tools.hue
import app.dewey.ui.tools.rememberPdfPicker
import app.dewey.ui.tools.rememberSaveAs

@Composable
fun UnlockToolScreen(toolkit: PdfToolkit, modifier: Modifier = Modifier) {
    val viewModel: UnlockViewModel = viewModel(factory = UnlockViewModel.factory(toolkit))
    val state by viewModel.state.collectAsStateWithLifecycle()

    val pickSource = rememberPdfPicker(onPicked = viewModel::onSourcePicked)
    val saveAs = rememberSaveAs(onCreated = viewModel::onDestinationChosen)

    UnlockContent(
        state = state,
        onPickSource = pickSource,
        onPasswordChanged = viewModel::onPasswordChanged,
        onPasswordVisibilityToggled = viewModel::onPasswordVisibilityToggled,
        onRun = { saveAs(state.suggestedFileName) },
        onReset = viewModel::reset,
        modifier = modifier,
    )
}

@Composable
private fun UnlockContent(
    state: UnlockUiState,
    onPickSource: () -> Unit,
    onPasswordChanged: (String) -> Unit,
    onPasswordVisibilityToggled: () -> Unit,
    onRun: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ToolScaffold(
        title = "Unlock",
        description = "Remove a PDF's password, given the password itself. This isn't a recovery tool — the " +
            "right password is still required.",
        state = state.run,
        runLabel = "Unlock and save",
        canRun = state.canRun,
        onRun = onRun,
        onReset = onReset,
        modifier = modifier,
        hue = ToolDestination.UNLOCK.group.hue,
        icon = ToolDestination.UNLOCK.icon,
    ) {
        FieldLabel("Document")
        FileSlot(file = state.source, prompt = "Choose a protected PDF", onPick = onPickSource, hue = ToolDestination.UNLOCK.group.hue)

        Spacer(Modifier.height(Dewey.spacing.row))
        FieldLabel("Password")
        PasswordField(
            value = state.password,
            onValueChange = onPasswordChanged,
            placeholder = "The document's password",
            visible = state.passwordVisible,
            onToggleVisible = onPasswordVisibilityToggled,
            hue = ToolDestination.UNLOCK.group.hue,
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B0F17, heightDp = 700, widthDp = 390)
@Composable
private fun UnlockPreview() {
    DeweyTheme {
        UnlockContent(
            state = UnlockUiState(source = PickedFile(android.net.Uri.EMPTY, "statement.pdf", 240_000)),
            onPickSource = {},
            onPasswordChanged = {},
            onPasswordVisibilityToggled = {},
            onRun = {},
            onReset = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B0F17, heightDp = 700, widthDp = 390)
@Composable
private fun UnlockWrongPasswordPreview() {
    DeweyTheme {
        UnlockContent(
            state = UnlockUiState(
                source = PickedFile(android.net.Uri.EMPTY, "statement.pdf", 240_000),
                password = "guess123",
                run = ToolRunState.Failed("That password doesn't open this PDF."),
            ),
            onPickSource = {},
            onPasswordChanged = {},
            onPasswordVisibilityToggled = {},
            onRun = {},
            onReset = {},
        )
    }
}
