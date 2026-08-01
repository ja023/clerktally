package com.vague.crewtally.testutil

import com.vague.crewtally.data.local.AttendanceEntryEntity
import com.vague.crewtally.data.local.AttendanceWriter
import com.vague.crewtally.data.local.ClerkRate
import java.time.LocalDate
import java.util.UUID

/**
 * Mirrors [com.vague.crewtally.data.local.RoomAttendanceWriter]'s real semantics against the
 * fake DAO: every write looks up the existing (projectId, clerkId, date) row and reuses its id
 * (never minting a fresh one for a pair that already has a row), so tests catch the same
 * silent-drop bug the real unique index would cause. [clearAttendance] deletes through the
 * fake DAO, which simulates the FK CASCADE onto extra-pay lines.
 */
class FakeAttendanceWriter(private val attendanceDao: FakeAttendanceEntryDao) : AttendanceWriter {

    var setAttendanceCallCount: Int = 0
        private set

    override suspend fun setAttendance(
        projectId: String,
        clerkId: String,
        date: LocalDate,
        present: Boolean,
        rateSnapshot: Long,
    ) {
        setAttendanceCallCount += 1
        val existing = attendanceDao.getByProjectClerkDate(projectId, clerkId, date)
        val entry = existing?.copy(present = present, rateSnapshot = rateSnapshot)
            ?: AttendanceEntryEntity(
                id = UUID.randomUUID().toString(),
                projectId = projectId,
                clerkId = clerkId,
                date = date,
                present = present,
                rateSnapshot = rateSnapshot,
            )
        attendanceDao.upsert(entry)
    }

    override suspend fun clearAttendance(projectId: String, clerkId: String, date: LocalDate) {
        attendanceDao.getByProjectClerkDate(projectId, clerkId, date)?.let { attendanceDao.delete(it) }
    }

    override suspend fun markAllPresent(projectId: String, date: LocalDate, clerkRates: List<ClerkRate>) {
        clerkRates.forEach { clerkRate ->
            if (attendanceDao.getByProjectClerkDate(projectId, clerkRate.clerkId, date) == null) {
                attendanceDao.upsert(
                    AttendanceEntryEntity(
                        id = UUID.randomUUID().toString(),
                        projectId = projectId,
                        clerkId = clerkRate.clerkId,
                        date = date,
                        present = true,
                        rateSnapshot = clerkRate.rateSnapshot,
                    ),
                )
            }
        }
    }

    override suspend fun ensureAttendanceEntry(
        projectId: String,
        clerkId: String,
        date: LocalDate,
        rateSnapshot: Long,
    ): String {
        val existing = attendanceDao.getByProjectClerkDate(projectId, clerkId, date)
        return if (existing != null) {
            existing.id
        } else {
            val id = UUID.randomUUID().toString()
            attendanceDao.upsert(
                AttendanceEntryEntity(
                    id = id,
                    projectId = projectId,
                    clerkId = clerkId,
                    date = date,
                    present = false,
                    rateSnapshot = rateSnapshot,
                ),
            )
            id
        }
    }
}
