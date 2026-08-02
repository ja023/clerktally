package com.vague.crewtally.report

/**
 * Pure pagination math for the PDF renderer. Kept separate from
 * [com.vague.crewtally.report.PdfReportRenderer]'s actual `android.graphics.pdf.PdfDocument`
 * drawing calls specifically so it is unit-testable on the JVM — [PdfReportRenderer] itself
 * needs a device/Robolectric to exercise (noted gap), but the "how many lines fit, and how do
 * they split across pages" logic underneath it does not.
 */
object ReportPaginator {

    /**
     * How many body lines fit on one page given [pageHeightPt] (PDF points, 1/72 inch — A4 is
     * 842pt tall), [topMarginPt] + [bottomMarginPt] reserved for the header/footer, and
     * [lineHeightPt] per line. Always at least 1 (a degenerate margin/line-height configuration
     * must never produce a zero-progress paginator that loops forever).
     */
    fun computeLinesPerPage(
        pageHeightPt: Float,
        topMarginPt: Float,
        bottomMarginPt: Float,
        lineHeightPt: Float,
    ): Int {
        require(lineHeightPt > 0f) { "lineHeightPt must be positive; got $lineHeightPt" }
        val usableHeight = pageHeightPt - topMarginPt - bottomMarginPt
        val fitted = (usableHeight / lineHeightPt).toInt()
        return maxOf(1, fitted)
    }

    /**
     * Splits [lines] into pages of at most [linesPerPage] lines each, preserving order. An empty
     * input yields a single empty page (so the PDF renderer always draws at least a header page,
     * matching "never clip" — a report with no content still shows its title and totals lines,
     * which callers always include).
     */
    fun paginate(lines: List<String>, linesPerPage: Int): List<List<String>> {
        require(linesPerPage > 0) { "linesPerPage must be positive; got $linesPerPage" }
        if (lines.isEmpty()) return listOf(emptyList())
        return lines.chunked(linesPerPage)
    }
}
