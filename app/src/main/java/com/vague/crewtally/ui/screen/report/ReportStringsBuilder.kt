package com.vague.crewtally.ui.screen.report

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.vague.crewtally.R
import com.vague.crewtally.report.ReportStrings

/**
 * Resolves every label [com.vague.crewtally.report.ReportLines] needs from `strings.xml` once,
 * for the share-screen ViewModels' factories to close over. See [ReportStrings]'s KDoc for why
 * this indirection exists — the report package is pure Kotlin with no [android.content.Context].
 */
@Composable
fun buildReportStrings(): ReportStrings = ReportStrings(
    appName = stringResource(R.string.app_name),
    clerkStatementTitle = stringResource(R.string.report_clerk_statement_title),
    companyReportTitle = stringResource(R.string.report_company_report_title),
    daysWorkedLabel = stringResource(R.string.report_days_worked_label),
    extrasLabel = stringResource(R.string.report_extras_label),
    paymentsLabel = stringResource(R.string.report_payments_label),
    earnedLabel = stringResource(R.string.report_earned_label),
    paidLabel = stringResource(R.string.report_paid_label),
    owedLabel = stringResource(R.string.report_owed_label),
    deductionLabel = stringResource(R.string.report_deduction_label),
    presentLabel = stringResource(R.string.report_present_label),
    absentLabel = stringResource(R.string.report_absent_label),
    noneRecordedLabel = stringResource(R.string.report_none_recorded),
    projectLabel = stringResource(R.string.report_project_label),
    grandTotalLabel = stringResource(R.string.report_grand_total_label),
    owedPhraseOwedTemplate = stringResource(R.string.owed_phrase_owed),
    owedPhraseAdvanceTemplate = stringResource(R.string.owed_phrase_advance),
    owedPhraseSettled = stringResource(R.string.owed_phrase_settled),
    summaryLineTemplate = stringResource(R.string.report_summary_line_template),
    pageLabelTemplate = stringResource(R.string.report_page_label_template),
)
