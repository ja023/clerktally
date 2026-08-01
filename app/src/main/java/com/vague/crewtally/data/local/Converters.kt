package com.vague.crewtally.data.local

import androidx.room.TypeConverter
import java.time.LocalDate

/**
 * Room type converters for the CrewTally schema.
 *
 * - [LocalDate] is stored as an ISO-8601 string (`"2026-08-01"`), not epoch millis: dates
 *   here are calendar days (attendance day, project start/end) with no time or zone, and a
 *   text date sorts and reads correctly without any zone math.
 * - [ProjectStatus] is stored as its enum constant name, so a stored value stays valid
 *   regardless of enum declaration order.
 *
 * Every converter is null-safe so it serves both nullable and non-null columns.
 */
class Converters {

    @TypeConverter
    fun fromLocalDate(value: LocalDate?): String? = value?.toString()

    @TypeConverter
    fun toLocalDate(value: String?): LocalDate? = value?.let(LocalDate::parse)

    @TypeConverter
    fun fromProjectStatus(value: ProjectStatus?): String? = value?.name

    @TypeConverter
    fun toProjectStatus(value: String?): ProjectStatus? = value?.let(ProjectStatus::valueOf)
}
