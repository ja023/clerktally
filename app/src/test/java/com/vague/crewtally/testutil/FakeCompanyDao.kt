package com.vague.crewtally.testutil

import com.vague.crewtally.data.local.CompanyDao
import com.vague.crewtally.data.local.CompanyEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** Hand-written [CompanyDao] fake, mirroring [FakeClerkDao]'s shape. */
class FakeCompanyDao : CompanyDao {
    private val companies = MutableStateFlow<List<CompanyEntity>>(emptyList())

    override suspend fun upsert(company: CompanyEntity) {
        companies.update { list -> list.filterNot { it.id == company.id } + company }
    }

    override suspend fun delete(company: CompanyEntity) {
        companies.update { list -> list.filterNot { it.id == company.id } }
    }

    override fun observeAll(): Flow<List<CompanyEntity>> =
        companies.map { list -> list.sortedBy { it.name.lowercase() } }

    override fun observeActive(): Flow<List<CompanyEntity>> =
        companies.map { list -> list.filterNot { it.archived }.sortedBy { it.name.lowercase() } }

    override fun observeById(id: String): Flow<CompanyEntity?> =
        companies.map { list -> list.find { it.id == id } }

    override suspend fun getById(id: String): CompanyEntity? = companies.value.find { it.id == id }

    fun seed(vararg entities: CompanyEntity) {
        companies.value = entities.toList()
    }

    /** Test assertion helper — the store's current contents, unsorted and unfiltered. */
    fun all(): List<CompanyEntity> = companies.value
}
