package app.dewey.ui.tools.thumbnails

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.dewey.pdf.PageThumbnailSource
import app.dewey.ui.theme.Dewey
import app.dewey.ui.theme.Hue
import app.dewey.ui.tools.PickedFile

/**
 * [PageGrid]'s select-mode toggle, carried into the full-screen viewer so a
 * page can be picked while actually reading it rather than only from its
 * thumbnail. Null in reorder mode, where there is nothing to select.
 */
class PageViewerSelection(val selected: Set<Int>, val onToggle: (Int) -> Unit)

/**
 * A full-screen look at one page, rendered through the same
 * [PageThumbnailSource] path as its grid card but at up to screen width — a
 * thumbnail proves a page is in the right place, but is too small to actually
 * read.
 *
 * Opened only from [PageGrid]'s own expand button, never from a tap or
 * long-press on the card: those already mean select and drag, and this stays
 * out of their way rather than overloading either.
 *
 * @param order the pages to browse, in the order [PageGrid] is currently
 *   showing them — [PageGridMode.Reorder]'s in-progress order, or every page
 *   0 until pageCount for [PageGridMode.Select] — so Previous/Next walk the
 *   same sequence the grid draws, not the document's original order.
 * @param startPosition a position within [order], not a page index.
 */
@Composable
fun PageViewer(
    order: List<Int>,
    startPosition: Int,
    file: PickedFile,
    source: PageThumbnailSource,
    hue: Hue,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    selection: PageViewerSelection? = null,
) {
    if (order.isEmpty()) return

    var position by remember(order) { mutableStateOf(startPosition.coerceIn(0, order.lastIndex)) }
    val pageIndex = order[position]

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = modifier.fillMaxSize().background(Dewey.colors.paper)) {
            ZoomablePage(source = source, file = file, pageIndex = pageIndex, modifier = Modifier.fillMaxSize())

            ViewerChrome(
                pageNumber = pageIndex + 1,
                position = position,
                lastPosition = order.lastIndex,
                hue = hue,
                onClose = onClose,
                onPrevious = { position = (position - 1).coerceAtLeast(0) },
                onNext = { position = (position + 1).coerceAtMost(order.lastIndex) },
                selected = selection?.let { pageIndex in it.selected },
                onToggleSelected = selection?.let { sel -> { sel.onToggle(pageIndex) } },
            )
        }
    }
}

/**
 * The page image itself, pinch-zoomable within [MIN_ZOOM]..[MAX_ZOOM].
 * Rendered at up to [FULL_VIEW_MAX_LONG_EDGE_PX] — the screen's own width in
 * pixels, capped so a tablet does not ask for a render larger than the point
 * of a "full screen" view actually needs.
 */
@Composable
private fun ZoomablePage(source: PageThumbnailSource, file: PickedFile, pageIndex: Int, modifier: Modifier = Modifier) {
    var scale by remember(pageIndex) { mutableStateOf(1f) }
    var offset by remember(pageIndex) { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val longEdgePx = remember(configuration.screenWidthDp, density) {
        with(density) { configuration.screenWidthDp.dp.roundToPx() }.coerceAtMost(FULL_VIEW_MAX_LONG_EDGE_PX)
    }

    Box(
        modifier = modifier.pointerInput(pageIndex) {
            detectTransformGestures { _, pan, zoom, _ ->
                val newScale = (scale * zoom).coerceIn(MIN_ZOOM, MAX_ZOOM)
                scale = newScale
                offset = if (newScale <= MIN_ZOOM) Offset.Zero else offset + pan
            }
        },
        contentAlignment = Alignment.Center,
    ) {
        PageThumbnailImage(
            source = source,
            file = file,
            pageIndex = pageIndex,
            longEdgePx = longEdgePx,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
        )
    }
}

@Composable
private fun ViewerChrome(
    pageNumber: Int,
    position: Int,
    lastPosition: Int,
    hue: Hue,
    onClose: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    selected: Boolean?,
    onToggleSelected: (() -> Unit)?,
) {
    Row(
        modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(Dewey.spacing.gutter),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChromeButton(icon = Icons.Rounded.Close, contentDescription = "Close", onClick = onClose)
        Text("Page $pageNumber", style = Dewey.type.Label, color = Dewey.colors.ink)
        if (selected != null && onToggleSelected != null) {
            ChromeButton(
                icon = if (selected) Icons.Rounded.TaskAlt else Icons.Rounded.RadioButtonUnchecked,
                contentDescription = if (selected) "Selected — tap to deselect this page" else "Select this page",
                tint = if (selected) hue.strong else Dewey.colors.inkMuted,
                onClick = onToggleSelected,
            )
        } else {
            Box(modifier = Modifier.size(CHROME_BUTTON_SIZE))
        }
    }

    Row(
        modifier = Modifier.fillMaxSize().navigationBarsPadding().padding(Dewey.spacing.gutter),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        ChromeButton(
            icon = Icons.Rounded.ChevronLeft,
            contentDescription = "Previous page",
            enabled = position > 0,
            onClick = onPrevious,
        )
        ChromeButton(
            icon = Icons.Rounded.ChevronRight,
            contentDescription = "Next page",
            enabled = position < lastPosition,
            onClick = onNext,
        )
    }
}

@Composable
private fun ChromeButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = Dewey.colors.ink,
) {
    Box(
        modifier = modifier
            .size(CHROME_BUTTON_SIZE)
            .clip(CircleShape)
            .background(Dewey.colors.glass.copy(alpha = 0.85f))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = if (enabled) tint else Dewey.colors.inkFaint)
    }
}

private val CHROME_BUTTON_SIZE = 40.dp
private const val MIN_ZOOM = 1f
private const val MAX_ZOOM = 4f
private const val FULL_VIEW_MAX_LONG_EDGE_PX = 1600
