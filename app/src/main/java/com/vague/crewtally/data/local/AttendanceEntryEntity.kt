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
 *
 * [explicitlyMarked] distinguishes a REAL mark from a synthetic carrier row: extras may attach
 * to an Unmarked/Absent clerk (LOCKED), and the row that holds them is created with
 * present=false, explicitlyMarked=false purely to give the extras somewhere to live — it must
 * never masquerade as a deliberate Absent. Any Present/Absent tap sets it true. Deleting the
 * last extra line off a present=false, explicitlyMarked=false row deletes the row itself
 * (Phase 3 amendment (c)); a present=false row the user actually tapped Absent on
 * (explicitlyMarked=true) never auto-deletes this way.
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
    /** True for a real Present/Absent mark; false only for an extras-only carrier row. */
    val explicitlyMarked: Boolean = true,
)
