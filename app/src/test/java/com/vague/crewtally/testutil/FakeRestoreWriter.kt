package com.vague.crewtally.testutil

import com.vague.crewtally.backup.BackupPayload
import com.vague.crewtally.backup.RestoreWriter
import com.vague.crewtally.backup.toEntity

/**
 * Mirrors [com.vague.crewtally.backup.RoomRestoreWriter]'s documented delete-leaves-first,
 * insert-parents-first sequence against the hand-written fake DAOs — same reasoning as
 * [FakeAttendanceWriter] mirroring [com.vague.crewtally.data.local.RoomAttendanceWriter]. A real
 * Room transaction's rollback-on-failure can't be exercised on the plain JVM, so
 * `RestoreWriterTest`'s atomicity case wraps this the same way `AttendanceWriterTest`'s
 * `FaultyWalkInWriter` does: a fault injected BEFORE any write proves the "both applied or
 * neither" contract every caller relies on.
 */
class FakeRestoreWriter(
    private val companyDao: FakeCompanyDao,
    private val clerkDao: FakeClerkDao,
    private val projectDao: FakeProjectDao,
    private val rosterEntryDao: FakeRosterEntryDao,
    private val attendanceEntryDao: FakeAttendanceEntryDao,
    private val extraPayLineDao: FakeExtraPayLineDao,
    private val paymentDao: FakePaymentDao,
) : RestoreWriter {

    override suspend fun restore(payload: BackupPayload) {
        extraPayLineDao.deleteAll()
        paymentDao.deleteAll()
        attendanceEntryDao.deleteAll()
        rosterEntryDao.deleteAll()
        projectDao.deleteAll()
        clerkDao.deleteAll()
        companyDao.deleteAll()

        companyDao.upsertAll(payload.companies.map { it.toEntity() })
        clerkDao.upsertAll(payload.clerks.map { it.toEntity() })
        projectDao.upsertAll(payload.projects.map { it.toEntity() })
        rosterEntryDao.upsertAll(payload.rosterEntries.map { it.toEntity() })
        attendanceEntryDao.upsertAll(payload.attendanceEntries.map { it.toEntity() })
        extraPayLineDao.upsertAll(payload.extraPayLines.map { it.toEntity() })
        paymentDao.upsertAll(payload.payments.map { it.toEntity() })
    }
}
