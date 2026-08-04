package com.vague.crewtally.testutil

import com.vague.crewtally.data.local.ProjectDao
import com.vague.crewtally.data.local.ProjectEntity
import com.vague.crewtally.data.local.ProjectStatus
import com.vague.crewtally.data.local.ProjectSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory [ProjectDao] fake for headless JVM ViewModel tests. Insertion order is preserved
 * (updates replace in place, never reordering) so [getMostRecentCurrency] can mimic the real
 * DAO's `ORDER BY rowid DESC` behavior without a real SQLite engine.
 */
class FakeProjectDao : ProjectDao {
    private val entries = mutableListOf<ProjectEntity>()
    private val entriesFlow = MutableStateFlow<List<ProjectEntity>>(emptyList())

    /** Test-controlled join data for [observeSummariesByStatus] — set by the test as needed. */
    var companyNames: Map<String, String> = emptyMap()
    var rosterSizes: Map<String, Int> = emptyMap()

    override suspend fun upsert(project: ProjectEntity) {
        val index = entries.indexOfFirst { it.id == project.id }
        if (index >= 0) entries[index] = project else entries.add(project)
        entriesFlow.value = entries.toList()
    }

    override suspend fun delete(project: ProjectEntity) {
        entries.removeAll { it.id == project.id }
        entriesFlow.value = entries.toList()
    }

    override fun observeAll(): Flow<List<ProjectEntity>> = entriesFlow

    override fun observeByCompany(companyId: String): Flow<List<ProjectEntity>> =
        entriesFlow.map { list -> list.filter { it.companyId == companyId } }

    override fun observeById(id: String): Flow<ProjectEntity?> =
        entriesFlow.map { list -> list.find { it.id == id } }

    override suspend fun getById(id: String): ProjectEntity? = entries.find { it.id == id }

    override fun observeSummariesByStatus(status: ProjectStatus): Flow<List<ProjectSummary>> =
        entriesFlow.map { list ->
            list.filter { it.status == status }.map { project -> toSummary(project) }
        }

    override fun observeAllSummaries(): Flow<List<ProjectSummary>> =
        entriesFlow.map { list -> list.map { project -> toSummary(project) } }

    private fun toSummary(project: ProjectEntity): ProjectSummary = ProjectSummary(
        project = project,
        companyName = companyNames[project.companyId].orEmpty(),
        rosterSize = rosterSizes[project.id] ?: 0,
    )

    override suspend fun getMostRecentCurrency(): String? = entries.lastOrNull()?.currency

    override suspend fun countByCompany(companyId: String): Int =
        entries.count { it.companyId == companyId }

    override suspend fun getAll(): List<ProjectEntity> = entries.toList()

    override suspend fun upsertAll(projects: List<ProjectEntity>) {
        projects.forEach { project ->
            val index = entries.indexOfFirst { it.id == project.id }
            if (index >= 0) entries[index] = project else entries.add(project)
        }
        entriesFlow.value = entries.toList()
    }

    override suspend fun deleteAll() {
        entries.clear()
        entriesFlow.value = emptyList()
    }

    fun seed(vararg projects: ProjectEntity) {
        entries.addAll(projects)
        entriesFlow.value = entries.toList()
    }
}
