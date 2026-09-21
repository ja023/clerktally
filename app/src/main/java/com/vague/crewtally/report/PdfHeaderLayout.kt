package com.vague.crewtally.report

/**
 * Where the brand mark and the two header text lines sit at the top of every PDF statement page
 * (LOCKED v1.2: "report headers: top of every PDF statement"). Split out of [PdfReportRenderer]
 * for the same reason as [ReportPaginator]: it is arithmetic, so it can be unit-tested on the
 * JVM, while the renderer's `android.graphics` calls cannot.
 *
 * All values are PDF points (1/72 inch), matching [PdfReportRenderer]'s page box.
 */
object PdfHeaderLayout {

    /** PDF points per millimetre: 72 points per inch, 25.4 mm per inch. */
    private const val POINTS_PER_MM = 72f / 25.4f

    /** The mark prints as a 10 mm square, large enough to read but never crowding the title. */
    const val MARK_SIZE_PT: Float = 10f * POINTS_PER_MM

    /** Clear space between the mark and the header text that runs beside it. */
    const val MARK_GAP_PT: Float = 10f

    /**
     * The left edge of the header text lines: the page margin, then the mark, then the gap. Body
     * lines keep the plain [marginPt] so the tabular report columns stay where v1.1 put them;
     * only the two header lines are pushed right, beside the mark.
     */
    fun headerTextLeftPt(marginPt: Float): Float = marginPt + MARK_SIZE_PT + MARK_GAP_PT

    /**
     * Where the body text may start: below BOTH the last header baseline and the bottom of the
     * mark, plus [headerGapPt]. Taking the max is what keeps a tall mark from ever colliding
     * with the first body line if the header text sizes are ever tuned down.
     */
    fun contentTopPt(marginPt: Float, lastHeaderBaselinePt: Float, headerGapPt: Float): Float =
        maxOf(lastHeaderBaselinePt, marginPt + MARK_SIZE_PT) + headerGapPt
}
