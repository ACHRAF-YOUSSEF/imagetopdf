package tech.youssefachraf.image_to_pdf.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.util.LruCache
import androidx.core.graphics.createBitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * In-memory LRU cache and off-main-thread renderer for PDF page thumbnails.
 */
object PdfThumbnailCache {
    private const val MAX_CACHE_ENTRIES = 32
    private val cache = object : LruCache<String, Bitmap>(MAX_CACHE_ENTRIES) {}

    fun cacheKey(uri: Uri, pageIndex: Int, maxDimensionPx: Int): String {
        return "$uri#$pageIndex@$maxDimensionPx"
    }

    fun getCached(key: String): Bitmap? {
        return synchronized(cache) {
            cache.get(key)?.takeUnless { it.isRecycled }
        }
    }

    fun invalidateUri(uri: Uri) {
        val prefix = "$uri#"
        synchronized(cache) {
            val snapshotKeys = cache.snapshot().keys.toList()
            snapshotKeys.forEach { key ->
                if (key.startsWith(prefix)) {
                    cache.remove(key)
                }
            }
        }
    }

    fun clear() {
        synchronized(cache) {
            cache.evictAll()
        }
    }

    suspend fun loadPageThumbnail(
        context: Context,
        uri: Uri,
        pageIndex: Int = 0,
        maxDimensionPx: Int = 360,
    ): Bitmap? {
        val key = cacheKey(uri, pageIndex, maxDimensionPx)
        getCached(key)?.let { return it }

        return withContext(Dispatchers.IO) {
            try {
                val rendered = renderPageThumbnail(context, uri, pageIndex, maxDimensionPx)
                if (rendered != null) {
                    synchronized(cache) {
                        cache.put(key, rendered)
                    }
                }
                rendered
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun renderPageThumbnail(
        context: Context,
        uri: Uri,
        pageIndex: Int,
        maxDimensionPx: Int,
    ): Bitmap? {
        return context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                if (pageIndex !in 0 until renderer.pageCount) {
                    return@use null
                }
                renderer.openPage(pageIndex).use { page ->
                    val maxSide = max(page.width, page.height).coerceAtLeast(1)
                    val scale = maxDimensionPx.toFloat() / maxSide.toFloat()
                    val targetW = (page.width * scale).roundToInt().coerceAtLeast(1)
                    val targetH = (page.height * scale).roundToInt().coerceAtLeast(1)
                    val bitmap = createBitmap(targetW, targetH)
                    Canvas(bitmap).drawColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap
                }
            }
        }
    }
}
