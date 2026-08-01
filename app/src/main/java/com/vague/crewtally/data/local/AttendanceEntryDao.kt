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

    /** Used by the clerk delete-eligibility check (Phase 1): an attendance row is history. */
    @Query("SELECT COUNT(*) FROM attendance_entries WHERE clerkId = :clerkId")
    suspend fun countByClerk(clerkId: String): Int
}
