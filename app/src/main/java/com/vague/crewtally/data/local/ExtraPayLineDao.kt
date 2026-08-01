package com.vague.crewtally.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** CRUD + flows for extra-pay lines. Feature-specific queries arrive in later phases. */
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
}
