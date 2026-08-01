package com.vague.crewtally.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A clerk's assignment to a project, carrying the agreed [dailyRate]. One row per
 * (project, clerk) pair — enforced by the unique index below.
 *
 * onDelete = RESTRICT on both links: a roster row is money-bearing (it sets the rate a
 * clerk is paid), so neither the project nor the clerk can be deleted while it exists.
 * The (projectId, clerkId) index has projectId as its prefix, which also satisfies the
 * project foreign key; clerkId gets its own index for its foreign key.
 */
@Entity(
    tableName = "roster_entries",
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
        Index(value = ["projectId", "clerkId"], unique = true),
        Index("clerkId"),
    ],
)
data class RosterEntryEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val clerkId: String,
    /** Daily rate in MINOR units (e.g. cents). Money is never a floating-point value. */
    val dailyRate: Long,
)
