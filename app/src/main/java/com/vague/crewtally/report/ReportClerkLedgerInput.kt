package com.vague.crewtally.report

import com.vague.crewtally.data.local.AttendanceEntryEntity
import com.vague.crewtally.data.local.ExtraPayLineWithDate
import com.vague.crewtally.data.local.PaymentEntity

/**
 * Raw per-clerk ledger rows for one project — the shared pure input to [ProjectStatementBuilder]
 * and the extended [CompanyTotalsBuilder] (v1.1). Unlike the Phase 5 company-report input it
 * replaces (which carried pre-summed earned/extras/paid totals), this carries the RAW rows,
 * because both v1.1 builders need day-level detail: [ProjectStatementBuilder] to filter by date
 * range, [CompanyTotalsBuilder] to additionally emit per-clerk-per-day activity lines.
 */
data class ReportClerkLedgerInput(
    val clerkId: String,
    val clerkName: String,
    val attendance: List<AttendanceEntryEntity> = emptyList(),
    val extras: List<ExtraPayLineWithDate> = emptyList(),
    val payments: List<PaymentEntity> = emptyList(),
)

/** [ReportClerkLedgerInput] after [ReportDateRange] filtering — internal to the two v1.1 builders. */
internal data class FilteredClerkLedger(
    val clerkId: String,
    val clerkName: String,
    val attendance: List<AttendanceEntryEntity>,
    val extras: List<ExtraPayLineWithDate>,
    val payments: List<PaymentEntity>,
) {
    /**
     * Whether this clerk has any row at all once the range is applied. A clerk who worked
     * outside the selected window drops off the range-scoped statement entirely (LOCKED v1.1:
     * "every total in the statement reflects the selected range only") rather than appearing as
     * an all-zero row.
     */
    val hasActivity: Boolean get() = attendance.isNotEmpty() || extras.isNotEmpty() || payments.isNotEmpty()
}

/** Applies [range] to one clerk's raw ledger, shared by both v1.1 builders. */
internal fun ReportClerkLedgerInput.filteredBy(range: ReportDateRange): FilteredClerkLedger = FilteredClerkLedger(
    clerkId = clerkId,
    clerkName = clerkName,
    attendance = attendance.filter { range.contains(it.date) },
    extras = extras.filter { range.contains(it.date) },
    payments = payments.filter { range.contains(it.date) },
)
