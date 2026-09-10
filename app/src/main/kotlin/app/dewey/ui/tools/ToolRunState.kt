package app.dewey.ui.tools

import android.net.Uri

/**
 * Where one run of a PDF tool has got to.
 *
 * Every tool screen shares this rather than inventing its own, so the same
 * four situations always look the same: nothing started, working, finished,
 * or failed with a reason a person can act on.
 */
sealed interface ToolRunState {
    data object Idle : ToolRunState

    data object Running : ToolRunState

    /**
     * @param summary what happened, in a sentence — "Merged 3 PDFs into 41
     *   pages", "Saved 12 images, skipped page 7".
     * @param output the file written, when there is exactly one to open.
     */
    data class Done(val summary: String, val output: Uri? = null) : ToolRunState

    data class Failed(val message: String) : ToolRunState
}
