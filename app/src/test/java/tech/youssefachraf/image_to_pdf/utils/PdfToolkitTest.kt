package tech.youssefachraf.image_to_pdf.utils

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfDocument
import android.net.Uri
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
class PdfToolkitTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        PdfThumbnailCache.clear()
    }

    @Test
    fun importPdfs_importsValidPdfAndRejectsCorruptFile() = runTest {
        val validPdf = createSamplePdfFile("import_source", pageCount = 2)
        val imported = PdfToolkit.importPdfs(context, listOf(Uri.fromFile(validPdf)))

        assertEquals(1, imported.size)
        assertEquals(2, imported.first().pageCount)
        assertTrue(imported.first().sizeBytes > 0L)

        val corrupt = File.createTempFile("not_a_pdf_", ".pdf", context.cacheDir).apply {
            writeText("NOT_PDF_BYTES")
        }
        var rejected = false
        try {
            PdfToolkit.importPdfs(context, listOf(Uri.fromFile(corrupt)))
        } catch (_: IllegalArgumentException) {
            rejected = true
        }
        assertTrue(rejected)
    }

    @Test
    fun compressPdf_preservesPageCountAcrossPresets() = runTest {
        val source = createSamplePdfFile("compress_src", pageCount = 3)
        val sourceUri = Uri.fromFile(source)

        for (level in CompressionLevel.entries) {
            val result = PdfToolkit.compressPdf(
                context = context,
                sourceUri = sourceUri,
                level = level,
                outputBaseName = "compressed_${level.name.lowercase()}",
            )
            assertEquals(3, result.pdfInfo.pageCount)
            assertTrue(result.compressedSizeBytes > 0L)
        }
    }

    @Test
    fun mergePdfs_combinesAllPagesInOrder() = runTest {
        val pdfA = createSamplePdfFile("merge_a", pageCount = 2)
        val pdfB = createSamplePdfFile("merge_b", pageCount = 3)

        val merged = PdfToolkit.mergePdfs(
            context = context,
            sourceUris = listOf(Uri.fromFile(pdfA), Uri.fromFile(pdfB)),
            outputBaseName = "merged_output",
        )
        assertEquals("merged_output.pdf", merged.name)
        assertEquals(5, merged.pageCount)
        assertTrue(merged.sizeBytes > 0L)
    }

    @Test
    fun pdfToJpg_exportsDecodableJpegImagesAndThumbnailCacheCachesPages() = runTest {
        val source = createSamplePdfFile("jpg_src", pageCount = 3)
        val sourceUri = Uri.fromFile(source)

        val exported = PdfToolkit.pdfToJpg(
            context = context,
            sourceUri = sourceUri,
            selectedPageIndices = setOf(0, 2),
            quality = JpgQuality.STANDARD,
            baseName = "export_sample",
        )
        assertEquals(2, exported.size)
        exported.forEach { imgUri ->
            context.contentResolver.openInputStream(imgUri)?.use { stream ->
                val bmp = BitmapFactory.decodeStream(stream)
                assertNotNull(bmp)
                bmp!!.recycle()
            }
        }

        val thumb1 = PdfThumbnailCache.loadPageThumbnail(context, sourceUri, pageIndex = 0, maxDimensionPx = 200)
        assertNotNull(thumb1)
        val cached = PdfThumbnailCache.getCached(PdfThumbnailCache.cacheKey(sourceUri, 0, 200))
        assertNotNull(cached)
        PdfThumbnailCache.invalidateUri(sourceUri)
    }

    @Test
    fun splitOrExtractPdf_supportsCombinedAndSeparateOutputs() = runTest {
        val source = createSamplePdfFile("split_src", pageCount = 4)
        val sourceUri = Uri.fromFile(source)

        val combined = PdfToolkit.splitOrExtractPdf(
            context = context,
            sourceUri = sourceUri,
            pageIndices = listOf(0, 2),
            splitIntoSeparateFiles = false,
            outputBaseName = "split_combined",
        )
        assertEquals(1, combined.size)
        assertEquals(2, combined.first().pageCount)

        val separate = PdfToolkit.splitOrExtractPdf(
            context = context,
            sourceUri = sourceUri,
            pageIndices = listOf(1, 3),
            splitIntoSeparateFiles = true,
            outputBaseName = "split_single",
        )
        assertEquals(2, separate.size)
        assertTrue(separate.all { it.pageCount == 1 })
    }

    @Test
    fun textToPdf_generatesMultiPagePdfForLongDocument() = runTest {
        val longBody = (1..90).joinToString("\n") { line ->
            "Paragraph line $line with enough words to span across page boundaries cleanly."
        }
        val info = PdfToolkit.textToPdf(
            context = context,
            title = "Multi-page Note",
            body = longBody,
            pageSize = PageSize.A4,
            outputBaseName = "long_note_test",
        )
        assertEquals("long_note_test.pdf", info.name)
        assertTrue(info.pageCount >= 2)
    }

    private fun createSamplePdfFile(prefix: String, pageCount: Int): File {
        val file = File.createTempFile("${prefix}_", ".pdf", context.cacheDir)
        val doc = PdfDocument()
        for (i in 1..pageCount) {
            val page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, i).create())
            doc.finishPage(page)
        }
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
        return file
    }
}
