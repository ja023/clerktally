package com.vague.crewtally.testutil

import com.vague.crewtally.data.local.ClerkProjectAmount
import com.vague.crewtally.data.local.PaymentDao
import com.vague.crewtally.data.local.PaymentEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** Hand-written [PaymentDao] fake — only [countByClerk] is exercised by Phase 1 tests. */
class FakePaymentDao : PaymentDao {
    private val payments = MutableStateFlow<List<PaymentEntity>>(emptyList())

    override suspend fun upsert(payment: PaymentEntity) {
        payments.update { list -> list.filterNot { it.id == payment.id } + payment }
    }

    override suspend fun delete(payment: PaymentEntity) {
        payments.update { list -> list.filterNot { it.id == payment.id } }
    }

    override fun observeByProject(projectId: String): Flow<List<PaymentEntity>> =
        payments.map { list -> list.filter { it.projectId == projectId } }

    override fun observeForClerkOnProject(projectId: String, clerkId: String): Flow<List<PaymentEntity>> =
        payments.map { list -> list.filter { it.projectId == projectId && it.clerkId == clerkId } }

    override fun observeByClerk(clerkId: String): Flow<List<PaymentEntity>> =
        payments.map { list -> list.filter { it.clerkId == clerkId } }

    override fun observeById(id: String): Flow<PaymentEntity?> =
        payments.map { list -> list.find { it.id == id } }

    override suspend fun getById(id: String): PaymentEntity? = payments.value.find { it.id == id }

    override fun observeAll(): Flow<List<PaymentEntity>> = payments

    override fun observePaymentsRollup(): Flow<List<ClerkProjectAmount>> =
        payments.map { list -> rollup(list) }

    override fun observePaymentsRollupForClerk(clerkId: String): Flow<List<ClerkProjectAmount>> =
        payments.map { list -> rollup(list.filter { it.clerkId == clerkId }) }

    private fun rollup(list: List<PaymentEntity>): List<ClerkProjectAmount> =
        list.groupBy { it.projectId to it.clerkId }
            .map { (key, group) -> ClerkProjectAmount(key.first, key.second, group.sumOf { it.amount }) }

    override suspend fun countByClerk(clerkId: String): Int =
        payments.value.count { it.clerkId == clerkId }

    override suspend fun getAll(): List<PaymentEntity> = payments.value

    override suspend fun upsertAll(payments: List<PaymentEntity>) {
        this.payments.update { list -> list.filterNot { existing -> payments.any { it.id == existing.id } } + payments }
    }

    override suspend fun deleteAll() {
        payments.value = emptyList()
    }

    fun seed(vararg entities: PaymentEntity) {
        payments.value = entities.toList()
    }
}
