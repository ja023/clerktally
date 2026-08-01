package com.vague.crewtally.data.local

import androidx.room.Embedded

/**
 * One roster row for the project-detail roster section: the roster entry itself plus the
 * clerk's name, pre-joined by SQL so the detail screen doesn't need a second lookup per row.
 */
data class RosterRowSummary(
    @Embedded val entry: RosterEntryEntity,
    val clerkName: String,
)
