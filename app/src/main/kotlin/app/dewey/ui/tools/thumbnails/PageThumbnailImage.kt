package app.dewey.ui.tools.thumbnails

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BrokenImage
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import app.dewey.pdf.PageThumbnailSource
import app.dewey.ui.theme.Dewey
import app.dewey.ui.tools.PickedFile

/**
 * One page's thumbnail, fetched from [source] off the main thread and shown
 * once ready.
 *
 * A soft sunken placeholder fills the space while it renders and if it fails
 * — the tool screen itself already turns an encrypted or unreadable *file*
 * into [app.dewey.ui.tools.toolFailureMessage] before a grid ever appears (see
 * each page-tool ViewModel's `loadPageCount`), so a failure reaching down
 * here is one page's own render going wrong, shown as a broken-image glyph
 * rather than blocking the rest of the grid.
 *
 * @param longEdgePx defaults to card-sized; [PageViewer] passes a much larger
 *   value for its full-screen render, through the same renderer path.
 */
@Composable
fun PageThumbnailImage(
    source: PageThumbnailSource,
    file: PickedFile,
    pageIndex: Int,
    modifier: Modifier = Modifier,
    longEdgePx: Int = THUMBNAIL_LONG_EDGE_PX,
) {
    var bitmap by remember(file.uri, pageIndex, longEdgePx) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(file.uri, pageIndex, longEdgePx) { mutableStateOf(false) }

    LaunchedEffect(file.uri, pageIndex, longEdgePx) {
        source.render(file.uri, file.sizeBytes, pageIndex, longEdgePx).fold(
            onSuccess = { bitmap = it },
            onFailure = { failed = true },
        )
    }

    val current = bitmap
    when {
        current != null -> Image(
            bitmap = current.asImageBitmap(),
            contentDescription = null,
            modifier = modifier.fillMaxSize(),
        )

        failed -> Box(
            modifier = modifier.fillMaxSize().background(Dewey.colors.paperSunken),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.BrokenImage, contentDescription = null, tint = Dewey.colors.inkFaint)
        }

        else -> Box(modifier = modifier.fillMaxSize().background(Dewey.colors.paperSunken))
    }
}

/** Enough detail for a card a few centimetres wide, without a full-page render's memory cost. */
private const val THUMBNAIL_LONG_EDGE_PX = 320
