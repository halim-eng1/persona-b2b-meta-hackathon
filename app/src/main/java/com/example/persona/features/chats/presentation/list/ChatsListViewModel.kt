package com.example.persona.features.chats.presentation.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.persona.core.database.dao.ConversationMetadataDao
import com.example.persona.core.database.entity.ConversationMetadataEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import javax.inject.Inject

enum class ChatFilter(val label: String) {
    ALL("All"),
    MAIN_APP("Main WhatsApp"),
    BUSINESS("WhatsApp Business"),
    CLONE("Dual / Clone"),
    NEEDS_ATTENTION("Needs Attention 🚨")
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
    private val conversationMetadataDao: ConversationMetadataDao
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

    fun onFilterSelected(filter: ChatFilter) {
        _selectedFilter.value = filter
    }

    private fun ConversationMetadataEntity.toUiModel(): ConversationItemUiModel {
        val prefix = when (lastMessageSenderType.uppercase()) {
            "AI_AGENT" -> "🤖: "
            "MERCHANT_MANUAL" -> "👤 You: "
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

            return if (dt.year == nowDt.year && dt.monthNumber == nowDt.monthNumber && dt.dayOfMonth == nowDt.dayOfMonth) {
                "%02d:%02d".format(dt.hour, dt.minute)
            } else if (dt.year == nowDt.year && dt.monthNumber == nowDt.monthNumber && dt.dayOfMonth == nowDt.dayOfMonth - 1) {
                "Yesterday"
            } else {
                "%02d/%02d/%04d".format(dt.dayOfMonth, dt.monthNumber, dt.year)
            }
        } catch (e: Exception) {
            return ""
        }
    }
}
