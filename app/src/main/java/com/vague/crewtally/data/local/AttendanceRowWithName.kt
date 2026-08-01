package com.vague.crewtally.data.local

import androidx.room.Embedded

/**
 * One attendance row for the day screen: the entry itself plus the clerk's name, pre-joined
 * by SQL. The join is what lets the day screen render a day-only walk-in (a clerk with an
 * attendance row but NO roster row) by name without a second per-row lookup.
 */
data class AttendanceRowWithName(
    @Embedded val entry: AttendanceEntryEntity,
    val clerkName: String,
)
