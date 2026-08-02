package com.vague.crewtally.backup

import com.vague.crewtally.data.local.AttendanceEntryDao
import com.vague.crewtally.data.local.ClerkDao
import com.vague.crewtally.data.local.CompanyDao
import com.vague.crewtally.data.local.ExtraPayLineDao
import com.vague.crewtally.data.local.PaymentDao
import com.vague.crewtally.data.local.ProjectDao
import com.vague.crewtally.data.local.RosterEntryDao

/**
 * Assembles a [BackupPayload] from the suspend `getAll()` on all 7 DAOs (added this phase
 * specifically to back this export) — a straight one-shot read, not a `Flow`, since a backup is
 * a snapshot in time, not a live view. Individual DAO parameters (not a Room [CrewTallyDatabase]
 * reference) mirror every ViewModel factory's shape in this codebase, so this stays testable
 * against the same hand-written fakes.
 */
object BackupExporter {

    suspend fun export(
        companyDao: CompanyDao,
        clerkDao: ClerkDao,
        projectDao: ProjectDao,
        rosterEntryDao: RosterEntryDao,
        attendanceEntryDao: AttendanceEntryDao,
        extraPayLineDao: ExtraPayLineDao,
        paymentDao: PaymentDao,
        appVersionName: String,
        exportedAtEpochMillis: Long,
    ): BackupPayload = BackupPayload(
        schemaVersion = BackupPayload.CURRENT_SCHEMA_VERSION,
        exportedAt = exportedAtEpochMillis,
        appVersionName = appVersionName,
        companies = companyDao.getAll().map { it.toDto() },
        clerks = clerkDao.getAll().map { it.toDto() },
        projects = projectDao.getAll().map { it.toDto() },
        rosterEntries = rosterEntryDao.getAll().map { it.toDto() },
        attendanceEntries = attendanceEntryDao.getAll().map { it.toDto() },
        extraPayLines = extraPayLineDao.getAll().map { it.toDto() },
        payments = paymentDao.getAll().map { it.toDto() },
    )
}
