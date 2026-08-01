package com.vague.crewtally.testutil

import com.vague.crewtally.data.local.RosterEntryDao
import com.vague.crewtally.data.local.RosterEntryEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** Hand-written [RosterEntryDao] fake — only [countByClerk] is exercised by Phase 1 tests. */
class FakeRosterEntryDao : RosterEntryDao {
    private val entries = MutableStateFlow<List<RosterEntryEntity>>(emptyList())

    override suspend fun upsert(entry: RosterEntryEntity) {
        entries.update { list -> list.filterNot { it.id == entry.id } + entry }
    }

    override suspend fun delete(entry: RosterEntryEntity) {
        entries.update { list -> list.filterNot { it.id == entry.id } }
    }

    override fun observeByProject(projectId: String): Flow<List<RosterEntryEntity>> =
        entries.map { list -> list.filter { it.projectId == projectId } }

    override fun observeByClerk(clerkId: String): Flow<List<RosterEntryEntity>> =
        entries.map { list -> list.filter { it.clerkId == clerkId } }

    override suspend fun getForPair(projectId: String, clerkId: String): RosterEntryEntity? =
        entries.value.find { it.projectId == projectId && it.clerkId == clerkId }

    override suspend fun countByClerk(clerkId: String): Int =
        entries.value.count { it.clerkId == clerkId }

    fun seed(vararg entities: RosterEntryEntity) {
        entries.value = entities.toList()
    }
}
