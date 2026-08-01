package com.vague.crewtally.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** CRUD + flows for clerks. Feature-specific queries arrive in later phases. */
@Dao
interface ClerkDao {

    @Upsert
    suspend fun upsert(clerk: ClerkEntity)

    @Delete
    suspend fun delete(clerk: ClerkEntity)

    @Query("SELECT * FROM clerks ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<ClerkEntity>>

    @Query("SELECT * FROM clerks WHERE active = 1 ORDER BY name COLLATE NOCASE")
    fun observeActive(): Flow<List<ClerkEntity>>

    @Query("SELECT * FROM clerks WHERE id = :id")
    fun observeById(id: String): Flow<ClerkEntity?>

    @Query("SELECT * FROM clerks WHERE id = :id")
    suspend fun getById(id: String): ClerkEntity?
}
