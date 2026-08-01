package com.vague.crewtally.ui.util

/**
 * Case-insensitive "does this record match the search box" check, shared by the Clerks and
 * Companies list ViewModels. A blank query matches everything (an empty search box shows
 * the full list, not nothing). Pure and dependency-free so it's covered by a plain JVM test
 * without any Android framework or database involved.
 */
fun matchesSearch(query: String, vararg fields: String): Boolean {
    val term = query.trim()
    if (term.isEmpty()) return true
    return fields.any { it.contains(term, ignoreCase = true) }
}
