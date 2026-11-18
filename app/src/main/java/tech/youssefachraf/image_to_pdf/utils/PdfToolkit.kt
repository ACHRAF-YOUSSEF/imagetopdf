package tech.youssefachraf.image_to_pdf.utils

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.text.TextPaint
import androidx.core.content.FileProvider
import androidx.core.graphics.createBitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import kotlin.math.max
import kotlin.math.roundToInt

enum class CompressionLevel(
    val label: String,
    val description: String,
    val dpi: Int,
    val jpegQuality: Int
) {
    LOW("High Quality", "150 DPI · Crisp text & graphics", 150, 80),
    BALANCED("Balanced", "120 DPI · Recommended everyday size", 120, 62),
    EXTREME("Max Savings", "90 DPI · Smallest file size", 90, 42)
}

enum class JpgQuality(
    val label: String,
    val description: String,
    val dpi: Int,
    val jpegQuality: Int
) {
    STANDARD("Standard · 150 DPI", "Fast sharing & smaller files", 150, 85),
    HD("HD · 216 DPI", "Sharp print-ready pages", 216, 92)
}

data class CompressionResult(
    val pdfInfo: PdfFileInfo,
    val originalSizeBytes: Long,
    val compressedSizeBytes: Long
) {
    val savedPercent: Int
        get() = if (originalSizeBytes <= 0L) {
            0
        } else {
            (((originalSizeBytes - compressedSizeBytes).toDouble() / originalSizeBytes.toDouble()) * 100.0)
                .roundToInt()
                .coerceIn(0, 99)
        }
}

/**
 * Offline PDF manipulation toolkit for importing, compressing, merging, splitting, and exporting PDFs.
 */
object PdfToolkit {
    private const val PDF_POINT_DPI = 72f
    private const val MAX_COMPRESS_DIMENSION = 2200
    private const val MAX_MERGE_SPLIT_DIMENSION = 2480
    private const val MAX_JPG_DIMENSION = 3000
    private const val PICTURES_SUBDIR = "ImageToPdf"
    private const val JPEG_MIME_TYPE = "image/jpeg"

    suspend fun importPdfs(context: Context, sourceUris: List<Uri>): List<PdfFileInfo> =
        withContext(Dispatchers.IO) {
            require(sourceUris.isNotEmpty()) { "Select at least one PDF file to import." }
            val imported = mutableListOf<PdfFileInfo>()
            sourceUris.forEachIndexed { index, sourceUri ->
                currentCoroutineContext().ensureActive()
                val rawName = resolveDisplayName(context, sourceUri, "imported_${index + 1}.pdf")
                val pageCount = readPageCount(context, sourceUri)
                if (pageCount <= 0) {
                    throw IllegalArgumentException("\"$rawName\" is not a valid or readable PDF.")
                }
                val baseName = PdfConverter.sanitizeBaseName(rawName, "imported_${index + 1}")
                val (_, resolvedBase) = PdfConverter.resolveFileName(context, baseName)
                val targetName = PdfConverter.ensurePdfExtension(resolvedBase)

                val savedUri = context.contentResolver.openInputStream(sourceUri)?.use { stream ->
                    PdfConverter.writePdfStreamToDownloads(context, stream, targetName)
                } ?: throw IOException("Could not read \"$rawName\".")

                imported.add(
                    PdfConverter.querySinglePdfInfo(
                        context = context,
                        uri = savedUri,
                        fallbackName = targetName,
                        fallbackPageCount = pageCount
                    )
                )
            }
            imported
        }

