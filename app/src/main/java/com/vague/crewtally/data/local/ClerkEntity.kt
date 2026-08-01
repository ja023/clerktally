package com.vague.crewtally.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A clerk (crew member) the supervisor rosters onto projects. `active` retires a clerk
 * without deleting their attendance and payment history.
 */
@Entity(tableName = "clerks")
data class ClerkEntity(
    @PrimaryKey val id: String,
    val name: String,
    val phone: String = "",
    val notes: String = "",
    val active: Boolean = true,
)
