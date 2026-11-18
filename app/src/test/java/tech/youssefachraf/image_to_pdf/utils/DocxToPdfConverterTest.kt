package tech.youssefachraf.image_to_pdf.utils

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

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
class DocxToPdfConverterTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun inspectAndConvertDocx_parsesParagraphsTablesAndImages() = runTest {
        val docxFile = createSampleDocxFile()
        val uri = Uri.fromFile(docxFile)

        val summary = DocxToPdfConverter.inspectDocx(context, uri)
        assertEquals(docxFile.name, summary.fileName)
        assertTrue(summary.paragraphCount >= 3)
        assertEquals(1, summary.tableCount)
        assertEquals(1, summary.imageCount)
        assertTrue(summary.previewText.contains("Quarterly Report"))

        val pdfInfo = DocxToPdfConverter.convertDocxToPdf(
            context = context,
            uri = uri,
            outputBaseName = "docx_test_output",
            pageSize = PageSize.A4,
        )
        assertEquals("docx_test_output.pdf", pdfInfo.name)
        assertTrue(pdfInfo.pageCount >= 1)
        assertTrue(pdfInfo.sizeBytes > 0L)
    }

    @Test(expected = IllegalArgumentException::class)
    fun extractSafeDocxEntries_rejectsZipSlipTraversal() {
        val bytes = ByteArrayOutputStream().use { baos ->
            ZipOutputStream(baos).use { zip ->
                zip.putNextEntry(ZipEntry("../../evil.xml"))
                zip.write("<evil/>".toByteArray())
                zip.closeEntry()
            }
            baos.toByteArray()
        }
        DocxToPdfConverter.extractSafeDocxEntries(ByteArrayInputStream(bytes))
    }

    @Test(expected = IllegalArgumentException::class)
    fun extractSafeDocxEntries_rejectsNonDocxZipWithoutDocumentXml() {
        val bytes = ByteArrayOutputStream().use { baos ->
            ZipOutputStream(baos).use { zip ->
                zip.putNextEntry(ZipEntry("readme.txt"))
                zip.write("plain text".toByteArray())
                zip.closeEntry()
            }
            baos.toByteArray()
        }
        DocxToPdfConverter.extractSafeDocxEntries(ByteArrayInputStream(bytes))
    }

    private fun createSampleDocxFile(): File {
        val tempFile = File.createTempFile("sample_doc_", ".docx", context.cacheDir)
        val pngBytes = ByteArrayOutputStream().use { out ->
            val bmp = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
            bmp.recycle()
            out.toByteArray()
        }

        val contentTypes = """
            <?xml version="1.0" encoding="UTF-8"?>
            <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
              <Default Extension="xml" ContentType="application/xml"/>
              <Default Extension="png" ContentType="image/png"/>
            </Types>
        """.trimIndent()

        val relsXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
              <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="media/image1.png"/>
            </Relationships>
        """.trimIndent()

        val documentXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"
                        xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"
                        xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main">
              <w:body>
                <w:p>
                  <w:pPr><w:pStyle w:val="Heading1"/><w:jc w:val="center"/></w:pPr>
                  <w:r><w:t>Quarterly Report</w:t></w:r>
                </w:p>
                <w:p>
                  <w:r>
                    <w:rPr><w:b/><w:i/><w:u w:val="single"/><w:color w:val="2F81F7"/><w:sz w:val="26"/></w:rPr>
                    <w:t>Styled summary paragraph.</w:t>
                  </w:r>
                </w:p>
                <w:p>
                  <w:pPr><w:numPr><w:ilvl w:val="0"/><w:numId w:val="1"/></w:numPr></w:pPr>
                  <w:r><w:t>First bullet point</w:t></w:r>
                </w:p>
                <w:tbl>
                  <w:tr>
                    <w:tc><w:p><w:r><w:t>Metric</w:t></w:r></w:p></w:tc>
                    <w:tc><w:p><w:r><w:t>Value</w:t></w:r></w:p></w:tc>
                  </w:tr>
                  <w:tr>
                    <w:tc><w:p><w:r><w:t>PDFs</w:t></w:r></w:p></w:tc>
                    <w:tc><w:p><w:r><w:t>42</w:t></w:r></w:p></w:tc>
                  </w:tr>
                </w:tbl>
                <w:p>
                  <w:r>
                    <w:drawing><a:blip r:embed="rId1"/></w:drawing>
                  </w:r>
                </w:p>
              </w:body>
            </w:document>
        """.trimIndent()

        ZipOutputStream(tempFile.outputStream()).use { zip ->
            writeZipText(zip, "[Content_Types].xml", contentTypes)
            writeZipText(zip, "word/_rels/document.xml.rels", relsXml)
            writeZipText(zip, "word/document.xml", documentXml)
            zip.putNextEntry(ZipEntry("word/media/image1.png"))
            zip.write(pngBytes)
            zip.closeEntry()
        }
        return tempFile
    }

    private fun writeZipText(zip: ZipOutputStream, name: String, text: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(text.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }
}
