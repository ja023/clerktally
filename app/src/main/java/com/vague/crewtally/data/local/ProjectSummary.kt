package com.vague.crewtally.data.local

import androidx.room.Embedded

/**
 * A project row for the Projects tab list: the project itself plus the two fields the row
 * needs to render (company name, roster size) pre-joined by SQL so the list screen doesn't
 * have to combine per-project flows in Kotlin. [companyName] falls back to an empty string
 * if the company link is ever dangling (shouldn't happen — companies RESTRICT-delete while
 * referenced — but a LEFT JOIN keeps the row queryable rather than silently dropped).
 */
data class ProjectSummary(
    @Embedded val project: ProjectEntity,
    val companyName: String,
    val rosterSize: Int,
)