    suspend fun compressPdf(
        context: Context,
        sourceUri: Uri,
        level: CompressionLevel,
        outputBaseName: String,
        overwrite: Boolean = false
    ): CompressionResult = withContext(Dispatchers.IO) {
        val originalSize = measureUriSizeBytes(context, sourceUri)
        val scale = level.dpi.toFloat() / PDF_POINT_DPI
        val targetName = PdfConverter.ensurePdfExtension(outputBaseName)

        var renderedPages = 0
        val pdfDocument = PdfDocument()
        try {
            withPdfRenderer(context, sourceUri) { renderer ->
                val count = renderer.pageCount
                require(count > 0) { "Selected PDF has no pages." }
                renderedPages = count

                for (index in 0 until count) {
                    currentCoroutineContext().ensureActive()
                    appendCompressedPage(renderer, index, pdfDocument, scale, level.jpegQuality)
                }
            }
            val savedUri = PdfConverter.savePdf(
                context = context,
                pdfDocument = pdfDocument,
                fileName = targetName,
                overwriteExisting = overwrite
            )
            val pdfInfo = PdfConverter.querySinglePdfInfo(
                context = context,
                uri = savedUri,
                fallbackName = targetName,
                fallbackPageCount = renderedPages
            )
            val finalSize = pdfInfo.sizeBytes.takeIf { it > 0L }
                ?: measureUriSizeBytes(context, savedUri)
            CompressionResult(
                pdfInfo = pdfInfo.copy(sizeBytes = finalSize),
                originalSizeBytes = originalSize,
                compressedSizeBytes = finalSize
            )
        } finally {
            pdfDocument.close()
        }
    }

    suspend fun mergePdfs(
        context: Context,
        sourceUris: List<Uri>,
        outputBaseName: String,
        overwrite: Boolean = false
    ): PdfFileInfo = withContext(Dispatchers.IO) {
        require(sourceUris.size >= 2) { "Select at least 2 PDF files to merge." }
        val targetName = PdfConverter.ensurePdfExtension(outputBaseName)
        var totalPages = 0
        val pdfDocument = PdfDocument()
        try {
            sourceUris.forEach { sourceUri ->
                withPdfRenderer(context, sourceUri) { renderer ->
                    for (index in 0 until renderer.pageCount) {
                        currentCoroutineContext().ensureActive()
                        totalPages += 1
                        appendRenderedPage(
                            renderer = renderer,
                            sourcePageIndex = index,
                            targetDocument = pdfDocument,
                            targetPageNumber = totalPages
                        )
                    }
                }
            }
            require(totalPages > 0) { "None of the selected PDFs contain pages." }
            val savedUri = PdfConverter.savePdf(
                context = context,
                pdfDocument = pdfDocument,
                fileName = targetName,
                overwriteExisting = overwrite
            )
            PdfConverter.querySinglePdfInfo(
                context = context,
                uri = savedUri,
                fallbackName = targetName,
                fallbackPageCount = totalPages
            )
        } finally {
            pdfDocument.close()
        }
    }

    suspend fun pdfToJpg(
        context: Context,
        sourceUri: Uri,
        selectedPageIndices: Set<Int>,
        quality: JpgQuality,
        baseName: String
    ): List<Uri> = withContext(Dispatchers.IO) {
        require(selectedPageIndices.isNotEmpty()) { "Select at least one page to export." }
        val cleanBase = PdfConverter.sanitizeBaseName(baseName, "pdf_page")
        val sortedPages = selectedPageIndices.sorted()
        val scale = quality.dpi.toFloat() / PDF_POINT_DPI
        val exportedUris = mutableListOf<Uri>()

        withPdfRenderer(context, sourceUri) { renderer ->
            val pageCount = renderer.pageCount
            require(pageCount > 0) { "Selected PDF has no pages." }
            sortedPages.forEach { pageIndex ->
                currentCoroutineContext().ensureActive()
                require(pageIndex in 0 until pageCount) {
                    "Page ${pageIndex + 1} is outside the PDF range (1..$pageCount)."
                }
                val fileName = "${cleanBase}_page_${pageIndex + 1}.jpg"
                val uri = renderer.openPage(pageIndex).use { page ->
                    val (w, h) = scaledDimensions(page.width, page.height, scale, MAX_JPG_DIMENSION)
                    val bitmap = renderPageToBitmap(page, w, h)
                    try {
                        saveJpgToPictures(context, bitmap, fileName, quality.jpegQuality)
                    } finally {
                        bitmap.recycle()
                    }
                }
                exportedUris.add(uri)
            }
        }
        exportedUris
    }

