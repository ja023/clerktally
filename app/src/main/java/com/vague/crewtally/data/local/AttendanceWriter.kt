package com.vague.crewtally.data.local

import androidx.room.withTransaction
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** A (clerk, rate-in-minor-units) pair passed to a bulk mark-all-present write. */
data class ClerkRate(val clerkId: String, val rateSnapshot: Long)

/** Result of [AttendanceWriter.clearAttendance]. */
sealed interface ClearAttendanceResult {
    /** The row (if any) was deleted, or there was nothing to delete. */
    data object Cleared : ClearAttendanceResult

    /** The row has extras and [AttendanceWriter.clearAttendance] was not called with force=true — nothing was deleted. */
    data object BlockedByExtras : ClearAttendanceResult
}

/**
 * The atomic write seam for attendance, mirroring [ProjectRosterWriter]. Every method that
 * touches the (projectId, clerkId, date) unique index does its own "look up the existing row
 * id, then write" inside a single transaction — the same invariant [RosterEntryDao.upsert]'s
 * KDoc protects, applied to [AttendanceEntryDao]. Minting a fresh UUID for a pair that already
 * has a row would fail the insert on the unique index and then silently no-op the update
 * fallback, so the id must always be reused.
 *
 * [RoomAttendanceWriter] also serializes every one of these methods behind ONE internal mutex,
 * so writes fired from different screens (day screen, extras screen, walk-in screen — each a
 * separate ViewModel instance) still apply in the order they were tapped instead of racing each
 * other. A mutex that lived on a single ViewModel could never provide that guarantee across
 * screens; only the shared writer can — which is why every screen's ViewModel factory pulls
 * [CrewTallyApplication.attendanceWriter] (the ONE app-wide instance) rather than constructing
 * its own `RoomAttendanceWriter(database)`. A screen that built its own would get its own
 * private mutex, silently defeating this guarantee.
 *
 * An interface (not a concrete class) so ViewModel tests substitute an in-memory fake instead
 * of a real Room transaction.
 */
interface AttendanceWriter {

    /**
     * Sets one clerk's attendance for a day: reuses the existing (projectId, clerkId, date)
     * row id if there is one (so a fast Present -> Absent -> Present sequence ends as exactly
     * one row), otherwise inserts a new row. [rateSnapshot] is copied in AT WRITE TIME — this
     * is the only moment a rate is captured, so later roster-rate edits never rewrite it. A
     * real Present/Absent tap always leaves the row explicitlyMarked=true (see
     * [AttendanceEntryEntity.explicitlyMarked]).
     */
    suspend fun setAttendance(
        projectId: String,
        clerkId: String,
        date: LocalDate,
        present: Boolean,
        rateSnapshot: Long,
    )

    /**
     * Removes one clerk's attendance for a day (back to Unmarked). A no-op ([ClearAttendanceResult.Cleared])
     * when there is no row. When the row has extra-pay lines and [force] is false, nothing is
     * deleted and [ClearAttendanceResult.BlockedByExtras] is returned so the caller can warn
     * first — this check and the delete run in the SAME transaction so a concurrently-added
     * extra can never sneak in between the check and the delete (the bug a client-side
     * pre-check outside any lock would have). Passing force=true (after the user confirms)
     * deletes regardless; its extra-pay lines go with it via the schema's FK CASCADE.
     */
    suspend fun clearAttendance(
        projectId: String,
        clerkId: String,
        date: LocalDate,
        force: Boolean = false,
    ): ClearAttendanceResult

    /**
     * Marks present every clerk in [clerkRates] that has NO row yet for the day, in one
     * transaction. Clerks with an existing row — including an explicit Absent — are skipped,
     * so "Mark all present" never overwrites a deliberate mark.
     */
    suspend fun markAllPresent(projectId: String, date: LocalDate, clerkRates: List<ClerkRate>)

    /**
     * Ensures a carrier attendance row exists so extras can attach to it, returning its id.
     * If a row already exists (any present value) its id is returned unchanged; otherwise a
     * present=false, explicitlyMarked=false row is created (extras are allowed on an unmarked
     * or absent clerk — LOCKED decision — and a present=false carrier reads as Absent until
     * the day screen's own tap explicitly marks it). The carrier's rate prefers the clerk's
     * CURRENT active roster rate for this project over [rateSnapshot] — the caller's value may
     * be stale if the day screen has been open a while — falling back to [rateSnapshot] only
     * when there is no active roster row (a day-only walk-in has none to read).
     */
    suspend fun ensureAttendanceEntry(
        projectId: String,
        clerkId: String,
        date: LocalDate,
        rateSnapshot: Long,
    ): String

