package com.example.persona.features.contacts.presentation

import com.example.persona.core.database.entity.ContactEntity
import com.example.persona.core.database.entity.ContactTag
import com.example.persona.features.contacts.domain.model.ContactStatsSummary

data class ContactsUiState(
    val contacts: List<ContactEntity> = emptyList(),
    val searchQuery: String = "",
    val selectedTagFilter: ContactTag? = null,
    val statsSummary: ContactStatsSummary = ContactStatsSummary(),
    val isLoading: Boolean = false,
    val selectedContactForEdit: ContactEntity? = null
)
