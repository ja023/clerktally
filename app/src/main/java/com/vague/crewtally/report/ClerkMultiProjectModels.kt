package com.vague.crewtally.report

import com.vague.crewtally.data.local.AttendanceEntryEntity
import com.vague.crewtally.data.local.ExtraPayLineWithDate
import com.vague.crewtally.data.local.PaymentEntity
import com.vague.crewtally.data.local.ProjectStatus

/**
 * Which half of a clerk's project history a cross-project statement covers (LOCKED v1.2: "two
 * cross-project clerk statements"). ACTIVE is defined NEGATIVELY — "neither completed nor
 * archived" — so a status added to [ProjectStatus] later lands in ACTIVE by default rather than
 * silently disappearing from both statements.
 */
enum class ClerkProjectBucket {
    ACTIVE,
    HISTORY,
    ;

    fun includes(status: ProjectStatus): Boolean = when (this) {
        ACTIVE -> status != ProjectStatus.COMPLETED && status != ProjectStatus.ARCHIVED
        HISTORY -> status == ProjectStatus.COMPLETED || status == ProjectStatus.ARCHIVED
    }
}

/**
 * One project a clerk worked on, carrying that clerk's RAW ledger rows on it — the pure input to
 * [ClerkMultiProjectStatementBuilder]. Raw rows (not pre-summed totals) because the builder both
 * date-range-filters them and emits the same dated day/extra/payment lines the per-project
 * [ClerkStatement] shows.
 */
data class ClerkProjectLedgerInput(
    val projectId: String,
    val projectName: String,
    val companyName: String,
    val currency: String,
    val status: ProjectStatus,
    val attendance: List<AttendanceEntryEntity> = emptyList(),
    val extras: List<ExtraPayLineWithDate> = emptyList(),
    val payments: List<PaymentEntity> = emptyList(),
)

/**
 * One project section of a cross-project clerk statement. It wraps a full per-project
 * [ClerkStatement] rather than re-deriving its own totals, so a section inside the combined
 * document and the standalone per-project statement for the same project can never disagree.
 */
data class ClerkMultiProjectSection(val projectId: String, val statement: ClerkStatement)

/**
 * One currency's grand total across a statement's sections. Cross-currency amounts are NEVER
 * summed (LOCKED) — there is one of these per currency, never a combined figure.
 *
 * [earned] is base attendance pay and [extras] the signed extra-pay total, kept apart for the
 * same reason [ClerkStatement] keeps them apart; the rendered "earned" figure is their sum.
 */
data class ClerkCurrencyTotal(val currency: String, val earned: Long, val extras: Long, val paid: Long) {
    /** owed = earned + extras − paid, same convention as [com.vague.crewtally.balance.BalanceCalculator.owed]. */
    val owed: Long get() = earned + extras - paid
}

/**
 * A cross-project clerk statement (NEW v1.2): one document per [bucket], with a per-project
 * section carrying the same dated lines and subtotal as the per-project statement, then grand
 * totals grouped per currency. [range]-scoped throughout (LOCKED v1.1 range presets).
 *
 * A bucket with no projects — or no in-range activity on any of them — yields empty lists rather
 * than a null document; the share screen shows its empty message and offers nothing to share.
 */
data class ClerkMultiProjectStatement(
    val clerkName: String,
    val bucket: ClerkProjectBucket,
    val range: ReportDateRange,
    val sections: List<ClerkMultiProjectSection>,
    val grandTotalsByCurrency: List<ClerkCurrencyTotal>,
) {
    val isEmpty: Boolean get() = sections.isEmpty()
}
