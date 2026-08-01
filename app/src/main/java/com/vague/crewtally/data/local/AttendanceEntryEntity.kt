package com.vague.crewtally.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

/**
 * One clerk's attendance on one project on one calendar day. Full-day only — [present] is a
 * plain boolean, no half-days or multipliers (LOCKED decision #6).
 *
 * [rateSnapshot] is the daily rate COPIED from the roster at save time (LOCKED data model):
 * editing a clerk's rate mid-project never rewrites past attendance, so historical pay stays
 * exactly what it was on the day. One row per (project, clerk, date) — the unique index below.
 *
 * onDelete = RESTRICT on both links (money-bearing). The (projectId, clerkId, date) index has
 * projectId as its prefix, satisfying the project foreign key; clerkId gets its own index.
 */
@Entity(
    tableName = "attendance_entries",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = ClerkEntity::class,
            parentColumns = ["id"],
            childColumns = ["clerkId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["projectId", "clerkId", "date"], unique = true),
        Index("clerkId"),
    ],
)
data class AttendanceEntryEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val clerkId: String,
    val date: LocalDate,
    val present: Boolean,
    /** Daily rate in MINOR units, snapshotted from the roster when this row was saved. */
    val rateSnapshot: Long,
)
