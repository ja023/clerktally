package com.vague.crewtally.report

/**
 * The CrewTally brand mark (LOCKED 2026-09-21) as plain numbers: the same rectangles and the
 * same arrowhead polygon as `res/drawable/ic_brand_mark.xml` and `design/logo.svg`, on the same
 * 1000 x 1000 unit square, with the same two sampled fills.
 *
 * The on-screen placements inflate the drawable (see `ui/components/CrewTallyBrandMark.kt`).
 * The PDF statement header cannot: [PdfReportRenderer] draws straight onto a
 * `android.graphics.Canvas`, and inflating a VectorDrawable would drag a `Context`, resource
 * inflation, and a density into what is otherwise pure geometry. So the mark is redrawn from
 * these coordinates instead, which keeps the numbers unit-testable on the JVM (see
 * `BrandMarkGeometryTest`) the way [ReportPaginator] keeps the pagination math testable.
 *
 * The mark is deliberately two-colour rather than a tint: grey frame plus navy arrow. Both read
 * as distinct mid/dark greys when a statement is printed in black and white.
 *
 * **Keep this file, `ic_brand_mark.xml` and `design/logo.svg` in sync.**
 */
object BrandMarkGeometry {

    /** Width and height of the unit square these coordinates are expressed in. */
    const val VIEWPORT = 1000f

    /** The open square frame, sampled from the source artwork. */
    const val FRAME_COLOR = 0xFF6F7072.toInt()

    /** The arrow breaking in through the frame's open corner, sampled from the source artwork. */
    const val ARROW_COLOR = 0xFF013B7A.toInt()

    /** An axis-aligned rectangle in mark units (or, once scaled, in output units). */
    data class MarkRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
        val width: Float get() = right - left
        val height: Float get() = bottom - top
    }

    /** A single corner of the arrowhead polygon, in mark units (or, once scaled, output units). */
    data class MarkPoint(val x: Float, val y: Float)

    /**
     * The grey frame: left edge, bottom edge, the top edge's left-hand stub, and the right
     * edge's lower stub. The two stubs are what leave the top-right corner open for the arrow.
     */
    val frameRects: List<MarkRect> = listOf(
        MarkRect(60f, 60f, 126f, 940f),
        MarkRect(60f, 874f, 940f, 940f),
        MarkRect(60f, 60f, 484f, 126f),
        MarkRect(874f, 518f, 940f, 940f),
    )

    /** The navy arrow's straight parts: the outer corner bracket, then the L-shaped arrowhead. */
    val arrowRects: List<MarkRect> = listOf(
        MarkRect(556f, 60f, 940f, 132f),
        MarkRect(868f, 60f, 940f, 446f),
        MarkRect(448f, 187f, 520f, 552f),
        MarkRect(448f, 480f, 815f, 552f),
    )

    /** The arrow's 45 degree shaft, running from the outer corner down into the arrowhead. */
    val arrowShaft: List<MarkPoint> = listOf(
        MarkPoint(503f, 548f),
        MarkPoint(936f, 115f),
        MarkPoint(885f, 64f),
        MarkPoint(452f, 497f),
    )

    /**
     * Maps one mark-unit rectangle onto an output square of [size] whose top-left corner sits at
     * ([originX], [originY]) — e.g. a 10 mm box in PDF points at the top-left of a page header.
     */
    fun scaleRect(rect: MarkRect, originX: Float, originY: Float, size: Float): MarkRect {
        val factor = scaleFactor(size)
        return MarkRect(
            left = originX + rect.left * factor,
            top = originY + rect.top * factor,
            right = originX + rect.right * factor,
            bottom = originY + rect.bottom * factor,
        )
    }

    /** Maps one mark-unit point onto the same output square as [scaleRect]. */
    fun scalePoint(point: MarkPoint, originX: Float, originY: Float, size: Float): MarkPoint {
        val factor = scaleFactor(size)
        return MarkPoint(x = originX + point.x * factor, y = originY + point.y * factor)
    }

    private fun scaleFactor(size: Float): Float {
        require(size > 0f) { "size must be positive; got $size" }
        return size / VIEWPORT
    }
}
