package com.vague.crewtally.testutil

import com.vague.crewtally.data.local.AttendanceExtraTotal
import com.vague.crewtally.data.local.ExtraPayLineDao
import com.vague.crewtally.data.local.ExtraPayLineEntity
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * Hand-written [ExtraPayLineDao] fake. Its day-scoped queries need to know which entries fall
 * on a project+date, so it resolves that through the wired [attendance] fake — mirroring the
 * real DAO's join against attendance_entries.
 */
class FakeExtraPayLineDao(private val attendance: FakeAttendanceEntryDao) : ExtraPayLineDao {
    val lines = MutableStateFlow<List<ExtraPayLineEntity>>(emptyList())

    override suspend fun upsert(line: ExtraPayLineEntity) {
        lines.update { list -> list.filterNot { it.id == line.id } + line }
    }

    override suspend fun delete(line: ExtraPayLineEntity) {
        lines.update { list -> list.filterNot { it.id == line.id } }
    }

    override fun observeForAttendance(attendanceEntryId: String): Flow<List<ExtraPayLineEntity>> =
        lines.map { list -> list.filter { it.attendanceEntryId == attendanceEntryId } }

    override suspend fun getForAttendance(attendanceEntryId: String): List<ExtraPayLineEntity> =
        lines.value.filter { it.attendanceEntryId == attendanceEntryId }

    override fun observeDayExtraTotals(projectId: String, date: LocalDate): Flow<List<AttendanceExtraTotal>> =
        combine(lines, attendance.entries) { list, entries ->
            val dayEntryIds = entries
                .filter { it.projectId == projectId && it.date == date }
                .map { it.id }
                .toSet()
            list.filter { it.attendanceEntryId in dayEntryIds }
                .groupBy { it.attendanceEntryId }
                .map { (entryId, group) -> AttendanceExtraTotal(entryId, group.sumOf { it.amount }) }
        }

    /** Simulates FK CASCADE: called from [FakeAttendanceEntryDao.delete] when an entry is removed. */
    fun deleteForAttendance(attendanceEntryId: String) {
        lines.update { list -> list.filterNot { it.attendanceEntryId == attendanceEntryId } }
    }

    fun seed(vararg entities: ExtraPayLineEntity) {
        lines.value = entities.toList()
    }

    fun all(): List<ExtraPayLineEntity> = lines.value
}
