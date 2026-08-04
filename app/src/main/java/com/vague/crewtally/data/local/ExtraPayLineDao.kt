package com.vague.crewtally.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/** CRUD + flows for extra-pay lines. */
@Dao
interface ExtraPayLineDao {

    @Upsert
    suspend fun upsert(line: ExtraPayLineEntity)

    @Delete
    suspend fun delete(line: ExtraPayLineEntity)

    @Query("SELECT * FROM extra_pay_lines WHERE attendanceEntryId = :attendanceEntryId")
    fun observeForAttendance(attendanceEntryId: String): Flow<List<ExtraPayLineEntity>>

    @Query("SELECT * FROM extra_pay_lines WHERE attendanceEntryId = :attendanceEntryId")
    suspend fun getForAttendance(attendanceEntryId: String): List<ExtraPayLineEntity>

    /**
     * Signed extras total per attendance entry for one project on one date — feeds each day
     * row's extras figure. Entries with no lines simply don't appear (an inner join), so the
     * day screen treats a missing entry id as zero.
     */
    @Query(
        """
        SELECT x.attendanceEntryId AS attendanceEntryId, SUM(x.amount) AS total
        FROM extra_pay_lines x
        JOIN attendance_entries a ON a.id = x.attendanceEntryId
        WHERE a.projectId = :projectId AND a.date = :date
        GROUP BY x.attendanceEntryId
        """,
    )
    fun observeDayExtraTotals(projectId: String, date: LocalDate): Flow<List<AttendanceExtraTotal>>

    /**
     * Every extra-pay line for one clerk on one project, each carrying its day's date (joined
     * from the carrier attendance entry), oldest first — the clerk balance screen's extras ledger.
     */
    @Query(
        """
        SELECT x.*, a.date AS date
        FROM extra_pay_lines x
        JOIN attendance_entries a ON a.id = x.attendanceEntryId
        WHERE a.projectId = :projectId AND a.clerkId = :clerkId
        ORDER BY a.date
        """,
    )
    fun observeForClerkOnProject(projectId: String, clerkId: String): Flow<List<ExtraPayLineWithDate>>

    /**
     * Signed extras total per (project, clerk) across the whole book — the extras component of
     * every Home balance. Each extra line belongs to exactly one attendance entry, which belongs
     * to exactly one (project, clerk), so grouping by the entry's project+clerk never double-counts.
     */
    @Query(
        """
        SELECT a.projectId AS projectId, a.clerkId AS clerkId, SUM(x.amount) AS amount
        FROM extra_pay_lines x
        JOIN attendance_entries a ON a.id = x.attendanceEntryId
        GROUP BY a.projectId, a.clerkId
        """,
    )
    fun observeExtrasRollup(): Flow<List<ClerkProjectAmount>>

    /** The same extras roll-up scoped to one clerk — the clerk profile's extras component. */
    @Query(
        """
        SELECT a.projectId AS projectId, a.clerkId AS clerkId, SUM(x.amount) AS amount
        FROM extra_pay_lines x
        JOIN attendance_entries a ON a.id = x.attendanceEntryId
        WHERE a.clerkId = :clerkId
        GROUP BY a.projectId, a.clerkId
        """,
    )
    fun observeExtrasRollupForClerk(clerkId: String): Flow<List<ClerkProjectAmount>>

    /**
     * Every extra-pay line across the whole book, reactively, each carrying its carrier
     * attendance entry's date/project/clerk — the v1.1 project statement and extended company
     * statement's raw extras feed (see [ExtraPayLineWithClerk]'s KDoc for why a new projection
     * was needed rather than reusing [observeForClerkOnProject]).
     */
    @Query(
        """
        SELECT x.*, a.date AS date, a.projectId AS projectId, a.clerkId AS clerkId
        FROM extra_pay_lines x
        JOIN attendance_entries a ON a.id = x.attendanceEntryId
        """,
    )
    fun observeAllWithClerk(): Flow<List<ExtraPayLineWithClerk>>

    /** Every extra-pay line, verbatim — the backup export's extra_pay_lines table. */
    @Query("SELECT * FROM extra_pay_lines")
    suspend fun getAll(): List<ExtraPayLineEntity>

    /** Bulk insert used only by [com.vague.crewtally.backup.RestoreWriter] to repopulate from a backup. */
    @Upsert
    suspend fun upsertAll(lines: List<ExtraPayLineEntity>)

    /** Wipes the table — only [com.vague.crewtally.backup.RestoreWriter] calls this, inside its transaction. */
    @Query("DELETE FROM extra_pay_lines")
    suspend fun deleteAll()
}
