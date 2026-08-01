package com.vague.crewtally.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** CRUD + flows for roster assignments. Feature-specific queries arrive in later phases. */
@Dao
interface RosterEntryDao {

    @Upsert
    suspend fun upsert(entry: RosterEntryEntity)

    @Delete
    suspend fun delete(entry: RosterEntryEntity)

    @Query("SELECT * FROM roster_entries WHERE projectId = :projectId")
    fun observeByProject(projectId: String): Flow<List<RosterEntryEntity>>

    @Query("SELECT * FROM roster_entries WHERE clerkId = :clerkId")
    fun observeByClerk(clerkId: String): Flow<List<RosterEntryEntity>>

    @Query(
        "SELECT * FROM roster_entries WHERE projectId = :projectId AND clerkId = :clerkId LIMIT 1",
    )
    suspend fun getForPair(projectId: String, clerkId: String): RosterEntryEntity?

    /** Used by the clerk delete-eligibility check (Phase 1): a roster row is history. */
    @Query("SELECT COUNT(*) FROM roster_entries WHERE clerkId = :clerkId")
    suspend fun countByClerk(clerkId: String): Int

    /** Active (non-removed) roster rows for a project, joined with clerk name, name-sorted. */
    @Query(
        """
        SELECT r.*, c.name AS clerkName
        FROM roster_entries r
        JOIN clerks c ON c.id = r.clerkId
        WHERE r.projectId = :projectId AND r.removedAt IS NULL
        ORDER BY c.name COLLATE NOCASE
        """,
    )
    fun observeActiveRosterForProject(projectId: String): Flow<List<RosterRowSummary>>

    /**
     * A clerk's most recent daily rate across every project they've ever been rostered on
     * (active or removed), ordered by SQLite's implicit rowid — i.e. by roster row creation.
     * Used to pre-fill the rate suggestion when assigning a clerk (LOCKED data model). Null
     * when the clerk has never been rostered anywhere.
     */
    @Query("SELECT dailyRate FROM roster_entries WHERE clerkId = :clerkId ORDER BY rowid DESC LIMIT 1")
    suspend fun getMostRecentRateForClerk(clerkId: String): Long?
}
