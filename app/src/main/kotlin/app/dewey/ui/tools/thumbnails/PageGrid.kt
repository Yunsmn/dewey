package app.dewey.ui.tools.thumbnails

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.dewey.pdf.PageThumbnailSource
import app.dewey.ui.theme.Dewey
import app.dewey.ui.theme.DeweyTheme
import app.dewey.ui.theme.Hue
import app.dewey.ui.tools.PickedFile
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState

/** What [PageGrid] lets someone do to the pages it shows. */
sealed interface PageGridMode {
    /**
     * Drag to reorder. [order] is the whole page order so far — each element
     * the document's *original*, 0-based page index — so a page's thumbnail
     * follows it as it moves rather than swapping images between fixed
     * slots. [onMove] is called with positions within [order], not page
     * indices.
     */
    data class Reorder(val order: List<Int>, val onMove: (from: Int, to: Int) -> Unit) : PageGridMode

    /** Tap to toggle. [selected] is 0-based page indices. */
    data class Select(
        val pageCount: Int,
        val selected: Set<Int>,
        val onToggle: (Int) -> Unit,
        /** Applied to selected cards only — Rotate's live preview of the turn about to be saved. */
        val previewRotationDegrees: Int = 0,
    ) : PageGridMode
}

/**
 * The visual page picker behind Reorder, Extract, Delete and Rotate: a grid
 * of page thumbnails instead of a typed range, because dragging a page where
 * it belongs — or tapping the ones you want — is the thing a person is
 * actually trying to do. See [PageThumbnailSource] for how the thumbnails
 * themselves are rendered, and [PageViewer] for the full-screen look a
 * thumbnail is too small to read a page from.
 *
 * Two columns wide on a phone, not four: a page has to be big enough on
 * screen to recognise at a glance, which is the same reason a thumbnail too
 * small to read has its own full-screen escape hatch in the first place.
 *
 * A fixed [height] rather than [Modifier.fillMaxSize] on purpose: every tool
 * screen scrolls its whole body in one `verticalScroll` column (see
 * ToolScaffold), and a lazy grid measured with no height bound inside a
 * scrolling column throws — the same crash HomeToolsGrid's doc comment
 * already warns about for a LazyVerticalGrid inside a LazyColumn. Bounding
 * this grid's height makes it scroll internally instead, which is the fix in
 * both places.
 */
@Composable
fun PageGrid(
    mode: PageGridMode,
    thumbnailSource: PageThumbnailSource,
    file: PickedFile,
    hue: Hue,
    modifier: Modifier = Modifier,
    height: Dp = 420.dp,
) {
    val haptics = LocalHapticFeedback.current
    // Which position (within the grid's current display order) the
    // full-screen viewer is open on, if any. Transient presentation state,
    // not part of a tool's saved-able form — unlike selection or page order,
    // nothing is lost by forgetting it, so it lives here rather than in a
    // ViewModel. Keyed on the file so switching documents can't leave a
    // viewer open on a page from the one before it.
    var viewerPosition by remember(file.uri) { mutableStateOf<Int?>(null) }

    when (mode) {
        is PageGridMode.Reorder -> {
            val gridState = rememberLazyGridState()
            val reorderState = rememberReorderableLazyGridState(gridState) { from, to ->
                mode.onMove(from.index, to.index)
            }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = CARD_MIN_WIDTH),
                state = gridState,
                modifier = modifier.fillMaxWidth().height(height),
                contentPadding = PaddingValues(Dewey.spacing.tight),
                horizontalArrangement = Arrangement.spacedBy(Dewey.spacing.row),
                verticalArrangement = Arrangement.spacedBy(Dewey.spacing.row),
            ) {
                itemsIndexed(mode.order, key = { _, pageIndex -> pageIndex }) { position, pageIndex ->
                    ReorderableItem(reorderState, key = pageIndex) { isDragging ->
                        PageCard(
                            pageNumber = pageIndex + 1,
                            hue = hue,
                            isDragging = isDragging,
                            onExpand = { viewerPosition = position },
                            modifier = Modifier
                                .longPressDraggableHandle(
                                    onDragStarted = { haptics.performHapticFeedback(HapticFeedbackType.LongPress) },
                                )
                                .semantics {
                                    customActions = listOfNotNull(
                                        if (position > 0) {
                                            CustomAccessibilityAction("Move earlier") { mode.onMove(position, position - 1); true }
                                        } else {
                                            null
                                        },
                                        if (position < mode.order.lastIndex) {
                                            CustomAccessibilityAction("Move later") { mode.onMove(position, position + 1); true }
                                        } else {
                                            null
                                        },
                                    )
                                },
                            thumbnail = { PageThumbnailImage(thumbnailSource, file, pageIndex) },
                        )
                    }
                }
            }

            viewerPosition?.let { position ->
                PageViewer(
                    order = mode.order,
                    startPosition = position,
                    file = file,
                    source = thumbnailSource,
                    hue = hue,
                    onClose = { viewerPosition = null },
                )
            }
        }

        is PageGridMode.Select -> {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = CARD_MIN_WIDTH),
                modifier = modifier.fillMaxWidth().height(height),
                contentPadding = PaddingValues(Dewey.spacing.tight),
                horizontalArrangement = Arrangement.spacedBy(Dewey.spacing.row),
                verticalArrangement = Arrangement.spacedBy(Dewey.spacing.row),
            ) {
                items(mode.pageCount, key = { it }) { pageIndex ->
                    val isSelected = pageIndex in mode.selected
                    PageCard(
                        pageNumber = pageIndex + 1,
                        hue = hue,
                        checked = isSelected,
                        dimmed = !isSelected,
                        rotationDegrees = if (isSelected) mode.previewRotationDegrees else 0,
                        onExpand = { viewerPosition = pageIndex },
                        modifier = Modifier
                            .clickable(role = Role.Checkbox, onClick = { mode.onToggle(pageIndex) })
                            .semantics { selected = isSelected },
                        thumbnail = { PageThumbnailImage(thumbnailSource, file, pageIndex) },
                    )
                }
            }

            viewerPosition?.let { position ->
                PageViewer(
                    order = remember(mode.pageCount) { (0 until mode.pageCount).toList() },
                    startPosition = position,
                    file = file,
                    source = thumbnailSource,
                    hue = hue,
                    onClose = { viewerPosition = null },
                    selection = PageViewerSelection(mode.selected, mode.onToggle),
                )
            }
        }
    }
}

