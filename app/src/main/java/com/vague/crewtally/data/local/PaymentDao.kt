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

    /** The single payment behind the edit form, observed so an external change reflects live. */
    @Query("SELECT * FROM payments WHERE id = :id")
    fun observeById(id: String): Flow<PaymentEntity?>

    @Query("SELECT * FROM payments WHERE id = :id")
    suspend fun getById(id: String): PaymentEntity?

    /**
     * Total paid per (project, clerk) across the whole book — the payments component of every
     * derived balance on the Home dashboard.
     */
    @Query(
        """
        SELECT projectId AS projectId, clerkId AS clerkId, SUM(amount) AS amount
        FROM payments
        GROUP BY projectId, clerkId
        """,
    )
    fun observePaymentsRollup(): Flow<List<ClerkProjectAmount>>

    /** The same payments roll-up scoped to one clerk — the clerk profile's payments component. */
    @Query(
        """
        SELECT projectId AS projectId, clerkId AS clerkId, SUM(amount) AS amount
        FROM payments
        WHERE clerkId = :clerkId
        GROUP BY projectId, clerkId
        """,
    )
    fun observePaymentsRollupForClerk(clerkId: String): Flow<List<ClerkProjectAmount>>

    /** Used by the clerk delete-eligibility check (Phase 1): a payment row is history. */
    @Query("SELECT COUNT(*) FROM payments WHERE clerkId = :clerkId")
    suspend fun countByClerk(clerkId: String): Int
}
