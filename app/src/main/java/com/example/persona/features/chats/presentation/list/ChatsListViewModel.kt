package com.example.persona.features.chats.presentation.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.persona.core.database.dao.ChatHistoryDao
import com.example.persona.core.database.dao.ContactDao
import com.example.persona.core.database.dao.ConversationMetadataDao
import com.example.persona.core.database.dao.OrderDao
import com.example.persona.core.database.entity.ConversationMetadataEntity
import com.example.persona.features.orders.domain.EgyptianOrderUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import javax.inject.Inject

enum class ChatFilter(
    val label: String,
    @androidx.annotation.StringRes val labelResId: Int
) {
    ALL("All", com.example.persona.R.string.chat_filter_all),
    MAIN_APP("Main WhatsApp", com.example.persona.R.string.chat_filter_main_app),
    BUSINESS("WhatsApp Business", com.example.persona.R.string.chat_filter_business),
    CLONE("Dual / Clone", com.example.persona.R.string.chat_filter_clone),
    NEEDS_ATTENTION("Needs Attention", com.example.persona.R.string.chat_filter_needs_attention)
}

data class ConversationItemUiModel(
    val contactName: String,
    val sourceClass: String,
    val sourceLabel: String,
    val lastMessageSnippet: String,
    val formattedTime: String,
    val unreadCount: Int,
    val needsAttention: Boolean,
    val isAiPaused: Boolean
)

data class ChatsListUiState(
    val conversations: List<ConversationItemUiModel> = emptyList(),
    val searchQuery: String = "",
    val selectedFilter: ChatFilter = ChatFilter.ALL,
    val totalUnreadCount: Int = 0,
    val attentionCount: Int = 0
)

