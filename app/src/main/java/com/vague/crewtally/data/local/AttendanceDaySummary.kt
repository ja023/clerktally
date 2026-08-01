package com.vague.crewtally.data.local

import java.time.LocalDate

/**
 * One row of the project-detail attendance history: a single day's headline numbers. Present
 * count is how many clerks were marked present that day; [extrasTotal] is the signed sum of
 * every extra-pay line on that day (may be negative if deductions outweigh extras). Days with
 * no attendance rows at all never appear — a day exists here only once something was recorded.
 */
data class AttendanceDaySummary(
    val date: LocalDate,
    val presentCount: Int,
    /** Signed sum of the day's extra-pay lines, in MINOR units. Zero when there are none. */
    val extrasTotal: Long,
)
