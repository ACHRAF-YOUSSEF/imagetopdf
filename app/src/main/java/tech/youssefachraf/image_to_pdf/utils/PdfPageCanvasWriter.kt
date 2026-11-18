package tech.youssefachraf.image_to_pdf.utils

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.graphics.withSave
import androidx.core.graphics.withTranslation
import kotlin.math.min

/**
 * Single-pass paginated writer backed by Android's [PdfDocument] for text, styled spans, tables, and images.
 */
internal class PdfPageCanvasWriter(
    private val pageWidth: Int,
    private val pageHeight: Int,
    private val margin: Float = DEFAULT_MARGIN,
) : AutoCloseable {

    val pdfDocument = PdfDocument()
    val contentWidth: Int = (pageWidth - margin * 2f).toInt().coerceAtLeast(1)

    private val bottomLimit: Float = pageHeight - margin - FOOTER_RESERVE
    private val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(100, 116, 139)
        textSize = 9.5f
        textAlign = Paint.Align.RIGHT
    }

    private var currentPageNumber = 0
    private var currentPage: PdfDocument.Page? = null
    private var currentY = margin

    fun ensurePageStarted(): Canvas {
        val active = currentPage
        if (active != null) return active.canvas
        return startNextPage()
    }

    fun startNextPage(): Canvas {
        finishCurrentPageIfNeeded()
        currentPageNumber += 1
        val info = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, currentPageNumber).create()
        val page = pdfDocument.startPage(info)
        page.canvas.drawColor(Color.WHITE)
        currentPage = page
        currentY = margin
        return page.canvas
    }

    fun drawTextBlock(
        text: CharSequence,
        paint: TextPaint,
        alignment: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL,
        spaceAfterPt: Float = 8f,
        leftIndentPt: Float = 0f,
    ) {
        if (text.isEmpty()) {
            currentY += spaceAfterPt
            return
        }
        val availableWidth = (contentWidth - leftIndentPt).toInt().coerceAtLeast(1)
        val layout = StaticLayout.Builder
            .obtain(text, 0, text.length, paint, availableWidth)
            .setAlignment(alignment)
            .setLineSpacing(2f, 1.15f)
            .setIncludePad(false)
            .build()

        var lineStartIndex = 0
        while (lineStartIndex < layout.lineCount) {
            ensurePageStarted()
            val maxSliceHeight = (bottomLimit - currentY).coerceAtLeast(0f)
            val sliceEndIndex = findFittingEndLine(layout, lineStartIndex, maxSliceHeight)

            if (sliceEndIndex <= lineStartIndex) {
                startNextPage()
                continue
            }

            val canvas = ensurePageStarted()
            val sliceTop = layout.getLineTop(lineStartIndex).toFloat()
            val sliceBottom = layout.getLineBottom(sliceEndIndex - 1).toFloat()
            val sliceHeight = (sliceBottom - sliceTop).coerceAtLeast(0f)

            canvas.withSave {
                canvas.translate(margin + leftIndentPt, currentY - sliceTop)
                canvas.clipRect(0f, sliceTop, availableWidth.toFloat(), sliceBottom)
                layout.draw(canvas)
            }
            currentY += sliceHeight
            lineStartIndex = sliceEndIndex

            if (lineStartIndex < layout.lineCount) {
                startNextPage()
            }
        }
        currentY += spaceAfterPt
    }

    fun drawBitmapBlock(bitmap: Bitmap, spaceAfterPt: Float = 12f) {
        if (bitmap.width <= 0 || bitmap.height <= 0) return
        ensurePageStarted()

        val maxBoxHeight = (bottomLimit - margin).coerceAtLeast(1f)
        val naturalScale = min(
            contentWidth.toFloat() / bitmap.width.toFloat(),
            maxBoxHeight / bitmap.height.toFloat()
        ).coerceAtMost(1f)
        val drawWidth = bitmap.width * naturalScale
        val drawHeight = bitmap.height * naturalScale

        if (currentY + drawHeight > bottomLimit && currentY > margin) {
            startNextPage()
        }

        val canvas = ensurePageStarted()
        val left = margin + (contentWidth - drawWidth) / 2f
        val rect = RectF(left, currentY, left + drawWidth, currentY + drawHeight)
        canvas.drawBitmap(bitmap, null, rect, Paint(Paint.FILTER_BITMAP_FLAG))
        currentY += drawHeight + spaceAfterPt
    }

    fun drawTableBlock(
        rows: List<List<String>>,
        cellPaint: TextPaint,
        headerPaint: TextPaint,
    ) {
        if (rows.isEmpty()) return
        val colCount = rows.maxOf { it.size }.coerceAtLeast(1)
        val colWidth = contentWidth.toFloat() / colCount.toFloat()
        val cellTextWidth = (colWidth - TABLE_CELL_PADDING * 2f).toInt().coerceAtLeast(1)
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(203, 213, 225)
            style = Paint.Style.STROKE
            strokeWidth = 0.8f
        }
        val headerBgPaint = Paint().apply {
            color = Color.rgb(241, 245, 249)
            style = Paint.Style.FILL
        }

        rows.forEachIndexed { rowIndex, row ->
            val paintToUse = if (rowIndex == 0) headerPaint else cellPaint
            val cellLayouts = (0 until colCount).map { colIndex ->
                val value = row.getOrNull(colIndex).orEmpty()
                StaticLayout.Builder
                    .obtain(value, 0, value.length, paintToUse, cellTextWidth)
                    .setLineSpacing(1f, 1.1f)
                    .setIncludePad(false)
                    .build()
            }
            val rowHeight = (cellLayouts.maxOfOrNull { it.height } ?: 14) + TABLE_CELL_PADDING * 2f

            if (currentY + rowHeight > bottomLimit && currentY > margin) {
                startNextPage()
            }

            val canvas = ensurePageStarted()
            drawTableRow(
                canvas = canvas,
                rowIndex = rowIndex,
                colWidth = colWidth,
                rowHeight = rowHeight,
                cellLayouts = cellLayouts,
                borderPaint = borderPaint,
                headerBgPaint = headerBgPaint
            )
            currentY += rowHeight
        }
        currentY += 10f
    }

    private fun drawTableRow(
        canvas: Canvas,
        rowIndex: Int,
        colWidth: Float,
        rowHeight: Float,
        cellLayouts: List<StaticLayout>,
        borderPaint: Paint,
        headerBgPaint: Paint,
    ) {
        cellLayouts.forEachIndexed { colIndex, cellLayout ->
            val left = margin + colIndex * colWidth
            val rect = RectF(left, currentY, left + colWidth, currentY + rowHeight)
            if (rowIndex == 0) {
                canvas.drawRect(rect, headerBgPaint)
            }
            canvas.drawRect(rect, borderPaint)
            canvas.withTranslation(left + TABLE_CELL_PADDING, currentY + TABLE_CELL_PADDING) {
                cellLayout.draw(this)
            }
        }
    }

    fun finish(): PdfDocument {
        if (currentPageNumber == 0) {
            startNextPage()
        }
        finishCurrentPageIfNeeded()
        return pdfDocument
    }

    override fun close() {
        pdfDocument.close()
    }

    private fun finishCurrentPageIfNeeded() {
        val page = currentPage ?: return
        page.canvas.drawText(
            "Page $currentPageNumber",
            pageWidth - margin,
            pageHeight - margin * 0.45f,
            footerPaint
        )
        pdfDocument.finishPage(page)
        currentPage = null
    }

    private fun findFittingEndLine(
        layout: StaticLayout,
        startLine: Int,
        availableHeight: Float
    ): Int {
        val baseTop = layout.getLineTop(startLine).toFloat()
        var endLine = startLine
        while (endLine < layout.lineCount) {
            val lineBottom = layout.getLineBottom(endLine).toFloat() - baseTop
            if (lineBottom > availableHeight && endLine > startLine) {
                break
            }
            if (lineBottom > availableHeight) {
                return startLine
            }
            endLine += 1
        }
        return endLine
    }

    companion object {
        private const val DEFAULT_MARGIN = 44f
        private const val FOOTER_RESERVE = 24f
        private const val TABLE_CELL_PADDING = 6f
    }
}
