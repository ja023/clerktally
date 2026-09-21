package com.vague.crewtally.report

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure header-placement math for the branded PDF page header (see [PdfReportRenderer]'s KDoc). */
class PdfHeaderLayoutTest {

    private val tolerance = 0.01f

    @Test
    fun `mark prints at ten millimetres square`() {
        // 10mm = 10 / 25.4 inch * 72 points
        assertEquals(28.346f, PdfHeaderLayout.MARK_SIZE_PT, tolerance)
    }

    @Test
    fun `header text starts clear of the mark`() {
        val left = PdfHeaderLayout.headerTextLeftPt(marginPt = 40f)
        assertEquals(40f + PdfHeaderLayout.MARK_SIZE_PT + PdfHeaderLayout.MARK_GAP_PT, left, tolerance)
        assertTrue("text would overlap the mark", left > 40f + PdfHeaderLayout.MARK_SIZE_PT)
    }

    @Test
    fun `content starts below the last header baseline when the text is the taller side`() {
        // The shipped A4 header: 40pt margin, title baseline 56, identity baseline 73, 12pt gap.
        val top = PdfHeaderLayout.contentTopPt(marginPt = 40f, lastHeaderBaselinePt = 73f, headerGapPt = 12f)
        assertEquals(85f, top, tolerance)
    }

    @Test
    fun `content clears the mark when the header text is shorter than the mark`() {
        // A deliberately short header: the mark, not the text, sets where the body may start.
        val top = PdfHeaderLayout.contentTopPt(marginPt = 40f, lastHeaderBaselinePt = 50f, headerGapPt = 12f)
        assertEquals(40f + PdfHeaderLayout.MARK_SIZE_PT + 12f, top, tolerance)
        assertTrue("body line would collide with the mark", top > 40f + PdfHeaderLayout.MARK_SIZE_PT)
    }

    @Test
    fun `adding the mark did not change how many body lines fit an A4 page`() {
        // Guards the v1.1 pagination: the 10mm mark tucks inside the existing two-line header,
        // so no statement gains a page just because the header is now branded.
        val top = PdfHeaderLayout.contentTopPt(marginPt = 40f, lastHeaderBaselinePt = 73f, headerGapPt = 12f)
        val linesPerPage = ReportPaginator.computeLinesPerPage(
            pageHeightPt = 842f,
            topMarginPt = top,
            bottomMarginPt = 40f + 28f,
            lineHeightPt = 16f,
        )
        assertEquals(43, linesPerPage) // (842 - 85 - 68) / 16 = 43.06
    }
}
