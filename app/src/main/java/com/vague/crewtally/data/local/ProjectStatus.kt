package com.vague.crewtally.data.local

/**
 * Lifecycle of a project. Persisted as its [name] string via [Converters] so the on-disk
 * value is stable and human-readable, and reordering the enum never corrupts stored rows.
 */
enum class ProjectStatus {
    ACTIVE,
    COMPLETED,
    ARCHIVED,
}
