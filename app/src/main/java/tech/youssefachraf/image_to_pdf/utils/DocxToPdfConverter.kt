package tech.youssefachraf.image_to_pdf.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipInputStream

data class DocxSummary(
    val fileName: String,
    val paragraphCount: Int,
    val tableCount: Int,
    val imageCount: Int,
    val previewText: String
)

internal sealed interface DocxBlock {
    data class Paragraph(
        val text: CharSequence,
        val textSizeSp: Float,
        val alignment: Layout.Alignment,
        val spaceAfterPt: Float,
        val isListItem: Boolean,
    ) : DocxBlock

    data class Table(val rows: List<List<String>>) : DocxBlock

    class Image(val bytes: ByteArray) : DocxBlock
}

private data class RunStyle(
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val strikethrough: Boolean = false,
    val sizeScale: Float = 1f,
    val colorInt: Int? = null,
)

private data class ParagraphStyle(
    val textSizeSp: Float = DEFAULT_BODY_SIZE_SP,
    val isHeading: Boolean = false,
    val alignment: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL,
    val isListItem: Boolean = false,
    val spaceAfterPt: Float = DEFAULT_PARAGRAPH_SPACING_PT,
)

private const val DEFAULT_BODY_SIZE_SP = 11.5f
private const val DEFAULT_PARAGRAPH_SPACING_PT = 7f

/**
 * Offline `.docx` (OOXML) parser and paginated PDF converter using safe ZipInputStream + XmlPullParser.
 */
object DocxToPdfConverter {
    private const val DOCUMENT_XML_PATH = "word/document.xml"
    private const val RELS_XML_PATH = "word/_rels/document.xml.rels"
    private const val MEDIA_PREFIX = "word/media/"
    private const val MAX_ZIP_ENTRIES = 2000
    private const val MAX_TOTAL_UNCOMPRESSED_BYTES = 30L * 1024L * 1024L
    private const val MAX_SINGLE_ENTRY_BYTES = 10L * 1024L * 1024L
    private const val MAX_EMBEDDED_IMAGE_DIM = 1600
    private const val PREVIEW_MAX_CHARS = 320

    suspend fun inspectDocx(context: Context, uri: Uri): DocxSummary = withContext(Dispatchers.IO) {
        val displayName = PdfToolkit.resolveDisplayName(context, uri, "document.docx")
        val blocks = parseFromUri(context, uri)
        val paragraphs = blocks.filterIsInstance<DocxBlock.Paragraph>()
        val tables = blocks.filterIsInstance<DocxBlock.Table>()
        val images = blocks.filterIsInstance<DocxBlock.Image>()

        val preview = paragraphs
            .asSequence()
            .map { it.text.toString().trim() }
            .filter { it.isNotEmpty() }
            .take(5)
            .joinToString("\n")
            .take(PREVIEW_MAX_CHARS)

        DocxSummary(
            fileName = displayName,
            paragraphCount = paragraphs.size,
            tableCount = tables.size,
            imageCount = images.size,
            previewText = preview
        )
    }

