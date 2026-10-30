package com.example.persona.features.contacts.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.persona.core.database.dao.ConversationMetadataDao
import com.example.persona.core.database.entity.ContactEntity
import com.example.persona.core.database.entity.ContactTag
import com.example.persona.features.contacts.domain.model.ContactStatsSummary
import com.example.persona.features.contacts.domain.repository.ContactRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ContactsViewModel @Inject constructor(
    private val contactRepository: ContactRepository,
    private val conversationMetadataDao: ConversationMetadataDao? = null
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedTagFilter = MutableStateFlow<ContactTag?>(null)
    val selectedTagFilter: StateFlow<ContactTag?> = _selectedTagFilter.asStateFlow()

    private val _selectedContactForEdit = MutableStateFlow<ContactEntity?>(null)
    val selectedContactForEdit: StateFlow<ContactEntity?> = _selectedContactForEdit.asStateFlow()

    private val _isLoading = MutableStateFlow(false)

    val statsSummary: StateFlow<ContactStatsSummary> = contactRepository
        .observeStatsSummary()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ContactStatsSummary()
        )

    @OptIn(kotlinx.coroutines.FlowPreview::class)
    private val _debouncedSearchQuery: Flow<String> = _searchQuery.debounce { query ->
        if (query.isBlank()) 0L else 300L
    }

    private val _filteredContacts: Flow<List<ContactEntity>> = combine(
        _debouncedSearchQuery,
        _selectedTagFilter
    ) { query, tag ->
        query to tag
    }.flatMapLatest { (query, tag) ->
        contactRepository.observeContacts(query = query, tagFilter = tag)
    }

    private data class FilterParams(
        val query: String,
        val tag: ContactTag?
    )

    private val _filterParams = combine(_searchQuery, _selectedTagFilter) { query, tag ->
        FilterParams(query, tag)
    }

    val uiState: StateFlow<ContactsUiState> = combine(
        _filteredContacts,
        _filterParams,
        statsSummary,
        _selectedContactForEdit,
        _isLoading
    ) { contacts, filter, stats, editContact, loading ->
        ContactsUiState(
            contacts = contacts,
            searchQuery = filter.query,
            selectedTagFilter = filter.tag,
            statsSummary = stats,
            isLoading = loading,
            selectedContactForEdit = editContact
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ContactsUiState(isLoading = true)
    )

    init {
        syncFromConversations()
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun onTagFilterSelected(tag: ContactTag?) {
        _selectedTagFilter.value = tag
    }

    fun onSelectContactForEdit(contact: ContactEntity?) {
        _selectedContactForEdit.value = contact
    }

    fun onUpdateContact(id: Long, newTag: ContactTag, newNotes: String, newPhone: String) {
        viewModelScope.launch {
            contactRepository.updateTag(id, newTag)
            contactRepository.updateNotes(id, newNotes)
            contactRepository.updatePhone(id, newPhone)
            _selectedContactForEdit.value = null
        }
    }

    fun onSaveNewContact(name: String, phone: String, tag: ContactTag, notes: String) {
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) return
        viewModelScope.launch {
            val entity = ContactEntity(
                contactName = trimmedName,
                phoneNumber = phone.trim(),
                tag = tag.name,
                notes = notes.trim(),
                lastActiveTimestamp = System.currentTimeMillis()
            )
            contactRepository.upsertContact(entity)
        }
    }

    fun onDeleteContact(id: Long) {
        viewModelScope.launch {
            contactRepository.deleteContact(id)
            if (_selectedContactForEdit.value?.id == id) {
                _selectedContactForEdit.value = null
            }
        }
    }

    /**
     * Seeds CRM contacts from existing WhatsApp conversation metadata if not already registered.
     */
    fun syncFromConversations() {
        val dao = conversationMetadataDao ?: return
        viewModelScope.launch {
            try {
                val conversations = dao.observeAllConversations().first()
                for (conv in conversations) {
                    val existing = contactRepository.getContactByName(conv.contactName)
                    if (existing == null) {
                        contactRepository.upsertContact(
                            ContactEntity(
                                contactName = conv.contactName,
                                phoneNumber = if (conv.contactName.startsWith("+") || conv.contactName.startsWith("01")) conv.contactName else "",
                                tag = ContactTag.REGULAR.name,
                                lastActiveTimestamp = conv.lastMessageTimestamp
                            )
                        )
                    }
                }
            } catch (_: Exception) {
                // Non-blocking sync
            }
        }
    }
}
