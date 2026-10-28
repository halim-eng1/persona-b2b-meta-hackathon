package com.example.persona.features.chats.presentation.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.persona.core.data.repository.UserPreferencesRepository
import com.example.persona.core.database.dao.ChatHistoryDao
import com.example.persona.core.database.dao.ConversationMetadataDao
import com.example.persona.core.database.entity.ChatHistoryEntity
import com.example.persona.features.auto_reply.data.service.NotificationActionCache
import com.example.persona.features.auto_reply.data.service.WhatsAppNotificationListener
import com.example.persona.features.auto_reply.domain.orchestrator.AutoReplyOrchestrator
import com.example.persona.features.chats.domain.usecase.ManualReplyResult
import com.example.persona.features.chats.domain.usecase.SendManualReplyUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject

enum class ReplyCapability {
    READY_VIA_NOTIFICATION,
    AWAITING_ACTIVE_NOTIFICATION
}

enum class ExportTimeRange(
    val titleArabic: String,
    val titleEnglish: String,
    val subtitle: String,
    val exportLabel: String
) {
    ALL_TIME(
        titleArabic = "كامل المحادثة",
        titleEnglish = "All Time",
        subtitle = "All Time • كافة الرسائل المسجلة",
        exportLabel = "All Time (كامل المحادثة)"
    ),
    TODAY(
        titleArabic = "محادثة اليوم",
        titleEnglish = "Today",
        subtitle = "Today • رسائل اليوم",
        exportLabel = "Today (محادثة اليوم)"
    ),
    LAST_TWO_HOURS(
        titleArabic = "آخر ساعتين",
        titleEnglish = "Last 2 Hours",
        subtitle = "Last 2 Hours • آخر ساعتين",
        exportLabel = "Last 2 Hours (آخر ساعتين)"
    )
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
    val fallbackTargetPackage: String? = null,
    val botName: String = UserPreferencesRepository.DEFAULT_BOT_NAME,
    val isAiGenerating: Boolean = false
)

