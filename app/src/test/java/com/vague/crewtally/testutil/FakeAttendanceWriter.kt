package com.vague.crewtally.testutil

import com.vague.crewtally.data.local.AttendanceEntryEntity
import com.vague.crewtally.data.local.AttendanceWriter
import com.vague.crewtally.data.local.ClearAttendanceResult
import com.vague.crewtally.data.local.ClerkRate
import com.vague.crewtally.data.local.ExtraPayLineEntity
import com.vague.crewtally.data.local.RosterEntryEntity
import java.time.LocalDate
import java.util.UUID

/**
 * Mirrors [com.vague.crewtally.data.local.RoomAttendanceWriter]'s real semantics against the
 * fake DAOs: every write looks up the existing (projectId, clerkId, date) row and reuses its id
 * (never minting a fresh one for a pair that already has a row), so tests catch the same
 * silent-drop bug the real unique index would cause. [clearAttendance] checks for extras and
 * deletes through the fake DAO (which simulates the FK CASCADE onto extra-pay lines);
 * [deleteExtraLine] mirrors the real writer's "last line on a carrier-only row deletes the row
 * too" rule; [ensureAttendanceEntry] mirrors the real writer's "prefer the current active
 * roster rate" rule; [saveWalkIn] mirrors the real writer's single-transaction attendance +
 * roster write.
 */
class FakeAttendanceWriter(
    private val attendanceDao: FakeAttendanceEntryDao,
    private val extraPayLineDao: FakeExtraPayLineDao,
    private val rosterEntryDao: FakeRosterEntryDao,
) : AttendanceWriter {

    var setAttendanceCallCount: Int = 0
        private set

    var saveWalkInCallCount: Int = 0
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
        val entry = existing?.copy(present = present, rateSnapshot = rateSnapshot, explicitlyMarked = true)
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

    override suspend fun clearAttendance(
        projectId: String,
        clerkId: String,
        date: LocalDate,
        force: Boolean,
    ): ClearAttendanceResult {
        val existing = attendanceDao.getByProjectClerkDate(projectId, clerkId, date)
            ?: return ClearAttendanceResult.Cleared
        if (!force && extraPayLineDao.getForAttendance(existing.id).isNotEmpty()) {
            return ClearAttendanceResult.BlockedByExtras
        }
        attendanceDao.delete(existing)
        return ClearAttendanceResult.Cleared
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
            val currentRate = rosterEntryDao.getActiveDailyRate(projectId, clerkId) ?: rateSnapshot
            val id = UUID.randomUUID().toString()
            attendanceDao.upsert(
                AttendanceEntryEntity(
                    id = id,
                    projectId = projectId,
                    clerkId = clerkId,
                    date = date,
                    present = false,
                    rateSnapshot = currentRate,
                    explicitlyMarked = false,
                ),
            )
            id
        }
    }

    override suspend fun deleteExtraLine(line: ExtraPayLineEntity) {
        extraPayLineDao.delete(line)
        val entry = attendanceDao.getById(line.attendanceEntryId)
        if (entry != null && !entry.present && !entry.explicitlyMarked) {
            if (extraPayLineDao.getForAttendance(entry.id).isEmpty()) {
                attendanceDao.delete(entry)
            }
        }
    }

    override suspend fun saveWalkIn(
        projectId: String,
        clerkId: String,
        date: LocalDate,
        rateSnapshot: Long,
        alsoAddToRoster: Boolean,
    ) {
        saveWalkInCallCount += 1
        val existingEntry = attendanceDao.getByProjectClerkDate(projectId, clerkId, date)
        val entry = existingEntry?.copy(present = true, rateSnapshot = rateSnapshot, explicitlyMarked = true)
            ?: AttendanceEntryEntity(
                id = UUID.randomUUID().toString(),
                projectId = projectId,
                clerkId = clerkId,
                date = date,
                present = true,
                rateSnapshot = rateSnapshot,
            )
        attendanceDao.upsert(entry)

        if (alsoAddToRoster) {
            val existingRoster = rosterEntryDao.getForPair(projectId, clerkId)
            val rosterEntry = (
                existingRoster ?: RosterEntryEntity(
                    id = UUID.randomUUID().toString(),
                    projectId = projectId,
                    clerkId = clerkId,
                    dailyRate = rateSnapshot,
                )
                ).copy(dailyRate = rateSnapshot, removedAt = null)
            rosterEntryDao.upsert(rosterEntry)
        }
    }
}
