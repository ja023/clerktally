package com.vague.crewtally.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** CRUD + flows for roster assignments. Feature-specific queries arrive in later phases. */
@Dao
interface RosterEntryDao {

    /**
     * Room's generated upsert: INSERT, falling back to an UPDATE by primary key on conflict.
     * ⚠️ For a (projectId, clerkId) pair that already has a row (active or soft-removed),
     * callers MUST reuse that row's existing [RosterEntryEntity.id] rather than minting a
     * new one. A fresh id for an existing pair fails the INSERT on the unique index, and the
     * UPDATE fallback then matches zero rows (wrong id) — the write is silently dropped, not
     * an error. See [com.vague.crewtally.data.local.ProjectRosterWriter.upsertRosterEntryForPair]
     * for the transaction that makes the required "look up id, then write" atomic.
     */
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

    /**
     * The clerk's CURRENT active daily rate on this exact project, or null when they have no
     * active roster row (a day-only walk-in, or a removed roster clerk). [AttendanceWriter]
     * prefers this live value over a rateSnapshot threaded through nav args from screen-open
     * time, since the roster rate can change while the day screen is sitting open.
     */
    @Query("SELECT dailyRate FROM roster_entries WHERE projectId = :projectId AND clerkId = :clerkId AND removedAt IS NULL LIMIT 1")
    suspend fun getActiveDailyRate(projectId: String, clerkId: String): Long?

    /** Every roster row (active and soft-removed), verbatim — the backup export's roster_entries table. */
    @Query("SELECT * FROM roster_entries")
    suspend fun getAll(): List<RosterEntryEntity>

    /** Bulk insert used only by [com.vague.crewtally.backup.RestoreWriter] to repopulate from a backup. */
    @Upsert
    suspend fun upsertAll(entries: List<RosterEntryEntity>)

    /** Wipes the table — only [com.vague.crewtally.backup.RestoreWriter] calls this, inside its transaction. */
    @Query("DELETE FROM roster_entries")
    suspend fun deleteAll()
}
