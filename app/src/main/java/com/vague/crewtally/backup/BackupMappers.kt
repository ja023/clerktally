package com.vague.crewtally.backup

import com.vague.crewtally.data.local.AttendanceEntryEntity
import com.vague.crewtally.data.local.ClerkEntity
import com.vague.crewtally.data.local.CompanyEntity
import com.vague.crewtally.data.local.ExtraPayLineEntity
import com.vague.crewtally.data.local.PaymentEntity
import com.vague.crewtally.data.local.ProjectEntity
import com.vague.crewtally.data.local.ProjectStatus
import com.vague.crewtally.data.local.RosterEntryEntity
import java.time.LocalDate

// --- Entity -> Dto (export) -----------------------------------------------------------------

fun CompanyEntity.toDto(): CompanyDto = CompanyDto(id, name, contactPerson, phone, notes, archived)

fun ClerkEntity.toDto(): ClerkDto = ClerkDto(id, name, phone, notes, active)

fun ProjectEntity.toDto(): ProjectDto =
    ProjectDto(id, companyId, name, location, startDate.toString(), endDate?.toString(), status.name, currency, notes)

fun RosterEntryEntity.toDto(): RosterEntryDto = RosterEntryDto(id, projectId, clerkId, dailyRate, removedAt)

fun AttendanceEntryEntity.toDto(): AttendanceEntryDto =
    AttendanceEntryDto(id, projectId, clerkId, date.toString(), present, rateSnapshot, explicitlyMarked)

fun ExtraPayLineEntity.toDto(): ExtraPayLineDto = ExtraPayLineDto(id, attendanceEntryId, label, amount)

fun PaymentEntity.toDto(): PaymentDto = PaymentDto(id, projectId, clerkId, date.toString(), amount, note)

// --- Dto -> Entity (restore) -----------------------------------------------------------------
// Each throws (never silently coerces) on a malformed value — [BackupValidator] runs first and
// is expected to catch every case; these are the last line of defense that turns a slipped-past
// bad value into a clear failure INSIDE the restore transaction (which then rolls back) rather
// than a corrupted row.

fun CompanyDto.toEntity(): CompanyEntity = CompanyEntity(id, name, contactPerson, phone, notes, archived)

fun ClerkDto.toEntity(): ClerkEntity = ClerkEntity(id, name, phone, notes, active)

fun ProjectDto.toEntity(): ProjectEntity = ProjectEntity(
    id = id,
    companyId = companyId,
    name = name,
    location = location,
    startDate = LocalDate.parse(startDate),
    endDate = endDate?.let(LocalDate::parse),
    status = ProjectStatus.valueOf(status),
    currency = currency,
    notes = notes,
)

fun RosterEntryDto.toEntity(): RosterEntryEntity = RosterEntryEntity(id, projectId, clerkId, dailyRate, removedAt)

fun AttendanceEntryDto.toEntity(): AttendanceEntryEntity =
    AttendanceEntryEntity(id, projectId, clerkId, LocalDate.parse(date), present, rateSnapshot, explicitlyMarked)

fun ExtraPayLineDto.toEntity(): ExtraPayLineEntity = ExtraPayLineEntity(id, attendanceEntryId, label, amount)

fun PaymentDto.toEntity(): PaymentEntity = PaymentEntity(id, projectId, clerkId, LocalDate.parse(date), amount, note)
