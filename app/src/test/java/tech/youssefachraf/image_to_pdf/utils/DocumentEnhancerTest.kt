package tech.youssefachraf.image_to_pdf.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class DocumentEnhancerTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun applyFilterToFile_producesValidOutputsForAllScanFilters() = runTest {
        val rawFile = File.createTempFile("scan_raw_", ".jpg", context.cacheDir)
        val srcBitmap = Bitmap.createBitmap(40, 60, Bitmap.Config.ARGB_8888)
        Canvas(srcBitmap).drawColor(Color.rgb(60, 140, 220))
        rawFile.outputStream().use { out ->
            srcBitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
        }
        srcBitmap.recycle()

        val originalCopy = File.createTempFile("scan_orig_", ".jpg", context.cacheDir)
        val magicCopy = File.createTempFile("scan_magic_", ".jpg", context.cacheDir)
        val bwCopy = File.createTempFile("scan_bw_", ".jpg", context.cacheDir)

        DocumentEnhancer.applyFilterToFile(rawFile, ScanFilter.ORIGINAL, originalCopy)
        DocumentEnhancer.applyFilterToFile(rawFile, ScanFilter.MAGIC_COLOR, magicCopy)
        DocumentEnhancer.applyFilterToFile(rawFile, ScanFilter.DOCUMENT_BW, bwCopy)

        listOf(originalCopy, magicCopy, bwCopy).forEach { file ->
            assertTrue(file.exists() && file.length() > 0L)
            val decoded = BitmapFactory.decodeFile(file.absolutePath)
            assertNotNull(decoded)
            assertEquals(40, decoded!!.width)
            assertEquals(60, decoded.height)
            decoded.recycle()
        }

        val bwBitmap = BitmapFactory.decodeFile(bwCopy.absolutePath)!!
        val pixel = bwBitmap.getPixel(20, 30)
        assertEquals(Color.red(pixel), Color.green(pixel))
        assertEquals(Color.green(pixel), Color.blue(pixel))
        bwBitmap.recycle()
    }
}
