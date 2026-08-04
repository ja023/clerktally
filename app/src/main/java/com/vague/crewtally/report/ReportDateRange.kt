package com.vague.crewtally.report

import java.time.LocalDate

/**
 * Which preset the v1.1 range-selection UI is showing (LOCKED v1.1 decision): All time / This
 * month / Last month / Custom (two date pickers). Lives here (not the UI layer) so
 * [ReportDateRanges.resolve] — the preset-to-bounds math — stays pure and unit-testable.
 */
enum class ReportRangePreset { ALL_TIME, THIS_MONTH, LAST_MONTH, CUSTOM }

/**
 * An inclusive date-range filter for the v1.1 statements ([ProjectStatementBuilder] and the
 * extended [CompanyTotalsBuilder]). Both bounds null = "All time" (LOCKED default) — every date
 * is then admitted. Builders take this directly rather than a preset so the filtering math stays
 * pure Kotlin (LOCKED: "implement filtering in the pure builders... DAO queries may stay
 * unfiltered").
 */
data class ReportDateRange(val start: LocalDate?, val end: LocalDate?) {

    val isAllTime: Boolean get() = start == null && end == null

    /** Whether [date] falls inside this range — an unset bound never excludes on its own side. */
    fun contains(date: LocalDate): Boolean {
        if (start != null && date < start) return false
        if (end != null && date > end) return false
        return true
    }

    companion object {
        val ALL_TIME = ReportDateRange(start = null, end = null)
    }
}

/**
 * Resolves a [ReportRangePreset] into concrete [ReportDateRange] bounds anchored on [today] — a
 * plain parameter (never `LocalDate.now()` internally) so preset resolution is deterministic and
 * unit-testable across a month/year boundary without mocking the clock.
 */
object ReportDateRanges {

    fun resolve(
        preset: ReportRangePreset,
        today: LocalDate,
        customStart: LocalDate?,
        customEnd: LocalDate?,
    ): ReportDateRange = when (preset) {
        ReportRangePreset.ALL_TIME -> ReportDateRange.ALL_TIME
        ReportRangePreset.THIS_MONTH -> monthRange(today)
        ReportRangePreset.LAST_MONTH -> monthRange(today.minusMonths(1))
        ReportRangePreset.CUSTOM -> ReportDateRange(customStart, customEnd)
    }

    private fun monthRange(anchor: LocalDate): ReportDateRange =
        ReportDateRange(anchor.withDayOfMonth(1), anchor.withDayOfMonth(anchor.lengthOfMonth()))
}
