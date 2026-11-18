package tech.youssefachraf.image_to_pdf.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import androidx.core.net.toUri
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import tech.youssefachraf.image_to_pdf.testutil.ShadowFileProvider
import tech.youssefachraf.image_to_pdf.testutil.ShadowPdfDocument
import tech.youssefachraf.image_to_pdf.testutil.ShadowPdfDocumentPage
import tech.youssefachraf.image_to_pdf.testutil.ShadowPdfRenderer
import tech.youssefachraf.image_to_pdf.testutil.ShadowPdfRendererPage
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [28],
    shadows = [
        ShadowFileProvider::class,
        ShadowPdfDocument::class,
        ShadowPdfDocumentPage::class,
        ShadowPdfRenderer::class,
        ShadowPdfRendererPage::class,
    ]
)
class PdfConverterTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun calculateInSampleSize_scalesLargeDimensionsByPowersOfTwo() {
        assertEquals(1, PdfConverter.calculateInSampleSize(0, 100, 1500, 1500))
        assertEquals(1, PdfConverter.calculateInSampleSize(1200, 900, 1500, 1500))
        assertEquals(2, PdfConverter.calculateInSampleSize(3200, 2400, 1500, 1500))
        assertEquals(4, PdfConverter.calculateInSampleSize(6400, 4800, 1500, 1500))
    }

    @Test
    fun sanitizeAndResolvePageDimensions_returnExpectedValues() {
        assertEquals("my_doc", PdfConverter.sanitizeBaseName("my/doc.pdf"))
        assertEquals("notes.pdf", PdfConverter.ensurePdfExtension("notes"))
        assertEquals(595 to 842, PdfConverter.resolvePageDimensions(PageSize.A4))
        assertEquals(612 to 792, PdfConverter.resolvePageDimensions(PageSize.LETTER))
        assertEquals(800 to 600, PdfConverter.resolvePageDimensions(PageSize.AUTO, 800, 600))
    }

    @Test
    fun convert_createsPdfForAllPageSizesAndResolvesFileNameConflicts() = runTest {
        val img1 = createTempJpeg(120, 180, Color.BLUE)
        val img2 = createTempJpeg(240, 160, Color.RED)
        val uris = listOf(Uri.fromFile(img1), Uri.fromFile(img2))

        val a4Uri = PdfConverter.convert(
            context = context,
            uris = uris,
            pageSize = PageSize.A4,
            fileName = "unit_test_a4",
        )
        assertTrue(a4Uri.toString().isNotBlank())

        val (exists, suggested) = PdfConverter.resolveFileName(context, "unit_test_a4")
        assertTrue(exists)
        assertEquals("unit_test_a4_(1)", suggested)

        val (missingExists, missingName) = PdfConverter.resolveFileName(context, "unique_nonexistent_name")
        assertFalse(missingExists)
        assertEquals("unique_nonexistent_name", missingName)

        PdfConverter.convert(
            context = context,
            uris = uris,
            pageSize = PageSize.LETTER,
            fileName = "unit_test_letter",
        )
        PdfConverter.convert(
            context = context,
            uris = uris,
            pageSize = PageSize.AUTO,
            fileName = "unit_test_auto",
            overwriteExisting = true,
        )

        val defaultName = PdfConverter.generateDefaultFileName()
        assertTrue(defaultName.startsWith("document_"))
    }

    @Test
    fun mergeAndDeduplicatePdfs_removesDuplicatesByNameAndSizeAndPreservesDistinctFiles() {
        val safDoc = PdfFileInfo(
            uri = "content://com.android.externalstorage.documents/tree/primary%3ADownload%2FImageToPdf/document/doc1".toUri(),
            name = "invoice.pdf",
            pageCount = 2,
            sizeBytes = 2048L,
            dateModified = 2000L,
        )
        val duplicateMediaStore = PdfFileInfo(
            uri = "content://media/external/downloads/12".toUri(),
            name = "Invoice.pdf",
            pageCount = 2,
            sizeBytes = 2048L,
            dateModified = 2000L,
        )
        val legacyRootPdf = PdfFileInfo(
            uri = "content://media/external/downloads/9".toUri(),
            name = "older_root.pdf",
            pageCount = 1,
            sizeBytes = 512L,
            dateModified = 3000L,
        )

        val merged = PdfConverter.mergeAndDeduplicatePdfs(
            primary = listOf(safDoc),
            secondary = listOf(duplicateMediaStore, legacyRootPdf),
        )

        assertEquals(2, merged.size)
        assertEquals(legacyRootPdf.uri, merged[0].uri)
        assertEquals(safDoc.uri, merged[1].uri)

        val tempFile = File.createTempFile("delete_me_", ".pdf", context.cacheDir)
        assertTrue(tempFile.exists())
        assertTrue(PdfConverter.deletePdf(context, Uri.fromFile(tempFile)))
        assertFalse(tempFile.exists())
    }

    @Test
    fun pdfStorageFolder_formatsInitialUriAndTreeDisplayNamesCleanly() {
        val initialUri = PdfStorageFolder.buildInitialFolderUri()
        assertTrue(initialUri.toString().contains("Download%2FImageToPdf"))

        val linked = "content://com.android.externalstorage.documents/tree/primary%3ADownload%2FImageToPdf".toUri()
        assertEquals("Download/ImageToPdf", PdfStorageFolder.formatTreeUriDisplayName(linked))
    }

    private fun createTempJpeg(width: Int, height: Int, color: Int): File {
        val file = File.createTempFile("img_", ".jpg", context.cacheDir)
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(bmp).drawColor(color)
        file.outputStream().use { out ->
            bmp.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        bmp.recycle()
        return file
    }
}
