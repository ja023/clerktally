package com.vague.crewtally.data.local

/**
 * One component sum for a single (project, clerk) pair, produced by the money roll-up queries
 * (earnings, extras, or payments). Three of these lists — one per component — are folded into
 * a per-pair balance by [com.vague.crewtally.balance.BalanceCalculator.rollup]; keeping the
 * three sums as separate SQL GROUP BYs and joining them in pure Kotlin avoids a gnarly
 * three-way outer join in SQLite (which has no FULL OUTER JOIN) while staying trivially
 * testable. [amount] is in MINOR units and may be negative (an extras roll-up can net negative
 * from deductions).
 */
data class ClerkProjectAmount(
    val projectId: String,
    val clerkId: String,
    val amount: Long,
)