    suspend fun splitOrExtractPdf(
        context: Context,
        sourceUri: Uri,
        pageIndices: List<Int>,
        splitIntoSeparateFiles: Boolean,
        outputBaseName: String
    ): List<PdfFileInfo> = withContext(Dispatchers.IO) {
        require(pageIndices.isNotEmpty()) { "Select at least one page to extract." }
        val cleanBase = PdfConverter.sanitizeBaseName(outputBaseName, "extracted")
        val orderedPages = pageIndices.distinct().sorted()
        val results = mutableListOf<PdfFileInfo>()

        withPdfRenderer(context, sourceUri) { renderer ->
            val count = renderer.pageCount
            require(orderedPages.all { it in 0 until count }) {
                "One or more selected pages are out of bounds (1..$count)."
            }
            if (splitIntoSeparateFiles) {
                orderedPages.forEach { pageIndex ->
                    currentCoroutineContext().ensureActive()
                    val candidateName = "${cleanBase}_p${pageIndex + 1}"
                    val (_, uniqueBase) = PdfConverter.resolveFileName(context, candidateName)
                    val targetName = PdfConverter.ensurePdfExtension(uniqueBase)
                    val info = exportSingleSubsetPdf(
                        context = context,
                        renderer = renderer,
                        pageIndices = listOf(pageIndex),
                        targetName = targetName
                    )
                    results.add(info)
                }
            } else {
                val (_, uniqueBase) = PdfConverter.resolveFileName(context, cleanBase)
                val targetName = PdfConverter.ensurePdfExtension(uniqueBase)
                val info = exportSingleSubsetPdf(
                    context = context,
                    renderer = renderer,
                    pageIndices = orderedPages,
                    targetName = targetName
                )
                results.add(info)
            }
        }
        results
    }

    suspend fun textToPdf(
        context: Context,
        title: String,
        body: String,
        pageSize: PageSize,
        outputBaseName: String,
        overwrite: Boolean = false
    ): PdfFileInfo = withContext(Dispatchers.IO) {
        val trimmedTitle = title.trim()
        val trimmedBody = body.trim()
        require(trimmedTitle.isNotEmpty() || trimmedBody.isNotEmpty()) {
            "Enter a title or body text before generating a PDF."
        }
        val (pageWidth, pageHeight) = PdfConverter.resolvePageDimensions(pageSize)
        val targetName = PdfConverter.ensurePdfExtension(outputBaseName)

        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(15, 23, 42)
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(30, 41, 59)
            textSize = 11.5f
        }