@HiltViewModel
class ChatDetailViewModel @Inject constructor(
    private val chatHistoryDao: ChatHistoryDao,
    private val conversationMetadataDao: ConversationMetadataDao,
    private val sendManualReplyUseCase: SendManualReplyUseCase,
    savedStateHandle: SavedStateHandle,
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    val contactName: String = savedStateHandle["contactName"] ?: ""
    val sourceClass: String = savedStateHandle["sourceClass"] ?: "BUSINESS"

    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText

    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending

    private val _fallbackTargetPackage = MutableStateFlow<String?>(null)
    val fallbackTargetPackage: StateFlow<String?> = _fallbackTargetPackage

    private val _replyCapability = MutableStateFlow(ReplyCapability.AWAITING_ACTIVE_NOTIFICATION)
    val replyCapability: StateFlow<ReplyCapability> = _replyCapability

    private data class InputState(
        val text: String,
        val isSending: Boolean,
        val fallbackPkg: String?
    )

    private val _inputState = combine(_inputText, _isSending, _fallbackTargetPackage) { text, sending, pkg ->
        InputState(text, sending, pkg)
    }

    private data class AiPersonaState(
        val botName: String,
        val isAiGenerating: Boolean
    )

    private val _botNameFlow = userPreferencesRepository.botName
    private val _isAiGeneratingFlow = AutoReplyOrchestrator.activeProcessingContacts.map { activeSet ->
        activeSet.contains(contactName.trim().lowercase())
    }

    private val _aiPersonaState = combine(_botNameFlow, _isAiGeneratingFlow) { botName, isGenerating ->
        AiPersonaState(botName, isGenerating)
    }

    val uiState: StateFlow<ChatDetailUiState> = combine(
        chatHistoryDao.observeMessagesForContact(contactName, sourceClass),
        conversationMetadataDao.observeAllConversations(),
        _replyCapability,
        _inputState,
        _aiPersonaState
    ) { rawMessages, allConversations, capability, inputState, personaState ->
        val convMeta = allConversations.firstOrNull {
            it.contactName.equals(contactName, ignoreCase = true) &&
                it.sourceClass.equals(sourceClass, ignoreCase = true)
        }

        val uiMessages = rawMessages.map { it.toUiModel() }

        ChatDetailUiState(
            contactName = contactName,
            sourceClass = sourceClass,
            messages = uiMessages,
            isAiPaused = convMeta?.isAiPaused ?: false,
            needsHumanAttention = convMeta?.needsHumanAttention ?: false,
            replyCapability = capability,
            inputText = inputState.text,
            isSending = inputState.isSending,
            fallbackTargetPackage = inputState.fallbackPkg,
            botName = personaState.botName,
            isAiGenerating = personaState.isAiGenerating
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ChatDetailUiState(contactName = contactName, sourceClass = sourceClass)
    )

    init {
        // Mark conversation and messages as read when thread is opened
        markAsRead()
        checkNotificationReadiness()

        // Re-check notification readiness on background thread when incoming message turns arrive
        viewModelScope.launch {
            chatHistoryDao.observeMessagesForContact(contactName, sourceClass).collect {
                checkNotificationReadiness()
            }
        }
    }

    fun checkNotificationReadiness() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val hasCached = NotificationActionCache.get(sourceClass, contactName) != null
            if (hasCached) {
                _replyCapability.value = ReplyCapability.READY_VIA_NOTIFICATION
                return@launch
            }

            val targetPkg = when (sourceClass.uppercase()) {
                "MAIN_APP" -> "com.whatsapp"
                "BUSINESS" -> "com.whatsapp.w4b"
                else -> null
            }

            val active = WhatsAppNotificationListener.instance?.get()?.activeNotifications ?: emptyArray()
            val hasLive = active.any { sbn ->
                (targetPkg == null || sbn.packageName == targetPkg) &&
                (sbn.notification.flags and android.app.Notification.FLAG_GROUP_SUMMARY == 0) &&
                sbn.notification.extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString()
                    .equals(contactName, ignoreCase = true) &&
                sbn.notification.actions?.any { act -> act.remoteInputs?.any { it.resultKey != null } == true } == true
            }

            _replyCapability.value = if (hasLive) {
                ReplyCapability.READY_VIA_NOTIFICATION
            } else {
                ReplyCapability.AWAITING_ACTIVE_NOTIFICATION
            }
        }
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

    fun retryMessage(messageId: Long, messageText: String) {
        viewModelScope.launch {
            sendManualReplyUseCase(
                contactName = contactName,
                sourceClass = sourceClass,
                replyText = messageText,
                retryMessageId = messageId
            )
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

            val epochDiff = nowDt.date.toEpochDays() - dt.date.toEpochDays()
            when (epochDiff) {
                0 -> "Today"
                1 -> "Yesterday"
                else -> "%02d/%02d/%04d".format(dt.dayOfMonth, dt.monthNumber, dt.year)
            }
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * Exports the chat history for this contact in the chosen [range].
     * Fetches rows from [ChatHistoryDao], formats the transcript into a clean Arabic/English
     * text report, and invokes [onExportReady] on completion.
     */
    fun exportChat(
        range: ExportTimeRange,
        currentTimeMs: Long = System.currentTimeMillis(),
        onExportReady: (String) -> Unit
    ) {
        viewModelScope.launch {
            val sinceTimestamp = calculateSinceTimestamp(range, currentTimeMs)
            val messages = chatHistoryDao.getMessagesSince(contactName, sourceClass, sinceTimestamp)
            val storeName = userPreferencesRepository.getStoreNameSync()
            val botName = userPreferencesRepository.getBotNameSync()
            val transcript = buildExportTranscript(
                contactName = contactName,
                sourceClass = sourceClass,
                storeName = storeName,
                botName = botName,
                range = range,
                exportTimestampMs = currentTimeMs,
                messages = messages
            )
            onExportReady(transcript)
        }
    }

    /**
     * Clears messages for this contact in the chosen [range].
     * Executes delete on [ChatHistoryDao] so messages disappear immediately from the screen.
     */
    fun clearChat(
        range: ClearChatTimeRange,
        currentTimeMs: Long = System.currentTimeMillis()
    ) {
        viewModelScope.launch {
            val sinceTimestamp = calculateClearSinceTimestamp(range, currentTimeMs)
            chatHistoryDao.deleteMessagesSince(contactName, sourceClass, sinceTimestamp)

            val latestRemaining = chatHistoryDao.getLatestMessageForSource(contactName, sourceClass)
            if (latestRemaining == null) {
                conversationMetadataDao.deleteConversation(contactName, sourceClass)
            } else {
                conversationMetadataDao.updateLastMessage(
                    contactName = contactName,
                    sourceClass = sourceClass,
                    lastMessageText = latestRemaining.messageText,
                    lastMessageTimestamp = latestRemaining.timestamp,
                    lastMessageSenderType = latestRemaining.senderType
                )
            }
        }
    }

    internal fun calculateClearSinceTimestamp(
        range: ClearChatTimeRange,
        currentTimeMs: Long = System.currentTimeMillis()
    ): Long {
        return when (range) {
            ClearChatTimeRange.ALL_TIME -> 0L
            ClearChatTimeRange.LAST_HOUR -> currentTimeMs - (1 * 60 * 60 * 1000L)
            ClearChatTimeRange.LAST_TWO_HOURS -> currentTimeMs - (2 * 60 * 60 * 1000L)
            ClearChatTimeRange.TODAY -> {
                val cal = Calendar.getInstance().apply {
                    timeInMillis = currentTimeMs
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                cal.timeInMillis
            }
        }
    }

    internal fun calculateSinceTimestamp(
        range: ExportTimeRange,
        currentTimeMs: Long = System.currentTimeMillis()
    ): Long {
        return when (range) {
            ExportTimeRange.ALL_TIME -> 0L
            ExportTimeRange.TODAY -> {
                val cal = Calendar.getInstance().apply {
                    timeInMillis = currentTimeMs
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                cal.timeInMillis
            }
            ExportTimeRange.LAST_TWO_HOURS -> {
                currentTimeMs - (2 * 60 * 60 * 1000L)
            }
        }
    }

    internal fun buildExportTranscript(
        contactName: String,
        sourceClass: String,
        storeName: String,
        botName: String,
        range: ExportTimeRange,
        exportTimestampMs: Long,
        messages: List<ChatHistoryEntity>
    ): String {
        val exportDateStr = formatExportTimestamp(exportTimestampMs)
        val lines = mutableListOf<String>()
        lines.add("سجل محادثة: $contactName ($sourceClass)")
        lines.add("المتجر: $storeName")
        lines.add("تاريخ التصدير: $exportDateStr")
        lines.add("نطاق التصدير: ${range.exportLabel}")
        lines.add(TRANSCRIPT_DIVIDER)

        for (msg in messages) {
            val timeStr = formatExportTimestamp(msg.timestamp)
            val senderLabel = when {
                msg.isFromUser || msg.senderType.equals("CUSTOMER", ignoreCase = true) -> "العميل"
                msg.senderType.equals("MERCHANT_MANUAL", ignoreCase = true) -> "التاجر (Manual)"
                else -> "$botName (AI)"
            }
            lines.add("[$timeStr] $senderLabel: ${msg.messageText}")
        }

        lines.add(TRANSCRIPT_DIVIDER)
        return lines.joinToString("\n")
    }

    private fun formatExportTimestamp(timestampMs: Long): String {
        return try {
            val dt = Instant.fromEpochMilliseconds(timestampMs).toLocalDateTime(TimeZone.currentSystemDefault())
            String.format(
                Locale.US,
                "%04d-%02d-%02d %02d:%02d:%02d",
                dt.year,
                dt.monthNumber,
                dt.dayOfMonth,
                dt.hour,
                dt.minute,
                dt.second
            )
        } catch (e: Exception) {
            ""
        }
    }

    companion object {
        const val TRANSCRIPT_DIVIDER = "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    }
}
