package com.vague.crewtally.data.local

/**
 * The signed extras total for one attendance entry on the day screen, used to render each
 * clerk row's "extras" figure without loading every individual line. [total] is the sum of
 * that entry's extra-pay lines in MINOR units (may be negative).
 */
data class AttendanceExtraTotal(
    val attendanceEntryId: String,
    val total: Long,
)
