package tech.youssefachraf.image_to_pdf.utils

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class PageSize {
    A4,
    LETTER,
    AUTO
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
    private const val A4_WIDTH = 595
    private const val A4_HEIGHT = 842
    private const val LETTER_WIDTH = 612
    private const val LETTER_HEIGHT = 792

    fun generateDefaultFileName(): String {
        val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
        return "document_${sdf.format(Date())}"
    }

    fun resolveFileName(context: Context, baseName: String): Pair<Boolean, String> {
        // Check if baseName.pdf exists
        if (!fileExists(context, "$baseName.pdf")) {
            return Pair(false, baseName)
        }
        // Find next available
        var counter = 1
        while (fileExists(context, "${baseName}_($counter).pdf")) {
            counter++
        }
        return Pair(true, "${baseName}_($counter)")
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
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            File(downloadsDir, fileName).exists()
        }
    }

    suspend fun convert(
        context: Context,
        uris: List<Uri>,
        pageSize: PageSize = PageSize.A4,
        fileName: String? = null
    ): Uri {
        val pdfDocument = PdfDocument()
        try {
            uris.forEachIndexed { index, uri ->
                val bitmap = decodeSampledBitmap(context, uri) ?: return@forEachIndexed

                val (pageWidth, pageHeight) = when (pageSize) {
                    PageSize.A4 -> Pair(A4_WIDTH, A4_HEIGHT)
                    PageSize.LETTER -> Pair(LETTER_WIDTH, LETTER_HEIGHT)
                    PageSize.AUTO -> Pair(bitmap.width, bitmap.height)
                }

                val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, index + 1).create()
                val page = pdfDocument.startPage(pageInfo)

                drawBitmapFitted(page.canvas, bitmap, pageWidth.toFloat(), pageHeight.toFloat())

                pdfDocument.finishPage(page)
                bitmap.recycle()
            }

            val actualFileName = fileName ?: "ImageToPdf_${System.currentTimeMillis()}"
            return savePdf(context, pdfDocument, "$actualFileName.pdf")
        } finally {
            pdfDocument.close()
        }
    }

    private fun drawBitmapFitted(canvas: Canvas, bitmap: Bitmap, pageWidth: Float, pageHeight: Float) {
        val bitmapRatio = bitmap.width.toFloat() / bitmap.height.toFloat()
        val pageRatio = pageWidth / pageHeight

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
        } catch (_: Exception) {
            null
        }
    }

    private fun calculateInSampleSize(
        width: Int,
        height: Int,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        var sampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while ((halfHeight / sampleSize) >= reqHeight && (halfWidth / sampleSize) >= reqWidth) {
                sampleSize *= 2
            }
        }
        return sampleSize
    }

    private fun savePdf(context: Context, pdfDocument: PdfDocument, fileName: String): Uri {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }

            val resolver = context.contentResolver
            val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

            val itemUri = resolver.insert(collection, contentValues)
                ?: throw Exception("MediaStore insert failed — cannot create Downloads entry")

            resolver.openOutputStream(itemUri)?.use { outputStream ->
                pdfDocument.writeTo(outputStream)
            } ?: throw Exception("Could not open output stream for MediaStore URI")

            contentValues.clear()
            contentValues.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(itemUri, contentValues, null, null)

            itemUri
        } else {
            val downloadsDir =
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val file = File(downloadsDir, fileName)

            file.outputStream().use { outputStream ->
                pdfDocument.writeTo(outputStream)
            }

            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        }
    }

    /**
     * Query all PDFs from MediaStore Downloads.
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
            val selectionArgs = arrayOf("application/pdf")
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
                    val date = cursor.getLong(dateCol) * 1000 // seconds -> ms

                    val uri = android.content.ContentUris.withAppendedId(collection, id)
                    val pageCount = getPdfPageCount(context, uri)

                    pdfs.add(PdfFileInfo(uri, name, pageCount, size, date))
                }
            }
        } else {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
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

    private fun getPdfPageCount(context: Context, uri: Uri): Int {
        return try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                val renderer = android.graphics.pdf.PdfRenderer(pfd)
                val count = renderer.pageCount
                renderer.close()
                count
            } ?: 0
        } catch (_: Exception) {
            0
        }
    }

    fun deletePdf(context: Context, uri: Uri): Boolean {
        return try {
            context.contentResolver.delete(uri, null, null) > 0
        } catch (_: Exception) {
            false
        }
    }

    fun renamePdf(context: Context, uri: Uri, newName: String): Boolean {
        return try {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, if (newName.endsWith(".pdf")) newName else "$newName.pdf")
            }
            context.contentResolver.update(uri, values, null, null) > 0
        } catch (_: Exception) {
            false
        }
    }
}
