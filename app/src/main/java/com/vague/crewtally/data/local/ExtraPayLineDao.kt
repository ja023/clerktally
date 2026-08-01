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
}
