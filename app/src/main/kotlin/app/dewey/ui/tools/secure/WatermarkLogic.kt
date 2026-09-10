package app.dewey.ui.tools.secure

import android.net.Uri
import app.dewey.ui.tools.ToolRunState
import app.dewey.ui.tools.toolFailureMessage

/** Whether a watermark run has a document and something to stamp on it. */
fun canRunWatermark(hasSource: Boolean, text: String): Boolean = hasSource && text.isNotBlank()

/** What to tell the user after stamping a watermark onto every page. */
fun watermarkSummary(text: String): String = "Watermarked every page with \"${text.trim()}\"."

/** Where a watermark run lands: nothing here needs clearing, unlike [ProtectUiState]'s password. */
fun WatermarkUiState.afterRun(result: Result<Unit>, target: Uri): WatermarkUiState = result.fold(
    onSuccess = { copy(run = ToolRunState.Done(watermarkSummary(text), target)) },
    onFailure = { copy(run = ToolRunState.Failed(toolFailureMessage(it))) },
)
