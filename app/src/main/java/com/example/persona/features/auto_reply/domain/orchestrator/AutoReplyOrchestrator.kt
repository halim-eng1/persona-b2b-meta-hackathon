package com.example.persona.features.auto_reply.domain.orchestrator

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.persona.R
import com.example.persona.core.data.repository.UserPreferencesRepository
import com.example.persona.core.database.dao.ChatHistoryDao
import com.example.persona.core.database.dao.FailedQueryDao
import com.example.persona.core.database.dao.OrderDao
import com.example.persona.core.database.entity.ChatHistoryEntity
import com.example.persona.core.database.entity.FailedQueryEntity
import com.example.persona.core.database.entity.OrderEntity
import com.example.persona.core.database.entity.OrderStatus
import com.example.persona.core.di.ApplicationScope
import com.example.persona.core.logging.PersonaLogger
import com.example.persona.core.logging.PersonaLogTags
import com.example.persona.features.auto_reply.data.service.IncomingMessageDispatcher
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
import com.example.persona.features.chat_import.domain.model.IntentType
import com.example.persona.core.network.dto.AgenticLlmResponse
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import javax.inject.Inject
import javax.inject.Singleton

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
    @ApplicationContext private val context: Context,
    @ApplicationScope private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "AutoReplyOrchestrator"
        private const val ALARM_CHANNEL_ID = "hitl_alarm_channel"
        private val SAFETY_KEYWORDS = listOf(
            "ضروري", "مشكلة", "مستشفى", "الحقني", "مصيبة", "urgent", "help"
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

    // LRU Cache for last 3 globally transmitted replies (hash to timestamp)
    private val recentReplyCache = ConcurrentHashMap<Int, Long>()
    private val recentReplyQueue = ConcurrentLinkedQueue<Int>()

    private fun addReplyToCache(text: String) {
        val hash = text.hashCode()
        val now = System.currentTimeMillis()
        synchronized(this) {
            recentReplyQueue.remove(hash)
            recentReplyCache[hash] = now
            recentReplyQueue.add(hash)
            while (recentReplyQueue.size > 3) {
                val evicted = recentReplyQueue.poll()
                if (evicted != null) {
                    recentReplyCache.remove(evicted)
                }
            }
        }
    }

    private fun getActiveReplyHashes(): List<Int> {
        val now = System.currentTimeMillis()
        val expiredThreshold = now - 30_000L // 30 seconds
        synchronized(this) {
            val iterator = recentReplyQueue.iterator()
            while (iterator.hasNext()) {
                val hash = iterator.next()
                val ts = recentReplyCache[hash] ?: 0L
                if (ts < expiredThreshold) {
                    iterator.remove()
                    recentReplyCache.remove(hash)
                }
            }
            return recentReplyQueue.toList()
        }
    }

    private var isCollecting = false

    init {
        createAlarmNotificationChannel()
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
                // enforce queue size safety (drop oldest)
                while (receivedNotifications.size > MAX_CONTEXT_QUEUE_SIZE) {
                    receivedNotifications.poll()
                }
                // prune messages older than 10 minutes to prevent memory leak
                val cutOff = System.currentTimeMillis() - 10 * 60 * 1000L
                receivedNotifications.removeIf { it.postTime < cutOff }

                channel.send(notification)
            }
        }
        Log.i(TAG, "AutoReplyOrchestrator started listening to IncomingMessageDispatcher")
    }

    private suspend fun processNotificationSequentially(rawNotification: WhatsAppNotification) {
        // 1. Global switch
        if (!prefsRepository.isAutoReplyEnabledSync()) {
            PersonaLogger.d(PersonaLogTags.ORCHESTRATOR, "Notification rejected")
            Log.d(TAG, "Auto-reply globally disabled. Skipping ${rawNotification.title}")
            return
        }

        // 2. Load style preferences
        val stylePrefs = prefsRepository.getStylePreferencesSync()
        val cooldownMs = stylePrefs.cooldownMins * 60_000L

        // 3. Pre-process notification
        val processed = processor.process(rawNotification)

        Log.i(PersonaLogTags.LLM, "📩 Incoming message from [${processed.contactName}]: \"${processed.originalText}\"")

        // 3.1. Persist inbound message immediately to chat_history
        val chatHistoryEntity = ChatHistoryEntity(
            contactName = processed.contactName,
            messageText = processed.originalText,
            isFromUser = true,
            timestamp = processed.postTime,
            embedding = null,
            embeddingState = "PENDING"
        )
        val insertedId = chatHistoryDao.insert(chatHistoryEntity)
        Log.d(TAG, "Chat history row inserted for contact=${processed.contactName}, id=$insertedId")

        // 3.2. Launch background embedding generation coroutine on scope (detached from notification thread)
        scope.launch(Dispatchers.Default) {
            val claimSuccess = chatHistoryDao.claimForProcessing(insertedId)
            if (claimSuccess > 0) {
                try {
                    val startTime = System.currentTimeMillis()
                    val embedding = embeddingEngine.embedForAutoReply(processed.normalizedText)
                    val latency = System.currentTimeMillis() - startTime
                    Log.i("PersonaMetrics", "metric=embedding_latency_ms value=$latency contact=${processed.contactName.hashCode()}")
                    if (embedding != null) {
                        chatHistoryDao.updateEmbedding(insertedId, embedding)
                        chatHistoryDao.updateEmbeddingState(insertedId, "DONE")
                        Log.d(TAG, "Background embedding completed and saved for chatHistory id=$insertedId")
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

        // 4. Cooldown policy
        val now = System.currentTimeMillis()
        val lastReplyTime = cooldownMap[processed.contactName] ?: 0L
        if (stylePrefs.cooldownMins != 0 && now - lastReplyTime < cooldownMs) {
            PersonaLogger.d(PersonaLogTags.ORCHESTRATOR, "Cooldown triggered")
            Log.d(TAG, "Ignoring '${processed.contactName}': in cooldown (${stylePrefs.cooldownMins} min)")
            Log.i("PersonaMetrics", "metric=notification_drop_rate value=1 contact=${processed.contactName.hashCode()}")
            return
        }

        // 5. Human safety filter
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

            // Trigger alarm vibration & sound notification
            triggerHitlAlarm(processed.contactName, processed.originalText, 1.0f)
            
            Log.i("PersonaMetrics", "metric=notification_drop_rate value=1 contact=${processed.contactName.hashCode()}")
            Log.i("PersonaMetrics", "metric=hitl_rate value=1 contact=${processed.contactName.hashCode()}")

            // Auto-reply with holding message to customer
            sendImmediateReply(processed, ensureHitlSuffix(context.getString(R.string.hitl_holding_message)))
            return
        }

        // 6. Media Fallback handling
        if (containsMediaIndicators(processed.originalText)) {
            Log.i(TAG, "Media indicator detected in message from ${processed.contactName}. Sending media fallback.")
            Log.i("PersonaMetrics", "metric=notification_drop_rate value=1 contact=${processed.contactName.hashCode()}")
            sendImmediateReply(processed, context.getString(R.string.media_fallback_message))
            return
        }

        // 7. Tier A Interception (Greetings/Farewells short-circuit)
        val intent = processed.context.intent
        if (intent == IntentType.GREETING) {
            Log.i(TAG, "Greeting intent detected. Sending Tier A greeting template.")
            val defaultGreeting = context.getString(R.string.greeting_template_default)
            val template = prefsRepository.getGreetingTemplateSync(defaultGreeting)
            sendImmediateReply(processed, template)
            return
        } else if (intent == IntentType.CLOSING) {
            Log.i(TAG, "Closing intent detected. Sending Tier A farewell template.")
            val defaultFarewell = context.getString(R.string.farewell_template_default)
            val template = prefsRepository.getFarewellTemplateSync(defaultFarewell)
            sendImmediateReply(processed, template)
            return
        }

        // 8. RAG Retrieval Engine
        val priorMessages = getPriorMessages(rawNotification)
        val hasContext = priorMessages.isNotEmpty()

        val richContext = if (hasContext) {
            val prunedPrior = priorMessages.takeLast(2)
            val normalizedPrior = prunedPrior.map { normalizer.normalize(it) ?: it }
            normalizedPrior.joinToString("\n") + "\n" + processed.normalizedText
        } else {
            processed.normalizedText
        }

        val activeHashes = getActiveReplyHashes()
        var candidates: List<RetrievalCandidate> = emptyList()
        var maxScore = 0f
        val queryEmbeddingForPersonalSweep: FloatArray?

        if (hasContext) {
            // Late Fusion Parallel Dual Search (Late Fusion)
            val queryEmbeddingA = embeddingEngine.embedForAutoReply(richContext)
            val queryEmbeddingB = embeddingEngine.embedForAutoReply(processed.normalizedText)
            queryEmbeddingForPersonalSweep = queryEmbeddingB ?: queryEmbeddingA

            if (queryEmbeddingA != null && queryEmbeddingB != null) {
                val (candidatesA, scoreA) = semanticRetrievalRepository.retrieveCandidates(
                    queryEmbedding = queryEmbeddingA,
                    minimumSimilarityThreshold = -1.0f,
                    queryText = processed.normalizedText,
                    recentReplyHashes = activeHashes
                )

                val (candidatesB, scoreB) = semanticRetrievalRepository.retrieveCandidates(
                    queryEmbedding = queryEmbeddingB,
                    minimumSimilarityThreshold = -1.0f,
                    queryText = processed.normalizedText,
                    recentReplyHashes = activeHashes
                )

                maxScore = maxOf(scoreA, scoreB)

                val combined = candidatesA + candidatesB
                val uniqueCandidates = combined
                    .groupBy { it.chunkText }
                    .map { (_, group) -> group.maxByOrNull { it.similarityScore }!! }

                val topB = candidatesB.firstOrNull()
                candidates = if (topB != null && topB.baseSimilarityScore > 0.90f) {
                    val prioritized = uniqueCandidates.first { it.chunkText == topB.chunkText }
                    val remaining = uniqueCandidates.filter { it.chunkText != topB.chunkText }
                        .sortedByDescending { it.similarityScore }
                    listOf(prioritized) + remaining
                } else {
                    uniqueCandidates.sortedByDescending { it.similarityScore }
                }
            }
        } else {
            val queryEmbedding = embeddingEngine.embedForAutoReply(richContext)
            queryEmbeddingForPersonalSweep = queryEmbedding
            if (queryEmbedding != null) {
                val (retrievedCandidates, bestScore) = semanticRetrievalRepository.retrieveCandidates(
                    queryEmbedding = queryEmbedding,
                    minimumSimilarityThreshold = -1.0f,
                    queryText = processed.normalizedText,
                    recentReplyHashes = activeHashes
                )
                candidates = retrievedCandidates
                maxScore = bestScore
            }
        }

        // 8.1. Context Assembly (Sources A, B, C)
        val contextStart = System.currentTimeMillis()
        
        // Source A: Chronological turns
        val chatHistory = chatHistoryDao.getRecentMessagesExcept(
            contactName = processed.contactName,
            excludeId = insertedId,
            limit = 6
        )
        
        // Source B: Global candidates (candidates)
        
        // Source C: Personal client state
        val activeOrders = orderDao.getActiveOrdersByContact(processed.contactName)
        val embeddedMessages = chatHistoryDao.getEmbeddedMessages(processed.contactName, limit = 20)
        val personalChunks = if (queryEmbeddingForPersonalSweep != null) {
            embeddedMessages.mapNotNull { msg ->
                val msgEmbedding = msg.embedding
                if (msgEmbedding != null) {
                    msg to dotProduct(queryEmbeddingForPersonalSweep, msgEmbedding)
                } else {
                    null
                }
            }.sortedByDescending { it.second }
             .take(2)
             .map { it.first }
        } else {
            emptyList()
        }
        
        val contextBuildMs = System.currentTimeMillis() - contextStart
        Log.d(TAG, "📊 Context assembly completed in ${contextBuildMs}ms: chatTurns=${chatHistory.size}, activeOrders=${activeOrders.size}, personalChunks=${personalChunks.size}")
        Log.i("PersonaMetrics", "metric=context_build_ms value=$contextBuildMs contact=${processed.contactName.hashCode()}")

        // 9. Tier B HITL Trigger
        if (candidates.isEmpty()) {
            Log.d(TAG, "No RAG candidates above threshold for ${processed.contactName}. Best score: $maxScore. Triggering Tier B HITL.")

            // Log unanswered failed query
            val failedQuery = FailedQueryEntity(
                contactDisplayName = processed.contactName,
                queryText = processed.originalText,
                bestScore = maxScore
            )
            failedQueryDao.insert(failedQuery)

            // Trigger alarm notification to notify owner immediately
            triggerHitlAlarm(processed.contactName, processed.originalText, maxScore)
            
            Log.i("PersonaMetrics", "metric=hitl_rate value=1 contact=${processed.contactName.hashCode()}")

            // Auto-reply with holding message to customer
            sendImmediateReply(processed, ensureHitlSuffix(context.getString(R.string.hitl_holding_message)))
            return
        }

        // 10. Agentic LLM Generation with Re-Query Loop
        val lastSessionReplyTime = lastReplyTimestamps[processed.contactName] ?: 0L
        val isOngoing = (System.currentTimeMillis() - lastSessionReplyTime) < 300_000L

        // 10a. First LLM pass — may return REPLY or RE_QUERY
        val rawLlmOutput = replyGenerationRepository.generateReply(
            notification = processed,
            candidates = candidates,
            isOngoingConversation = isOngoing,
            chatHistory = chatHistory,
            activeOrders = activeOrders,
            personalChunks = personalChunks,
            maxTokens = 180
        )
        if (rawLlmOutput.isNullOrBlank()) {
            Log.d(TAG, "LLM returned null/blank — all models failed. HITL fallback.")
            triggerHitlAlarm(processed.contactName, processed.originalText, maxScore)
            Log.i("PersonaMetrics", "metric=hitl_rate value=1 contact=${processed.contactName.hashCode()}")
            sendImmediateReply(processed, ensureHitlSuffix(context.getString(R.string.hitl_holding_message)))
            return
        }

        // 10b. Parse structured JSON (with markdown code-block stripping)
        val sanitizedOutput = sanitizeLlmJson(rawLlmOutput)
        val agenticResponse = try {
            agenticJson.decodeFromString<AgenticLlmResponse>(sanitizedOutput)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse agentic JSON, using raw text as reply: $sanitizedOutput", e)
            null  // Graceful degradation — treat as raw reply text
        }

        Log.i("PersonaMetrics", "metric=order_extraction_rate value=${if (agenticResponse?.extractedOrder != null) 1 else 0} contact=${processed.contactName.hashCode()}")

        // 5.1 & 5.2 Order Ingestion & Deduplication Ingestion Routing
        var overriddenReplyText: String? = null
        if (agenticResponse?.extractedOrder != null) {
            val extracted = agenticResponse.extractedOrder
            val normalizedCustomerName = normalizeField(extracted.customerName)
            val normalizedDeliveryAddress = normalizeField(extracted.deliveryAddress)
            val normalizedOrderDetails = normalizeField(extracted.orderDetails)
            val normalizedCustomNotes = normalizeField(extracted.customNotes)

            // Deduplication Check
            val existingOrder = orderDao.getActiveMatchingOrder(processed.contactName)
            if (existingOrder != null) {
                // Check if last bot message was pivot
                val recentChatTurns = chatHistoryDao.getRecentMessages(processed.contactName, limit = 6)
                val lastBotMsg = recentChatTurns.firstOrNull { !it.isFromUser }
                val lastWasPivot = lastBotMsg?.messageText?.contains("أنا شايف إنك لسه عامل أوردر مشابه من شوية") == true

                if (lastWasPivot) {
                    val newNotes = buildString {
                        normalizedCustomerName?.let { append("الاسم: $it\n") }
                        normalizedDeliveryAddress?.let { append("العنوان: $it\n") }
                        normalizedOrderDetails?.let { append("التفاصيل: $it\n") }
                        normalizedCustomNotes?.let { append("ملاحظات: $it\n") }
                    }.trim()
                    if (newNotes.isNotEmpty()) {
                        orderDao.appendCustomNotes(existingOrder.id, newNotes)
                        Log.i(TAG, "Merged order details into existing order id=${existingOrder.id} for ${processed.contactName}")
                    }
                } else {
                    Log.i(TAG, "🔄 Duplicate order detected for ${processed.contactName}. Sending verification pivot.")
                    overriddenReplyText = "أنا شايف إنك لسه عامل أوردر مشابه من شوية 😊 تحب نعدل على الأوردر القديم ونضيف عليه التفاصيل الجديدة دي، ولا ده أوردر جديد خالص يا فندم؟"
                }
            } else {
                val newOrder = OrderEntity(
                    contactName = processed.contactName,
                    customerNameFromLlm = normalizedCustomerName,
                    deliveryAddress = normalizedDeliveryAddress,
                    orderDetails = normalizedOrderDetails,
                    customNotes = normalizedCustomNotes,
                    status = OrderStatus.PENDING.name
                )
                val insertedOrderId = orderDao.insert(newOrder)
                Log.i(TAG, "📦 New order ingested for ${processed.contactName}: orderId=$insertedOrderId")
            }
        }

        val rawReplyText: String? = if (overriddenReplyText != null) {
            overriddenReplyText
        } else {
            when {
                // Case 0: Handover to human representative (LLM requested HITL)
                agenticResponse?.action == "HITL" -> {
                    Log.i(TAG, "LLM action=HITL. Triggering alarm and hand-off notification.")
                    triggerHitlAlarm(processed.contactName, processed.originalText, maxScore)
                    try {
                        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                        val vibrationPattern = longArrayOf(0, 500, 200, 500)
                        val notification = NotificationCompat.Builder(context, ALARM_CHANNEL_ID)
                            .setSmallIcon(R.drawable.ic_notification)
                            .setContentTitle("طلب تحويل لبشري 🚨")
                            .setContentText("العميل ${processed.contactName} يطلب التحدث مع موظف بشري الآن!")
                            .setStyle(NotificationCompat.BigTextStyle().bigText("العميل ${processed.contactName} يطلب التحدث مع موظف بشري الآن!"))
                            .setVibrate(vibrationPattern)
                            .setPriority(NotificationCompat.PRIORITY_HIGH)
                            .setCategory(NotificationCompat.CATEGORY_ALARM)
                            .build()
                        notificationManager.notify(1003, notification)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to post hand-off notification to merchant", e)
                    }
                    val hitlReply = if (!agenticResponse.finalReplyText.isNullOrBlank()) {
                        agenticResponse.finalReplyText
                    } else {
                        "تمام يا فندم، ثواني وهيكون مع حضرتك ممثل خدمة العملاء فوراً لمساعدتك 🙏"
                    }
                    Log.i("PersonaMetrics", "metric=hitl_rate value=1 contact=${processed.contactName.hashCode()}")
                    sendImmediateReply(processed, ensureHitlSuffix(hitlReply))
                    return
                }

                // Case 1: Valid REPLY action
                agenticResponse?.action == "REPLY" && !agenticResponse.finalReplyText.isNullOrBlank() -> {
                    Log.i(TAG, "LLM action=REPLY. Dispatching final reply.")
                    agenticResponse.finalReplyText
                }

                // Case 2: RE_QUERY — execute one agentic loopback
                agenticResponse?.action == "RE_QUERY" && !agenticResponse.refinedSearchKeyword.isNullOrBlank() -> {
                    Log.i(TAG, "LLM action=RE_QUERY. Refined keyword: '${agenticResponse.refinedSearchKeyword}'")
                    PersonaLogger.d(PersonaLogTags.LLM, "RE_QUERY loop triggered")

                    // 10c. Embed the refined keyword
                    val refinedEmbedding = embeddingEngine.embedForAutoReply(agenticResponse.refinedSearchKeyword)
                    if (refinedEmbedding == null) {
                        Log.e(TAG, "Failed to embed refined keyword — aborting RE_QUERY")
                        null
                    } else {
                        // 10d. Second vector sweep with refined keyword
                        val (newChunks, _) = semanticRetrievalRepository.retrieveCandidates(
                            queryEmbedding = refinedEmbedding,
                            minimumSimilarityThreshold = -1.0f,
                            queryText = agenticResponse.refinedSearchKeyword,
                            recentReplyHashes = activeHashes
                        )

                        if (newChunks.isEmpty()) {
                            Log.d(TAG, "RE_QUERY returned no new chunks — HITL fallback")
                            null
                        } else {
                            Log.i(TAG, "RE_QUERY returned ${newChunks.size} new chunks. Executing final LLM pass.")

                            // 10e. Second LLM pass — REPLY-only mode (no further RE_QUERY allowed)
                            val finalOutput = replyGenerationRepository.generateFinalReply(
                                queryText = processed.originalText,
                                chunks = newChunks,
                                isOngoingConversation = isOngoing,
                                chatHistory = chatHistory,
                                activeOrders = activeOrders,
                                personalChunks = personalChunks,
                                maxTokens = 350
                            )

                            if (finalOutput.isNullOrBlank()) {
                                null
                            } else {
                                // Parse the final output
                                val sanitizedFinal = sanitizeLlmJson(finalOutput)
                                val finalParsed = try {
                                    agenticJson.decodeFromString<AgenticLlmResponse>(sanitizedFinal)
                                } catch (_: Exception) {
                                    null
                                }

                                if (finalParsed?.action == "HITL") {
                                    Log.i(TAG, "Second-pass LLM action=HITL. Handing off to human.")
                                    triggerHitlAlarm(processed.contactName, processed.originalText, maxScore)
                                    try {
                                        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                                        val vibrationPattern = longArrayOf(0, 500, 200, 500)
                                        val notification = NotificationCompat.Builder(context, ALARM_CHANNEL_ID)
                                            .setSmallIcon(R.drawable.ic_notification)
                                            .setContentTitle("طلب تحويل لبشري 🚨")
                                            .setContentText("العميل ${processed.contactName} يطلب التحدث مع موظف بشري الآن!")
                                            .setStyle(NotificationCompat.BigTextStyle().bigText("العميل ${processed.contactName} يطلب التحدث مع موظف بشري الآن!"))
                                            .setVibrate(vibrationPattern)
                                            .setPriority(NotificationCompat.PRIORITY_HIGH)
                                            .setCategory(NotificationCompat.CATEGORY_ALARM)
                                            .build()
                                        notificationManager.notify(1003, notification)
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Failed to post hand-off notification to merchant", e)
                                    }
                                    val hitlReply = if (!finalParsed.finalReplyText.isNullOrBlank()) {
                                        finalParsed.finalReplyText
                                    } else {
                                        "تمام يا فندم، ثواني وهيكون مع حضرتك ممثل خدمة العملاء فوراً لمساعدتك 🙏"
                                    }
                                    Log.i("PersonaMetrics", "metric=hitl_rate value=1 contact=${processed.contactName.hashCode()}")
                                    sendImmediateReply(processed, ensureHitlSuffix(hitlReply))
                                    return
                                }

                                // Use parsed finalReplyText, or fall back to raw output
                                finalParsed?.finalReplyText ?: finalOutput
                            }
                        }
                    }
                }

                // Case 3: Graceful degradation — unparseable JSON, use raw LLM text
                agenticResponse == null -> {
                    Log.i(TAG, "Using raw LLM output as reply (JSON parse failed)")
                    sanitizedOutput
                }

                else -> null
            }
        }

        if (rawReplyText.isNullOrBlank()) {
            Log.d(TAG, "Agentic loop produced no reply — HITL fallback.")
            triggerHitlAlarm(processed.contactName, processed.originalText, maxScore)
            Log.i("PersonaMetrics", "metric=hitl_rate value=1 contact=${processed.contactName.hashCode()}")
            sendImmediateReply(processed, ensureHitlSuffix(context.getString(R.string.hitl_holding_message)))
            return
        }

        // 11. Style post-processing (emoji rate is 0f since it's professional B2B)
        val styledReplyText = stylePostProcessor.process(
            rawReply = rawReplyText,
            prefs = stylePrefs,
            context = processed.context,
            userEmojiRate = 0f
        )

        // 12. Execute direct reply via RemoteInput
        delay(stylePrefs.replyDelaySeconds * 1000L)
        val executionResult = directReplyExecutionUseCase.execute(
            processed.notificationKey,
            styledReplyText,
            processed.contactName
        )

        // 13. Audit logging
        when (executionResult) {
            is ReplyExecutionResult.Success -> {
                cooldownMap[processed.contactName] = System.currentTimeMillis()
                lastReplyTimestamps[processed.contactName] = System.currentTimeMillis()
                addReplyToCache(styledReplyText)

                // Persist bot reply to chat history
                val botChatHistory = ChatHistoryEntity(
                    contactName = processed.contactName,
                    messageText = styledReplyText,
                    isFromUser = false,
                    timestamp = System.currentTimeMillis(),
                    embedding = null,
                    embeddingState = "PENDING"
                )
                val botInsertedId = chatHistoryDao.insert(botChatHistory)
                Log.d(TAG, "Bot reply persisted to chat history, id=$botInsertedId")

                // Launch background embedding coroutine
                scope.launch(Dispatchers.Default) {
                    val claimSuccess = chatHistoryDao.claimForProcessing(botInsertedId)
                    if (claimSuccess > 0) {
                        try {
                            val startTime = System.currentTimeMillis()
                            val embedding = embeddingEngine.embedForAutoReply(styledReplyText)
                            val latency = System.currentTimeMillis() - startTime
                            Log.i("PersonaMetrics", "metric=embedding_latency_ms value=$latency contact=${processed.contactName.hashCode()}")
                            if (embedding != null) {
                                chatHistoryDao.updateEmbedding(botInsertedId, embedding)
                                chatHistoryDao.updateEmbeddingState(botInsertedId, "DONE")
                            } else {
                                chatHistoryDao.updateEmbeddingState(botInsertedId, "FAILED")
                            }
                        } catch (e: Exception) {
                            chatHistoryDao.updateEmbeddingState(botInsertedId, "FAILED")
                        }
                    }
                }

                auditLogRepository.logExecutedReply(processed, candidates.first(), styledReplyText)
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
    }

    private suspend fun sendImmediateReply(processed: ProcessedNotification, text: String) {
        val executionResult = directReplyExecutionUseCase.execute(
            processed.notificationKey,
            text,
            processed.contactName
        )
        when (executionResult) {
            is ReplyExecutionResult.Success -> {
                cooldownMap[processed.contactName] = System.currentTimeMillis()
                lastReplyTimestamps[processed.contactName] = System.currentTimeMillis()
                addReplyToCache(text)

                // Persist bot reply to chat history
                val botChatHistory = ChatHistoryEntity(
                    contactName = processed.contactName,
                    messageText = text,
                    isFromUser = false,
                    timestamp = System.currentTimeMillis(),
                    embedding = null,
                    embeddingState = "PENDING"
                )
                val botInsertedId = chatHistoryDao.insert(botChatHistory)
                Log.d(TAG, "Immediate bot reply persisted to chat history, id=$botInsertedId")

                // Launch background embedding
                scope.launch(Dispatchers.Default) {
                    val claimSuccess = chatHistoryDao.claimForProcessing(botInsertedId)
                    if (claimSuccess > 0) {
                        try {
                            val startTime = System.currentTimeMillis()
                            val embedding = embeddingEngine.embedForAutoReply(text)
                            val latency = System.currentTimeMillis() - startTime
                            Log.i("PersonaMetrics", "metric=embedding_latency_ms value=$latency contact=${processed.contactName.hashCode()}")
                            if (embedding != null) {
                                chatHistoryDao.updateEmbedding(botInsertedId, embedding)
                                chatHistoryDao.updateEmbeddingState(botInsertedId, "DONE")
                            } else {
                                chatHistoryDao.updateEmbeddingState(botInsertedId, "FAILED")
                            }
                        } catch (e: Exception) {
                            chatHistoryDao.updateEmbeddingState(botInsertedId, "FAILED")
                        }
                    }
                }

                // Log template execution in audit logs
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

    private fun triggerHitlAlarm(contactName: String, queryText: String, score: Float) {
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val vibrationPattern = longArrayOf(0, 500, 200, 500)

            val title = context.getString(R.string.hitl_alarm_title)
            val body = context.getString(R.string.hitl_alarm_body)

            val notification = NotificationCompat.Builder(context, ALARM_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText("$contactName: $queryText")
                .setStyle(NotificationCompat.BigTextStyle().bigText("$body\n\nالعميل: $contactName\nالرسالة: $queryText"))
                .setVibrate(vibrationPattern)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .build()

            notificationManager.notify(1002, notification)
            Log.i(TAG, "HITL alarm notification posted for $contactName (score: $score)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to trigger HITL alarm notification", e)
        }
    }

    private fun ensureHitlSuffix(text: String): String {
        val suffix = "تمام يا فندم، ثواني وهيكون مع حضرتك ممثل خدمة العملاء فوراً لمساعدتك في أقرب وقت متاح! 🙏"
        return if (text.trimEnd().endsWith(suffix.trim())) text
               else "$text\n$suffix"
    }

    private fun normalizeField(value: String?): String? {
        return value?.trim()?.replace(Regex("\\s+"), " ")
    }

    private fun dotProduct(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size) return 0f
        var sum = 0f
        for (i in a.indices) sum += a[i] * b[i]
        return sum
    }

    private fun createAlarmNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Failed Query Alarm"
            val descriptionText = "Alerts when a customer query requires human manual takeover"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(ALARM_CHANNEL_ID, name, importance).apply {
                description = descriptionText
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500)
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun containsMediaIndicators(text: String): Boolean {
        val mediaEmojis = listOf("📷", "🎵", "🎤", "📹", "🎥", "📄", "📸", "🎬", "🎙️")
        if (mediaEmojis.any { text.contains(it) }) return true
        val lower = text.lowercase()
        val mediaKeywords = listOf(
            "photo", "video", "audio", "document", "voice note", "voice message",
            "صورة", "فيديو", "رسالة صوتية", "ملف", "مستند"
        )
        return mediaKeywords.any { lower.contains(it) }
    }

    /**
     * Lenient JSON parser for agentic LLM responses.
     * Ignores unknown keys to handle extra fields from non-compliant models.
     */
    private val agenticJson = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * Strips Markdown code-block wrappers that free-tier LLMs frequently inject
     * around their JSON output (e.g., ```json ... ``` or ``` ... ```).
     * Also trims leading/trailing whitespace and blank lines.
     */
    private fun sanitizeLlmJson(raw: String): String {
        var cleaned = raw.trim()
        // Remove opening ```json or ``` with optional language tag
        cleaned = cleaned.removePrefix("```json").removePrefix("```JSON").removePrefix("```")
        // Remove closing ```
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length - 3)
        }
        return cleaned.trim()
    }

    private fun getPriorMessages(current: WhatsAppNotification): List<String> {
        val timeLimitMs = CONTEXT_WINDOW_MINUTES * 60 * 1000L

        // Priority 1: Queue from debounce (actively accumulated in receivedNotifications)
        val list = receivedNotifications.toList()
        val currentIndex = list.indexOf(current)
        if (currentIndex > 0) {
            val priorFromQueue = list.subList(0, currentIndex)
                .filter { it.title == current.title && (current.postTime - it.postTime) in 0..timeLimitMs }
            if (priorFromQueue.isNotEmpty()) {
                return priorFromQueue.map { it.text }
            }
        } else {
            // Fallback: search queue by title & timestamp matching
            val priorFromQueue = list
                .filter { it.title == current.title && it.postTime < current.postTime && (current.postTime - it.postTime) in 0..timeLimitMs }
            if (priorFromQueue.isNotEmpty()) {
                return priorFromQueue.map { it.text }
            }
        }

        // Priority 2: Notification.EXTRA_MESSAGES
        if (current.extraMessages.isNotEmpty()) {
            val priorExtras = current.extraMessages.dropLast(1)
            val priorFromExtras = priorExtras
                .filter { !it.isUser && (current.postTime - it.timestamp) in 0..timeLimitMs }
            if (priorFromExtras.isNotEmpty()) {
                return priorFromExtras.map { it.text }
            }
        }

        // Priority 3: Fallback to empty list (current message only)
        return emptyList()
    }
}
