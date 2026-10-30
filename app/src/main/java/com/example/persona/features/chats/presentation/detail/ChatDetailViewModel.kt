package com.example.persona.features.chats.presentation.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.persona.core.data.repository.UserPreferencesRepository
import com.example.persona.core.database.dao.ChatHistoryDao
import com.example.persona.core.database.dao.ContactDao
import com.example.persona.core.database.dao.ConversationMetadataDao
import com.example.persona.core.database.dao.OrderDao
import com.example.persona.core.database.entity.ChatHistoryEntity
import com.example.persona.features.auto_reply.data.service.NotificationActionCache
import com.example.persona.features.auto_reply.data.service.WhatsAppNotificationListener
import com.example.persona.features.auto_reply.domain.orchestrator.AutoReplyOrchestrator
import com.example.persona.features.chats.domain.usecase.DismissHitlAttentionUseCase
import com.example.persona.features.chats.domain.usecase.ManualReplyResult
import com.example.persona.features.chats.domain.usecase.SendManualReplyUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import javax.inject.Inject

@HiltViewModel
class ChatDetailViewModel @Inject constructor(
    private val chatHistoryDao: ChatHistoryDao,
    private val conversationMetadataDao: ConversationMetadataDao,
    private val sendManualReplyUseCase: SendManualReplyUseCase,
    savedStateHandle: SavedStateHandle,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val chatExportFormatter: ChatExportFormatter = ChatExportFormatter(),
    private val orderDao: OrderDao? = null,
    private val contactDao: ContactDao? = null,
    private val dismissHitlAttentionUseCase: DismissHitlAttentionUseCase? = null
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

    private val _uiEvents = Channel<ChatDetailUiEvent>(Channel.BUFFERED)
    val uiEvents: Flow<ChatDetailUiEvent> = _uiEvents.receiveAsFlow()

    private val _messageLimit = MutableStateFlow(50)
    val messageLimit: StateFlow<Int> = _messageLimit.asStateFlow()

    fun loadMoreMessages() {
        _messageLimit.value += 50
    }

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

    private val activeOrderFlow: Flow<ActiveOrderSummary?> = if (orderDao != null) {
        orderDao.observeAllOrders().map { allOrders ->
            val matching = allOrders.firstOrNull { order ->
                order.contactName.equals(contactName, ignoreCase = true) &&
                    order.status !in listOf("SHIPPED", "CANCELLED", "RETURNED", "DELIVERED")
            }
            matching?.let {
                ActiveOrderSummary(
                    id = it.id,
                    status = it.status,
                    orderDetails = it.orderDetails ?: "تفاصيل الطلب غير مسجلة",
                    totalAmount = it.totalAmount,
                    deliveryAddress = it.deliveryAddress
                )
            }
        }
    } else {
        flowOf(null)
    }

    private val contactPhoneFlow: Flow<String?> = if (contactDao != null) {
        contactDao.observeAllContacts().map { contacts ->
            contacts.firstOrNull { it.contactName.equals(contactName, ignoreCase = true) }?.phoneNumber
                ?.takeIf { it.isNotBlank() }
        }
    } else {
        flowOf(null)
    }

    private val _secondaryChatState = combine(
        _replyCapability,
        _inputState,
        _aiPersonaState,
        activeOrderFlow,
        contactPhoneFlow
    ) { capability, input, persona, order, phone ->
        Triple(capability, input, persona) to (order to phone)
    }

    val uiState: StateFlow<ChatDetailUiState> = combine(
        chatHistoryDao.observeMessagesForContact(contactName, sourceClass),
        conversationMetadataDao.observeAllConversations(),
        _secondaryChatState,
        _messageLimit
    ) { rawMessages, allConversations, (triplet, orderAndPhone), limit ->
        val (capability, inputState, personaState) = triplet
        val (activeOrder, contactPhone) = orderAndPhone

        val convMeta = allConversations.firstOrNull {
            it.contactName.equals(contactName, ignoreCase = true) &&
                it.sourceClass.equals(sourceClass, ignoreCase = true)
        }

        val totalCount = rawMessages.size
        val hasMore = totalCount > limit
        val windowed = if (hasMore) rawMessages.takeLast(limit) else rawMessages
        val uiMessages = windowed.map { it.toUiModel() }

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
            isAiGenerating = personaState.isAiGenerating,
            activeOrder = activeOrder,
            contactPhoneNumber = contactPhone,
            hasMoreMessages = hasMore,
            totalMessageCount = totalCount
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

    /**
     * Directly clears the [needsHumanAttention] flag without requiring an active notification.
     */
    fun resolveAttention() {
        viewModelScope.launch {
            val useCase = dismissHitlAttentionUseCase
            if (useCase != null) {
                useCase(contactName, sourceClass)
            } else {
                conversationMetadataDao.setNeedsHumanAttention(contactName, sourceClass, false)
            }
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
        val isVoice = messageText.contains("[رسالة صوتية من العميل]") || messageText.contains("🎤")
        val cleanText = messageText
            .replace("[رسالة صوتية من العميل]:", "")
            .replace("[رسالة صوتية من العميل]", "")
            .trim()
        val hasMedia = isMediaMessage(messageText)

        return ChatMessageUiModel(
            id = id,
            text = cleanText,
            isFromUser = isFromUser,
            senderType = senderType,
            deliveryStatus = deliveryStatus,
            timestampMs = timestamp,
            formattedTime = timeStr,
            dateHeader = dateHeaderStr,
            hasMediaIndicator = hasMedia,
            isVoiceNote = isVoice
        )
    }

    private fun isMediaMessage(text: String): Boolean {
        val lower = text.lowercase()
        return lower.contains("📷") ||
            lower.contains("🎤") ||
            lower.contains("voice message") ||
            lower.startsWith("photo") ||
            lower.startsWith("صورة:") ||
            lower.startsWith("فيديو:")
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
            val sinceTimestamp = chatExportFormatter.calculateSinceTimestamp(range, currentTimeMs)
            val messages = chatHistoryDao.getMessagesSince(contactName, sourceClass, sinceTimestamp)
            val storeName = userPreferencesRepository.getStoreNameSync()
            val botName = userPreferencesRepository.getBotNameSync()
            val transcript = chatExportFormatter.buildExportTranscript(
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
     * Preserves conversation metadata and AI paused flag instead of hard deletion.
     */
    fun clearChat(
        range: ClearChatTimeRange,
        currentTimeMs: Long = System.currentTimeMillis()
    ) {
        viewModelScope.launch {
            val sinceTimestamp = chatExportFormatter.calculateClearSinceTimestamp(range, currentTimeMs)
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
    ): Long = chatExportFormatter.calculateClearSinceTimestamp(range, currentTimeMs)

    internal fun calculateSinceTimestamp(
        range: ExportTimeRange,
        currentTimeMs: Long = System.currentTimeMillis()
    ): Long = chatExportFormatter.calculateSinceTimestamp(range, currentTimeMs)

    internal fun buildExportTranscript(
        contactName: String,
        sourceClass: String,
        storeName: String,
        botName: String,
        range: ExportTimeRange,
        exportTimestampMs: Long,
        messages: List<ChatHistoryEntity>
    ): String = chatExportFormatter.buildExportTranscript(
        contactName = contactName,
        sourceClass = sourceClass,
        storeName = storeName,
        botName = botName,
        range = range,
        exportTimestampMs = exportTimestampMs,
        messages = messages
    )

    companion object {
        const val TRANSCRIPT_DIVIDER = ChatExportFormatter.TRANSCRIPT_DIVIDER
    }
}
