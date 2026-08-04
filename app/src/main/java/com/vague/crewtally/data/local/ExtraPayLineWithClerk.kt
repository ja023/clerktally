package com.vague.crewtally.data.local

import androidx.room.Embedded
import java.time.LocalDate

/**
 * An extra-pay line joined with its carrier attendance entry's date, project, and clerk — the
 * v1.1 project statement and extended company statement's whole-book extras feed (mirrors
 * [ExtraPayLineWithDate], which only carries the date because its caller already knows the
 * project+clerk it asked for; these two new reports need the project/clerk on the row itself
 * since they group by clerk across the whole project or whole company).
 */
data class ExtraPayLineWithClerk(
    @Embedded val line: ExtraPayLineEntity,
    val date: LocalDate,
    val projectId: String,
    val clerkId: String,
)