        PdfPageCanvasWriter(pageWidth, pageHeight).use { writer ->
            if (trimmedTitle.isNotEmpty()) {
                writer.drawTextBlock(trimmedTitle, titlePaint, spaceAfterPt = 14f)
            }
            if (trimmedBody.isNotEmpty()) {
                val paragraphs = trimmedBody.split("\n")
                paragraphs.forEach { paragraph ->
                    currentCoroutineContext().ensureActive()
                    if (paragraph.isBlank()) {
                        writer.drawTextBlock("", bodyPaint, spaceAfterPt = 8f)
                    } else {
                        writer.drawTextBlock(paragraph, bodyPaint, spaceAfterPt = 6f)
                    }
                }
            }
            val document = writer.finish()
            val pageCount = document.pages.size.coerceAtLeast(1)
            val savedUri = PdfConverter.savePdf(
                context = context,
                pdfDocument = document,
                fileName = targetName,
                overwriteExisting = overwrite
            )
            PdfConverter.querySinglePdfInfo(
                context = context,
                uri = savedUri,
                fallbackName = targetName,
                fallbackPageCount = pageCount
            )
        }
    }

    suspend fun readPageCount(context: Context, uri: Uri): Int = withContext(Dispatchers.IO) {
        try {
            withPdfRenderer(context, uri) { it.pageCount }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            0
        }
    }

    fun resolveDisplayName(context: Context, uri: Uri, fallback: String): String {
        val queried = try {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val col = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (col >= 0) cursor.getString(col) else null
                } else {
                    null
                }
            }
        } catch (_: Exception) {
            null
        }
        val candidate = queried?.takeIf { it.isNotBlank() }
            ?: uri.lastPathSegment?.substringAfterLast('/')
            ?: fallback
        return candidate.ifBlank { fallback }
    }

    fun measureUriSizeBytes(context: Context, uri: Uri): Long {
        if (uri.scheme == "file") {
            val file = uri.path?.let { File(it) }
            if (file != null && file.exists()) return file.length()
        }
        val queried = try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val idx = c.getColumnIndex(OpenableColumns.SIZE)
                    if (idx >= 0 && !c.isNull(idx)) c.getLong(idx) else null
                } else {
                    null
                }
            }
        } catch (_: Exception) {
            null
        }
        if (queried != null && queried > 0L) return queried

        val fdSize = try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize }
        } catch (_: Exception) {
            null
        }
        if (fdSize != null && fdSize > 0L) return fdSize

        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var total = 0L
                while (true) {
                    val read = stream.read(buffer)
                    if (read <= 0) break
                    total += read
                }
                total
            } ?: 0L
        } catch (_: Exception) {
            0L
        }
    }

    private fun exportSingleSubsetPdf(
        context: Context,
        renderer: PdfRenderer,
        pageIndices: List<Int>,
        targetName: String
    ): PdfFileInfo {
        val pdfDocument = PdfDocument()
        try {
            pageIndices.forEachIndexed { idx, sourceIndex ->
                appendRenderedPage(
                    renderer = renderer,
                    sourcePageIndex = sourceIndex,
                    targetDocument = pdfDocument,
                    targetPageNumber = idx + 1
                )
            }
            val savedUri = PdfConverter.savePdf(
                context = context,
                pdfDocument = pdfDocument,
                fileName = targetName
            )
            return PdfConverter.querySinglePdfInfo(
                context = context,
                uri = savedUri,
                fallbackName = targetName,
                fallbackPageCount = pageIndices.size
            )
        } finally {
            pdfDocument.close()
        }
    }

    private fun appendCompressedPage(
        renderer: PdfRenderer,
        pageIndex: Int,
        targetDocument: PdfDocument,
        scale: Float,
        jpegQuality: Int
    ) {
        renderer.openPage(pageIndex).use { page ->
            val (targetW, targetH) = scaledDimensions(
                page.width,
                page.height,
                scale,
                MAX_COMPRESS_DIMENSION
            )
            val renderedBitmap = renderPageToBitmap(page, targetW, targetH)
            var redecodedBitmap: Bitmap? = null
            try {
                val compressedBytes = ByteArrayOutputStream().use { baos ->
                    renderedBitmap.compress(Bitmap.CompressFormat.JPEG, jpegQuality, baos)
                    baos.toByteArray()
                }
                val decodeOpts = BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.RGB_565
                }
                redecodedBitmap = BitmapFactory.decodeByteArray(
                    compressedBytes,
                    0,
                    compressedBytes.size,
                    decodeOpts
                ) ?: renderedBitmap

                drawBitmapOnNewPage(
                    targetDocument = targetDocument,
                    bitmap = redecodedBitmap,
                    pageWidth = page.width,
                    pageHeight = page.height,
                    pageNumber = pageIndex + 1
                )
            } finally {
                if (redecodedBitmap != null && redecodedBitmap !== renderedBitmap) {
                    redecodedBitmap.recycle()
                }
                renderedBitmap.recycle()
            }
        }
    }

    private fun appendRenderedPage(
        renderer: PdfRenderer,
        sourcePageIndex: Int,
        targetDocument: PdfDocument,
        targetPageNumber: Int
    ) {
        renderer.openPage(sourcePageIndex).use { page ->
            val (targetW, targetH) = scaledDimensions(
                page.width,
                page.height,
                2f,
                MAX_MERGE_SPLIT_DIMENSION
            )
            val bitmap = renderPageToBitmap(page, targetW, targetH)
            try {
                drawBitmapOnNewPage(
                    targetDocument = targetDocument,
                    bitmap = bitmap,
                    pageWidth = page.width,
                    pageHeight = page.height,
                    pageNumber = targetPageNumber
                )
            } finally {
                bitmap.recycle()
            }
        }
    }

    private fun drawBitmapOnNewPage(
        targetDocument: PdfDocument,
        bitmap: Bitmap,
        pageWidth: Int,
        pageHeight: Int,
        pageNumber: Int
    ) {
        val safeW = pageWidth.coerceAtLeast(1)
        val safeH = pageHeight.coerceAtLeast(1)
        val pageInfo = PdfDocument.PageInfo.Builder(safeW, safeH, pageNumber).create()
        val outPage = targetDocument.startPage(pageInfo)
        val destRect = RectF(0f, 0f, safeW.toFloat(), safeH.toFloat())
        outPage.canvas.drawBitmap(bitmap, null, destRect, Paint(Paint.FILTER_BITMAP_FLAG))
        targetDocument.finishPage(outPage)
    }

    private fun renderPageToBitmap(page: PdfRenderer.Page, targetWidth: Int, targetHeight: Int): Bitmap {
        val bitmap = createBitmap(targetWidth, targetHeight)
        Canvas(bitmap).drawColor(Color.WHITE)
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        return bitmap
    }

    private fun scaledDimensions(
        pageWidth: Int,
        pageHeight: Int,
        scale: Float,
        maxDimension: Int
    ): Pair<Int, Int> {
        val safeW = pageWidth.coerceAtLeast(1)
        val safeH = pageHeight.coerceAtLeast(1)
        val rawW = (safeW * scale).roundToInt().coerceAtLeast(1)
        val rawH = (safeH * scale).roundToInt().coerceAtLeast(1)
        val peak = max(rawW, rawH)
        if (peak <= maxDimension) {
            return rawW to rawH
        }
        val clampFactor = maxDimension.toFloat() / peak.toFloat()
        return (rawW * clampFactor).roundToInt().coerceAtLeast(1) to
            (rawH * clampFactor).roundToInt().coerceAtLeast(1)
    }

    private fun saveJpgToPictures(
        context: Context,
        bitmap: Bitmap,
        fileName: String,
        jpegQuality: Int
    ): Uri {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, JPEG_MIME_TYPE)
                put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    "${Environment.DIRECTORY_PICTURES}/$PICTURES_SUBDIR"
                )
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val uri = resolver.insert(collection, values)
                ?: throw IOException("Could not create Pictures entry for $fileName")
            try {
                resolver.openOutputStream(uri)?.use { out ->
                    if (!bitmap.compress(Bitmap.CompressFormat.JPEG, jpegQuality, out)) {
                        throw IOException("Failed to encode JPEG for $fileName")
                    }
                } ?: throw IOException("Could not open output stream for $fileName")

                val publish = ContentValues().apply {
                    put(MediaStore.Images.Media.IS_PENDING, 0)
                }
                resolver.update(uri, publish, null, null)
                uri
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                throw e
            }
        } else {
            val picturesRoot = legacyPublicPicturesDir()
            val appDir = File(picturesRoot, PICTURES_SUBDIR).apply { mkdirs() }
            val file = PdfConverter.resolveSafeChildFile(appDir, fileName)
            file.outputStream().use { out ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, jpegQuality, out)) {
                    throw IOException("Failed to encode JPEG for $fileName")
                }
            }
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }
    }

    /**
     * Returns the public Pictures directory on Android 8.0–9 (API 26–28) for exporting user-requested
     * JPG pages cleanly under Pictures/ImageToPdf with sanitized filenames.
     */
    @Suppress("DEPRECATION", "kotlin:S5324", "S5324")
    private fun legacyPublicPicturesDir(): File =
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)

    private inline fun <T> withPdfRenderer(
        context: Context,
        uri: Uri,
        block: (PdfRenderer) -> T
    ): T {
        val directPfd = try {
            context.contentResolver.openFileDescriptor(uri, "r")
        } catch (_: Exception) {
            null
        }
        if (directPfd != null) {
            return directPfd.use { pfd ->
                PdfRenderer(pfd).use(block)
            }
        }

        val tempFile = File.createTempFile("pdf_proxy_", ".pdf", context.cacheDir)
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: throw IOException("Unable to open PDF stream for $uri")

            return ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use(block)
            }
        } finally {
            tempFile.delete()
        }
    }
}
