package com.vague.crewtally.report

import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

/**
 * Draws a report's [ReportLines] output into a simple, printable, multi-page A4 PDF using the
 * platform `android.graphics.pdf.PdfDocument` — no third-party PDF library (LOCKED environment
 * constraint). This class talks directly to `android.graphics`, so — like `ContactSearcher`'s
 * Robolectric gap noted in the project's review debt — it needs a device or Robolectric to
 * exercise directly and is NOT covered by the JVM unit test suite; [ReportPaginator], the pure
 * line-count math underneath it, is what carries the test coverage for this layer (see
 * `ReportPaginatorTest`).
 *
 * Body text runs at 12pt (LOCKED "large readable type ~12-14pt body"); the first line of the
 * input is drawn as a bold header repeated on every page, so a printed page 2 still reads who
 * and what the report is for without flipping back to page 1.
 */
object PdfReportRenderer {
    private const val PAGE_WIDTH_PT = 595 // A4 at 72dpi
    private const val PAGE_HEIGHT_PT = 842
    private const val MARGIN_PT = 40f
    private const val HEADER_TEXT_SIZE_PT = 16f
    private const val BODY_TEXT_SIZE_PT = 12f
    private const val FOOTER_TEXT_SIZE_PT = 10f
    private const val LINE_HEIGHT_PT = 16f
    private const val HEADER_GAP_PT = 12f
    private const val FOOTER_RESERVE_PT = 28f

    fun render(lines: List<String>, pageLabelTemplate: String, outputFile: File) {
        val headerText = lines.firstOrNull().orEmpty()
        val bodyLines = if (lines.isEmpty()) emptyList() else lines.drop(1)

        val headerPaint = Paint().apply {
            textSize = HEADER_TEXT_SIZE_PT
            isAntiAlias = true
            isFakeBoldText = true
        }
        val bodyPaint = Paint().apply {
            textSize = BODY_TEXT_SIZE_PT
            isAntiAlias = true
        }
        val footerPaint = Paint().apply {
            textSize = FOOTER_TEXT_SIZE_PT
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }

        val topMargin = MARGIN_PT + HEADER_TEXT_SIZE_PT + HEADER_GAP_PT
        val linesPerPage = ReportPaginator.computeLinesPerPage(
            pageHeightPt = PAGE_HEIGHT_PT.toFloat(),
            topMarginPt = topMargin,
            bottomMarginPt = MARGIN_PT + FOOTER_RESERVE_PT,
            lineHeightPt = LINE_HEIGHT_PT,
        )
        val pages = ReportPaginator.paginate(bodyLines, linesPerPage)

        val document = PdfDocument()
        pages.forEachIndexed { index, pageLines ->
            val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH_PT, PAGE_HEIGHT_PT, index + 1).create()
            val page = document.startPage(pageInfo)
            val canvas = page.canvas

            canvas.drawText(headerText, MARGIN_PT, MARGIN_PT + HEADER_TEXT_SIZE_PT, headerPaint)

            var y = topMargin + LINE_HEIGHT_PT
            pageLines.forEach { line ->
                canvas.drawText(line, MARGIN_PT, y, bodyPaint)
                y += LINE_HEIGHT_PT
            }

            val footerText = String.format(Locale.ROOT, pageLabelTemplate, index + 1, pages.size)
            canvas.drawText(footerText, PAGE_WIDTH_PT / 2f, PAGE_HEIGHT_PT - MARGIN_PT / 2f, footerPaint)

            document.finishPage(page)
        }

        FileOutputStream(outputFile).use { out -> document.writeTo(out) }
        document.close()
    }
}
