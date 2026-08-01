package com.vague.crewtally.testutil

import com.vague.crewtally.data.local.ClerkDao
import com.vague.crewtally.data.local.ClerkEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * Hand-written [ClerkDao] fake for headless ViewModel tests (per the ecc testing rule:
 * fakes over mocks). Backed by a [MutableStateFlow] so it mirrors Room's live-query
 * behavior — an upsert/delete is immediately visible to anything observing [observeAll].
 */
class FakeClerkDao : ClerkDao {
    private val clerks = MutableStateFlow<List<ClerkEntity>>(emptyList())

    override suspend fun upsert(clerk: ClerkEntity) {
        clerks.update { list -> list.filterNot { it.id == clerk.id } + clerk }
    }

    override suspend fun delete(clerk: ClerkEntity) {
        clerks.update { list -> list.filterNot { it.id == clerk.id } }
    }

    override fun observeAll(): Flow<List<ClerkEntity>> =
        clerks.map { list -> list.sortedBy { it.name.lowercase() } }

    override fun observeActive(): Flow<List<ClerkEntity>> =
        clerks.map { list -> list.filter { it.active }.sortedBy { it.name.lowercase() } }

    override fun observeById(id: String): Flow<ClerkEntity?> =
        clerks.map { list -> list.find { it.id == id } }

    override suspend fun getById(id: String): ClerkEntity? = clerks.value.find { it.id == id }

    /** Test setup helper — seeds the store directly, bypassing [upsert]. */
    fun seed(vararg entities: ClerkEntity) {
        clerks.value = entities.toList()
    }

    /** Test assertion helper — the store's current contents, unsorted and unfiltered. */
    fun all(): List<ClerkEntity> = clerks.value
}
