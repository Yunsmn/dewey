package app.dewey.ui.tools.secure

import app.dewey.ui.tools.OptionRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
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
fun ProtectToolScreen(toolkit: PdfToolkit, modifier: Modifier = Modifier) {
    val viewModel: ProtectViewModel = viewModel(factory = ProtectViewModel.factory(toolkit))
    val state by viewModel.state.collectAsStateWithLifecycle()

    val pickSource = rememberPdfPicker(onPicked = viewModel::onSourcePicked)
    val saveAs = rememberSaveAs(onCreated = viewModel::onDestinationChosen)

    ProtectContent(
        state = state,
        onPickSource = pickSource,
        onPasswordChanged = viewModel::onPasswordChanged,
        onConfirmPasswordChanged = viewModel::onConfirmPasswordChanged,
        onPasswordVisibilityToggled = viewModel::onPasswordVisibilityToggled,
        onAllowPrintingChosen = viewModel::onAllowPrintingChosen,
        onRun = { saveAs(state.suggestedFileName) },
        onReset = viewModel::reset,
        modifier = modifier,
    )
}

@Composable
private fun ProtectContent(
    state: ProtectUiState,
    onPickSource: () -> Unit,
    onPasswordChanged: (String) -> Unit,
    onConfirmPasswordChanged: (String) -> Unit,
    onPasswordVisibilityToggled: () -> Unit,
    onAllowPrintingChosen: (Boolean) -> Unit,
    onRun: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ToolScaffold(
        title = "Protect",
        description = "Lock this PDF with a password. Anyone opening it needs the password to read a word of " +
            "it — and a forgotten one cannot be recovered, so keep it somewhere safe.",
        state = state.run,
        runLabel = "Protect and save",
        canRun = state.canRun,
        onRun = onRun,
        onReset = onReset,
        modifier = modifier,
    ) {
        FieldLabel("Document")
        FileSlot(file = state.source, prompt = "Choose a PDF", onPick = onPickSource)

        Spacer(Modifier.height(Dewey.spacing.row))
        FieldLabel("Password")
        PasswordField(
            value = state.password,
            onValueChange = onPasswordChanged,
            placeholder = "At least 8 characters",
            visible = state.passwordVisible,
            onToggleVisible = onPasswordVisibilityToggled,
        )
        protectPasswordReason(state.password)?.let { reason ->
            Spacer(Modifier.height(Dewey.spacing.hairline))
            Text(reason, style = Dewey.type.Meta, color = Dewey.colors.danger)
        }

        Spacer(Modifier.height(Dewey.spacing.row))
        FieldLabel("Confirm password")
        PasswordField(
            value = state.confirmPassword,
            onValueChange = onConfirmPasswordChanged,
            placeholder = "Type it again",
            visible = state.passwordVisible,
            onToggleVisible = onPasswordVisibilityToggled,
        )
        protectConfirmReason(state.password, state.confirmPassword)?.let { reason ->
            Spacer(Modifier.height(Dewey.spacing.hairline))
            Text(reason, style = Dewey.type.Meta, color = Dewey.colors.danger)
        }

        Spacer(Modifier.height(Dewey.spacing.row))
        FieldLabel("Printing")
        OptionRow(
            options = listOf(true, false),
            selected = state.allowPrinting,
            label = { allow -> if (allow) "Allow printing" else "Ask readers not to print" },
            onSelected = onAllowPrintingChosen,
        )
        Spacer(Modifier.height(Dewey.spacing.hairline))
        Text(
            "A request only — not every reader obeys it once the password opens the file.",
            style = Dewey.type.Meta,
            color = Dewey.colors.inkMuted,
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B0F17, heightDp = 780, widthDp = 390)
@Composable
private fun ProtectPreview() {
    DeweyTheme {
        ProtectContent(
            state = ProtectUiState(
                source = PickedFile(android.net.Uri.EMPTY, "lease.pdf", 482_000),
                password = "sunlitgarden",
                confirmPassword = "sunlitgard",
            ),
            onPickSource = {},
            onPasswordChanged = {},
            onConfirmPasswordChanged = {},
            onPasswordVisibilityToggled = {},
            onAllowPrintingChosen = {},
            onRun = {},
            onReset = {},
        )
    }
}
