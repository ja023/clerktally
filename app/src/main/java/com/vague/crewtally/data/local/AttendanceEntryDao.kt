package com.vague.crewtally.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/** CRUD + flows for attendance. Feature-specific queries arrive in later phases. */
@Dao
interface AttendanceEntryDao {

    @Upsert
    suspend fun upsert(entry: AttendanceEntryEntity)

    @Delete
    suspend fun delete(entry: AttendanceEntryEntity)

    @Query("SELECT * FROM attendance_entries WHERE projectId = :projectId AND date = :date")
    fun observeByProjectAndDate(projectId: String, date: LocalDate): Flow<List<AttendanceEntryEntity>>

    @Query("SELECT * FROM attendance_entries WHERE projectId = :projectId AND clerkId = :clerkId")
    fun observeForClerkOnProject(projectId: String, clerkId: String): Flow<List<AttendanceEntryEntity>>

    @Query("SELECT * FROM attendance_entries WHERE id = :id")
    suspend fun getById(id: String): AttendanceEntryEntity?

    /** Used by the clerk delete-eligibility check (Phase 1): an attendance row is history. */
    @Query("SELECT COUNT(*) FROM attendance_entries WHERE clerkId = :clerkId")
    suspend fun countByClerk(clerkId: String): Int
}
