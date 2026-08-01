package com.vague.crewtally.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** CRUD + flows for projects. Feature-specific queries arrive in later phases. */
@Dao
interface ProjectDao {

    @Upsert
    suspend fun upsert(project: ProjectEntity)

    @Delete
    suspend fun delete(project: ProjectEntity)

    @Query("SELECT * FROM projects ORDER BY startDate DESC")
    fun observeAll(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE companyId = :companyId ORDER BY startDate DESC")
    fun observeByCompany(companyId: String): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE id = :id")
    fun observeById(id: String): Flow<ProjectEntity?>

    @Query("SELECT * FROM projects WHERE id = :id")
    suspend fun getById(id: String): ProjectEntity?

    /** Used by the company delete-eligibility check (Phase 1): a project row is history. */
    @Query("SELECT COUNT(*) FROM projects WHERE companyId = :companyId")
    suspend fun countByCompany(companyId: String): Int
}
