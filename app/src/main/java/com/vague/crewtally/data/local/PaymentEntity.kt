package com.vague.crewtally.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

/**
 * A payment made to a clerk against one project's books (per-project settlement — LOCKED
 * decision #3). A payment may exceed the outstanding balance; the surplus reads as an advance
 * (LOCKED decision #7). There is no "paid in full" flag — that is derived when a payment
 * equals the outstanding balance.
 *
 * onDelete = RESTRICT on both links (money-bearing): a project or clerk with payments on the
 * books cannot be deleted.
 */
@Entity(
    tableName = "payments",
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
        Index("projectId"),
        Index("clerkId"),
    ],
)
data class PaymentEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val clerkId: String,
    val date: LocalDate,
    /** Payment amount in MINOR units. Always positive; direction is fixed (clerk is paid). */
    val amount: Long,
    val note: String = "",
)
