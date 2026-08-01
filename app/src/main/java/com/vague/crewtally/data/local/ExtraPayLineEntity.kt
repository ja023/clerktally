package com.vague.crewtally.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * An adjustment attached to one attendance entry: a preset (Lunch / Transport / Bonus) or a
 * custom [label], with a signed [amount]. A NEGATIVE amount is a deduction — damage, penalty,
 * or advance repayment (LOCKED decision #7).
 *
 * onDelete = CASCADE: an extra-pay line has no meaning without its attendance day, so it is
 * removed with it. This is the one CASCADE in the schema; every money relation to a project
 * or clerk is RESTRICT instead.
 */
@Entity(
    tableName = "extra_pay_lines",
    foreignKeys = [
        ForeignKey(
            entity = AttendanceEntryEntity::class,
            parentColumns = ["id"],
            childColumns = ["attendanceEntryId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("attendanceEntryId")],
)
data class ExtraPayLineEntity(
    @PrimaryKey val id: String,
    val attendanceEntryId: String,
    val label: String,
    /** SIGNED amount in MINOR units. Negative = deduction; positive = extra pay. */
    val amount: Long,
)
