package com.vague.crewtally.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** CRUD + flows for payments. Feature-specific queries arrive in later phases. */
@Dao
interface PaymentDao {

    @Upsert
    suspend fun upsert(payment: PaymentEntity)

    @Delete
    suspend fun delete(payment: PaymentEntity)

    @Query("SELECT * FROM payments WHERE projectId = :projectId ORDER BY date DESC")
    fun observeByProject(projectId: String): Flow<List<PaymentEntity>>

    @Query(
        "SELECT * FROM payments WHERE projectId = :projectId AND clerkId = :clerkId ORDER BY date DESC",
    )
    fun observeForClerkOnProject(projectId: String, clerkId: String): Flow<List<PaymentEntity>>

    @Query("SELECT * FROM payments WHERE clerkId = :clerkId ORDER BY date DESC")
    fun observeByClerk(clerkId: String): Flow<List<PaymentEntity>>
}
