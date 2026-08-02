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
 * Body text runs at 12pt (LOCKED "large readable type ~12-14pt body"); [ReportLines]' first TWO
 * lines (the title, then the clerk/company/project or company identity line) are drawn as a
 * two-line header repeated on EVERY page — a printed page 2 still reads WHO and WHAT the report
 * is for without flipping back to page 1, not just the generic app-name title line.
 */
object PdfReportRenderer {
    private const val PAGE_WIDTH_PT = 595 // A4 at 72dpi
    private const val PAGE_HEIGHT_PT = 842
    private const val MARGIN_PT = 40f
    private const val TITLE_TEXT_SIZE_PT = 16f
    private const val IDENTITY_TEXT_SIZE_PT = 13f
    private const val BODY_TEXT_SIZE_PT = 12f
    private const val FOOTER_TEXT_SIZE_PT = 10f
    private const val LINE_HEIGHT_PT = 16f
    private const val HEADER_LINE_GAP_PT = 4f
    private const val HEADER_GAP_PT = 12f
    private const val FOOTER_RESERVE_PT = 28f

    fun render(lines: List<String>, pageLabelTemplate: String, outputFile: File) {
        val titleText = lines.getOrNull(0).orEmpty()
        val identityText = lines.getOrNull(1).orEmpty()
        val bodyLines = if (lines.size <= 2) emptyList() else lines.drop(2)

        val titlePaint = Paint().apply {
            textSize = TITLE_TEXT_SIZE_PT
            isAntiAlias = true
            isFakeBoldText = true
        }
        val identityPaint = Paint().apply {
            textSize = IDENTITY_TEXT_SIZE_PT
            isAntiAlias = true
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

        val titleBaselineY = MARGIN_PT + TITLE_TEXT_SIZE_PT
        val identityBaselineY = titleBaselineY + HEADER_LINE_GAP_PT + IDENTITY_TEXT_SIZE_PT
        val topMargin = identityBaselineY + HEADER_GAP_PT
        val linesPerPage = ReportPaginator.computeLinesPerPage(
            pageHeightPt = PAGE_HEIGHT_PT.toFloat(),
            topMarginPt = topMargin,
            bottomMarginPt = MARGIN_PT + FOOTER_RESERVE_PT,
            lineHeightPt = LINE_HEIGHT_PT,
        )
        val pages = ReportPaginator.paginate(bodyLines, linesPerPage)

        val document = PdfDocument()
        try {
            pages.forEachIndexed { index, pageLines ->
                val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH_PT, PAGE_HEIGHT_PT, index + 1).create()
                val page = document.startPage(pageInfo)
                val canvas = page.canvas

                canvas.drawText(titleText, MARGIN_PT, titleBaselineY, titlePaint)
                canvas.drawText(identityText, MARGIN_PT, identityBaselineY, identityPaint)

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
        } finally {
            document.close()
        }
    }
}
