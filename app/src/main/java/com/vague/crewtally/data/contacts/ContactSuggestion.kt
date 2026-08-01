package com.vague.crewtally.data.contacts

/** One device-contact match offered by [ContactsSearcher]: enough to fill a name+phone pair. */
data class ContactSuggestion(
    val id: String,
    val name: String,
    val phone: String,
)
