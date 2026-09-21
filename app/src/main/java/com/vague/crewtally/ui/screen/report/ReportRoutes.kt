package com.vague.crewtally.ui.screen.report

import com.vague.crewtally.report.ClerkProjectBucket

/** Route templates for the Phase 5 (+ v1.1, v1.2) report share screens. */
object ReportRoutes {
    const val PROJECT_ID = "projectId"
    const val CLERK_ID = "clerkId"
    const val COMPANY_ID = "companyId"

    const val REPORTS_HUB = "report/hub"
    const val PROJECT_STATEMENT_PICKER = "report/project/pick"
    const val COMPANY_STATEMENT_PICKER = "report/company/pick"

    const val CLERK_STATEMENT_TEMPLATE = "report/clerk/{$PROJECT_ID}/{$CLERK_ID}"
    const val COMPANY_REPORT_TEMPLATE = "report/company/{$COMPANY_ID}"
    const val PROJECT_STATEMENT_TEMPLATE = "report/project/{$PROJECT_ID}"

    /**
     * The two v1.2 cross-project clerk statements. Each bucket is its own route with a LITERAL
     * last segment rather than one shared `{bucket}` argument: that keeps them unambiguous
     * against [CLERK_STATEMENT_TEMPLATE], which sits at the same depth with two argument
     * segments — androidx's route scoring prefers the literal match (the same property the v1.1
     * `report/project/pick` route relies on).
     */
    const val CLERK_ACTIVE_STATEMENT_TEMPLATE = "report/clerk/{$CLERK_ID}/active"
    const val CLERK_HISTORY_STATEMENT_TEMPLATE = "report/clerk/{$CLERK_ID}/history"

    fun clerkStatement(projectId: String, clerkId: String): String = "report/clerk/$projectId/$clerkId"

    fun clerkBucketStatement(clerkId: String, bucket: ClerkProjectBucket): String = when (bucket) {
        ClerkProjectBucket.ACTIVE -> "report/clerk/$clerkId/active"
        ClerkProjectBucket.HISTORY -> "report/clerk/$clerkId/history"
    }

    fun companyReport(companyId: String): String = "report/company/$companyId"

    fun projectStatement(projectId: String): String = "report/project/$projectId"
}
