package com.vague.crewtally.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A company the supervisor runs projects for. String UUID primary key (stable across
 * backup export/import, which a later phase adds). `archived` hides a company without
 * deleting its history.
 *
 * `contactPerson` and `phone` replace Phase 0's placeholder `contact` field (renamed, not
 * added alongside it — nothing used the old field and v1 is still amendable since no APK
 * has been installed yet). They hold the point of contact at the company office, filled
 * either by hand or via the shared contact-search field.
 */
@Entity(tableName = "companies")
data class CompanyEntity(
    @PrimaryKey val id: String,
    val name: String,
    val contactPerson: String = "",
    val phone: String = "",
    val notes: String = "",
    val archived: Boolean = false,
)
