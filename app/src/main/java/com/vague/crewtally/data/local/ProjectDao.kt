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

    /**
     * Project rows for one Projects-tab segment, each pre-joined with its company name and
     * active roster size (LOCKED Phase 2 decision: rows show name, company, roster size).
     */
    @Query(
        """
        SELECT p.*, COALESCE(c.name, '') AS companyName,
            (SELECT COUNT(*) FROM roster_entries r
                WHERE r.projectId = p.id AND r.removedAt IS NULL) AS rosterSize
        FROM projects p
        LEFT JOIN companies c ON c.id = p.companyId
        WHERE p.status = :status
        ORDER BY p.startDate DESC
        """,
    )
    fun observeSummariesByStatus(status: ProjectStatus): Flow<List<ProjectSummary>>

    /**
     * The currency of the most recently CREATED project (ordered by SQLite's implicit rowid,
     * which is assigned in insertion order and never reused for these never-hard-deleted
     * rows) — the "last used" default for a new project's currency (LOCKED decision #2).
     */
    @Query("SELECT currency FROM projects ORDER BY rowid DESC LIMIT 1")
    suspend fun getMostRecentCurrency(): String?

    /** Every project row, verbatim — the backup export's projects table. */
    @Query("SELECT * FROM projects")
    suspend fun getAll(): List<ProjectEntity>

    /** Bulk insert used only by [com.vague.crewtally.backup.RestoreWriter] to repopulate from a backup. */
    @Upsert
    suspend fun upsertAll(projects: List<ProjectEntity>)

    /** Wipes the table — only [com.vague.crewtally.backup.RestoreWriter] calls this, inside its transaction. */
    @Query("DELETE FROM projects")
    suspend fun deleteAll()
}
