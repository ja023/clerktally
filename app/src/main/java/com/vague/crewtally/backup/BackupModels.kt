package com.vague.crewtally.backup

import kotlinx.serialization.Serializable

/**
 * Serializable mirrors of every Room entity, field-for-field, with dates as ISO-8601 strings
 * (matching [com.vague.crewtally.data.local.Converters]' on-disk convention) — entities
 * themselves stay Room-only (no `@Serializable`) rather than double-purposing them as the wire
 * format, so a future Room schema change and a future backup schema change are two independent
 * decisions instead of one annotation accidentally coupling them.
 */
@Serializable
data class CompanyDto(
    val id: String,
    val name: String,
    val contactPerson: String,
    val phone: String,
    val notes: String,
    val archived: Boolean,
)

@Serializable
data class ClerkDto(val id: String, val name: String, val phone: String, val notes: String, val active: Boolean)

@Serializable
data class ProjectDto(
    val id: String,
    val companyId: String,
    val name: String,
    val location: String,
    val startDate: String,
    val endDate: String?,
    val status: String,
    val currency: String,
    val notes: String,
)

@Serializable
data class RosterEntryDto(
    val id: String,
    val projectId: String,
    val clerkId: String,
    val dailyRate: Long,
    val removedAt: Long?,
)

@Serializable
data class AttendanceEntryDto(
    val id: String,
    val projectId: String,
    val clerkId: String,
    val date: String,
    val present: Boolean,
    val rateSnapshot: Long,
    val explicitlyMarked: Boolean,
)

@Serializable
data class ExtraPayLineDto(val id: String, val attendanceEntryId: String, val label: String, val amount: Long)

@Serializable
data class PaymentDto(
    val id: String,
    val projectId: String,
    val clerkId: String,
    val date: String,
    val amount: Long,
    val note: String,
)

/**
 * The whole-database backup payload (LOCKED Phase 5): a schema version for forward
 * compatibility, an export timestamp, the app version that produced it, and every row of all
 * 7 tables verbatim.
 */
@Serializable
data class BackupPayload(
    val schemaVersion: Int,
    val exportedAt: Long,
    val appVersionName: String,
    val companies: List<CompanyDto>,
    val clerks: List<ClerkDto>,
    val projects: List<ProjectDto>,
    val rosterEntries: List<RosterEntryDto>,
    val attendanceEntries: List<AttendanceEntryDto>,
    val extraPayLines: List<ExtraPayLineDto>,
    val payments: List<PaymentDto>,
) {
    companion object {
        /** The only schema version this build knows how to write or restore. */
        const val CURRENT_SCHEMA_VERSION = 1
    }
}

/** Row counts for a payload — echoed in the restore confirm dialog and the success screen. */
data class BackupCounts(
    val companies: Int,
    val clerks: Int,
    val projects: Int,
    val rosterEntries: Int,
    val attendanceEntries: Int,
    val extraPayLines: Int,
    val payments: Int,
) {
    companion object {
        fun of(payload: BackupPayload): BackupCounts = BackupCounts(
            companies = payload.companies.size,
            clerks = payload.clerks.size,
            projects = payload.projects.size,
            rosterEntries = payload.rosterEntries.size,
            attendanceEntries = payload.attendanceEntries.size,
            extraPayLines = payload.extraPayLines.size,
            payments = payload.payments.size,
        )
    }
}
