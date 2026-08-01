package com.vague.crewtally.testutil

import com.vague.crewtally.data.local.CompanyDao
import com.vague.crewtally.data.local.CompanyEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** In-memory [CompanyDao] fake for headless JVM ViewModel tests. */
class FakeCompanyDao : CompanyDao {
    private val entries = mutableListOf<CompanyEntity>()
    private val entriesFlow = MutableStateFlow<List<CompanyEntity>>(emptyList())

    override suspend fun upsert(company: CompanyEntity) {
        val index = entries.indexOfFirst { it.id == company.id }
        if (index >= 0) entries[index] = company else entries.add(company)
        entriesFlow.value = entries.toList()
    }

    override suspend fun delete(company: CompanyEntity) {
        entries.removeAll { it.id == company.id }
        entriesFlow.value = entries.toList()
    }

    override fun observeAll(): Flow<List<CompanyEntity>> = entriesFlow

    override fun observeActive(): Flow<List<CompanyEntity>> =
        entriesFlow.map { list -> list.filter { !it.archived } }

    override fun observeById(id: String): Flow<CompanyEntity?> =
        entriesFlow.map { list -> list.find { it.id == id } }

    override suspend fun getById(id: String): CompanyEntity? = entries.find { it.id == id }

    fun seed(vararg companies: CompanyEntity) {
        entries.addAll(companies)
        entriesFlow.value = entries.toList()
    }
}
