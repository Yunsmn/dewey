package app.dewey.ui.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import app.dewey.ui.components.GlassCard
import app.dewey.ui.components.NavBarClearance
import app.dewey.ui.components.SectionHeading
import app.dewey.ui.theme.Dewey
import app.dewey.ui.theme.DeweyTheme

/** How the tools are grouped on the hub, in display order. */
enum class ToolGroup(val label: String) {
    CAPTURE("Capture"),
    PAGES("Pages"),
    CONVERT("Convert"),
    PROTECT("Protect and mark"),
}

/**
 * Every tool the hub can open, in the order it lists them.
 *
 * The route lives here, beside the words that describe it, so adding a tool
 * is one entry rather than a string repeated in the hub and again in the
 * navigation graph where the two can disagree.
 */
enum class ToolDestination(
    val route: String,
    val title: String,
    val blurb: String,
    val group: ToolGroup,
) {
    SCAN("tools/scan", "Scan a document", "Photograph pages into a clean PDF.", ToolGroup.CAPTURE),

    MERGE("tools/merge", "Merge", "Join several PDFs into one.", ToolGroup.PAGES),
    EXTRACT("tools/extract", "Extract pages", "Save chosen pages as a new PDF.", ToolGroup.PAGES),
    ROTATE("tools/rotate", "Rotate", "Turn pages a quarter at a time.", ToolGroup.PAGES),
    REORDER("tools/reorder", "Reorder", "Move a page to where it belongs.", ToolGroup.PAGES),
    DELETE_PAGES("tools/delete-pages", "Delete pages", "Remove pages you don't need.", ToolGroup.PAGES),

    PDF_TO_IMAGES("tools/pdf-to-images", "PDF to images", "Save each page as a picture.", ToolGroup.CONVERT),
    IMAGES_TO_PDF("tools/images-to-pdf", "Images to PDF", "Turn photos into a document.", ToolGroup.CONVERT),
    COMPRESS("tools/compress", "Compress", "Make a PDF smaller to send.", ToolGroup.CONVERT),

    PROTECT("tools/protect", "Add a password", "Encrypt a PDF so it needs a password to open.", ToolGroup.PROTECT),
    UNLOCK("tools/unlock", "Remove a password", "Save an unlocked copy of a PDF you can open.", ToolGroup.PROTECT),
    WATERMARK("tools/watermark", "Watermark", "Stamp text across every page.", ToolGroup.PROTECT),
    PAGE_NUMBERS("tools/page-numbers", "Page numbers", "Number the pages.", ToolGroup.PROTECT),
}

/**
 * The list of tools. Grouped rather than a grid of icons: thirteen tiles with
 * emoji make a person read every label to find one, while four short labelled
 * groups let them skip straight to the kind of thing they want to do.
 */
@Composable
fun ToolsHubScreen(onOpen: (ToolDestination) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            // Below the status bar: the app draws edge to edge, and without
            // this the title sits under the clock. Before the scroll, so the
            // content scrolls beneath the inset rather than into it.
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(start = Dewey.spacing.gutter, end = Dewey.spacing.gutter, top = Dewey.spacing.block, bottom = NavBarClearance),
    ) {
        Text("Tools", style = Dewey.type.Display, color = Dewey.colors.ink)
        Spacer(Modifier.height(Dewey.spacing.tight))
        Text(
            "Everything here works on the phone. Nothing is uploaded, and your original file is never changed.",
            style = Dewey.type.Body,
            color = Dewey.colors.inkMuted,
        )

        for (group in ToolGroup.entries) {
            val tools = ToolDestination.entries.filter { it.group == group }
            if (tools.isEmpty()) continue

            Spacer(Modifier.height(Dewey.spacing.block))
            SectionHeading(label = group.label, count = tools.size)
            Spacer(Modifier.height(Dewey.spacing.tight))

            Column(verticalArrangement = Arrangement.spacedBy(Dewey.spacing.tight)) {
                for (tool in tools) {
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        accent = if (group == ToolGroup.CAPTURE) Dewey.colors.accent else null,
                        onClick = { onOpen(tool) },
                    ) {
                        Text(tool.title, style = Dewey.type.Title, color = Dewey.colors.ink)
                        Spacer(Modifier.height(Dewey.spacing.hairline))
                        Text(tool.blurb, style = Dewey.type.Meta, color = Dewey.colors.inkMuted)
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B0F17, heightDp = 900, widthDp = 390)
@Composable
private fun ToolsHubPreview() {
    DeweyTheme { ToolsHubScreen(onOpen = {}) }
}
