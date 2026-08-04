package com.vague.crewtally.report

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ReportDateRange.contains] and [ReportDateRanges.resolve]'s preset math (LOCKED v1.1: All
 * time / This month / Last month / Custom).
 */
class ReportDateRangeTest {

    @Test
    fun `All time has null bounds and contains every date`() {
        val range = ReportDateRange.ALL_TIME

        assertTrue(range.isAllTime)
        assertTrue(range.contains(LocalDate.of(2000, 1, 1)))
        assertTrue(range.contains(LocalDate.of(2099, 12, 31)))
    }

    @Test
    fun `a bounded range excludes dates outside the inclusive bounds`() {
        val range = ReportDateRange(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31))

        assertFalse(range.contains(LocalDate.of(2026, 2, 28)))
        assertTrue(range.contains(LocalDate.of(2026, 3, 1)))
        assertTrue(range.contains(LocalDate.of(2026, 3, 31)))
        assertFalse(range.contains(LocalDate.of(2026, 4, 1)))
    }

    @Test
    fun `THIS_MONTH resolves to the first and last day of today's month`() {
        val today = LocalDate.of(2026, 2, 15)

        val range = ReportDateRanges.resolve(ReportRangePreset.THIS_MONTH, today, null, null)

        assertEquals(LocalDate.of(2026, 2, 1), range.start)
        assertEquals(LocalDate.of(2026, 2, 28), range.end)
    }

    @Test
    fun `LAST_MONTH resolves to the previous calendar month, correctly crossing a year boundary`() {
        val today = LocalDate.of(2026, 1, 15)

        val range = ReportDateRanges.resolve(ReportRangePreset.LAST_MONTH, today, null, null)

        assertEquals(LocalDate.of(2025, 12, 1), range.start)
        assertEquals(LocalDate.of(2025, 12, 31), range.end)
    }

    @Test
    fun `CUSTOM resolves to exactly the supplied bounds`() {
        val start = LocalDate.of(2026, 5, 10)
        val end = LocalDate.of(2026, 5, 20)

        val range = ReportDateRanges.resolve(ReportRangePreset.CUSTOM, LocalDate.of(2026, 6, 1), start, end)

        assertEquals(start, range.start)
        assertEquals(end, range.end)
    }

    @Test
    fun `ALL_TIME preset resolves to ReportDateRange ALL_TIME regardless of custom bounds`() {
        val range = ReportDateRanges.resolve(
            ReportRangePreset.ALL_TIME,
            LocalDate.of(2026, 6, 1),
            LocalDate.of(2020, 1, 1),
            LocalDate.of(2020, 1, 31),
        )

        assertTrue(range.isAllTime)
    }
}
