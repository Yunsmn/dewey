package app.dewey.ui.tools

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BrandingWatermark
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.FormatListNumbered
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.MergeType
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.RotateRight
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import app.dewey.ui.theme.Dewey
import app.dewey.ui.theme.Hue

/** How the tools are grouped, in display order. Each group owns one hue — see [hue]. */
enum class ToolGroup(val label: String) {
    CAPTURE("Capture"),
    PAGES("Pages"),
    CONVERT("Convert"),
    PROTECT("Protect"),
    MARK("Mark"),
}

/** The colour family a group's tiles, icons and screens are drawn in. */
val ToolGroup.hue: Hue
    @Composable get() = when (this) {
        ToolGroup.CAPTURE -> Dewey.colors.hues.scan
        ToolGroup.PAGES -> Dewey.colors.hues.pages
        ToolGroup.CONVERT -> Dewey.colors.hues.convert
        ToolGroup.PROTECT -> Dewey.colors.hues.protect
        ToolGroup.MARK -> Dewey.colors.hues.mark
    }

/**
 * Every tool, in the order it is listed.
 *
 * The route lives here, beside the words and the icon that describe it, so
 * adding a tool is one entry rather than a string repeated in a grid and again
 * in the navigation graph where the two can disagree.
 *
 * @param shortTitle what fits under an icon on the Home grid; [title] is the
 *   tool screen's own heading.
 */
enum class ToolDestination(
    val route: String,
    val title: String,
    val shortTitle: String,
    val blurb: String,
    val group: ToolGroup,
    val icon: ImageVector,
) {
    SCAN("tools/scan", "Scan a document", "Scan", "Photograph pages into a clean PDF.", ToolGroup.CAPTURE, Icons.Rounded.DocumentScanner),

    MERGE("tools/merge", "Merge", "Merge", "Join several PDFs into one.", ToolGroup.PAGES, Icons.Rounded.MergeType),
    EXTRACT("tools/extract", "Extract pages", "Extract", "Save chosen pages as a new PDF.", ToolGroup.PAGES, Icons.Rounded.ContentCut),
    ROTATE("tools/rotate", "Rotate", "Rotate", "Turn pages a quarter at a time.", ToolGroup.PAGES, Icons.Rounded.RotateRight),
    REORDER("tools/reorder", "Reorder", "Reorder", "Move a page to where it belongs.", ToolGroup.PAGES, Icons.Rounded.SwapVert),
    DELETE_PAGES("tools/delete-pages", "Delete pages", "Delete", "Remove pages you don't need.", ToolGroup.PAGES, Icons.Rounded.DeleteSweep),

    PDF_TO_IMAGES("tools/pdf-to-images", "PDF to images", "To images", "Save each page as a picture.", ToolGroup.CONVERT, Icons.Rounded.Image),
    IMAGES_TO_PDF("tools/images-to-pdf", "Images to PDF", "To PDF", "Turn photos into a document.", ToolGroup.CONVERT, Icons.Rounded.PictureAsPdf),
    COMPRESS("tools/compress", "Compress", "Compress", "Make a PDF smaller to send.", ToolGroup.CONVERT, Icons.Rounded.Compress),

    PROTECT("tools/protect", "Add a password", "Lock", "Encrypt a PDF so it needs a password to open.", ToolGroup.PROTECT, Icons.Rounded.Lock),
    UNLOCK("tools/unlock", "Remove a password", "Unlock", "Save an unlocked copy of a PDF you can open.", ToolGroup.PROTECT, Icons.Rounded.LockOpen),

    WATERMARK("tools/watermark", "Watermark", "Watermark", "Stamp text across every page.", ToolGroup.MARK, Icons.Rounded.BrandingWatermark),
    PAGE_NUMBERS("tools/page-numbers", "Page numbers", "Numbers", "Number the pages.", ToolGroup.MARK, Icons.Rounded.FormatListNumbered),
}
