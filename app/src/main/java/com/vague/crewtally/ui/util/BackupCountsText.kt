package com.vague.crewtally.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import com.vague.crewtally.R
import com.vague.crewtally.backup.BackupCounts

/**
 * Joins already-resolved plural count phrases into one sentence fragment, e.g.
 * "1 company, 3 clerks, 0 projects, ...". Pure and unit-testable without Android resources;
 * [backupCountsSentence] is the resource-resolving half that calls it.
 */
fun assembleBackupCountsSentence(phrases: List<String>): String = phrases.joinToString(", ")

/**
 * The full plural-correct "N companies, N clerks, ..." fragment shared by the restore confirm
 * dialog and the restore success panel (LOCKED: never "1 companies" — mirrors
 * [R.plurals.roster_size]'s pattern, one `<plurals>` resource per noun).
 */
@Composable
fun backupCountsSentence(counts: BackupCounts): String = assembleBackupCountsSentence(
    listOf(
        pluralStringResource(R.plurals.backup_count_companies, counts.companies, counts.companies),
        pluralStringResource(R.plurals.backup_count_clerks, counts.clerks, counts.clerks),
        pluralStringResource(R.plurals.backup_count_projects, counts.projects, counts.projects),
        pluralStringResource(R.plurals.backup_count_roster_entries, counts.rosterEntries, counts.rosterEntries),
        pluralStringResource(
            R.plurals.backup_count_attendance_records,
            counts.attendanceEntries,
            counts.attendanceEntries,
        ),
        pluralStringResource(R.plurals.backup_count_extra_pay_lines, counts.extraPayLines, counts.extraPayLines),
        pluralStringResource(R.plurals.backup_count_payments, counts.payments, counts.payments),
    ),
)
