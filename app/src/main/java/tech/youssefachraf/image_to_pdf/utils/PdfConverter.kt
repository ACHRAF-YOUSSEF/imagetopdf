package tech.youssefachraf.image_to_pdf.utils

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import kotlinx.coroutines.CancellationException
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class PageSize(val label: String) {
    A4("A4"),
    LETTER("Letter"),
    AUTO("Fit Image")
}

data class PdfFileInfo(
    val uri: Uri,
    val name: String,
    val pageCount: Int,
    val sizeBytes: Long,
    val dateModified: Long
)

object PdfConverter {
    private const val MAX_IMAGE_DIMENSION = 1500
    internal const val A4_WIDTH = 595
    internal const val A4_HEIGHT = 842
    internal const val LETTER_WIDTH = 612
    internal const val LETTER_HEIGHT = 792
    internal const val PDF_MIME_TYPE = "application/pdf"
    internal const val PDF_EXTENSION = ".pdf"
    private val INVALID_FILE_CHARS = Regex("[/\\\\:*?\"<>|\\x00-\\x1F]")

    fun generateDefaultFileName(prefix: String = "document"): String {
        val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
        return "${sanitizeBaseName(prefix, "document")}_${sdf.format(Date())}"
    }

    internal fun sanitizeBaseName(rawName: String, fallback: String = "document"): String {
        val withoutExt = rawName.trim().removeSuffix(PDF_EXTENSION).removeSuffix(".PDF")
        val cleaned = withoutExt.replace(INVALID_FILE_CHARS, "_").trim('.', ' ')
        return cleaned.ifBlank { fallback }
    }

    internal fun ensurePdfExtension(fileName: String): String {
        val cleanBase = sanitizeBaseName(fileName)
        return "$cleanBase$PDF_EXTENSION"
    }

    internal fun resolvePageDimensions(
        pageSize: PageSize,
        fallbackWidth: Int = A4_WIDTH,
        fallbackHeight: Int = A4_HEIGHT
    ): Pair<Int, Int> {
        return when (pageSize) {
            PageSize.A4 -> A4_WIDTH to A4_HEIGHT
            PageSize.LETTER -> LETTER_WIDTH to LETTER_HEIGHT
            PageSize.AUTO -> fallbackWidth.coerceAtLeast(1) to fallbackHeight.coerceAtLeast(1)
        }
    }

    fun resolveFileName(context: Context, baseName: String): Pair<Boolean, String> {
        val cleanBase = sanitizeBaseName(baseName)
        if (!fileExists(context, "$cleanBase$PDF_EXTENSION")) {
            return Pair(false, cleanBase)
        }
        var counter = 1
        while (fileExists(context, "${cleanBase}_($counter)$PDF_EXTENSION")) {
            counter++
        }
        return Pair(true, "${cleanBase}_($counter)")
    }

