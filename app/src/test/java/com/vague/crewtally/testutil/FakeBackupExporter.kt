package com.vague.crewtally.testutil

import com.vague.crewtally.backup.BackupExporter
import com.vague.crewtally.backup.BackupPayload
import com.vague.crewtally.backup.toDto

/**
 * Mirrors [com.vague.crewtally.backup.RoomBackupExporter]'s field-for-field DAO-to-DTO mapping
 * against the hand-written fake DAOs, without the real transaction wrapping — same reasoning as
 * [FakeRestoreWriter] mirroring [com.vague.crewtally.backup.RoomRestoreWriter]: a real Room
 * transaction can't be exercised on the plain JVM, so this fake proves the assembly logic while
 * [BackupExporterTest] leaves the actual snapshot-consistency guarantee to the real implementation.
 */
class FakeBackupExporter(
    private val companyDao: FakeCompanyDao,
    private val clerkDao: FakeClerkDao,
    private val projectDao: FakeProjectDao,
    private val rosterEntryDao: FakeRosterEntryDao,
    private val attendanceEntryDao: FakeAttendanceEntryDao,
    private val extraPayLineDao: FakeExtraPayLineDao,
    private val paymentDao: FakePaymentDao,
) : BackupExporter {

    override suspend fun export(appVersionName: String, exportedAtEpochMillis: Long): BackupPayload = BackupPayload(
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