@HiltViewModel
class ChatsListViewModel @Inject constructor(
    private val conversationMetadataDao: ConversationMetadataDao,
    private val chatHistoryDao: ChatHistoryDao? = null,
    private val contactDao: ContactDao? = null,
    private val orderDao: OrderDao? = null
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _selectedFilter = MutableStateFlow(ChatFilter.ALL)
    val selectedFilter: StateFlow<ChatFilter> = _selectedFilter

    val uiState: StateFlow<ChatsListUiState> = combine(
        conversationMetadataDao.observeAllConversations(),
        _searchQuery,
        _selectedFilter,
        conversationMetadataDao.observeTotalUnreadCount(),
        conversationMetadataDao.observeTotalAttentionCount()
    ) { allConversations, query, filter, totalUnread, attentionCount ->
        val filtered = allConversations.filter { conv ->
            val matchesFilter = when (filter) {
                ChatFilter.ALL -> true
                ChatFilter.MAIN_APP -> conv.sourceClass.equals("MAIN_APP", ignoreCase = true)
                ChatFilter.BUSINESS -> conv.sourceClass.equals("BUSINESS", ignoreCase = true)
                ChatFilter.CLONE -> conv.sourceClass.equals("CLONE", ignoreCase = true)
                ChatFilter.NEEDS_ATTENTION -> conv.needsHumanAttention || conv.unreadCount > 0
            }

            val matchesQuery = if (query.isBlank()) {
                true
            } else {
                conv.contactName.contains(query, ignoreCase = true) ||
                    (conv.lastMessageText?.contains(query, ignoreCase = true) == true)
            }

            matchesFilter && matchesQuery
        }.map { it.toUiModel() }

        ChatsListUiState(
            conversations = filtered,
            searchQuery = query,
            selectedFilter = filter,
            totalUnreadCount = totalUnread,
            attentionCount = attentionCount
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ChatsListUiState()
    )

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    init {
        syncConversationsWithContacts()
    }

    fun syncConversationsWithContacts() {
        val cDao = contactDao ?: return
        viewModelScope.launch {
            try {
                val contacts = cDao.observeAllContacts().first()
                for (contact in contacts) {
                    val existing = conversationMetadataDao.getLatestConversationForContact(contact.contactName)
                    if (existing == null) {
                        // Check if an existing conversation exists under the raw phone number
                        val normalizedPhone = EgyptianOrderUtils.extractEgyptianMobilePhone(contact.phoneNumber)
                        val phoneCandidate = contact.phoneNumber.takeIf { it.isNotBlank() }
                        val rawPhoneConv = phoneCandidate?.let { conversationMetadataDao.getLatestConversationForContact(it) }
                            ?: normalizedPhone?.let { conversationMetadataDao.getLatestConversationForContact(it) }

                        if (rawPhoneConv != null) {
                            // Automatically merge raw phone conversation into this named contact
                            mergeConversations(rawPhoneConv.contactName, contact.contactName)
                        } else {
                            conversationMetadataDao.upsert(
                                ConversationMetadataEntity(
                                    contactName = contact.contactName,
                                    sourceClass = "BUSINESS",
                                    lastMessageTimestamp = contact.lastActiveTimestamp,
                                    lastMessageText = null,
                                    lastMessageSenderType = ""
                                )
                            )
                        }
                    }
                }
            } catch (_: Exception) {
                // Graceful fallback for tests or missing tables
            }
        }
    }

    fun deleteConversation(contactName: String, sourceClass: String) {
        viewModelScope.launch {
            chatHistoryDao?.deleteMessagesSince(contactName, sourceClass, 0L)
            conversationMetadataDao.deleteConversation(contactName, sourceClass)
            contactDao?.deleteContactByName(contactName)
        }
    }

    /**
     * Merges an unsaved/temporary contact conversation (like a raw phone number) into
     * an existing or named contact conversation.
     * Reassigns all chat history and orders to [targetContactName], then cleans up the old metadata.
     */
    fun mergeConversations(sourceContactName: String, targetContactName: String) {
        if (sourceContactName.equals(targetContactName, ignoreCase = true)) return
        viewModelScope.launch {
            try {
                chatHistoryDao?.reassignContactMessages(sourceContactName, targetContactName)
                orderDao?.reassignContactOrders(sourceContactName, targetContactName)
                conversationMetadataDao.deleteAllConversationsForContact(sourceContactName)
                contactDao?.deleteContactByName(sourceContactName)
            } catch (_: Exception) {
            }
        }
    }

    fun onFilterSelected(filter: ChatFilter) {
        _selectedFilter.value = filter
    }

    private fun ConversationMetadataEntity.toUiModel(): ConversationItemUiModel {
        val prefix = when (lastMessageSenderType.uppercase()) {
            "AI_AGENT" -> "AI: "
            "MERCHANT_MANUAL" -> "You: "
            else -> ""
        }
        val snippet = lastMessageText?.let { "$prefix$it" } ?: "No messages yet"

        val formattedTime = formatTimestamp(lastMessageTimestamp)
        val sourceLabel = when (sourceClass.uppercase()) {
            "MAIN_APP" -> "Main"
            "BUSINESS" -> "Business"
            "CLONE" -> "Clone"
            else -> sourceClass
        }

        return ConversationItemUiModel(
            contactName = contactName,
            sourceClass = sourceClass,
            sourceLabel = sourceLabel,
            lastMessageSnippet = snippet,
            formattedTime = formattedTime,
            unreadCount = unreadCount,
            needsAttention = needsHumanAttention,
            isAiPaused = isAiPaused
        )
    }

    private fun formatTimestamp(timestampMs: Long): String {
        if (timestampMs <= 0L) return ""
        try {
            val instant = Instant.fromEpochMilliseconds(timestampMs)
            val dt = instant.toLocalDateTime(TimeZone.currentSystemDefault())
            val nowDt = Instant.fromEpochMilliseconds(System.currentTimeMillis()).toLocalDateTime(TimeZone.currentSystemDefault())

            val epochDiff = nowDt.date.toEpochDays() - dt.date.toEpochDays()
            return when (epochDiff) {
                0 -> "%02d:%02d".format(dt.hour, dt.minute)
                1 -> "Yesterday"
                else -> "%02d/%02d/%04d".format(dt.dayOfMonth, dt.monthNumber, dt.year)
            }
        } catch (e: Exception) {
            return ""
        }
    }
}
