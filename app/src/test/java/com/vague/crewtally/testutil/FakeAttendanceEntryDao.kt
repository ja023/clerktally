package com.vague.crewtally.testutil

import com.vague.crewtally.data.local.AttendanceDaySummary
import com.vague.crewtally.data.local.AttendanceEntryDao
import com.vague.crewtally.data.local.AttendanceEntryEntity
import com.vague.crewtally.data.local.AttendanceRowWithName
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * Hand-written [AttendanceEntryDao] fake. Clerk names for the joined queries come from
 * [setClerkName]. [delete] simulates the schema's FK CASCADE by also dropping the entry's
 * extra-pay lines through the wired [extraPayLineDao], and [observeDaySummaries] reads the
 * same lines to sum a day's extras — so the Phase 3 tests exercise the real cascade and
 * summary math against the fakes.
 */
class FakeAttendanceEntryDao : AttendanceEntryDao {
    val entries = MutableStateFlow<List<AttendanceEntryEntity>>(emptyList())
    private val clerkNames = mutableMapOf<String, String>()

    /** Wired after construction so [delete] can cascade and [observeDaySummaries] can sum extras. */
    var extraPayLineDao: FakeExtraPayLineDao? = null

    override suspend fun upsert(entry: AttendanceEntryEntity) {
        entries.update { list -> list.filterNot { it.id == entry.id } + entry }
    }

    override suspend fun delete(entry: AttendanceEntryEntity) {
        entries.update { list -> list.filterNot { it.id == entry.id } }
        // Simulate ForeignKey.CASCADE: the entry's extra-pay lines go with it.
        extraPayLineDao?.deleteForAttendance(entry.id)
    }

    override fun observeByProjectAndDate(projectId: String, date: LocalDate): Flow<List<AttendanceEntryEntity>> =
        entries.map { list -> list.filter { it.projectId == projectId && it.date == date } }

    override fun observeDayWithNames(projectId: String, date: LocalDate): Flow<List<AttendanceRowWithName>> =
        entries.map { list ->
            list.filter { it.projectId == projectId && it.date == date }
                .map { AttendanceRowWithName(entry = it, clerkName = clerkNames[it.clerkId].orEmpty()) }
                .sortedBy { it.clerkName.lowercase() }
        }

    override fun observeByProjectClerkDate(
        projectId: String,
        clerkId: String,
        date: LocalDate,
    ): Flow<AttendanceEntryEntity?> =
        entries.map { list -> list.find { it.projectId == projectId && it.clerkId == clerkId && it.date == date } }

    override suspend fun getByProjectClerkDate(
        projectId: String,
        clerkId: String,
        date: LocalDate,
    ): AttendanceEntryEntity? =
        entries.value.find { it.projectId == projectId && it.clerkId == clerkId && it.date == date }

    override fun observeDaySummaries(projectId: String): Flow<List<AttendanceDaySummary>> {
        val linesFlow = extraPayLineDao?.lines ?: MutableStateFlow(emptyList())
        return combine(entries, linesFlow) { list, lines ->
            list.filter { it.projectId == projectId }
                .groupBy { it.date }
                .map { (date, dayEntries) ->
                    val dayEntryIds = dayEntries.map { it.id }.toSet()
                    AttendanceDaySummary(
                        date = date,
                        presentCount = dayEntries.count { it.present },
                        extrasTotal = lines.filter { it.attendanceEntryId in dayEntryIds }.sumOf { it.amount },
                    )
                }
                .sortedByDescending { it.date }
        }
    }

    override suspend fun getById(id: String): AttendanceEntryEntity? = entries.value.find { it.id == id }

    override suspend fun countByClerk(clerkId: String): Int =
        entries.value.count { it.clerkId == clerkId }

    fun setClerkName(clerkId: String, name: String) {
        clerkNames[clerkId] = name
    }

    fun seed(vararg entities: AttendanceEntryEntity) {
        entries.value = entities.toList()
    }

    fun all(): List<AttendanceEntryEntity> = entries.value
}
