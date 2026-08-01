package com.vague.crewtally.testutil

import com.vague.crewtally.data.local.ProjectDao
import com.vague.crewtally.data.local.ProjectEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** Hand-written [ProjectDao] fake — only [countByCompany] is exercised by Phase 1 tests. */
class FakeProjectDao : ProjectDao {
    private val projects = MutableStateFlow<List<ProjectEntity>>(emptyList())

    override suspend fun upsert(project: ProjectEntity) {
        projects.update { list -> list.filterNot { it.id == project.id } + project }
    }

    override suspend fun delete(project: ProjectEntity) {
        projects.update { list -> list.filterNot { it.id == project.id } }
    }

    override fun observeAll(): Flow<List<ProjectEntity>> =
        projects.map { list -> list.sortedByDescending { it.startDate } }

    override fun observeByCompany(companyId: String): Flow<List<ProjectEntity>> =
        projects.map { list -> list.filter { it.companyId == companyId } }

    override fun observeById(id: String): Flow<ProjectEntity?> =
        projects.map { list -> list.find { it.id == id } }

    override suspend fun getById(id: String): ProjectEntity? = projects.value.find { it.id == id }

    override suspend fun countByCompany(companyId: String): Int =
        projects.value.count { it.companyId == companyId }

    fun seed(vararg entities: ProjectEntity) {
        projects.value = entities.toList()
    }
}
