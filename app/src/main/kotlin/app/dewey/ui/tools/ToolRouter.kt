package app.dewey.ui.tools

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import app.dewey.pdf.PdfToolkit
import app.dewey.scan.DocumentScanner
import app.dewey.ui.scan.ScanScreen
import app.dewey.ui.scan.ScanViewModel
import app.dewey.ui.tools.pages.DeletePagesToolScreen
import app.dewey.ui.tools.pages.ExtractToolScreen
import app.dewey.ui.tools.pages.MergeToolScreen
import app.dewey.ui.tools.pages.ReorderToolScreen
import app.dewey.ui.tools.pages.RotateToolScreen
import app.dewey.ui.tools.raster.CompressToolScreen
import app.dewey.ui.tools.raster.ImagesToPdfToolScreen
import app.dewey.ui.tools.raster.PdfToImagesToolScreen
import app.dewey.ui.tools.secure.PageNumbersToolScreen
import app.dewey.ui.tools.secure.ProtectToolScreen
import app.dewey.ui.tools.secure.UnlockToolScreen
import app.dewey.ui.tools.secure.WatermarkToolScreen

/**
 * The screen behind each entry on the Tools hub.
 *
 * An exhaustive `when` over [ToolDestination] rather than a map, on purpose:
 * adding a tool to the hub without giving it a screen fails to compile, instead
 * of shipping a row that opens nothing.
 */
@Composable
fun ToolScreen(tool: ToolDestination, toolkit: PdfToolkit) {
    when (tool) {
        ToolDestination.SCAN -> {
            val context = LocalContext.current
            val model: ScanViewModel = viewModel(
                factory = ScanViewModel.factory(
                    scanner = DocumentScanner(context),
                    resolver = context.contentResolver,
                ),
            )
            ScanScreen(viewModel = model)
        }

        ToolDestination.MERGE -> MergeToolScreen(toolkit)
        ToolDestination.EXTRACT -> ExtractToolScreen(toolkit)
        ToolDestination.ROTATE -> RotateToolScreen(toolkit)
        ToolDestination.REORDER -> ReorderToolScreen(toolkit)
        ToolDestination.DELETE_PAGES -> DeletePagesToolScreen(toolkit)

        ToolDestination.PDF_TO_IMAGES -> PdfToImagesToolScreen(toolkit)
        ToolDestination.IMAGES_TO_PDF -> ImagesToPdfToolScreen(toolkit)
        ToolDestination.COMPRESS -> CompressToolScreen(toolkit)

        ToolDestination.PROTECT -> ProtectToolScreen(toolkit)
        ToolDestination.UNLOCK -> UnlockToolScreen(toolkit)
        ToolDestination.WATERMARK -> WatermarkToolScreen(toolkit)
        ToolDestination.PAGE_NUMBERS -> PageNumbersToolScreen(toolkit)
    }
}
