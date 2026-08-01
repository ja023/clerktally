package com.vague.crewtally.data.local

import androidx.room.withTransaction
import java.time.LocalDate
import java.util.UUID

/** A (clerk, rate-in-minor-units) pair passed to a bulk mark-all-present write. */
data class ClerkRate(val clerkId: String, val rateSnapshot: Long)

/**
 * The atomic write seam for attendance, mirroring [ProjectRosterWriter]. Every method that
 * touches the (projectId, clerkId, date) unique index does its own "look up the existing row
 * id, then write" inside a single transaction — the same invariant [RosterEntryDao.upsert]'s
 * KDoc protects, applied to [AttendanceEntryDao]. Minting a fresh UUID for a pair that already
 * has a row would fail the insert on the unique index and then silently no-op the update
 * fallback, so the id must always be reused.
 *
 * An interface (not a concrete class) so ViewModel tests substitute an in-memory fake instead
 * of a real Room transaction.
 */
interface AttendanceWriter {

    /**
     * Sets one clerk's attendance for a day: reuses the existing (projectId, clerkId, date)
     * row id if there is one (so a fast Present -> Absent -> Present sequence ends as exactly
     * one row), otherwise inserts a new row. [rateSnapshot] is copied in AT WRITE TIME — this
     * is the only moment a rate is captured, so later roster-rate edits never rewrite it.
     */
    suspend fun setAttendance(
        projectId: String,
        clerkId: String,
        date: LocalDate,
        present: Boolean,
        rateSnapshot: Long,
    )

    /**
     * Removes one clerk's attendance for a day (back to Unmarked). Deletes the row if present;
     * its extra-pay lines go with it via the schema's FK CASCADE (no manual line delete here).
     * A no-op when there is no row.
     */
    suspend fun clearAttendance(projectId: String, clerkId: String, date: LocalDate)

    /**
     * Marks present every clerk in [clerkRates] that has NO row yet for the day, in one
     * transaction. Clerks with an existing row — including an explicit Absent — are skipped,
     * so "Mark all present" never overwrites a deliberate mark.
     */
    suspend fun markAllPresent(projectId: String, date: LocalDate, clerkRates: List<ClerkRate>)

    /**
     * Ensures a carrier attendance row exists so extras can attach to it, returning its id.
     * If a row already exists (any present value) its id is returned unchanged; otherwise a
     * present=false row is created with [rateSnapshot] (extras are allowed on an unmarked or
     * absent clerk — LOCKED decision — and a present=false carrier reads as Absent).
     */
    suspend fun ensureAttendanceEntry(
        projectId: String,
        clerkId: String,
        date: LocalDate,
        rateSnapshot: Long,
    ): String
}

/** Production [AttendanceWriter] backed by a real [CrewTallyDatabase] transaction. */
class RoomAttendanceWriter(private val database: CrewTallyDatabase) : AttendanceWriter {

    override suspend fun setAttendance(
        projectId: String,
        clerkId: String,
        date: LocalDate,
        present: Boolean,
        rateSnapshot: Long,
    ) {
        database.withTransaction {
            val dao = database.attendanceEntryDao()
            val existing = dao.getByProjectClerkDate(projectId, clerkId, date)
            val entry = existing?.copy(present = present, rateSnapshot = rateSnapshot)
                ?: AttendanceEntryEntity(
                    id = UUID.randomUUID().toString(),
                    projectId = projectId,
                    clerkId = clerkId,
                    date = date,
                    present = present,
                    rateSnapshot = rateSnapshot,
                )
            dao.upsert(entry)
        }
    }

    override suspend fun clearAttendance(projectId: String, clerkId: String, date: LocalDate) {
        database.withTransaction {
            val dao = database.attendanceEntryDao()
            dao.getByProjectClerkDate(projectId, clerkId, date)?.let { dao.delete(it) }
        }
    }

    override suspend fun markAllPresent(projectId: String, date: LocalDate, clerkRates: List<ClerkRate>) {
        database.withTransaction {
            val dao = database.attendanceEntryDao()
            clerkRates.forEach { clerkRate ->
                if (dao.getByProjectClerkDate(projectId, clerkRate.clerkId, date) == null) {
                    dao.upsert(
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
    }

    override suspend fun ensureAttendanceEntry(
        projectId: String,
        clerkId: String,
        date: LocalDate,
        rateSnapshot: Long,
    ): String = database.withTransaction {
        val dao = database.attendanceEntryDao()
        val existing = dao.getByProjectClerkDate(projectId, clerkId, date)
        if (existing != null) {
            existing.id
        } else {
            val id = UUID.randomUUID().toString()
            dao.upsert(
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
