package com.vague.crewtally.ui.screen.money

/**
 * Route templates and type-safe builders for the Phase 4 money destinations: a clerk's
 * per-project balance, the payment form (record and edit), and the clerk profile. Ids travel as
 * plain path segments (UUIDs — no encoding needed). The balance and profile screens read the
 * clerk/project names and currency live from their ViewModels, so those are not route args.
 */
object MoneyRoutes {
    const val PROJECT_ID = "projectId"
    const val CLERK_ID = "clerkId"
    const val CLERK_NAME = "clerkName"
    const val PAYMENT_ID = "paymentId"

    const val BALANCE_TEMPLATE = "project/{$PROJECT_ID}/clerk/{$CLERK_ID}/balance"
    const val PAYMENT_NEW_TEMPLATE = "project/{$PROJECT_ID}/clerk/{$CLERK_ID}/payment/new/{$CLERK_NAME}"
    const val PAYMENT_EDIT_TEMPLATE =
        "project/{$PROJECT_ID}/clerk/{$CLERK_ID}/payment/{$PAYMENT_ID}/edit/{$CLERK_NAME}"
    const val PROFILE_TEMPLATE = "clerk/{$CLERK_ID}/profile"

    fun balance(projectId: String, clerkId: String): String =
        "project/$projectId/clerk/$clerkId/balance"

    fun paymentNew(projectId: String, clerkId: String, clerkName: String): String =
        "project/$projectId/clerk/$clerkId/payment/new/${android.net.Uri.encode(clerkName)}"

    fun paymentEdit(projectId: String, clerkId: String, paymentId: String, clerkName: String): String =
        "project/$projectId/clerk/$clerkId/payment/$paymentId/edit/${android.net.Uri.encode(clerkName)}"

    fun profile(clerkId: String): String = "clerk/$clerkId/profile"
}
