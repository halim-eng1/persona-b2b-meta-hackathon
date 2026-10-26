package com.example.persona.features.chats.presentation.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.persona.core.database.dao.ChatHistoryDao
import com.example.persona.core.database.dao.ConversationMetadataDao
import com.example.persona.core.database.entity.ChatHistoryEntity
import com.example.persona.features.auto_reply.data.service.NotificationActionCache
import com.example.persona.features.auto_reply.data.service.WhatsAppNotificationListener
import com.example.persona.features.chats.domain.usecase.ManualReplyResult
import com.example.persona.features.chats.domain.usecase.SendManualReplyUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import javax.inject.Inject

enum class ReplyCapability {
    READY_VIA_NOTIFICATION,
    AWAITING_ACTIVE_NOTIFICATION
}

data class ChatMessageUiModel(
    val id: Long,
    val text: String,
    val isFromUser: Boolean,
    val senderType: String,
    val deliveryStatus: String,
    val timestampMs: Long,
    val formattedTime: String,
    val dateHeader: String,
    val hasMediaIndicator: Boolean
)

data class ChatDetailUiState(
    val contactName: String = "",
    val sourceClass: String = "BUSINESS",
    val messages: List<ChatMessageUiModel> = emptyList(),
    val isAiPaused: Boolean = false,
    val needsHumanAttention: Boolean = false,
    val replyCapability: ReplyCapability = ReplyCapability.AWAITING_ACTIVE_NOTIFICATION,
    val inputText: String = "",
    val isSending: Boolean = false,
    val fallbackTargetPackage: String? = null
)

@HiltViewModel
class ChatDetailViewModel @Inject constructor(
    private val chatHistoryDao: ChatHistoryDao,
    private val conversationMetadataDao: ConversationMetadataDao,
    private val sendManualReplyUseCase: SendManualReplyUseCase,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val contactName: String = savedStateHandle["contactName"] ?: ""
    val sourceClass: String = savedStateHandle["sourceClass"] ?: "BUSINESS"

    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText

    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending

    private val _fallbackTargetPackage = MutableStateFlow<String?>(null)
    val fallbackTargetPackage: StateFlow<String?> = _fallbackTargetPackage

    val uiState: StateFlow<ChatDetailUiState> = combine(
        chatHistoryDao.observeMessagesForContact(contactName, sourceClass),
        conversationMetadataDao.observeAllConversations(),
        _inputText,
        _isSending,
        _fallbackTargetPackage
    ) { rawMessages, allConversations, input, sending, fallbackPkg ->
        val convMeta = allConversations.firstOrNull {
            it.contactName.equals(contactName, ignoreCase = true) &&
                it.sourceClass.equals(sourceClass, ignoreCase = true)
        }

        val hasAction = NotificationActionCache.get(sourceClass, contactName) != null ||
            WhatsAppNotificationListener.instance?.get()?.activeNotifications?.any { sbn ->
                sbn.notification.extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString()
                    .equals(contactName, ignoreCase = true)
            } == true

        val capability = if (hasAction) {
            ReplyCapability.READY_VIA_NOTIFICATION
        } else {
            ReplyCapability.AWAITING_ACTIVE_NOTIFICATION
        }

        val uiMessages = rawMessages.map { it.toUiModel() }

        ChatDetailUiState(
            contactName = contactName,
            sourceClass = sourceClass,
            messages = uiMessages,
            isAiPaused = convMeta?.isAiPaused ?: false,
            needsHumanAttention = convMeta?.needsHumanAttention ?: false,
            replyCapability = capability,
            inputText = input,
            isSending = sending,
            fallbackTargetPackage = fallbackPkg
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ChatDetailUiState(contactName = contactName, sourceClass = sourceClass)
    )

    init {
        // Mark conversation and messages as read when thread is opened
        markAsRead()
    }

    fun markAsRead() {
        if (contactName.isNotBlank()) {
            viewModelScope.launch {
                chatHistoryDao.markMessagesAsRead(contactName, sourceClass)
                conversationMetadataDao.markConversationRead(contactName, sourceClass)
            }
        }
    }

    fun onInputTextChanged(text: String) {
        _inputText.value = text
    }

    fun toggleAiPaused() {
        viewModelScope.launch {
            val currentPaused = uiState.value.isAiPaused
            conversationMetadataDao.setAiPaused(contactName, sourceClass, !currentPaused)
        }
    }

    fun sendMessage() {
        val text = _inputText.value.trim()
        if (text.isBlank() || _isSending.value) return

        _inputText.value = ""
        _isSending.value = true

        viewModelScope.launch {
            try {
                val result = sendManualReplyUseCase(contactName, sourceClass, text)
                when (result) {
                    is ManualReplyResult.Success -> {
                        _fallbackTargetPackage.value = null
                    }
                    is ManualReplyResult.Queued -> {
                        _fallbackTargetPackage.value = null
                    }
                    is ManualReplyResult.Failed -> {
                        _fallbackTargetPackage.value = result.targetPackage
                    }
                }
            } catch (e: Exception) {
                // Handled in use case
            } finally {
                _isSending.value = false
            }
        }
    }

    fun retryMessage(messageText: String) {
        viewModelScope.launch {
            sendManualReplyUseCase(contactName, sourceClass, messageText)
        }
    }

    private fun ChatHistoryEntity.toUiModel(): ChatMessageUiModel {
        val timeStr = formatTime(timestamp)
        val dateHeaderStr = formatDateHeader(timestamp)
        val hasMedia = isMediaMessage(messageText)

        return ChatMessageUiModel(
            id = id,
            text = messageText,
            isFromUser = isFromUser,
            senderType = senderType,
            deliveryStatus = deliveryStatus,
            timestampMs = timestamp,
            formattedTime = timeStr,
            dateHeader = dateHeaderStr,
            hasMediaIndicator = hasMedia
        )
    }

    private fun isMediaMessage(text: String): Boolean {
        val lower = text.lowercase()
        return lower.contains("📷") ||
            lower.contains("🎤") ||
            lower.contains("photo") ||
            lower.contains("voice message") ||
            lower.contains("صورة") ||
            lower.contains("رسالة صوتية")
    }

    private fun formatTime(timestampMs: Long): String {
        return try {
            val dt = Instant.fromEpochMilliseconds(timestampMs).toLocalDateTime(TimeZone.currentSystemDefault())
            "%02d:%02d".format(dt.hour, dt.minute)
        } catch (e: Exception) {
            ""
        }
    }

    private fun formatDateHeader(timestampMs: Long): String {
        return try {
            val dt = Instant.fromEpochMilliseconds(timestampMs).toLocalDateTime(TimeZone.currentSystemDefault())
            val nowDt = Instant.fromEpochMilliseconds(System.currentTimeMillis()).toLocalDateTime(TimeZone.currentSystemDefault())

            if (dt.year == nowDt.year && dt.monthNumber == nowDt.monthNumber && dt.dayOfMonth == nowDt.dayOfMonth) {
                "Today"
            } else if (dt.year == nowDt.year && dt.monthNumber == nowDt.monthNumber && dt.dayOfMonth == nowDt.dayOfMonth - 1) {
                "Yesterday"
            } else {
                "%02d/%02d/%04d".format(dt.dayOfMonth, dt.monthNumber, dt.year)
            }
        } catch (e: Exception) {
            ""
        }
    }
}
