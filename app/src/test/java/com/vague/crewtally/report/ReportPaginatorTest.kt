package com.vague.crewtally.report

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** Pure pagination math — the part of the PDF renderer that IS unit-testable (see [PdfReportRenderer]'s KDoc). */
class ReportPaginatorTest {

    @Test
    fun `computeLinesPerPage fits the usable height divided by line height`() {
        // 842pt page, 100pt reserved top+bottom, 16pt lines -> 742 / 16 = 46.375 -> 46
        val linesPerPage = ReportPaginator.computeLinesPerPage(
            pageHeightPt = 842f,
            topMarginPt = 60f,
            bottomMarginPt = 40f,
            lineHeightPt = 16f,
        )
        assertEquals(46, linesPerPage)
    }

    @Test
    fun `computeLinesPerPage never returns less than 1 even with a degenerate margin`() {
        val linesPerPage = ReportPaginator.computeLinesPerPage(
            pageHeightPt = 100f,
            topMarginPt = 90f,
            bottomMarginPt = 90f, // margins alone already exceed the page
            lineHeightPt = 16f,
        )
        assertEquals(1, linesPerPage)
    }

    @Test
    fun `paginate splits an exact multiple evenly`() {
        val lines = (1..10).map { "line$it" }
        val pages = ReportPaginator.paginate(lines, linesPerPage = 5)
        assertEquals(2, pages.size)
        assertEquals(5, pages[0].size)
        assertEquals(5, pages[1].size)
        assertEquals("line1", pages[0].first())
        assertEquals("line10", pages[1].last())
    }

    @Test
    fun `paginate leaves a partial remainder on its own trailing page`() {
        val lines = (1..7).map { "line$it" }
        val pages = ReportPaginator.paginate(lines, linesPerPage = 5)
        assertEquals(2, pages.size)
        assertEquals(5, pages[0].size)
        assertEquals(2, pages[1].size)
    }

    @Test
    fun `paginate of an empty list yields a single empty page, never zero pages`() {
        val pages = ReportPaginator.paginate(emptyList(), linesPerPage = 10)
        assertEquals(1, pages.size)
        assertEquals(0, pages[0].size)
    }

    @Test
    fun `paginate rejects a non-positive linesPerPage`() {
        assertThrows(IllegalArgumentException::class.java) {
            ReportPaginator.paginate(listOf("a"), linesPerPage = 0)
        }
    }
}
