package com.example.persona.features.auto_reply.domain.orchestrator

import android.content.Context
import android.util.Log
import com.example.persona.R
import com.example.persona.core.data.repository.UserPreferencesRepository
import com.example.persona.core.database.Converters
import com.example.persona.core.database.dao.BusinessKnowledgeDao
import com.example.persona.core.database.dao.ChatHistoryDao
import com.example.persona.core.database.dao.ConversationMetadataDao
import com.example.persona.core.database.dao.FailedQueryDao
import com.example.persona.core.database.dao.OrderDao
import com.example.persona.core.database.entity.ChatHistoryEntity
import com.example.persona.core.database.entity.FailedQueryEntity
import com.example.persona.core.database.entity.OrderStatus
import com.example.persona.core.di.ApplicationScope
import com.example.persona.core.logging.PersonaLogger
import com.example.persona.core.logging.PersonaLogTags
import com.example.persona.features.auto_reply.data.service.IncomingMessageDispatcher
import com.example.persona.features.auto_reply.data.service.WhatsAppMediaExtractor
import com.example.persona.features.auto_reply.data.repository.NotificationProcessorImpl
import com.example.persona.features.auto_reply.data.repository.SemanticRetrievalRepository
import com.example.persona.features.auto_reply.domain.modifier.StylePostProcessor
import com.example.persona.features.auto_reply.domain.repository.ReplyGenerationRepository
import com.example.persona.features.auto_reply.domain.repository.AuditLogRepository
import com.example.persona.features.auto_reply.domain.usecase.DirectReplyExecutionUseCase
import com.example.persona.features.auto_reply.domain.usecase.ReplyExecutionResult
import com.example.persona.features.auto_reply.domain.model.WhatsAppNotification
import com.example.persona.features.auto_reply.domain.model.RetrievalCandidate
import com.example.persona.features.auto_reply.domain.model.ProcessedNotification
import com.example.persona.features.chat_import.data.embedding.EmbeddingEngine
import com.example.persona.features.chat_import.data.normalizer.ArabicTextNormalizer
import com.example.persona.core.network.dto.AgenticLlmResponse
import com.example.persona.core.network.dto.ExtractedOrder
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import javax.inject.Inject
import javax.inject.Singleton

typealias ExtractedOrderDto = ExtractedOrder