    suspend fun convertDocxToPdf(
        context: Context,
        uri: Uri,
        outputBaseName: String,
        pageSize: PageSize = PageSize.A4,
        overwrite: Boolean = false
    ): PdfFileInfo = withContext(Dispatchers.IO) {
        val blocks = parseFromUri(context, uri)
        require(blocks.isNotEmpty()) { "The selected .docx document appears to be empty." }

        val (pageWidth, pageHeight) = PdfConverter.resolvePageDimensions(pageSize)
        val targetName = PdfConverter.ensurePdfExtension(outputBaseName)

        val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(30, 41, 59)
            textSize = DEFAULT_BODY_SIZE_SP
        }
        val cellPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(30, 41, 59)
            textSize = 10f
        }
        val headerCellPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(15, 23, 42)
            textSize = 10f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        PdfPageCanvasWriter(pageWidth, pageHeight).use { writer ->
            for (block in blocks) {
                currentCoroutineContext().ensureActive()
                renderBlock(writer, block, bodyPaint, cellPaint, headerCellPaint)
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

    private fun renderBlock(
        writer: PdfPageCanvasWriter,
        block: DocxBlock,
        bodyPaint: TextPaint,
        cellPaint: TextPaint,
        headerCellPaint: TextPaint
    ) {
        when (block) {
            is DocxBlock.Paragraph -> {
                val paragraphPaint = TextPaint(bodyPaint).apply {
                    textSize = block.textSizeSp
                }
                val indent = if (block.isListItem) 14f else 0f
                writer.drawTextBlock(
                    text = block.text,
                    paint = paragraphPaint,
                    alignment = block.alignment,
                    spaceAfterPt = block.spaceAfterPt,
                    leftIndentPt = indent
                )
            }
            is DocxBlock.Table -> writer.drawTableBlock(block.rows, cellPaint, headerCellPaint)
            is DocxBlock.Image -> {
                val bitmap = decodeSampledBytes(block.bytes) ?: return
                try {
                    writer.drawBitmapBlock(bitmap)
                } finally {
                    bitmap.recycle()
                }
            }
        }
    }

    private fun parseFromUri(context: Context, uri: Uri): List<DocxBlock> {
        val stream = context.contentResolver.openInputStream(uri)
            ?: throw IOException("Unable to open the selected .docx file.")
        val entries = stream.use { extractSafeDocxEntries(it) }
        val docBytes = entries[DOCUMENT_XML_PATH]
            ?: throw IllegalArgumentException("Selected file is not a valid Word (.docx) document.")
        val rels = parseRelationships(entries[RELS_XML_PATH])
        return parseDocumentXml(docBytes, rels, entries)
    }

    internal fun extractSafeDocxEntries(inputStream: InputStream): Map<String, ByteArray> {
        val kept = mutableMapOf<String, ByteArray>()
        var entryCount = 0
        var totalBytes = 0L

        ZipInputStream(BufferedInputStream(inputStream)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entryCount += 1
                if (entryCount > MAX_ZIP_ENTRIES) {
                    throw IllegalArgumentException("Archive exceeds safe entry limit.")
                }
                val name = entry.name.orEmpty()
                validateSafeEntryName(name)

                if (!entry.isDirectory) {
                    val remainingBudget = MAX_TOTAL_UNCOMPRESSED_BYTES - totalBytes
                    val bytes = readBoundedBytes(zip, remainingBudget)
                    totalBytes += bytes.size
                    if (shouldRetainEntry(name)) {
                        kept[name] = bytes
                    }
                }
                zip.closeEntry()
            }
        }

        if (DOCUMENT_XML_PATH !in kept) {
            throw IllegalArgumentException("Selected file is not a valid Word (.docx) document.")
        }
        return kept
    }

    private fun validateSafeEntryName(name: String) {
        val segments = name.split('/', '\\')
        if (name.isBlank() || name.startsWith("/") || '\\' in name || ".." in segments) {
            throw IllegalArgumentException("Unsafe archive entry path: $name")
        }
    }

    private fun shouldRetainEntry(name: String): Boolean {
        return name == DOCUMENT_XML_PATH || name == RELS_XML_PATH || name.startsWith(MEDIA_PREFIX)
    }

    private fun readBoundedBytes(input: InputStream, remainingBudget: Long): ByteArray {
        val maxRead = minOf(MAX_SINGLE_ENTRY_BYTES, remainingBudget.coerceAtLeast(0L))
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        val out = ByteArrayOutputStream()
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            total += read
            if (total > maxRead) {
                throw IllegalArgumentException("Document archive exceeds safe size limit.")
            }
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    private fun parseRelationships(relsBytes: ByteArray?): Map<String, String> {
        if (relsBytes == null) return emptyMap()
        val rels = mutableMapOf<String, String>()
        val parser = newSafeParser(relsBytes)
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG && parser.name == "Relationship") {
                val id = parser.getAttributeValue(null, "Id")
                val target = parser.getAttributeValue(null, "Target")
                if (!id.isNullOrBlank() && !target.isNullOrBlank()) {
                    rels[id] = normalizeMediaTarget(target)
                }
            }
            parser.next()
        }
        return rels
    }

    private fun normalizeMediaTarget(target: String): String {
        val trimmed = target.trimStart('/')
        return if (trimmed.startsWith("word/")) trimmed else "word/${trimmed.removePrefix("./")}"
    }

    private fun parseDocumentXml(
        xmlBytes: ByteArray,
        rels: Map<String, String>,
        entries: Map<String, ByteArray>
    ): List<DocxBlock> {
        val blocks = mutableListOf<DocxBlock>()
        val parser = newSafeParser(xmlBytes)
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "p" -> blocks.addAll(readParagraphBlocks(parser, rels, entries))
                    "tbl" -> blocks.add(readTableBlock(parser))
                }
            }
            parser.next()
        }
        return blocks
    }

    private fun readParagraphBlocks(
        parser: XmlPullParser,
        rels: Map<String, String>,
        entries: Map<String, ByteArray>
    ): List<DocxBlock> {
        val emitted = mutableListOf<DocxBlock>()
        val builder = SpannableStringBuilder()
        val imageRefs = mutableListOf<String>()
        var style = ParagraphStyle()

        while (true) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "pPr" -> style = readParagraphProperties(parser)
                    "r" -> readRun(parser, builder, imageRefs, style.isHeading)
                    "hyperlink" -> readHyperlink(parser, builder, imageRefs, style.isHeading)
                    else -> skipElement(parser)
                }
                XmlPullParser.END_TAG -> if (parser.name == "p") break
                XmlPullParser.END_DOCUMENT -> break
            }
        }

        if (style.isListItem && builder.isNotBlank()) {
            builder.insert(0, "• ")
        }
        if (builder.isNotEmpty()) {
            emitted.add(
                DocxBlock.Paragraph(
                    text = builder,
                    textSizeSp = style.textSizeSp,
                    alignment = style.alignment,
                    spaceAfterPt = style.spaceAfterPt,
                    isListItem = style.isListItem
                )
            )
        }
        imageRefs.forEach { relId ->
            val target = rels[relId]
            val bytes = target?.let { entries[it] }
            if (bytes != null) {
                emitted.add(DocxBlock.Image(bytes))
            }
        }
        return emitted
    }

    private fun readParagraphProperties(parser: XmlPullParser): ParagraphStyle {
        var style = ParagraphStyle()
        while (true) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> style = applyParagraphPropertyTag(parser, style)
                XmlPullParser.END_TAG -> if (parser.name == "pPr") return style
                XmlPullParser.END_DOCUMENT -> return style
            }
        }
    }

    private fun applyParagraphPropertyTag(
        parser: XmlPullParser,
        current: ParagraphStyle
    ): ParagraphStyle {
        return when (parser.name) {
            "pStyle" -> {
                val styleVal = readXmlValAttr(parser).orEmpty()
                skipElement(parser)
                applyHeadingStyle(styleVal, current)
            }
            "jc" -> {
                val jcVal = readXmlValAttr(parser)
                skipElement(parser)
                current.copy(alignment = mapAlignment(jcVal))
            }
            "numPr" -> {
                skipElement(parser)
                current.copy(isListItem = true, spaceAfterPt = 4f)
            }
            else -> {
                skipElement(parser)
                current
            }
        }
    }

    private fun applyHeadingStyle(styleId: String, current: ParagraphStyle): ParagraphStyle {
        val lower = styleId.lowercase()
        return when {
            lower == "title" || lower.contains("heading1") || lower == "h1" ->
                current.copy(textSizeSp = 19f, isHeading = true, spaceAfterPt = 10f)
            lower.contains("heading2") || lower == "h2" ->
                current.copy(textSizeSp = 16f, isHeading = true, spaceAfterPt = 8f)
            lower.contains("heading3") || lower == "h3" ->
                current.copy(textSizeSp = 13.5f, isHeading = true, spaceAfterPt = 7f)
            else -> current
        }
    }

    private fun mapAlignment(value: String?): Layout.Alignment {
        return when (value?.lowercase()) {
            "center" -> Layout.Alignment.ALIGN_CENTER
            "right", "end" -> Layout.Alignment.ALIGN_OPPOSITE
            else -> Layout.Alignment.ALIGN_NORMAL
        }
    }

    private fun readHyperlink(
        parser: XmlPullParser,
        builder: SpannableStringBuilder,
        imageRefs: MutableList<String>,
        forceBold: Boolean
    ) {
        while (true) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> if (parser.name == "r") {
                    readRun(parser, builder, imageRefs, forceBold)
                } else {
                    skipElement(parser)
                }
                XmlPullParser.END_TAG -> if (parser.name == "hyperlink") return
                XmlPullParser.END_DOCUMENT -> return
            }
        }
    }

    private fun readRun(
        parser: XmlPullParser,
        builder: SpannableStringBuilder,
        imageRefs: MutableList<String>,
        forceBold: Boolean
    ) {
        var style = RunStyle(bold = forceBold)
        val runText = StringBuilder()

        while (true) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "rPr" -> style = readRunProperties(parser, forceBold)
                    "t" -> runText.append(parser.nextText())
                    "tab" -> {
                        skipElement(parser)
                        runText.append("    ")
                    }
                    "br", "cr" -> {
                        skipElement(parser)
                        runText.append("\n")
                    }
                    "drawing", "pict" -> collectImageRelIds(parser, parser.name, imageRefs)
                    else -> skipElement(parser)
                }
                XmlPullParser.END_TAG -> if (parser.name == "r") break
                XmlPullParser.END_DOCUMENT -> break
            }
        }

        if (runText.isNotEmpty()) {
            val start = builder.length
            builder.append(runText)
            applyRunSpans(builder, start, builder.length, style)
        }
    }

    private fun readRunProperties(parser: XmlPullParser, forceBold: Boolean): RunStyle {
        var style = RunStyle(bold = forceBold)
        while (true) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> style = applyRunPropertyTag(parser, style)
                XmlPullParser.END_TAG -> if (parser.name == "rPr") return style
                XmlPullParser.END_DOCUMENT -> return style
            }
        }
    }

    private fun applyRunPropertyTag(parser: XmlPullParser, current: RunStyle): RunStyle {
        val tagName = parser.name
        val valAttr = readXmlValAttr(parser)
        skipElement(parser)
        val enabled = valAttr == null || (valAttr != "0" && valAttr != "false" && valAttr != "none")
        return when (tagName) {
            "b" -> current.copy(bold = current.bold || enabled)
            "i" -> current.copy(italic = enabled)
            "u" -> current.copy(underline = enabled)
            "strike", "dstrike" -> current.copy(strikethrough = enabled)
            "sz" -> {
                val halfPoints = valAttr?.toFloatOrNull()
                if (halfPoints != null && halfPoints > 0f) {
                    val scale = ((halfPoints / 2f) / DEFAULT_BODY_SIZE_SP).coerceIn(0.75f, 2.2f)
                    current.copy(sizeScale = scale)
                } else {
                    current
                }
            }
            "color" -> current.copy(colorInt = parseHexColorOrNull(valAttr))
            else -> current
        }
    }

    private fun parseHexColorOrNull(raw: String?): Int? {
        if (raw.isNullOrBlank() || raw.equals("auto", ignoreCase = true)) return null
        val cleaned = raw.removePrefix("#")
        if (cleaned.length != 6) return null
        val parsed = cleaned.toIntOrNull(16) ?: return null
        return Color.rgb((parsed shr 16) and 0xFF, (parsed shr 8) and 0xFF, parsed and 0xFF)
    }

    private fun applyRunSpans(
        builder: SpannableStringBuilder,
        start: Int,
        end: Int,
        style: RunStyle
    ) {
        val typefaceStyle = when {
            style.bold && style.italic -> Typeface.BOLD_ITALIC
            style.bold -> Typeface.BOLD
            style.italic -> Typeface.ITALIC
            else -> null
        }
        if (typefaceStyle != null) {
            builder.setSpan(StyleSpan(typefaceStyle), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        if (style.underline) {
            builder.setSpan(UnderlineSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        if (style.strikethrough) {
            builder.setSpan(StrikethroughSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        if (style.sizeScale != 1f) {
            builder.setSpan(
                RelativeSizeSpan(style.sizeScale),
                start,
                end,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        if (style.colorInt != null) {
            builder.setSpan(
                ForegroundColorSpan(style.colorInt),
                start,
                end,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
    }

    private fun collectImageRelIds(
        parser: XmlPullParser,
        endTag: String,
        imageRefs: MutableList<String>
    ) {
        while (true) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> if (parser.name == "blip" || parser.name == "imagedata") {
                    val embedId = findRelationshipAttr(parser)
                    if (!embedId.isNullOrBlank()) {
                        imageRefs.add(embedId)
                    }
                }
                XmlPullParser.END_TAG -> if (parser.name == endTag) return
                XmlPullParser.END_DOCUMENT -> return
            }
        }
    }

    private fun findRelationshipAttr(parser: XmlPullParser): String? {
        for (i in 0 until parser.attributeCount) {
            val attrName = parser.getAttributeName(i)
            if (attrName == "embed" || attrName == "id" || attrName == "r:embed" || attrName == "r:id") {
                return parser.getAttributeValue(i)
            }
        }
        return null
    }

    private fun readTableBlock(parser: XmlPullParser): DocxBlock.Table {
        val rows = mutableListOf<List<String>>()
        while (true) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "tr" -> rows.add(readTableRow(parser))
                    else -> skipElement(parser)
                }
                XmlPullParser.END_TAG -> if (parser.name == "tbl") return DocxBlock.Table(rows)
                XmlPullParser.END_DOCUMENT -> return DocxBlock.Table(rows)
            }
        }
    }

    private fun readTableRow(parser: XmlPullParser): List<String> {
        val cells = mutableListOf<String>()
        while (true) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "tc" -> cells.add(readTableCellText(parser))
                    else -> skipElement(parser)
                }
                XmlPullParser.END_TAG -> if (parser.name == "tr") return cells
                XmlPullParser.END_DOCUMENT -> return cells
            }
        }
    }

    private fun readTableCellText(parser: XmlPullParser): String {
        val cellText = StringBuilder()
        while (true) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "t" -> cellText.append(parser.nextText())
                    "br", "cr" -> {
                        skipElement(parser)
                        cellText.append("\n")
                    }
                    "tcPr" -> skipElement(parser)
                }
                XmlPullParser.END_TAG -> when (parser.name) {
                    "p" -> if (cellText.isNotEmpty() && !cellText.endsWith("\n")) cellText.append("\n")
                    "tc" -> return cellText.toString().trim()
                }
                XmlPullParser.END_DOCUMENT -> return cellText.toString().trim()
            }
        }
    }

    private fun readXmlValAttr(parser: XmlPullParser): String? {
        for (i in 0 until parser.attributeCount) {
            val attrName = parser.getAttributeName(i)
            if (attrName == "val" || attrName == "w:val") {
                return parser.getAttributeValue(i)
            }
        }
        return null
    }

    private fun skipElement(parser: XmlPullParser) {
        var depth = 1
        while (depth > 0) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> depth += 1
                XmlPullParser.END_TAG -> depth -= 1
                XmlPullParser.END_DOCUMENT -> return
            }
        }
    }

    private fun newSafeParser(bytes: ByteArray): XmlPullParser {
        val factory = XmlPullParserFactory.newInstance().apply {
            isNamespaceAware = true
            try {
                setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false)
            } catch (_: Exception) {
                // Not all pull parser implementations support explicit docdecl toggling.
            }
        }
        return factory.newPullParser().apply {
            setInput(ByteArrayInputStream(bytes), "UTF-8")
        }
    }

    private fun decodeSampledBytes(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = PdfConverter.calculateInSampleSize(
                bounds.outWidth,
                bounds.outHeight,
                MAX_EMBEDDED_IMAGE_DIM,
                MAX_EMBEDDED_IMAGE_DIM
            )
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions)
    }
}
