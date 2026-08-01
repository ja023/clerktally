package com.vague.crewtally.testutil

import com.vague.crewtally.data.local.AttendanceEntryDao
import com.vague.crewtally.data.local.AttendanceEntryEntity
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** Hand-written [AttendanceEntryDao] fake — only [countByClerk] is exercised by Phase 1 tests. */
class FakeAttendanceEntryDao : AttendanceEntryDao {
    private val entries = MutableStateFlow<List<AttendanceEntryEntity>>(emptyList())

    override suspend fun upsert(entry: AttendanceEntryEntity) {
        entries.update { list -> list.filterNot { it.id == entry.id } + entry }
    }

    override suspend fun delete(entry: AttendanceEntryEntity) {
        entries.update { list -> list.filterNot { it.id == entry.id } }
    }

    override fun observeByProjectAndDate(projectId: String, date: LocalDate): Flow<List<AttendanceEntryEntity>> =
        entries.map { list -> list.filter { it.projectId == projectId && it.date == date } }

    override fun observeForClerkOnProject(projectId: String, clerkId: String): Flow<List<AttendanceEntryEntity>> =
        entries.map { list -> list.filter { it.projectId == projectId && it.clerkId == clerkId } }

    override suspend fun getById(id: String): AttendanceEntryEntity? = entries.value.find { it.id == id }

    override suspend fun countByClerk(clerkId: String): Int =
        entries.value.count { it.clerkId == clerkId }

    fun seed(vararg entities: AttendanceEntryEntity) {
        entries.value = entities.toList()
    }
}