@Singleton
class AutoReplyOrchestrator @Inject constructor(
    private val incomingMessageDispatcher: IncomingMessageDispatcher,
    private val processor: NotificationProcessorImpl,
    private val embeddingEngine: EmbeddingEngine,
    private val semanticRetrievalRepository: SemanticRetrievalRepository,
    private val replyGenerationRepository: ReplyGenerationRepository,
    private val directReplyExecutionUseCase: DirectReplyExecutionUseCase,
    private val auditLogRepository: AuditLogRepository,
    private val prefsRepository: UserPreferencesRepository,
    private val stylePostProcessor: StylePostProcessor,
    private val normalizer: ArabicTextNormalizer,
    private val failedQueryDao: FailedQueryDao,
    private val chatHistoryDao: ChatHistoryDao,
    private val orderDao: OrderDao,
    private val businessKnowledgeDao: BusinessKnowledgeDao,
    private val conversationMetadataDao: ConversationMetadataDao,
    @ApplicationContext private val context: Context,
    @ApplicationScope private val scope: CoroutineScope,
    private val mediaExtractor: WhatsAppMediaExtractor? = null,
    private val orderIngestionManager: OrderIngestionManager = OrderIngestionManager(orderDao, chatHistoryDao),
    private val hitlNotificationHandler: HitlNotificationHandler = HitlNotificationHandler(context, conversationMetadataDao, prefsRepository),
    private val antiEchoGuard: AntiEchoGuard = AntiEchoGuard(),
    private val reasoningLeakSanitizer: ReasoningLeakSanitizer = ReasoningLeakSanitizer()
) {
    companion object {
        private const val TAG = "AutoReplyOrchestrator"
        const val ALARM_CHANNEL_ID = HitlNotificationHandler.ALARM_CHANNEL_ID
        const val CANONICAL_HITL_MESSAGE = ReasoningLeakSanitizer.CANONICAL_HITL_MESSAGE

        private val _activeProcessingContacts = kotlinx.coroutines.flow.MutableStateFlow<Set<String>>(emptySet())
        val activeProcessingContacts: kotlinx.coroutines.flow.StateFlow<Set<String>> = _activeProcessingContacts

        fun setContactProcessing(contactName: String, isProcessing: Boolean) {
            val key = contactName.trim().lowercase()
            _activeProcessingContacts.value = if (isProcessing) {
                _activeProcessingContacts.value + key
            } else {
                _activeProcessingContacts.value - key
            }
        }

        fun isContactProcessing(contactName: String): Boolean {
            return _activeProcessingContacts.value.contains(contactName.trim().lowercase())
        }

        fun clearActiveProcessingContacts() {
            _activeProcessingContacts.value = emptySet()
        }

        internal fun sanitizeLlmJson(raw: String): String = ReasoningLeakSanitizer.sanitizeLlmJson(raw)

        const val PIVOT_MARKER = OrderIngestionManager.PIVOT_MARKER
        const val NO_ACTIVE_ORDER_REPLY = OrderIngestionManager.NO_ACTIVE_ORDER_REPLY

        private val SAFETY_KEYWORDS = listOf(
            "ضروري", "مشكلة", "مستشفى", "الحقني", "مصيبة", "urgent", "help"
        )

        internal fun isPivotStillOpen(latestPivotTimestamp: Long?, consumedPivotTimestamps: Set<Long>): Boolean =
            OrderIngestionManager.isPivotStillOpen(latestPivotTimestamp, consumedPivotTimestamps)

        internal val agenticJson = ReasoningLeakSanitizer.agenticJson

        internal fun repairUnescapedJsonQuotes(json: String): String =
            ReasoningLeakSanitizer.repairUnescapedJsonQuotes(json)

        internal fun decodeAgenticResponseSafely(raw: String): AgenticLlmResponse =
            ReasoningLeakSanitizer.decodeAgenticResponseSafely(raw)

        internal fun mergeCandidates(
            pass1: List<RetrievalCandidate>,
            pass2: List<RetrievalCandidate>,
            limit: Int = 8,
            minScore: Float = 0.38f
        ): List<RetrievalCandidate> {
            val validPass2 = pass2.filter { it.similarityScore >= minScore }
                .sortedByDescending { it.similarityScore }
            val pass2Keys = validPass2.map { it.chunkId.ifBlank { it.chunkText } }.toSet()

            val validPass1 = pass1.filter { it.similarityScore >= minScore }
                .filterNot { pass2Keys.contains(it.chunkId.ifBlank { it.chunkText }) }
                .sortedByDescending { it.similarityScore }

            return (validPass2 + validPass1).take(limit)
        }

        internal fun calculateRagBaseThreshold(userThreshold: Float): Float {
            val clamped = userThreshold.coerceIn(0.65f, 0.85f)
            return 0.42f + (clamped - 0.65f) * 0.4f
        }

        internal fun isEchoRepetition(
            candidateReply: String,
            currentUserText: String,
            chatHistory: List<ChatHistoryEntity>,
            nowMs: Long = System.currentTimeMillis()
        ): Boolean = AntiEchoGuard.isEchoRepetition(candidateReply, currentUserText, chatHistory, nowMs)

        internal fun filterDuplicateNoteLines(
            existingNotes: String?,
            newNotes: String,
            existingDeliveryAddress: String? = null,
            existingCustomerName: String? = null
        ): String = OrderIngestionManager.filterDuplicateNoteLines(
            existingNotes, newNotes, existingDeliveryAddress, existingCustomerName
        )

        const val CONTEXT_WINDOW_MINUTES = 1L
        const val MAX_CONTEXT_QUEUE_SIZE = 50
    }

    // Channel for sequential processing — prevents concurrent ONNX executions (OOM prevention)
    private val channel = Channel<WhatsAppNotification>(Channel.UNLIMITED)

    // Queue for context window calculation with aggressive memory safety
    private val receivedNotifications = ConcurrentLinkedQueue<WhatsAppNotification>()

    // Cooldown map: contact name -> last reply timestamp (ms)
    private val cooldownMap = ConcurrentHashMap<String, Long>()

    // Session tracker for greeting suppression (last auto-reply timestamp per sender)
    private val lastReplyTimestamps = ConcurrentHashMap<String, Long>()

    private var isCollecting = false

    init {
        hitlNotificationHandler.createAlarmNotificationChannel()
        scope.launch {
            for (notification in channel) {
                try {
                    processNotificationSequentially(notification)
                } catch (e: Exception) {
                    Log.e(TAG, "Error in sequential processing of notification: ${notification.notificationKey}", e)
                }
            }
        }
    }

    fun start() {
        synchronized(this) {
            if (isCollecting) {
                Log.d(TAG, "Orchestrator is already collecting notifications")
                return
            }
            isCollecting = true
        }
        scope.launch {
            incomingMessageDispatcher.notifications.collect { notification ->
                receivedNotifications.add(notification)
                while (receivedNotifications.size > MAX_CONTEXT_QUEUE_SIZE) {
                    receivedNotifications.poll()
                }
                val cutOff = System.currentTimeMillis() - 10 * 60 * 1000L
                receivedNotifications.removeIf { it.postTime < cutOff }

                channel.send(notification)
            }
        }
        Log.i(TAG, "AutoReplyOrchestrator started listening to IncomingMessageDispatcher")
    }

    internal suspend fun processNotificationSequentially(rawNotification: WhatsAppNotification) {
        // 1. Pre-process notification FIRST
        val processed = processor.process(rawNotification)
        val sourceClassStr = processed.sourceClass.name

        Log.i(PersonaLogTags.LLM, "📩 Incoming message from [${processed.contactName}] ($sourceClassStr): \"${processed.originalText}\"")

        // 2. Persist inbound message immediately to chat_history
        val chatHistoryEntity = ChatHistoryEntity(
            contactName = processed.contactName,
            messageText = processed.originalText,
            isFromUser = true,
            timestamp = processed.postTime,
            embedding = null,
            embeddingState = "PENDING",
            sourceClass = sourceClassStr,
            senderType = "CUSTOMER",
            deliveryStatus = "SENT",
            isRead = false
        )
        val insertedId = chatHistoryDao.insert(chatHistoryEntity)
        Log.d(TAG, "Chat history row inserted for contact=${processed.contactName}, id=$insertedId")

        // 3. Upsert conversation_metadata for the live inbox
        conversationMetadataDao.recordMessageTurn(
            contactName = processed.contactName,
            sourceClass = sourceClassStr,
            messageText = processed.originalText,
            senderType = "CUSTOMER",
            timestamp = processed.postTime,
            notificationKey = processed.notificationKey,
            incrementUnread = true
        )

        // 4. Launch background embedding generation coroutine
        scope.launch(Dispatchers.Default) {
            val claimSuccess = chatHistoryDao.claimForProcessing(insertedId)
            if (claimSuccess > 0) {
                try {
                    val startTime = System.currentTimeMillis()
                    val embedding = embeddingEngine.embedForAutoReply(processed.normalizedText)
                    val latency = System.currentTimeMillis() - startTime
                    Log.i("PersonaMetrics", "metric=embedding_latency_ms value=$latency contact=${processed.contactName.hashCode()}")
                    if (embedding != null) {
                        val embeddingBlob = Converters().fromFloatArray(embedding)
                        if (embeddingBlob != null) {
                            chatHistoryDao.updateEmbeddingRaw(insertedId, embeddingBlob)
                            Log.d(TAG, "Background embedding completed and saved for chatHistory id=$insertedId")
                        } else {
                            chatHistoryDao.updateEmbeddingState(insertedId, "FAILED")
                        }
                    } else {
                        chatHistoryDao.updateEmbeddingState(insertedId, "FAILED")
                        Log.w(TAG, "Embedding returned null for chatHistory id=$insertedId")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Embedding generation failed for chatHistory id=$insertedId", e)
                    chatHistoryDao.updateEmbeddingState(insertedId, "FAILED")
                }
            }
        }
        Log.d(TAG, "⚡ Background embedding job launched for chatHistory id=$insertedId, state=PROCESSING")

        // 5. Global switch gate
        if (!prefsRepository.isAutoReplyEnabledSync()) {
            PersonaLogger.d(PersonaLogTags.ORCHESTRATOR, "Notification captured, auto-reply OFF")
            Log.d(TAG, "Auto-reply globally disabled. Stored inbound message, skipping reply for ${rawNotification.title}")
            return
        }

        // 6. Per-contact AI pause gate
        val convMeta = conversationMetadataDao.getConversation(processed.contactName, sourceClassStr)
        if (convMeta?.isAiPaused == true) {
            PersonaLogger.d(PersonaLogTags.ORCHESTRATOR, "Notification captured, AI paused for contact")
            Log.d(TAG, "AI auto-reply paused for contact ${processed.contactName} ($sourceClassStr). Stored inbound message, skipping reply.")
            return
        }

        // 7. Load style preferences
        val stylePrefs = prefsRepository.getStylePreferencesSync()
        val cooldownMs = stylePrefs.cooldownMins * 60_000L

        // 8. Cooldown policy
        val now = System.currentTimeMillis()
        val lastReplyTime = cooldownMap[processed.contactName] ?: 0L
        if (stylePrefs.cooldownMins != 0 && now - lastReplyTime < cooldownMs) {
            PersonaLogger.d(PersonaLogTags.ORCHESTRATOR, "Cooldown triggered")
            Log.d(TAG, "Ignoring '${processed.contactName}': in cooldown (${stylePrefs.cooldownMins} min)")
            Log.i("PersonaMetrics", "metric=notification_drop_rate value=1 contact=${processed.contactName.hashCode()}")
            return
        }

        // 9. Human safety filter
        val hasUrgentKeyword = SAFETY_KEYWORDS.any { keyword ->
            processed.originalText.contains(keyword, ignoreCase = true) ||
            processed.normalizedText.contains(keyword, ignoreCase = true)
        }
        if (hasUrgentKeyword) {
            PersonaLogger.d(PersonaLogTags.ORCHESTRATOR, "Safety filter triggered")
            PersonaLogger.d(PersonaLogTags.ORCHESTRATOR, "Reply queued")
            Log.w(TAG, "Safety filter triggered for ${processed.contactName}. Routing to queue.")
            val alertMessage = "[Safety Alert] urgent keyword detected. Message: ${processed.originalText}"
            auditLogRepository.queuePendingReply(processed, alertMessage)

            hitlNotificationHandler.handleHitlEscalation(processed, 1.0f)
            
            Log.i("PersonaMetrics", "metric=notification_drop_rate value=1 contact=${processed.contactName.hashCode()}")
            Log.i("PersonaMetrics", "metric=hitl_rate value=1 contact=${processed.contactName.hashCode()}")

            sendHitlHoldingReply(processed)
            return
        }

        // 10. Multimodal Media Processing & Fallback handling
        val isTranscribedVoice = processed.originalText.startsWith("[رسالة صوتية من العميل]:")
        var currentMediaFilePath = processed.mediaFilePath
        var effectiveProcessed = processed

        if (!isTranscribedVoice && containsMediaIndicators(processed.originalText)) {
            if (mediaExtractor != null) {
                if (mediaExtractor.isVoiceMessageIndicator(processed.originalText)) {
                    val voiceFile = mediaExtractor.captureLatestVoiceNote(processed.sourceClass, processed.postTime)
                    if (voiceFile != null) {
                        val transcribed = mediaExtractor.transcribeVoiceNote(voiceFile)
                        if (!transcribed.isNullOrBlank()) {
                            val updatedText = "[رسالة صوتية من العميل]: $transcribed"
                            effectiveProcessed = processed.copy(
                                originalText = updatedText,
                                normalizedText = normalizer.normalize(updatedText) ?: updatedText
                            )
                            Log.i(TAG, "🎙️ Successfully captured voice note and transcribed: '$updatedText'")
                        }
                    }
                } else if (currentMediaFilePath == null && mediaExtractor.isImageIndicator(processed.originalText)) {
                    val diskImage = mediaExtractor.findLatestImageOnDisk(processed.sourceClass, processed.postTime)
                    if (diskImage != null) {
                        currentMediaFilePath = diskImage.absolutePath
                        effectiveProcessed = processed.copy(mediaFilePath = currentMediaFilePath)
                        Log.i(TAG, "📸 Successfully captured image from disk fallback: $currentMediaFilePath")
                    }
                }
            }
        }

        val isVoiceTranscribedNow = effectiveProcessed.originalText.startsWith("[رسالة صوتية من العميل]:")
        // ONLY send media fallback if media indicator is present AND media extraction genuinely failed
        if (!isVoiceTranscribedNow && currentMediaFilePath == null && containsMediaIndicators(effectiveProcessed.originalText)) {
            Log.i(TAG, "Media indicator detected but extraction failed for ${effectiveProcessed.contactName}. Sending media fallback.")
            Log.i("PersonaMetrics", "metric=notification_drop_rate value=1 contact=${effectiveProcessed.contactName.hashCode()}")
            sendImmediateReply(effectiveProcessed, context.getString(R.string.media_fallback_message))
            return
        }

        if (currentMediaFilePath != null && effectiveProcessed.mediaFilePath == null) {
            effectiveProcessed = effectiveProcessed.copy(mediaFilePath = currentMediaFilePath)
        }

        setContactProcessing(effectiveProcessed.contactName, true)
        try {
            // 11. RAG Retrieval Engine (Single-Query Pass 1)
            val activeHashes = antiEchoGuard.getActiveReplyHashes()
            val ragBaseThreshold = calculateRagBaseThreshold(stylePrefs.similarityThreshold)
            var candidates: List<RetrievalCandidate> = emptyList()
            var maxScore = 0f

            val queryTextForEmbedding = if (effectiveProcessed.originalText.startsWith("[رسالة صوتية من العميل]:")) {
                effectiveProcessed.originalText.removePrefix("[رسالة صوتية من العميل]:").trim()
            } else {
                effectiveProcessed.normalizedText
            }

            val queryEmbedding = embeddingEngine.embedForAutoReply(queryTextForEmbedding)
            if (queryEmbedding != null) {
                val (retrievedCandidates, bestScore) = semanticRetrievalRepository.retrieveCandidates(
                    queryEmbedding = queryEmbedding,
                    minimumSimilarityThreshold = ragBaseThreshold,
                    queryText = queryTextForEmbedding,
                    recentReplyHashes = activeHashes
                )
                candidates = retrievedCandidates.filter { it.similarityScore >= 0.38f }
                maxScore = bestScore
            }

            val isImageWithGenericText = effectiveProcessed.mediaFilePath != null &&
                (effectiveProcessed.originalText.contains("Sent a photo", ignoreCase = true) || effectiveProcessed.originalText.trim() == "📷" || candidates.isEmpty())

            val finalCandidates = if (isImageWithGenericText && candidates.isEmpty()) {
                try {
                    businessKnowledgeDao.getProductCatalogChunks(limit = 5).map { proj ->
                        RetrievalCandidate(
                            chunkId = proj.chunkId,
                            headerContext = proj.headerContext,
                            chunkText = proj.chunkText,
                            similarityScore = 0.95f,
                            baseSimilarityScore = 0.95f
                        )
                    }
                } catch (e: Exception) {
                    candidates
                }
            } else {
                candidates
            }

            // Context Assembly
            val contextStart = System.currentTimeMillis()
            val chatHistory = chatHistoryDao.getRecentMessagesExceptForSource(
                contactName = effectiveProcessed.contactName,
                sourceClass = sourceClassStr,
                excludeId = insertedId,
                limit = 6
            )
            val activeOrders = orderDao.getActiveOrdersByContact(effectiveProcessed.contactName)
            val contextBuildMs = System.currentTimeMillis() - contextStart
            Log.d(TAG, "📊 Context assembly completed in ${contextBuildMs}ms: chatTurns=${chatHistory.size}, activeOrders=${activeOrders.size}")
            Log.i("PersonaMetrics", "metric=context_build_ms value=$contextBuildMs contact=${effectiveProcessed.contactName.hashCode()}")

            // 12. Agentic LLM Generation Pass 1
            val lastSessionReplyTime = lastReplyTimestamps[effectiveProcessed.contactName] ?: 0L
            val isOngoing = (System.currentTimeMillis() - lastSessionReplyTime) < 300_000L

            val rawLlmOutput = replyGenerationRepository.generateReply(
                notification = effectiveProcessed,
                candidates = finalCandidates,
                isOngoingConversation = isOngoing,
                chatHistory = chatHistory,
                activeOrders = activeOrders,
                personalChunks = emptyList(),
                maxTokens = 600
            )
            if (rawLlmOutput.isNullOrBlank()) {
                Log.d(TAG, "LLM returned null/blank — all models failed. HITL fallback.")
                hitlNotificationHandler.handleHitlEscalation(processed, maxScore)
                Log.i("PersonaMetrics", "metric=hitl_rate value=1 contact=${processed.contactName.hashCode()}")
                sendHitlHoldingReply(processed)
                return
            }

            // Parse structured JSON
            val agenticResponse = try {
                ReasoningLeakSanitizer.decodeAgenticResponseSafely(rawLlmOutput)
            } catch (e: Exception) {
                if (ReasoningLeakSanitizer.isLeakedReasoningText(rawLlmOutput)) {
                    Log.w(TAG, "Raw LLM output contains reasoning leakage — degrading safely to HITL: ${rawLlmOutput.take(200)}", e)
                } else {
                    Log.w(TAG, "LLM output is not valid JSON — degrading safely to HITL: ${rawLlmOutput.take(200)}", e)
                }
                ReasoningLeakSanitizer.buildUnsafeOutputHitlResponse()
            }.let { response ->
                if (ReasoningLeakSanitizer.isLeakedReasoningText(response.finalReplyText)) {
                    PersonaLogger.w(PersonaLogTags.LLM, "Reasoning leakage in parsed final_reply_text — routing to HITL guard")
                    Log.w(TAG, "Reasoning leakage in parsed final_reply_text — overriding with HITL guard.")
                    ReasoningLeakSanitizer.buildUnsafeOutputHitlResponse()
                } else {
                    response
                }
            }

            // Agentic Order Actions & Ingestion Routing
            var overriddenReplyText: String? = null
            val orderAction = agenticResponse.orderAction?.trim()?.uppercase()
                ?.takeIf { it == "CREATE" || it == "CANCEL" || it == "UPDATE" }

            Log.i("PersonaMetrics", "metric=order_extraction_rate value=${if (agenticResponse.extractedOrder != null) 1 else 0} contact=${processed.contactName.hashCode()}")
            Log.i("PersonaMetrics", "metric=order_action_usage value=${orderAction ?: if (agenticResponse.orderAction != null) "UNKNOWN" else "NONE"} contact=${processed.contactName.hashCode()}")

            if (agenticResponse.extractedOrder != null) {
                val extracted = agenticResponse.extractedOrder
                val existingOrder = orderDao.getActiveMatchingOrder(processed.contactName)

                when {
                    orderAction == "CANCEL" -> {
                        if (!orderIngestionManager.executeCancelOrderAction(processed)) {
                            overriddenReplyText = NO_ACTIVE_ORDER_REPLY
                        }
                    }
                    orderAction == "UPDATE" && existingOrder != null -> {
                        orderIngestionManager.applyOrderUpdates(existingOrder, extracted)
                        if (existingOrder.status == OrderStatus.PENDING.name) {
                            orderDao.updateStatus(existingOrder.id, OrderStatus.IN_PREPARATION.name)
                            Log.i(TAG, "📈 Order id=${existingOrder.id} advanced PENDING → IN_PREPARATION after customer confirm/update")
                        }
                    }
                    else -> {
                        val result = orderIngestionManager.handleOrderCreation(
                            extractedOrder = agenticResponse.extractedOrder,
                            contactName = processed.contactName,
                            customerText = processed.originalText,
                            orderAction = orderAction ?: "",
                            sourceClass = sourceClassStr
                        )
                        if (result is OrderIngestionResult.DuplicatePivot) {
                            overriddenReplyText = result.pivotReplyText
                        }
                    }
                }
            } else {
                when (orderAction) {
                    "CANCEL" -> {
                        if (!orderIngestionManager.executeCancelOrderAction(processed)) {
                            overriddenReplyText = NO_ACTIVE_ORDER_REPLY
                        }
                    }
                    "UPDATE" -> {
                        val existingOrder = orderDao.getActiveMatchingOrder(processed.contactName)
                        if (existingOrder == null) {
                            Log.i(TAG, "order_action=UPDATE but no active order for ${processed.contactName} — nothing to update")
                        } else if (existingOrder.status == OrderStatus.PENDING.name) {
                            orderDao.updateStatus(existingOrder.id, OrderStatus.IN_PREPARATION.name)
                            Log.i(TAG, "📈 Order id=${existingOrder.id} advanced PENDING → IN_PREPARATION after customer confirmation")
                        }
                    }
                    else -> Unit
                }
            }

            val rawAction = agenticResponse.action.trim().uppercase()
            val normalizedAction = when (rawAction) {
                "UPDATE", "CREATE" -> "REPLY"
                else -> rawAction
            }
            val effectiveAction = if (normalizedAction == "RE_QUERY" && agenticResponse.refinedSearchKeyword.isNullOrBlank() && !agenticResponse.finalReplyText.isNullOrBlank()) {
                "REPLY"
            } else {
                normalizedAction
            }

            val rawReplyText: String? = if (overriddenReplyText != null) {
                overriddenReplyText
            } else {
                when {
                    effectiveAction == "HITL" -> {
                        Log.i(TAG, "LLM action=HITL. Triggering alarm and hand-off notification.")
                        hitlNotificationHandler.handleHitlEscalation(processed, maxScore)
                        hitlNotificationHandler.notifyMerchantHandover(processed.contactName, processed.sourceClass.name, "1003")
                        val hitlReply = if (!agenticResponse.finalReplyText.isNullOrBlank()) {
                            agenticResponse.finalReplyText
                        } else {
                            CANONICAL_HITL_MESSAGE
                        }
                        Log.i("PersonaMetrics", "metric=hitl_rate value=1 contact=${processed.contactName.hashCode()}")
                        sendImmediateReply(processed, ReasoningLeakSanitizer.ensureHitlSuffix(hitlReply))
                        return
                    }

                    effectiveAction == "REPLY" -> {
                        Log.i(TAG, "LLM action=REPLY. Dispatching final reply.")
                        agenticResponse.finalReplyText
                    }

                    effectiveAction == "RE_QUERY" -> {
                        val refinedKeyword = agenticResponse.refinedSearchKeyword?.trim().orEmpty()
                        Log.i(TAG, "LLM action=RE_QUERY. Refined keyword: '$refinedKeyword'")
                        PersonaLogger.d(PersonaLogTags.LLM, "RE_QUERY loop triggered")

                        val isProductCatalogQuery = refinedKeyword.contains("منتج") ||
                            refinedKeyword.contains("المنتجات") ||
                            refinedKeyword.contains("أسعار") ||
                            refinedKeyword.contains("كتالوج") ||
                            processed.originalText.contains("منتج") ||
                            processed.originalText.contains("المنتجات") ||
                            processed.originalText.contains("أسعار") ||
                            processed.originalText.contains("كتالوج")

                        val catalogCandidates = if (isProductCatalogQuery) {
                            try {
                                businessKnowledgeDao.getProductCatalogChunks(limit = 5).map { proj ->
                                    RetrievalCandidate(
                                        chunkId = proj.chunkId,
                                        headerContext = proj.headerContext,
                                        chunkText = proj.chunkText,
                                        similarityScore = 0.99f,
                                        baseSimilarityScore = 0.99f
                                    )
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to load product catalog chunks", e)
                                emptyList()
                            }
                        } else {
                            emptyList()
                        }

                        val retrievedPass2: List<RetrievalCandidate> = if (refinedKeyword.isNotBlank()) {
                            val refinedEmbedding = embeddingEngine.embedForAutoReply(refinedKeyword)
                            if (refinedEmbedding != null) {
                                val (retrieved, _) = semanticRetrievalRepository.retrieveCandidates(
                                    queryEmbedding = refinedEmbedding,
                                    minimumSimilarityThreshold = ragBaseThreshold,
                                    queryText = refinedKeyword,
                                    recentReplyHashes = activeHashes
                                )
                                retrieved
                            } else {
                                emptyList()
                            }
                        } else {
                            emptyList()
                        }

                        val pass2Candidates = catalogCandidates + retrievedPass2
                        val mergedChunks = mergeCandidates(
                            pass1 = candidates,
                            pass2 = pass2Candidates,
                            limit = 8,
                            minScore = 0.38f
                        )

                        Log.i(TAG, "RE_QUERY merged to ${mergedChunks.size} chunks (Pass1: ${candidates.size}, Pass2: ${pass2Candidates.size}). Executing final LLM pass.")

                        val finalOutput = replyGenerationRepository.generateFinalReply(
                            queryText = processed.originalText,
                            chunks = mergedChunks,
                            isOngoingConversation = isOngoing,
                            chatHistory = chatHistory,
                            activeOrders = activeOrders,
                            personalChunks = emptyList(),
                            maxTokens = 750
                        )

                        if (finalOutput.isNullOrBlank()) {
                            null
                        } else {
                            val finalParsed = try {
                                ReasoningLeakSanitizer.decodeAgenticResponseSafely(finalOutput)
                            } catch (e: Exception) {
                                Log.w(TAG, "LLM final output is not valid JSON — degrading safely to HITL: ${finalOutput.take(200)}", e)
                                ReasoningLeakSanitizer.buildUnsafeOutputHitlResponse()
                            }.let { response ->
                                if (ReasoningLeakSanitizer.isLeakedReasoningText(response.finalReplyText)) {
                                    PersonaLogger.w(PersonaLogTags.LLM, "Reasoning leakage in final-pass final_reply_text — routing to HITL guard")
                                    Log.w(TAG, "Reasoning leakage in final-pass final_reply_text — overriding with HITL guard.")
                                    ReasoningLeakSanitizer.buildUnsafeOutputHitlResponse()
                                } else {
                                    response
                                }
                            }

                            val finalReplyOverride = orderIngestionManager.handleFinalPassOrderActions(finalParsed, processed)

                            if (finalParsed.action.trim().uppercase() == "HITL") {
                                Log.i(TAG, "Second-pass LLM action=HITL. Handing off to human.")
                                try {
                                    val failedQuery = FailedQueryEntity(
                                        contactDisplayName = processed.contactName,
                                        queryText = processed.originalText,
                                        bestScore = maxScore
                                    )
                                    failedQueryDao.insert(failedQuery)
                                } catch (e: Exception) {
                                    Log.e(TAG, "Failed to insert FailedQueryEntity", e)
                                }
                                hitlNotificationHandler.handleHitlEscalation(processed, maxScore)
                                hitlNotificationHandler.notifyMerchantHandover(processed.contactName, processed.sourceClass.name, "1003_2")
                                val hitlReply = if (!finalParsed.finalReplyText.isNullOrBlank()) {
                                    finalParsed.finalReplyText
                                } else {
                                    CANONICAL_HITL_MESSAGE
                                }
                                Log.i("PersonaMetrics", "metric=hitl_rate value=1 contact=${processed.contactName.hashCode()}")
                                sendImmediateReply(processed, ReasoningLeakSanitizer.ensureHitlSuffix(hitlReply))
                                return
                            }

                            finalReplyOverride ?: finalParsed.finalReplyText
                        }
                    }

                    else -> null
                }
            }

            val safeReplyText = if (!rawReplyText.isNullOrBlank()) {
                rawReplyText
            } else if (orderAction == null) {
                val storeName = prefsRepository.getStoreNameSync()
                PersonaLogger.w(TAG, "LLM returned blank final_reply_text for benign turn. Using persona fallback.")
                "أنا معاك وسامعك كويس يا فندم، بس قولّي نقدر نجهزلك إيه النهارده من منتجاتنا في ${storeName}؟ 💙"
            } else {
                Log.d(TAG, "Agentic loop produced no reply — HITL fallback.")
                hitlNotificationHandler.handleHitlEscalation(processed, maxScore)
                Log.i("PersonaMetrics", "metric=hitl_rate value=1 contact=${processed.contactName.hashCode()}")
                sendHitlHoldingReply(processed)
                return
            }

            // Style post-processing
            var styledReplyText = stylePostProcessor.process(
                rawReply = safeReplyText,
                prefs = stylePrefs,
                context = processed.context,
                userEmojiRate = 0f
            )

            // Anti-echo guard
            if (AntiEchoGuard.isEchoRepetition(styledReplyText, processed.originalText, chatHistory)) {
                if (orderAction == null) {
                    val storeName = prefsRepository.getStoreNameSync()
                    PersonaLogger.w(TAG, "Anti-echo guard triggered on standard conversational turn. Replacing with alternative persona closing.")
                    styledReplyText = "معاك يا فندم، اطلب اللي نفسك فيه من $storeName وأنا في الخدمة علطول 💙"
                } else {
                    Log.w(TAG, "Anti-echo guard triggered: duplicate reply for ${processed.contactName}. Degrading to HITL.")
                    hitlNotificationHandler.handleHitlEscalation(processed, maxScore)
                    sendHitlHoldingReply(processed)
                    return
                }
            }

            // 13. Execute direct reply via RemoteInput
            delay(stylePrefs.replyDelaySeconds * 1000L)
            val executionResult = directReplyExecutionUseCase.execute(
                processed.notificationKey,
                styledReplyText,
                processed.contactName,
                processed.sourceClass.name
            )

            // 14. Audit logging
            when (executionResult) {
                is ReplyExecutionResult.Success -> {
                    cooldownMap[processed.contactName] = System.currentTimeMillis()
                    lastReplyTimestamps[processed.contactName] = System.currentTimeMillis()
                    antiEchoGuard.addReplyToCache(styledReplyText)

                    val botChatHistory = ChatHistoryEntity(
                        contactName = processed.contactName,
                        messageText = styledReplyText,
                        isFromUser = false,
                        timestamp = System.currentTimeMillis(),
                        embedding = null,
                        embeddingState = "PENDING",
                        sourceClass = processed.sourceClass.name,
                        senderType = "AI_AGENT",
                        deliveryStatus = "SENT",
                        isRead = true
                    )
                    val botInsertedId = chatHistoryDao.insert(botChatHistory)
                    Log.d(TAG, "Bot reply persisted to chat history, id=$botInsertedId")

                    conversationMetadataDao.recordMessageTurn(
                        contactName = processed.contactName,
                        sourceClass = processed.sourceClass.name,
                        messageText = styledReplyText,
                        senderType = "AI_AGENT",
                        timestamp = System.currentTimeMillis(),
                        notificationKey = processed.notificationKey,
                        incrementUnread = false
                    )

                    scope.launch(Dispatchers.Default) {
                        val claimSuccess = chatHistoryDao.claimForProcessing(botInsertedId)
                        if (claimSuccess > 0) {
                            try {
                                val startTime = System.currentTimeMillis()
                                val embedding = embeddingEngine.embedForAutoReply(styledReplyText)
                                val latency = System.currentTimeMillis() - startTime
                                Log.i("PersonaMetrics", "metric=embedding_latency_ms value=$latency contact=${processed.contactName.hashCode()}")
                                if (embedding != null) {
                                    val embeddingBlob = Converters().fromFloatArray(embedding)
                                    if (embeddingBlob != null) {
                                        chatHistoryDao.updateEmbeddingRaw(botInsertedId, embeddingBlob)
                                    } else {
                                        chatHistoryDao.updateEmbeddingState(botInsertedId, "FAILED")
                                    }
                                } else {
                                    chatHistoryDao.updateEmbeddingState(botInsertedId, "FAILED")
                                }
                            } catch (e: Exception) {
                                chatHistoryDao.updateEmbeddingState(botInsertedId, "FAILED")
                            }
                        }
                    }

                    val auditCandidate = candidates.firstOrNull() ?: RetrievalCandidate(
                        chunkId = "CONVERSATIONAL_REPLY",
                        headerContext = "CONVERSATIONAL",
                        chunkText = styledReplyText,
                        similarityScore = 1.0f,
                        baseSimilarityScore = 1.0f
                    )
                    auditLogRepository.logExecutedReply(processed, auditCandidate, styledReplyText)
                    PersonaLogger.d(PersonaLogTags.ORCHESTRATOR, "Reply executed")
                    Log.i(TAG, "Auto-replied to ${processed.contactName}: '$styledReplyText'")
                
                    Log.i("PersonaMetrics", "metric=hitl_rate value=0 contact=${processed.contactName.hashCode()}")
                    Log.i("PersonaMetrics", "metric=notification_drop_rate value=0 contact=${processed.contactName.hashCode()}")
                }
                is ReplyExecutionResult.Aborted -> {
                    auditLogRepository.clearPendingRepliesForContact(processed.contactName)
                    PersonaLogger.d(PersonaLogTags.ORCHESTRATOR, "Reply aborted: notification no longer active")
                    Log.i(TAG, "Auto-reply aborted for ${processed.contactName}: notification is no longer active.")
                }
                is ReplyExecutionResult.Failed -> {
                    auditLogRepository.queuePendingReply(processed, styledReplyText)
                    PersonaLogger.d(PersonaLogTags.ORCHESTRATOR, "Reply queued")
                    Log.w(TAG, "Failed to send to ${processed.contactName}: ${executionResult.reason}. Queued.")
                }
            }
        } finally {
            setContactProcessing(processed.contactName, false)
        }
    }

    private suspend fun sendImmediateReply(processed: ProcessedNotification, text: String) {
        val executionResult = directReplyExecutionUseCase.execute(
            processed.notificationKey,
            text,
            processed.contactName,
            processed.sourceClass.name
        )
        when (executionResult) {
            is ReplyExecutionResult.Success -> {
                cooldownMap[processed.contactName] = System.currentTimeMillis()
                lastReplyTimestamps[processed.contactName] = System.currentTimeMillis()
                antiEchoGuard.addReplyToCache(text)

                val botChatHistory = ChatHistoryEntity(
                    contactName = processed.contactName,
                    messageText = text,
                    isFromUser = false,
                    timestamp = System.currentTimeMillis(),
                    embedding = null,
                    embeddingState = "PENDING",
                    sourceClass = processed.sourceClass.name,
                    senderType = "AI_AGENT",
                    deliveryStatus = "SENT",
                    isRead = true
                )
                val botInsertedId = chatHistoryDao.insert(botChatHistory)
                Log.d(TAG, "Immediate bot reply persisted to chat history, id=$botInsertedId")

                conversationMetadataDao.recordMessageTurn(
                    contactName = processed.contactName,
                    sourceClass = processed.sourceClass.name,
                    messageText = text,
                    senderType = "AI_AGENT",
                    timestamp = System.currentTimeMillis(),
                    notificationKey = processed.notificationKey,
                    incrementUnread = false
                )

                scope.launch(Dispatchers.Default) {
                    val claimSuccess = chatHistoryDao.claimForProcessing(botInsertedId)
                    if (claimSuccess > 0) {
                        try {
                            val startTime = System.currentTimeMillis()
                            val embedding = embeddingEngine.embedForAutoReply(text)
                            val latency = System.currentTimeMillis() - startTime
                            Log.i("PersonaMetrics", "metric=embedding_latency_ms value=$latency contact=${processed.contactName.hashCode()}")
                            if (embedding != null) {
                                val embeddingBlob = Converters().fromFloatArray(embedding)
                                if (embeddingBlob != null) {
                                    chatHistoryDao.updateEmbeddingRaw(botInsertedId, embeddingBlob)
                                } else {
                                    chatHistoryDao.updateEmbeddingState(botInsertedId, "FAILED")
                                }
                            } else {
                                chatHistoryDao.updateEmbeddingState(botInsertedId, "FAILED")
                            }
                        } catch (e: Exception) {
                            chatHistoryDao.updateEmbeddingState(botInsertedId, "FAILED")
                        }
                    }
                }

                val dummyCandidate = RetrievalCandidate(
                    chunkId = "TEMPLATE_INTERCEPT",
                    headerContext = "TEMPLATE",
                    chunkText = text,
                    similarityScore = 1.0f,
                    baseSimilarityScore = 1.0f
                )
                auditLogRepository.logExecutedReply(processed, dummyCandidate, text)
                Log.i(TAG, "Interception template sent to ${processed.contactName}: '$text'")
            }
            is ReplyExecutionResult.Failed -> {
                auditLogRepository.queuePendingReply(processed, text)
                Log.w(TAG, "Failed to send template to ${processed.contactName}: ${executionResult.reason}. Queued.")
            }
            is ReplyExecutionResult.Aborted -> {
                Log.i(TAG, "Interception template aborted for ${processed.contactName}: notification no longer active.")
            }
        }
    }

    private suspend fun sendHitlHoldingReply(processed: ProcessedNotification) {
        sendImmediateReply(processed, CANONICAL_HITL_MESSAGE)
    }

    internal fun containsMediaIndicators(text: String): Boolean {
        if (text.startsWith("[رسالة صوتية من العميل]:")) return false
        val mediaEmojis = listOf("📷", "🎵", "🎤", "📹", "🎥", "📄", "📸", "🎬", "🎙️")
        if (mediaEmojis.any { text.contains(it) }) return true
        val lower = text.lowercase()
        val mediaKeywords = listOf(
            "photo", "video", "audio", "document", "voice note", "voice message",
            "صورة", "فيديو", "رسالة صوتية", "ملف", "مستند"
        )
        return mediaKeywords.any { lower.contains(it) }
    }

    // Delegated helpers for backward compatibility with existing tests
    internal suspend fun handleOrderCreation(
        extractedOrder: ExtractedOrder?,
        contactName: String,
        customerText: String = "",
        orderAction: String? = "",
        sourceClass: String = "BUSINESS"
    ): OrderIngestionResult = orderIngestionManager.handleOrderCreation(
        extractedOrder, contactName, customerText, orderAction, sourceClass
    )

    internal suspend fun executeCancelOrderAction(processed: ProcessedNotification): Boolean =
        orderIngestionManager.executeCancelOrderAction(processed)

    internal suspend fun applyOrderUpdates(existingOrder: com.example.persona.core.database.entity.OrderEntity, extracted: ExtractedOrder?) =
        orderIngestionManager.applyOrderUpdates(existingOrder, extracted)

    internal suspend fun handleFinalPassOrderActions(
        finalParsed: AgenticLlmResponse,
        processed: ProcessedNotification
    ): String? = orderIngestionManager.handleFinalPassOrderActions(finalParsed, processed)
}

