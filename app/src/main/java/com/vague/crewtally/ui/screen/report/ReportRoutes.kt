package com.vague.crewtally.ui.screen.report

/** Route templates for the Phase 5 report share screens. */
object ReportRoutes {
    const val PROJECT_ID = "projectId"
    const val CLERK_ID = "clerkId"
    const val COMPANY_ID = "companyId"

    const val CLERK_STATEMENT_TEMPLATE = "report/clerk/{$PROJECT_ID}/{$CLERK_ID}"
    const val COMPANY_REPORT_TEMPLATE = "report/company/{$COMPANY_ID}"

    fun clerkStatement(projectId: String, clerkId: String): String = "report/clerk/$projectId/$clerkId"

    fun companyReport(companyId: String): String = "report/company/$companyId"
}
