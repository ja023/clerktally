package com.vague.crewtally.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A company the supervisor runs projects for. String UUID primary key (stable across
 * backup export/import, which a later phase adds). `archived` hides a company without
 * deleting its history.
 */
@Entity(tableName = "companies")
data class CompanyEntity(
    @PrimaryKey val id: String,
    val name: String,
    val contact: String = "",
    val notes: String = "",
    val archived: Boolean = false,
)
