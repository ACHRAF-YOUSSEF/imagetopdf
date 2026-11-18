package tech.youssefachraf.image_to_pdf.testutil

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.core.content.FileProvider
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadow.api.Shadow
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.OutputStream

@Implements(PdfDocument::class)
class ShadowPdfDocument {
    private val pageInfos = mutableListOf<PdfDocument.PageInfo>()
    private var closed = false
    private var activePage: PdfDocument.Page? = null

    @Implementation
    protected fun startPage(pageInfo: PdfDocument.PageInfo): PdfDocument.Page {
        check(!closed) { "document is closed!" }
        check(activePage == null) { "previous page not finished" }
        val page = Shadow.newInstanceOf(PdfDocument.Page::class.java)
        val shadowPage = Shadow.extract<ShadowPdfDocumentPage>(page)
        shadowPage.configure(pageInfo)
        activePage = page
        return page
    }

    @Implementation
    protected fun finishPage(page: PdfDocument.Page) {
        check(!closed) { "document is closed!" }
        pageInfos.add(page.info)
        activePage = null
    }

    @Implementation
    protected fun getPages(): List<PdfDocument.PageInfo> {
        return pageInfos.toList()
    }

    @Implementation
    protected fun writeTo(out: OutputStream) {
        check(!closed) { "document is closed!" }
        val dims = pageInfos.joinToString(",") { "${it.pageWidth}x${it.pageHeight}" }
        val payload = "%PDF-1.4\n%ROBO_PAGES:${pageInfos.size}|$dims\n%%EOF\n"
        out.write(payload.toByteArray(Charsets.UTF_8))
    }

    @Implementation
    protected fun close() {
        closed = true
    }

    @Implementation
    protected fun finalize() {
        // No native document handle in Robolectric shadow.
    }
}

@Implements(PdfDocument.Page::class)
class ShadowPdfDocumentPage {
    private var pageInfo: PdfDocument.PageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
    private var backingBitmap: Bitmap? = null
    private var backingCanvas: Canvas? = null

    fun configure(info: PdfDocument.PageInfo) {
        pageInfo = info
        val bmp = Bitmap.createBitmap(
            info.pageWidth.coerceAtLeast(1),
            info.pageHeight.coerceAtLeast(1),
            Bitmap.Config.ARGB_8888
        )
        backingBitmap = bmp
        backingCanvas = Canvas(bmp)
    }

    @Implementation
    protected fun getCanvas(): Canvas {
        return backingCanvas ?: Canvas()
    }

    @Implementation
    protected fun getInfo(): PdfDocument.PageInfo {
        return pageInfo
    }
}

@Implements(PdfRenderer::class)
class ShadowPdfRenderer {
    private var pageDims: List<Pair<Int, Int>> = emptyList()
    private var closed = false
    private var activePageOpen = false

    @Implementation
    protected fun __constructor__(input: ParcelFileDescriptor) {
        val bytes = FileInputStream(input.fileDescriptor).readBytes()
        val text = bytes.toString(Charsets.UTF_8)
        if (!text.startsWith("%PDF-")) {
            throw IOException("Not a valid PDF file")
        }
        val marker = "%ROBO_PAGES:"
        val idx = text.indexOf(marker)
        pageDims = if (idx >= 0) {
            val line = text.substring(idx + marker.length).lineSequence().firstOrNull().orEmpty()
            val parts = line.split('|', limit = 2)
            val count = parts[0].toIntOrNull()?.coerceAtLeast(1) ?: 1
            val dims = parts.getOrNull(1).orEmpty().split(',')
                .mapNotNull { token ->
                    val wh = token.split('x')
                    val w = wh.getOrNull(0)?.toIntOrNull()
                    val h = wh.getOrNull(1)?.toIntOrNull()
                    if (w != null && h != null) w to h else null
                }
            if (dims.size == count) dims else List(count) { 595 to 842 }
        } else {
            val pageMatches = Regex("/Type\\s*/Page\\b").findAll(text).count().coerceAtLeast(1)
            List(pageMatches) { 595 to 842 }
        }
    }

    @Implementation
    protected fun getPageCount(): Int = pageDims.size

    @Implementation
    protected fun openPage(index: Int): PdfRenderer.Page {
        check(!closed) { "renderer closed" }
        check(!activePageOpen) { "only one page can be open at a time" }
        require(index in pageDims.indices) { "Invalid page index: $index" }
        activePageOpen = true
        val page = Shadow.newInstanceOf(PdfRenderer.Page::class.java)
        val shadow = Shadow.extract<ShadowPdfRendererPage>(page)
        val (w, h) = pageDims[index]
        shadow.configure(index, w, h) { activePageOpen = false }
        return page
    }

    @Implementation
    protected fun close() {
        closed = true
    }

    @Implementation
    protected fun finalize() {
        // No native renderer handle in Robolectric shadow.
    }
}

@Implements(PdfRenderer.Page::class)
class ShadowPdfRendererPage {
    private var pageIndex: Int = 0
    private var width: Int = 595
    private var height: Int = 842
    private var onClose: (() -> Unit)? = null

    fun configure(index: Int, width: Int, height: Int, onClose: () -> Unit) {
        this.pageIndex = index
        this.width = width
        this.height = height
        this.onClose = onClose
    }

    @Implementation
    protected fun getIndex(): Int = pageIndex

    @Implementation
    protected fun getWidth(): Int = width

    @Implementation
    protected fun getHeight(): Int = height

    @Implementation
    protected fun render(
        destination: Bitmap,
        destClip: Rect?,
        transform: Matrix?,
        renderMode: Int
    ) {
        val canvas = Canvas(destination)
        val paint = Paint().apply { color = Color.rgb(30, 64, 175) }
        canvas.drawRect(8f, 8f, (destination.width - 8).toFloat(), (destination.height - 8).toFloat(), paint)
    }

    @Implementation
    protected fun close() {
        onClose?.invoke()
        onClose = null
    }

    @Implementation
    protected fun finalize() {
        // No native page handle in Robolectric shadow.
    }
}

@Implements(FileProvider::class)
class ShadowFileProvider {
    companion object {
        @JvmStatic
        @Implementation
        fun getUriForFile(context: Context, authority: String, file: File): Uri {
            return Uri.fromFile(file)
        }
    }
}
