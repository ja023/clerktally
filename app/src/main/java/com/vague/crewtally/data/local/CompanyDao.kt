package com.vague.crewtally.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** CRUD + flows for companies. Feature-specific queries arrive in later phases. */
@Dao
interface CompanyDao {

    @Upsert
    suspend fun upsert(company: CompanyEntity)

    @Delete
    suspend fun delete(company: CompanyEntity)

    @Query("SELECT * FROM companies ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<CompanyEntity>>

    @Query("SELECT * FROM companies WHERE archived = 0 ORDER BY name COLLATE NOCASE")
    fun observeActive(): Flow<List<CompanyEntity>>

    @Query("SELECT * FROM companies WHERE id = :id")
    fun observeById(id: String): Flow<CompanyEntity?>

    @Query("SELECT * FROM companies WHERE id = :id")
    suspend fun getById(id: String): CompanyEntity?
}