    /**
     * Deletes one extra-pay line, then — atomically in the same transaction — deletes its
     * carrier attendance row too IF that row is a carrier-only row (present=false,
     * explicitlyMarked=false, see [AttendanceEntryEntity.explicitlyMarked]) that has just lost
     * its LAST line. Without this the synthetic carrier row would linger forever once its
     * extras are gone, silently masquerading as a real Absent mark (Phase 3 amendment (c)). An
     * explicitly-marked Absent row never auto-deletes this way.
     */
    suspend fun deleteExtraLine(line: ExtraPayLineEntity)

    /**
     * Walk-in save is two things that must land together: the day's present=true attendance
     * row, and — only when [alsoAddToRoster] is true — the project roster row. Running these
     * as two separate transactions (as the ViewModel used to) can leave one written without
     * the other if the process dies in between; one transaction makes it atomic. The roster
     * upsert mirrors [ProjectRosterWriter.upsertRosterEntryForPair]'s look-up-id-then-write
     * shape inline rather than depending on that writer, since it must run inside this same
     * transaction.
     */
    suspend fun saveWalkIn(
        projectId: String,
        clerkId: String,
        date: LocalDate,
        rateSnapshot: Long,
        alsoAddToRoster: Boolean,
    )
}

/** Production [AttendanceWriter] backed by a real [CrewTallyDatabase] transaction. */
class RoomAttendanceWriter(private val database: CrewTallyDatabase) : AttendanceWriter {

    /** Serializes every method below across every caller (every attendance screen). */
    private val mutex = Mutex()

    override suspend fun setAttendance(
        projectId: String,
        clerkId: String,
        date: LocalDate,
        present: Boolean,
        rateSnapshot: Long,
    ) {
        mutex.withLock {
            database.withTransaction {
                val dao = database.attendanceEntryDao()
                val existing = dao.getByProjectClerkDate(projectId, clerkId, date)
                val entry = existing?.copy(present = present, rateSnapshot = rateSnapshot, explicitlyMarked = true)
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
    }

    override suspend fun clearAttendance(
        projectId: String,
        clerkId: String,
        date: LocalDate,
        force: Boolean,
    ): ClearAttendanceResult = mutex.withLock {
        database.withTransaction {
            val dao = database.attendanceEntryDao()
            val existing = dao.getByProjectClerkDate(projectId, clerkId, date)
            when {
                existing == null -> ClearAttendanceResult.Cleared
                !force && database.extraPayLineDao().getForAttendance(existing.id).isNotEmpty() ->
                    ClearAttendanceResult.BlockedByExtras
                else -> {
                    dao.delete(existing)
                    ClearAttendanceResult.Cleared
                }
            }
        }
    }

    override suspend fun markAllPresent(projectId: String, date: LocalDate, clerkRates: List<ClerkRate>) {
        mutex.withLock {
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
    }

    override suspend fun ensureAttendanceEntry(
        projectId: String,
        clerkId: String,
        date: LocalDate,
        rateSnapshot: Long,
    ): String = mutex.withLock {
        database.withTransaction {
            val dao = database.attendanceEntryDao()
            val existing = dao.getByProjectClerkDate(projectId, clerkId, date)
            if (existing != null) {
                existing.id
            } else {
                val currentRate = database.rosterEntryDao().getActiveDailyRate(projectId, clerkId) ?: rateSnapshot
                val id = UUID.randomUUID().toString()
                dao.upsert(
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
    }

    override suspend fun deleteExtraLine(line: ExtraPayLineEntity) {
        mutex.withLock {
            database.withTransaction {
                val extraDao = database.extraPayLineDao()
                extraDao.delete(line)
                val entryDao = database.attendanceEntryDao()
                val entry = entryDao.getById(line.attendanceEntryId)
                if (entry != null && !entry.present && !entry.explicitlyMarked) {
                    val remaining = extraDao.getForAttendance(entry.id)
                    if (remaining.isEmpty()) {
                        entryDao.delete(entry)
                    }
                }
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
        mutex.withLock {
            database.withTransaction {
                val attendanceDao = database.attendanceEntryDao()
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
                    val rosterDao = database.rosterEntryDao()
                    val existingRoster = rosterDao.getForPair(projectId, clerkId)
                    val rosterEntry = (
                        existingRoster ?: RosterEntryEntity(
                            id = UUID.randomUUID().toString(),
                            projectId = projectId,
                            clerkId = clerkId,
                            dailyRate = rateSnapshot,
                        )
                        ).copy(dailyRate = rateSnapshot, removedAt = null)
                    rosterDao.upsert(rosterEntry)
                }
            }
        }
    }
}
