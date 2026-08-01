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
}