    private fun fileExists(context: Context, fileName: String): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val projection = arrayOf(MediaStore.Downloads._ID)
            val selection = "${MediaStore.Downloads.DISPLAY_NAME} = ?"
            val selectionArgs = arrayOf(fileName)
            resolver.query(collection, projection, selection, selectionArgs, null)?.use {
                it.count > 0
            } ?: false
        } else {
            val downloadsDir = legacyPublicDownloadsDir()
            resolveSafeChildFile(downloadsDir, fileName).exists()
        }
    }

    suspend fun convert(
        context: Context,
        uris: List<Uri>,
        pageSize: PageSize = PageSize.A4,
        fileName: String? = null,
        overwriteExisting: Boolean = false
    ): Uri {
        require(uris.isNotEmpty()) { "Select at least one image." }

        val pdfDocument = PdfDocument()
        try {
            uris.forEachIndexed { index, uri ->
                val bitmap = decodeSampledBitmap(context, uri)
                    ?: throw IllegalArgumentException(
                        "Could not read image ${index + 1} of ${uris.size}. It may have been moved or is unsupported."
                    )

                try {
                    val (pageWidth, pageHeight) = resolvePageDimensions(
                        pageSize = pageSize,
                        fallbackWidth = bitmap.width,
                        fallbackHeight = bitmap.height
                    )

                    val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, index + 1).create()
                    val page = pdfDocument.startPage(pageInfo)
                    drawBitmapFitted(page.canvas, bitmap, pageWidth.toFloat(), pageHeight.toFloat())
                    pdfDocument.finishPage(page)
                } finally {
                    // Always release the decoded bitmap, including when drawing fails.
                    bitmap.recycle()
                }
            }

            val actualFileName = fileName ?: "ImageToPdf_${System.currentTimeMillis()}"
            return savePdf(
                context = context,
                pdfDocument = pdfDocument,
                fileName = ensurePdfExtension(actualFileName),
                overwriteExisting = overwriteExisting
            )
        } finally {
            pdfDocument.close()
        }
    }

    internal fun drawBitmapFitted(canvas: Canvas, bitmap: Bitmap, pageWidth: Float, pageHeight: Float) {
        val bitmapRatio = bitmap.width.toFloat() / bitmap.height.coerceAtLeast(1).toFloat()
        val pageRatio = pageWidth / pageHeight.coerceAtLeast(1f)

        val destWidth: Float
        val destHeight: Float
        if (bitmapRatio > pageRatio) {
            destWidth = pageWidth
            destHeight = pageWidth / bitmapRatio
        } else {
            destHeight = pageHeight
            destWidth = pageHeight * bitmapRatio
        }

        val left = (pageWidth - destWidth) / 2f
        val top = (pageHeight - destHeight) / 2f
        val destRect = RectF(left, top, left + destWidth, top + destHeight)
        canvas.drawBitmap(bitmap, null, destRect, Paint(Paint.FILTER_BITMAP_FLAG))
    }

    private fun decodeSampledBitmap(context: Context, uri: Uri): Bitmap? {
        return try {
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOptions)
            }

            if (boundsOptions.outWidth <= 0 || boundsOptions.outHeight <= 0) {
                return null
            }

            val sampleSize = calculateInSampleSize(
                boundsOptions.outWidth,
                boundsOptions.outHeight,
                MAX_IMAGE_DIMENSION,
                MAX_IMAGE_DIMENSION
            )

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    internal fun calculateInSampleSize(
        width: Int,
        height: Int,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        if (width <= 0 || height <= 0 || reqWidth <= 0 || reqHeight <= 0) return 1

        var sampleSize = 1
        while (
            width / (sampleSize * 2) >= reqWidth ||
            height / (sampleSize * 2) >= reqHeight
        ) {
            sampleSize *= 2
        }
        return sampleSize
    }

    internal fun savePdf(
        context: Context,
        pdfDocument: PdfDocument,
        fileName: String,
        overwriteExisting: Boolean = false
    ): Uri {
        val normalizedName = ensurePdfExtension(fileName)
        return writePdfToDownloads(context, normalizedName, overwriteExisting) { outputStream ->
            pdfDocument.writeTo(outputStream)
        }
    }

    internal fun writePdfStreamToDownloads(
        context: Context,
        inputStream: InputStream,
        fileName: String,
        overwriteExisting: Boolean = false
    ): Uri {
        val normalizedName = ensurePdfExtension(fileName)
        return writePdfToDownloads(context, normalizedName, overwriteExisting) { outputStream ->
            inputStream.copyTo(outputStream)
        }
    }

    private fun writePdfToDownloads(
        context: Context,
        normalizedName: String,
        overwriteExisting: Boolean,
        writer: (java.io.OutputStream) -> Unit
    ): Uri {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            writePdfMediaStore(context, normalizedName, overwriteExisting, writer)
        } else {
            writePdfLegacyDownloads(context, normalizedName, writer)
        }
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.Q)
    private fun writePdfMediaStore(
        context: Context,
        normalizedName: String,
        overwriteExisting: Boolean,
        writer: (java.io.OutputStream) -> Unit
    ): Uri {
        val resolver = context.contentResolver
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        if (overwriteExisting) {
            deleteExistingMediaStoreMatch(resolver, collection, normalizedName)
        }
        val contentValues = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, normalizedName)
            put(MediaStore.Downloads.MIME_TYPE, PDF_MIME_TYPE)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val itemUri = resolver.insert(collection, contentValues)
            ?: throw IOException("MediaStore insert failed — cannot create Downloads entry")

        return try {
            resolver.openOutputStream(itemUri)?.use { outputStream ->
                writer(outputStream)
            } ?: throw IOException("Could not open output stream for MediaStore URI")

            val publishValues = ContentValues().apply {
                put(MediaStore.Downloads.IS_PENDING, 0)
            }
            resolver.update(itemUri, publishValues, null, null)
            PdfThumbnailCache.invalidateUri(itemUri)
            itemUri
        } catch (exception: Exception) {
            resolver.delete(itemUri, null, null)
            throw exception
        }
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.Q)
    private fun deleteExistingMediaStoreMatch(
        resolver: ContentResolver,
        collection: Uri,
        normalizedName: String
    ) {
        try {
            resolver.delete(
                collection,
                "${MediaStore.Downloads.DISPLAY_NAME} = ?",
                arrayOf(normalizedName)
            )
        } catch (_: SecurityException) {
            // File may belong to another app under scoped storage; proceed with fresh insert.
        }
    }

    /**
     * Returns the public Downloads directory on Android 8.0–9 (API 26–28) where MediaStore.Downloads
     * does not exist. Only user-initiated exported PDFs with sanitized filenames are stored here.
     */
    @Suppress("DEPRECATION", "kotlin:S5324", "S5324")
    private fun legacyPublicDownloadsDir(): File =
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)

    internal fun resolveSafeChildFile(baseDir: File, fileName: String): File {
        val candidate = File(baseDir, fileName)
        val baseCanonical = baseDir.canonicalFile.toPath()
        val candidateCanonical = candidate.canonicalFile.toPath()
        require(candidateCanonical.startsWith(baseCanonical)) {
            "Invalid target file path: $fileName"
        }
        return candidate
    }

    private fun writePdfLegacyDownloads(
        context: Context,
        normalizedName: String,
        writer: (java.io.OutputStream) -> Unit
    ): Uri {
        val downloadsDir = legacyPublicDownloadsDir()
        if (!downloadsDir.exists()) {
            downloadsDir.mkdirs()
        }
        val file = resolveSafeChildFile(downloadsDir, normalizedName)
        file.outputStream().use { outputStream ->
            writer(outputStream)
        }
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        PdfThumbnailCache.invalidateUri(uri)
        return uri
    }

    /**
     * Query all PDFs from MediaStore Downloads (API 29+) or legacy Downloads folder.
     */
    fun queryAllPdfs(context: Context): List<PdfFileInfo> {
        val pdfs = mutableListOf<PdfFileInfo>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val projection = arrayOf(
                MediaStore.Downloads._ID,
                MediaStore.Downloads.DISPLAY_NAME,
                MediaStore.Downloads.SIZE,
                MediaStore.Downloads.DATE_MODIFIED,
            )
            val selection = "${MediaStore.Downloads.MIME_TYPE} = ?"
            val selectionArgs = arrayOf(PDF_MIME_TYPE)
            val sortOrder = "${MediaStore.Downloads.DATE_MODIFIED} DESC"

            resolver.query(collection, projection, selection, selectionArgs, sortOrder)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Downloads.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Downloads.SIZE)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Downloads.DATE_MODIFIED)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: "Unknown"
                    val size = cursor.getLong(sizeCol)
                    val date = cursor.getLong(dateCol) * 1000

                    val uri = ContentUris.withAppendedId(collection, id)
                    val pageCount = getPdfPageCount(context, uri)
                    pdfs.add(PdfFileInfo(uri, name, pageCount, size, date))
                }
            }
        } else {
            val downloadsDir = legacyPublicDownloadsDir()
            downloadsDir.listFiles()?.filter { it.extension.equals("pdf", ignoreCase = true) }
                ?.sortedByDescending { it.lastModified() }
                ?.forEach { file ->
                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file
                    )
                    val pageCount = getPdfPageCount(context, uri)
                    pdfs.add(
                        PdfFileInfo(
                            uri = uri,
                            name = file.name,
                            pageCount = pageCount,
                            sizeBytes = file.length(),
                            dateModified = file.lastModified()
                        )
                    )
                }
        }
        return pdfs
    }

    internal fun querySinglePdfInfo(
        context: Context,
        uri: Uri,
        fallbackName: String,
        fallbackPageCount: Int
    ): PdfFileInfo {
        val pageCount = getPdfPageCount(context, uri).takeIf { it > 0 } ?: fallbackPageCount
        val normalizedFallback = ensurePdfExtension(fallbackName)

        if (uri.scheme == ContentResolver.SCHEME_FILE) {
            val file = uri.path?.let { File(it) }
            if (file != null && file.exists()) {
                return PdfFileInfo(
                    uri = uri,
                    name = file.name,
                    pageCount = pageCount,
                    sizeBytes = file.length(),
                    dateModified = file.lastModified()
                )
            }
        }

        val queried = queryContentMetadata(context, uri)
        return PdfFileInfo(
            uri = uri,
            name = queried?.first ?: normalizedFallback,
            pageCount = pageCount,
            sizeBytes = queried?.second ?: measureStreamSize(context, uri),
            dateModified = System.currentTimeMillis()
        )
    }

    private fun queryContentMetadata(context: Context, uri: Uri): Pair<String?, Long?>? {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                val name = if (nameIdx >= 0) cursor.getString(nameIdx) else null
                val size = if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) cursor.getLong(sizeIdx) else null
                name to size
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun measureStreamSize(context: Context, uri: Uri): Long {
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

    internal fun getPdfPageCount(context: Context, uri: Uri): Int {
        return try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    renderer.pageCount
                }
            } ?: 0
        } catch (_: Exception) {
            0
        }
    }

    fun deletePdf(context: Context, uri: Uri): Boolean {
        return try {
            val deleted = context.contentResolver.delete(uri, null, null) > 0
            if (deleted) {
                PdfThumbnailCache.invalidateUri(uri)
            }
            deleted
        } catch (_: Exception) {
            false
        }
    }

    fun renamePdf(context: Context, uri: Uri, newName: String): Boolean {
        val normalizedName = ensurePdfExtension(newName)
        return try {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, normalizedName)
            }
            context.contentResolver.update(uri, values, null, null) > 0
        } catch (_: Exception) {
            false
        }
    }
}
