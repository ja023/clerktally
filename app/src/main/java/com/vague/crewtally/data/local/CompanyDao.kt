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

    /** Every company row, verbatim — the backup export's companies table. */
    @Query("SELECT * FROM companies")
    suspend fun getAll(): List<CompanyEntity>

    /** Bulk insert used only by [com.vague.crewtally.backup.RestoreWriter] to repopulate from a backup. */
    @Upsert
    suspend fun upsertAll(companies: List<CompanyEntity>)

    /** Wipes the table — only [com.vague.crewtally.backup.RestoreWriter] calls this, inside its transaction. */
    @Query("DELETE FROM companies")
    suspend fun deleteAll()
}
