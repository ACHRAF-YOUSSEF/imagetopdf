package tech.youssefachraf.image_to_pdf.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import androidx.core.graphics.createBitmap
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

enum class ScanFilter(val label: String) {
    ORIGINAL("Original"),
    MAGIC_COLOR("Magic Contrast"),
    DOCUMENT_BW("Crisp B&W")
}

/**
 * Enhances captured document photos locally using EXIF correction and ColorMatrix filters.
 */
object DocumentEnhancer {
    private const val MAX_SCAN_DIMENSION = 2200
    private const val JPEG_QUALITY = 90

    suspend fun applyFilterToFile(
        sourceFile: File,
        filter: ScanFilter,
        outputFile: File = sourceFile
    ): File = withContext(Dispatchers.IO) {
        if (filter == ScanFilter.ORIGINAL) {
            return@withContext if (outputFile == sourceFile) {
                sourceFile
            } else {
                sourceFile.copyTo(outputFile, overwrite = true)
            }
        }

        val orientedBitmap = decodeOrientedBitmap(sourceFile)
            ?: throw IOException("Could not decode scanned image: ${sourceFile.name}")
        val filteredBitmap = createBitmap(
            orientedBitmap.width,
            orientedBitmap.height,
        )
        try {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                colorFilter = ColorMatrixColorFilter(createColorMatrix(filter))
            }
            Canvas(filteredBitmap).drawBitmap(orientedBitmap, 0f, 0f, paint)
            outputFile.parentFile?.mkdirs()
            outputFile.outputStream().use { out ->
                filteredBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            }
            outputFile
        } finally {
            orientedBitmap.recycle()
            filteredBitmap.recycle()
        }
    }

    internal fun createColorMatrix(filter: ScanFilter): ColorMatrix {
        return when (filter) {
            ScanFilter.ORIGINAL -> ColorMatrix()
            ScanFilter.MAGIC_COLOR -> buildContrastSaturationMatrix(
                contrast = 1.35f,
                brightnessOffset = -18f,
                saturation = 1.15f
            )
            ScanFilter.DOCUMENT_BW -> buildContrastSaturationMatrix(
                contrast = 1.65f,
                brightnessOffset = -52f,
                saturation = 0f
            )
        }
    }

    private fun buildContrastSaturationMatrix(
        contrast: Float,
        brightnessOffset: Float,
        saturation: Float
    ): ColorMatrix {
        val saturationMatrix = ColorMatrix().apply { setSaturation(saturation) }
        val translate = (1f - contrast) * 128f + brightnessOffset
        val contrastMatrix = ColorMatrix(
            floatArrayOf(
                contrast, 0f, 0f, 0f, translate,
                0f, contrast, 0f, 0f, translate,
                0f, 0f, contrast, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            )
        )
        saturationMatrix.postConcat(contrastMatrix)
        return saturationMatrix
    }

    private fun decodeOrientedBitmap(file: File): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = PdfConverter.calculateInSampleSize(
                bounds.outWidth,
                bounds.outHeight,
                MAX_SCAN_DIMENSION,
                MAX_SCAN_DIMENSION
            )
        }
        val decoded = BitmapFactory.decodeFile(file.absolutePath, decodeOptions) ?: return null
        val matrix = readExifTransform(file)
        if (matrix.isIdentity) return decoded

        val rotated = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        if (rotated !== decoded) {
            decoded.recycle()
        }
        return rotated
    }

    private fun readExifTransform(file: File): Matrix {
        val matrix = Matrix()
        val orientation = try {
            ExifInterface(file.absolutePath).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        } catch (_: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
        }
        return matrix
    }
}
