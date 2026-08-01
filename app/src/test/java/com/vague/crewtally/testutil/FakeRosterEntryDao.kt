package com.vague.crewtally.testutil

import com.vague.crewtally.data.local.RosterEntryDao
import com.vague.crewtally.data.local.RosterEntryEntity
import com.vague.crewtally.data.local.RosterRowSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory [RosterEntryDao] fake. Like the real table, [upsert] on an existing id updates
 * that row IN PLACE (list position never changes) while a new id is appended — so
 * [getMostRecentRateForClerk], which returns the LAST matching entry, mimics the real DAO's
 * `ORDER BY rowid DESC LIMIT 1` (rowid survives updates, only insert order sets it).
 */
class FakeRosterEntryDao : RosterEntryDao {
    private val entries = mutableListOf<RosterEntryEntity>()
    private val entriesFlow = MutableStateFlow<List<RosterEntryEntity>>(emptyList())
    private val clerkNames = mutableMapOf<String, String>()

    override suspend fun upsert(entry: RosterEntryEntity) {
        val index = entries.indexOfFirst { it.id == entry.id }
        if (index >= 0) entries[index] = entry else entries.add(entry)
        entriesFlow.value = entries.toList()
    }

    override suspend fun delete(entry: RosterEntryEntity) {
        entries.removeAll { it.id == entry.id }
        entriesFlow.value = entries.toList()
    }

    override fun observeByProject(projectId: String): Flow<List<RosterEntryEntity>> =
        entriesFlow.map { list -> list.filter { it.projectId == projectId } }

    override fun observeByClerk(clerkId: String): Flow<List<RosterEntryEntity>> =
        entriesFlow.map { list -> list.filter { it.clerkId == clerkId } }

    override suspend fun getForPair(projectId: String, clerkId: String): RosterEntryEntity? =
        entries.find { it.projectId == projectId && it.clerkId == clerkId }

    override fun observeActiveRosterForProject(projectId: String): Flow<List<RosterRowSummary>> =
        entriesFlow.map { list ->
            list.filter { it.projectId == projectId && it.removedAt == null }
                .map { RosterRowSummary(entry = it, clerkName = clerkNames[it.clerkId].orEmpty()) }
        }

    override suspend fun getMostRecentRateForClerk(clerkId: String): Long? =
        entries.lastOrNull { it.clerkId == clerkId }?.dailyRate

    fun setClerkName(clerkId: String, name: String) {
        clerkNames[clerkId] = name
    }

    fun seed(vararg rosterEntries: RosterEntryEntity) {
        entries.addAll(rosterEntries)
        entriesFlow.value = entries.toList()
    }

    fun all(): List<RosterEntryEntity> = entries.toList()
}
