package app.dewey.pdf

import android.content.ContentResolver
import android.graphics.Bitmap
import android.net.Uri
import android.util.LruCache
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

/**
 * Renders page thumbnails for the page-tool grids (Reorder, Extract, Delete,
 * Rotate) on demand, off the main thread.
 *
 * Reuses [RasterPageSource]'s renderer-opening path — the same SAF fallback
 * [RasterTools] relies on — rather than duplicating it, but keeps the
 * renderer open across many small thumbnail requests instead of paying its
 * open cost (a file descriptor, sometimes a bounded copy into the cache) once
 * per page. Every request funnels through one coroutine that alone touches
 * the renderer: [android.graphics.pdf.PdfRenderer] allows only one open page
 * at a time and is not safe from two threads at once, so a grid rendering
 * eight visible cards concurrently still renders them one at a time here, not
 * eight renderers or eight threads racing one.
 *
 * One instance is meant to live for as long as one tool screen's chosen file
 * does — a page-tool ViewModel owns it and calls [close] from `onCleared()`.
 */
class PageThumbnailSource(
    resolver: ContentResolver,
    cacheDir: File,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    // The workspace's own limit, not the raster tools' lower one: a file the
    // page tools accept must also get its thumbnails, or a 50MB PDF would
    // load a page count and then show nothing but broken cards.
    maxSpoolBytes: Long = PdfWorkspace.MAX_BYTES,
    maxCacheBytes: Int = DEFAULT_CACHE_BYTES,
) {
    private val pageSource = RasterPageSource(resolver, cacheDir, maxSpoolBytes)
    private val scope = CoroutineScope(SupervisorJob() + io)
    private val sessionLock = Mutex()
    private val cache = ThumbnailCache(maxCacheBytes)
    private var session: Session? = null

    /** One queued or in-flight render. */
    private class Request(val pageIndex: Int, val longEdgePx: Int, val result: CompletableDeferred<Result<Bitmap>>)

    /** One open renderer, serving requests for [uri] until replaced or [close]d. */
    private class Session(val uri: Uri, val requests: Channel<Request>, val job: Job)

    /**
     * Renders (or returns from cache) page [pageIndex] of [uri], scaled so its
     * long edge lands near [longEdgePx].
     *
     * Cancelling the calling coroutine — a grid card scrolling out of view
     * before its render started — leaves this call's [CompletableDeferred] in
     * a cancelled state, which the render loop checks before spending work on
     * it. A card still on screen when its request finally runs gets rendered
     * normally; nothing here forcibly aborts a render already in progress,
     * since a single downsized page decode is short enough not to need to.
     */
    suspend fun render(uri: Uri, sizeBytes: Long, pageIndex: Int, longEdgePx: Int): Result<Bitmap> {
        val key = ThumbnailCache.Key(uri, pageIndex, longEdgePx)
        cache.get(key)?.let { return Result.success(it) }

        val opened = sessionFor(uri, sizeBytes)
            ?: return Result.failure(RasterToolException(RasterFailure.Unreadable(uri.lastPathSegment.orEmpty())))

        val deferred = CompletableDeferred<Result<Bitmap>>(parent = currentCoroutineContext()[Job])
        // The channel closes when its file is replaced or the renderer dies; a
        // request that never went in would otherwise be awaited forever.
        if (opened.requests.trySend(Request(pageIndex, longEdgePx, deferred)).isFailure) {
            deferred.cancel()
            return Result.failure(RasterToolException(RasterFailure.Unreadable(uri.lastPathSegment.orEmpty())))
        }
        val result = deferred.await()
        result.onSuccess { cache.put(key, it) }
        return result
    }

    /** Closes the current renderer, if any, and forgets the cache. Call when the screen goes away. */
    fun close() {
        session = null
        scope.cancel()
        cache.clear()
    }

    /** The open session for [uri], opening a new one if there isn't one, or the old one is for a different file. */
    private suspend fun sessionFor(uri: Uri, sizeBytes: Long): Session? = sessionLock.withLock {
        val current = session
        if (current != null && current.uri == uri && current.job.isActive) return current

        // A different file was open, or its session already finished (the
        // renderer failed to open last time) — either way it has nothing
        // left to serve.
        current?.requests?.close()

        val requests = Channel<Request>(Channel.UNLIMITED)
        val ready = CompletableDeferred<Boolean>()
        val job = scope.launch {
            pageSource.withRenderer(uri, sizeBytes) { renderer ->
                ready.complete(true)
                for (request in requests) {
                    if (!request.result.isActive) continue
                    val rendered = pageSource.renderPage(renderer, request.pageIndex, request.longEdgePx)
                    request.result.complete(
                        if (rendered != null) {
                            Result.success(rendered.bitmap)
                        } else {
                            Result.failure(RasterToolException(RasterFailure.Unreadable(uri.lastPathSegment.orEmpty())))
                        },
                    )
                }
            }.onFailure {
                // The renderer never opened (encrypted, unreadable, too
                // large). Nothing is queued yet — sessionFor doesn't hand out
                // the channel until ready resolves — so there is nothing to
                // fail, only this open attempt itself.
                requests.close()
                ready.complete(false)
            }
        }

        if (!ready.await()) return null
        Session(uri, requests, job).also { session = it }
    }

    private companion object {
        const val DEFAULT_CACHE_BYTES = 24 * 1024 * 1024
    }
}

/**
 * A small LRU keyed by document, page and render size, bounded by decoded
 * bitmap bytes rather than item count — a thumbnail rendered for a wider card
 * costs more bytes than one rendered for a narrower one, and a count-based
 * cache would let memory use swing with screen size for no reason.
 */
private class ThumbnailCache(maxBytes: Int) {
    data class Key(val uri: Uri, val pageIndex: Int, val longEdgePx: Int)

    private val cache = object : LruCache<Key, Bitmap>(maxBytes) {
        override fun sizeOf(key: Key, value: Bitmap): Int = value.byteCount
    }

    fun get(key: Key): Bitmap? = cache.get(key)
    fun put(key: Key, bitmap: Bitmap) { cache.put(key, bitmap) }
    fun clear() = cache.evictAll()
}
