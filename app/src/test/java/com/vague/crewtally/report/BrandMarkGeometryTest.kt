package com.vague.crewtally.report

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the brand mark's geometry and its scaling math — the part of the PDF header that IS
 * unit-testable (see [PdfReportRenderer]'s KDoc). These coordinates are duplicated in
 * `res/drawable/ic_brand_mark.xml` and `design/logo.svg`; if a shape moves in one place, the
 * numbers here should be updated deliberately, not silently.
 */
class BrandMarkGeometryTest {

    private val tolerance = 0.001f

    @Test
    fun `mark is drawn from four grey bars, four navy bars and one shaft`() {
        assertEquals(4, BrandMarkGeometry.frameRects.size)
        assertEquals(4, BrandMarkGeometry.arrowRects.size)
        assertEquals(4, BrandMarkGeometry.arrowShaft.size)
    }

    @Test
    fun `every shape stays inside the 1000 unit viewport`() {
        (BrandMarkGeometry.frameRects + BrandMarkGeometry.arrowRects).forEach { rect ->
            assertTrue(
                "rect $rect escapes the viewport",
                rect.left >= 0f && rect.top >= 0f &&
                    rect.right <= BrandMarkGeometry.VIEWPORT && rect.bottom <= BrandMarkGeometry.VIEWPORT,
            )
            assertTrue("rect $rect is empty or inverted", rect.width > 0f && rect.height > 0f)
        }
        BrandMarkGeometry.arrowShaft.forEach { point ->
            assertTrue(
                "point $point escapes the viewport",
                point.x in 0f..BrandMarkGeometry.VIEWPORT && point.y in 0f..BrandMarkGeometry.VIEWPORT,
            )
        }
    }

    @Test
    fun `frame is open at the top right corner`() {
        // The top bar stops short of the right edge and the right bar starts below the top edge:
        // that gap is what the navy arrow breaks in through.
        val topBar = BrandMarkGeometry.frameRects[2]
        val rightBar = BrandMarkGeometry.frameRects[3]
        assertTrue("top bar reaches the right edge", topBar.right < 940f)
        assertTrue("right bar reaches the top edge", rightBar.top > 126f)
    }

    @Test
    fun `colours are the sampled grey frame and navy arrow`() {
        assertEquals(0xFF6F7072.toInt(), BrandMarkGeometry.FRAME_COLOR)
        assertEquals(0xFF013B7A.toInt(), BrandMarkGeometry.ARROW_COLOR)
    }

    @Test
    fun `scaleRect maps the viewport onto the output square at the given origin`() {
        // A full-viewport rect scaled into a 100pt box at (40,40) must land on exactly that box.
        val fullViewport = BrandMarkGeometry.MarkRect(0f, 0f, 1000f, 1000f)
        val scaled = BrandMarkGeometry.scaleRect(fullViewport, originX = 40f, originY = 40f, size = 100f)
        assertEquals(40f, scaled.left, tolerance)
        assertEquals(40f, scaled.top, tolerance)
        assertEquals(140f, scaled.right, tolerance)
        assertEquals(140f, scaled.bottom, tolerance)
    }

    @Test
    fun `scalePoint places the shaft corner proportionally inside the output square`() {
        val scaled = BrandMarkGeometry.scalePoint(
            BrandMarkGeometry.MarkPoint(500f, 250f),
            originX = 10f,
            originY = 20f,
            size = 40f,
        )
        assertEquals(30f, scaled.x, tolerance) // 10 + 0.5 * 40
        assertEquals(30f, scaled.y, tolerance) // 20 + 0.25 * 40
    }

    @Test
    fun `every scaled shape stays within the output square`() {
        val origin = 40f
        val size = PdfHeaderLayout.MARK_SIZE_PT
        (BrandMarkGeometry.frameRects + BrandMarkGeometry.arrowRects).forEach { rect ->
            val scaled = BrandMarkGeometry.scaleRect(rect, origin, origin, size)
            assertTrue(
                "scaled rect $scaled escapes the ${size}pt box",
                scaled.left >= origin - tolerance && scaled.top >= origin - tolerance &&
                    scaled.right <= origin + size + tolerance && scaled.bottom <= origin + size + tolerance,
            )
        }
    }

    @Test
    fun `scaling to a non positive size is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            BrandMarkGeometry.scaleRect(BrandMarkGeometry.frameRects.first(), 0f, 0f, size = 0f)
        }
    }
}
