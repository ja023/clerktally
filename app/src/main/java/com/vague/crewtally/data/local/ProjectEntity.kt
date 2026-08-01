package com.vague.crewtally.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

/**
 * A project belonging to one company. Its payment books close independently of every other
 * project (per-project settlement — LOCKED decision #3).
 *
 * `currency` is cosmetic only (LOCKED decision #2): an ISO 4217 code chosen per project,
 * used to render a symbol. There is no conversion math anywhere.
 *
 * onDelete = RESTRICT on the company link: a company that still has projects (and therefore
 * attendance and money behind them) cannot be deleted out from under them.
 */
@Entity(
    tableName = "projects",
    foreignKeys = [
        ForeignKey(
            entity = CompanyEntity::class,
            parentColumns = ["id"],
            childColumns = ["companyId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("companyId")],
)
data class ProjectEntity(
    @PrimaryKey val id: String,
    val companyId: String,
    val name: String,
    val location: String = "",
    val startDate: LocalDate,
    val endDate: LocalDate? = null,
    val status: ProjectStatus = ProjectStatus.ACTIVE,
    /** ISO 4217 currency code (e.g. "USD"). Cosmetic — drives the symbol only. */
    val currency: String,
)
