package com.vague.crewtally.testutil

import com.vague.crewtally.data.local.ClerkDao
import com.vague.crewtally.data.local.ClerkEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** In-memory [ClerkDao] fake for headless JVM ViewModel tests. */
class FakeClerkDao : ClerkDao {
    private val entries = mutableListOf<ClerkEntity>()
    private val entriesFlow = MutableStateFlow<List<ClerkEntity>>(emptyList())

    override suspend fun upsert(clerk: ClerkEntity) {
        val index = entries.indexOfFirst { it.id == clerk.id }
        if (index >= 0) entries[index] = clerk else entries.add(clerk)
        entriesFlow.value = entries.toList()
    }

    override suspend fun delete(clerk: ClerkEntity) {
        entries.removeAll { it.id == clerk.id }
        entriesFlow.value = entries.toList()
    }

    override fun observeAll(): Flow<List<ClerkEntity>> = entriesFlow

    override fun observeActive(): Flow<List<ClerkEntity>> =
        entriesFlow.map { list -> list.filter { it.active } }

    override fun observeById(id: String): Flow<ClerkEntity?> =
        entriesFlow.map { list -> list.find { it.id == id } }

    override suspend fun getById(id: String): ClerkEntity? = entries.find { it.id == id }

    fun seed(vararg clerks: ClerkEntity) {
        entries.addAll(clerks)
        entriesFlow.value = entries.toList()
    }
}
