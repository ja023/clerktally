package com.vague.crewtally.data.local

import androidx.room.Embedded
import java.time.LocalDate

/**
 * An extra-pay line joined with the calendar date of its carrier attendance entry. The line
 * itself has no date column (the date lives on [AttendanceEntryEntity]); the clerk balance
 * ledger needs each line's day to show and order it, so the join surfaces it here rather than
 * forcing a second lookup per line.
 */
data class ExtraPayLineWithDate(
    @Embedded val line: ExtraPayLineEntity,
    val date: LocalDate,
)