@Composable
private fun PageCard(
    pageNumber: Int,
    hue: Hue,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
    checked: Boolean = false,
    dimmed: Boolean = false,
    isDragging: Boolean = false,
    rotationDegrees: Int = 0,
    thumbnail: @Composable () -> Unit,
) {
    val scale by animateFloatAsState(if (isDragging) 1.05f else 1f, label = "pageCardScale")
    val shape = RoundedCornerShape(Dewey.radii.small)

    Box(
        modifier = modifier
            .aspectRatio(PAGE_ASPECT_RATIO)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .then(if (isDragging) Modifier.shadow(elevation = 10.dp, shape = shape) else Modifier)
            .clip(shape)
            .background(Dewey.colors.paperSunken)
            .then(if (checked) Modifier.border(2.dp, hue.strong, shape) else Modifier),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { rotationZ = rotationDegrees.toFloat() }
                .alpha(if (dimmed) DIMMED_ALPHA else 1f),
        ) {
            thumbnail()
        }

        Icon(
            imageVector = Icons.Rounded.OpenInFull,
            contentDescription = "View page $pageNumber full-screen",
            tint = Dewey.colors.ink,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(Dewey.spacing.tight)
                .size(BADGE_SIZE)
                .clip(CircleShape)
                .background(Dewey.colors.paper.copy(alpha = 0.72f))
                .clickable(role = Role.Button, onClick = onExpand)
                .padding(5.dp),
        )

        if (checked) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(Dewey.spacing.tight)
                    .size(BADGE_SIZE)
                    .clip(CircleShape)
                    .background(hue.strong),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.TaskAlt,
                    contentDescription = null,
                    tint = Dewey.colors.onAccent,
                    modifier = Modifier.size(16.dp),
                )
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(Dewey.spacing.tight)
                .clip(RoundedCornerShape(50))
                .background(Dewey.colors.paper.copy(alpha = 0.72f))
                .padding(horizontal = Dewey.spacing.tight, vertical = 2.dp),
        ) {
            Text("$pageNumber", style = Dewey.type.Micro, color = Dewey.colors.ink, fontWeight = FontWeight.SemiBold)
        }
    }
}

private val CARD_MIN_WIDTH = 150.dp
private val BADGE_SIZE = 26.dp
private const val PAGE_ASPECT_RATIO = 0.75f
private const val DIMMED_ALPHA = 0.35f

@Preview(name = "Page cards — light", showBackground = true, backgroundColor = 0xFFF5F6FA)
@Composable
private fun PageCardsLightPreview() {
    DeweyTheme(dark = false) {
        PageCardPreviewRow()
    }
}

@Preview(name = "Page cards — dark", showBackground = true, backgroundColor = 0xFF0F1320)
@Composable
private fun PageCardsDarkPreview() {
    DeweyTheme(dark = true) {
        PageCardPreviewRow()
    }
}

@Composable
private fun PageCardPreviewRow() {
    val hue = Dewey.colors.hues.pages
    Row(
        horizontalArrangement = Arrangement.spacedBy(Dewey.spacing.row),
        modifier = Modifier.padding(Dewey.spacing.gutter),
    ) {
        PageCard(
            pageNumber = 1,
            hue = hue,
            onExpand = {},
            modifier = Modifier.width(110.dp),
            thumbnail = { FakeThumbnail(hue) },
        )
        PageCard(
            pageNumber = 2,
            hue = hue,
            onExpand = {},
            checked = true,
            modifier = Modifier.width(110.dp),
            thumbnail = { FakeThumbnail(hue) },
        )
        PageCard(
            pageNumber = 3,
            hue = hue,
            onExpand = {},
            dimmed = true,
            modifier = Modifier.width(110.dp),
            thumbnail = { FakeThumbnail(hue) },
        )
        PageCard(
            pageNumber = 4,
            hue = hue,
            onExpand = {},
            isDragging = true,
            modifier = Modifier.width(110.dp),
            thumbnail = { FakeThumbnail(hue) },
        )
    }
}

/** A stand-in for a rendered page — no [PageThumbnailSource] in a preview, only fake content. */
@Composable
private fun FakeThumbnail(hue: Hue) {
    Box(modifier = Modifier.fillMaxSize().background(hue.soft))
}
