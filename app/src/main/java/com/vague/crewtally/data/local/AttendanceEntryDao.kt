package com.vague.crewtally.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/** CRUD + flows for attendance. */
@Dao
interface AttendanceEntryDao {

    @Upsert
    suspend fun upsert(entry: AttendanceEntryEntity)

    @Delete
    suspend fun delete(entry: AttendanceEntryEntity)

    @Query("SELECT * FROM attendance_entries WHERE projectId = :projectId AND date = :date")
    fun observeByProjectAndDate(projectId: String, date: LocalDate): Flow<List<AttendanceEntryEntity>>

    /**
     * The day screen's roster of rows for one project on one date, each joined with its clerk
     * name. Includes day-only walk-ins (a clerk with an attendance row but no roster row) —
     * they would be invisible to a roster-only query, so the day screen unions this against
     * the active roster to build its full list.
     */
    @Query(
        """
        SELECT a.*, c.name AS clerkName
        FROM attendance_entries a
        JOIN clerks c ON c.id = a.clerkId
        WHERE a.projectId = :projectId AND a.date = :date
        ORDER BY c.name COLLATE NOCASE
        """,
    )
    fun observeDayWithNames(projectId: String, date: LocalDate): Flow<List<AttendanceRowWithName>>

    @Query("SELECT * FROM attendance_entries WHERE projectId = :projectId AND clerkId = :clerkId AND date = :date")
    fun observeByProjectClerkDate(projectId: String, clerkId: String, date: LocalDate): Flow<AttendanceEntryEntity?>

    @Query("SELECT * FROM attendance_entries WHERE projectId = :projectId AND clerkId = :clerkId AND date = :date")
    suspend fun getByProjectClerkDate(projectId: String, clerkId: String, date: LocalDate): AttendanceEntryEntity?

    /**
     * Per-day summary for the project-detail attendance history: one row per day that has any
     * attendance, newest first. [AttendanceDaySummary.extrasTotal] is computed by a correlated
     * subquery summing every extra-pay line on that day (Room can't SUM across two grouped
     * tables in one pass without double-counting the present rows).
     */
    @Query(
        """
        SELECT a.date AS date,
               SUM(CASE WHEN a.present THEN 1 ELSE 0 END) AS presentCount,
               COALESCE((
                   SELECT SUM(x.amount)
                   FROM extra_pay_lines x
                   JOIN attendance_entries a2 ON a2.id = x.attendanceEntryId
                   WHERE a2.projectId = a.projectId AND a2.date = a.date
               ), 0) AS extrasTotal
        FROM attendance_entries a
        WHERE a.projectId = :projectId
        GROUP BY a.date
        ORDER BY a.date DESC
        """,
    )
    fun observeDaySummaries(projectId: String): Flow<List<AttendanceDaySummary>>

    @Query("SELECT * FROM attendance_entries WHERE id = :id")
    suspend fun getById(id: String): AttendanceEntryEntity?

    /**
     * Every attendance row for one clerk on one project, oldest first — the clerk balance
     * screen's raw day ledger (it renders the present days and derives earnings from them).
     */
    @Query(
        "SELECT * FROM attendance_entries WHERE projectId = :projectId AND clerkId = :clerkId ORDER BY date",
    )
    fun observeForClerkOnProject(projectId: String, clerkId: String): Flow<List<AttendanceEntryEntity>>

    /**
     * Earned per (project, clerk) across the whole book: Σ of the rate snapshot over PRESENT
     * rows only (absent rows earn nothing). The earnings component of every Home balance.
     */
    @Query(
        """
        SELECT projectId AS projectId, clerkId AS clerkId,
               SUM(CASE WHEN present THEN rateSnapshot ELSE 0 END) AS amount
        FROM attendance_entries
        GROUP BY projectId, clerkId
        """,
    )
    fun observeEarningsRollup(): Flow<List<ClerkProjectAmount>>

    /** The same earnings roll-up scoped to one clerk — the clerk profile's earnings component. */
    @Query(
        """
        SELECT projectId AS projectId, clerkId AS clerkId,
               SUM(CASE WHEN present THEN rateSnapshot ELSE 0 END) AS amount
        FROM attendance_entries
        WHERE clerkId = :clerkId
        GROUP BY projectId, clerkId
        """,
    )
    fun observeEarningsRollupForClerk(clerkId: String): Flow<List<ClerkProjectAmount>>

    /** Used by the clerk delete-eligibility check (Phase 1): an attendance row is history. */
    @Query("SELECT COUNT(*) FROM attendance_entries WHERE clerkId = :clerkId")
    suspend fun countByClerk(clerkId: String): Int

    /**
     * Every attendance row across the whole book, reactively — feeds the v1.1 project statement
     * and extended company statement (both need raw per-clerk-per-day rows, not just the earned
     * roll-up), filtered down to one project (or one company's projects) in Kotlin the same way
     * [observeEarningsRollup] already is. [getAll] above is the suspend snapshot the backup
     * export uses; this is its reactive Flow counterpart for report screens.
     */
    @Query("SELECT * FROM attendance_entries")
    fun observeAll(): Flow<List<AttendanceEntryEntity>>

    /** Every attendance row, verbatim — the backup export's attendance_entries table. */
    @Query("SELECT * FROM attendance_entries")
    suspend fun getAll(): List<AttendanceEntryEntity>

    /** Bulk insert used only by [com.vague.crewtally.backup.RestoreWriter] to repopulate from a backup. */
    @Upsert
    suspend fun upsertAll(entries: List<AttendanceEntryEntity>)

    /** Wipes the table — only [com.vague.crewtally.backup.RestoreWriter] calls this, inside its transaction. */
    @Query("DELETE FROM attendance_entries")
    suspend fun deleteAll()
}
