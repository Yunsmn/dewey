package app.dewey.ui.tools

import app.dewey.pdf.PdfToolkit
import app.dewey.ui.tools.PickedFile

/**
 * The page count a screen shows once a PDF is picked, so a typed range can be
 * checked against real bounds before the tool ever tries to run.
 *
 * Returns the [PdfWorkspace][app.dewey.pdf.PdfWorkspace] failure rather than
 * swallowing it: an encrypted or unreadable file has to surface here, because
 * this is the only read the screen does before the user starts typing a
 * range — if this fails silently, "why won't it let me run" has no answer on
 * screen.
 */
suspend fun PdfToolkit.readPageCount(file: PickedFile): Result<Int> =
    workspace.read(file.uri, file.sizeBytes) { it.numberOfPages }
