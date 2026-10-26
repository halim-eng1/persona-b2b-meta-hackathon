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
import com.example.persona.core.database.Converters
import com.example.persona.core.database.dao.BusinessKnowledgeDao
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
import com.example.persona.core.network.dto.AgenticLlmResponse
import com.example.persona.core.network.dto.ExtractedOrder
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
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
    @ApplicationContext private val context: Context,
    @ApplicationScope private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "AutoReplyOrchestrator"
        private const val ALARM_CHANNEL_ID = "hitl_alarm_channel"
        const val CANONICAL_HITL_MESSAGE =
            "تمام يا فندم، ثواني وهيكون مع حضرتك ممثل خدمة العملاء فوراً لمساعدتك في أقرب وقت متاح! 🙏"

        /**
         * Strips Markdown code-block wrappers that free-tier LLMs frequently inject
         * around their JSON output (e.g., ```json ... ``` or ``` ... ```).
         * Also extracts the substring between the first '{' and last '}' to protect
         * against leading/trailing conversational filler or tokens.
         */
        internal fun sanitizeLlmJson(raw: String): String {
            var cleaned = raw.trim()
            cleaned = cleaned.removePrefix("```json").removePrefix("```JSON").removePrefix("```")
            if (cleaned.endsWith("```")) {
                cleaned = cleaned.substring(0, cleaned.length - 3)
            }
            cleaned = cleaned.trim()
            val firstBrace = cleaned.indexOf('{')
            val lastBrace = cleaned.lastIndexOf('}')
            if (firstBrace != -1 && lastBrace != -1 && lastBrace > firstBrace) {
                cleaned = cleaned.substring(firstBrace, lastBrace + 1)
            }
            return cleaned.trim()
        }

        /**
         * Stable prefix of the dedup verification pivot message. Used for the
         * (widened) 6-turn suppression scan — must stay a substring-compatible
         * prefix of the user-facing pivot text so emoji variants still match.
         */
        const val PIVOT_MARKER = "أنا شايف إنك لسه عامل أوردر مشابه"

        /**
         * Deterministic replacement reply when the model emits a CANCEL for a
         * contact that has no active order: the bot must never let prose claim
         * an irreversible state change that did not happen (f3.log lesson).
         */
        const val NO_ACTIVE_ORDER_REPLY =
            "يا فندم، ما لقيناش أوردر نشط باسم حضرتك في النظام حالياً 😊 لو في أوردر حضرتك شايفه ينتظر إلغاء، ابعتلنا تفاصيله وهنراجع حالاً."

        private val SAFETY_KEYWORDS = listOf(
            "ضروري", "مشكلة", "مستشفى", "الحقني", "مصيبة", "urgent", "help"
        )

        /**
         * Pure merge-once decision for the dedup verification pivot: a pivot is
         * still open when a pivot message exists in the recent window AND that
         * exact pivot instance has not already consumed an extraction merge.
         * Consumption is tracked per pivot timestamp (a set, since a contact can
         * cycle through several pivots), which keeps interleaved Q&A merging
         * (fix.log) while a lingering pivot can no longer absorb every later
         * extraction into the old order.
         */
        internal fun isPivotStillOpen(latestPivotTimestamp: Long?, consumedPivotTimestamps: Set<Long>): Boolean =
            latestPivotTimestamp != null && !consumedPivotTimestamps.contains(latestPivotTimestamp)

        internal val agenticJson = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

        /**
         * State machine that detects unescaped inner double-quotes inside JSON string values
         * and escapes them to \" so lenient JSON parsers do not fail with syntax errors.
         * An inner quote is identified when inside a string value and the next non-whitespace
         * character is not a structural token (',', '}', ']', or ':').
         */
        internal fun repairUnescapedJsonQuotes(json: String): String {
            val sb = StringBuilder()
            var inString = false
            var i = 0
            while (i < json.length) {
                val c = json[i]
                if (c == '\\' && inString && i + 1 < json.length) {
                    sb.append(c)
                    sb.append(json[i + 1])
                    i += 2
                    continue
                }
                if (c == '"') {
                    if (!inString) {
                        inString = true
                        sb.append(c)
                    } else {
                        var j = i + 1
                        while (j < json.length && json[j].isWhitespace()) {
                            j++
                        }
                        val nextChar = if (j < json.length) json[j] else null
                        if (nextChar == ',' || nextChar == '}' || nextChar == ']' || nextChar == ':') {
                            inString = false
                            sb.append(c)
                        } else {
                            sb.append("\\\"")
                        }
                    }
                } else {
                    sb.append(c)
                }
                i++
            }
            return sb.toString()
        }

        /**
         * Robust JSON decoder for Agentic LLM responses. Strips Markdown fences,
         * slices from first '{' to last '}', decodes, and if decoding fails, applies
         * [repairUnescapedJsonQuotes] state machine before retrying.
         */
        internal fun decodeAgenticResponseSafely(raw: String): AgenticLlmResponse {
            val sanitized = sanitizeLlmJson(raw)
            return try {
                agenticJson.decodeFromString<AgenticLlmResponse>(sanitized)
            } catch (e: Exception) {
                val repaired = repairUnescapedJsonQuotes(sanitized)
                agenticJson.decodeFromString<AgenticLlmResponse>(repaired)
            }
        }

        /**
         * Helper to merge candidates across passes: places ALL Pass 2 (RE_QUERY) candidates
         * (>= minScore) FIRST in rank order, and appends non-duplicate Pass 1 candidates
         * afterward up to limit (default 8). This prevents noisy Pass 1 chunks from evicting
         * refined Pass 2 product catalog chunks.
         */
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

        /**
         * Computes the base RAG threshold mapped from user preferences [0.65f, 0.85f]
         * to [0.42f, 0.50f] with clamp: 0.65f -> 0.42f, 0.75f -> 0.46f, 0.85f -> 0.50f.
         */
        internal fun calculateRagBaseThreshold(userThreshold: Float): Float {
            val clamped = userThreshold.coerceIn(0.65f, 0.85f)
            return 0.42f + (clamped - 0.65f) * 0.4f
        }

        /**
         * Pure helper to evaluate if a candidate reply is a duplicate of a recent assistant
         * response sent within the current session window (last 2 hours).
         */
        internal fun isEchoRepetition(
            candidateReply: String,
            currentUserText: String,
            chatHistory: List<ChatHistoryEntity>,
            nowMs: Long = System.currentTimeMillis()
        ): Boolean {
            val lastUserTurn = chatHistory.firstOrNull { it.isFromUser }
            if (lastUserTurn != null && currentUserText.trim().equals(lastUserTurn.messageText.trim(), ignoreCase = true)) {
                return false
            }

            val twoHoursAgo = nowMs - TimeUnit.HOURS.toMillis(2)
            val lastAssistantTurn = chatHistory.firstOrNull { !it.isFromUser && it.timestamp >= twoHoursAgo }
            return lastAssistantTurn != null && candidateReply.trim().lowercase() == lastAssistantTurn.messageText.trim().lowercase()
        }

        internal fun filterDuplicateNoteLines(
            existingNotes: String?,
            newNotes: String,
            existingDeliveryAddress: String? = null,
            existingCustomerName: String? = null
        ): String {
            if (newNotes.isBlank()) return ""
            val existingLines = existingNotes
                ?.lines()
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                ?.toMutableSet()
                ?: mutableSetOf()

            existingDeliveryAddress?.trim()?.takeIf { it.isNotEmpty() }?.let {
                existingLines.add("العنوان: $it")
            }
            existingCustomerName?.trim()?.takeIf { it.isNotEmpty() }?.let {
                existingLines.add("الاسم: $it")
            }

            return newNotes.lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !existingLines.contains(it) }
                .joinToString("\n")
                .trim()
        }

        const val CONTEXT_WINDOW_MINUTES = 1L
        const val MAX_CONTEXT_QUEUE_SIZE = 50
    }

    sealed interface OrderIngestionResult {
        data class Created(val orderId: Long) : OrderIngestionResult
        data class Merged(val orderId: Long) : OrderIngestionResult
        data class DuplicatePivot(val pivotReplyText: String) : OrderIngestionResult
        data object Ignored : OrderIngestionResult
    }

    // Channel for sequential processing — prevents concurrent ONNX executions (OOM prevention)
    private val channel = Channel<WhatsAppNotification>(Channel.UNLIMITED)

    // Queue for context window calculation with aggressive memory safety
    private val receivedNotifications = ConcurrentLinkedQueue<WhatsAppNotification>()

    // Cooldown map: contact name -> last reply timestamp (ms)
    private val cooldownMap = ConcurrentHashMap<String, Long>()

    // Session tracker for greeting suppression (last auto-reply timestamp per sender)
    private val lastReplyTimestamps = ConcurrentHashMap<String, Long>()

    // Merge-once guard for the dedup verification pivot: contact -> set of pivot
    // message timestamps that have already been answered by an extraction merge.
    // Without this, a pivot text lingering in the 6-turn window would absorb
    // every later extraction as an "edit" of the old order even when the
    // customer explicitly asked for a NEW order (mis-attribution guard).
    private val consumedPivotTimestamps = ConcurrentHashMap<String, MutableSet<Long>>()

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

    internal suspend fun processNotificationSequentially(rawNotification: WhatsAppNotification) {
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
            sendHitlHoldingReply(processed)
            return
        }

        // 6. Media Fallback handling
        if (containsMediaIndicators(processed.originalText)) {
            Log.i(TAG, "Media indicator detected in message from ${processed.contactName}. Sending media fallback.")
            Log.i("PersonaMetrics", "metric=notification_drop_rate value=1 contact=${processed.contactName.hashCode()}")
            sendImmediateReply(processed, context.getString(R.string.media_fallback_message))
            return
        }

        // 7. Tier A Interception — intentional no-op:
        // Greetings and farewells (IntentType.GREETING / IntentType.CLOSING) are
        // NOT short-circuited to deterministic templates anymore. They flow
        // through the RAG retrieval engine (step 8) and the LLM generation pass
        // (step 10) so the bot engages conversationally with its dynamic AI
        // persona, honoring the customer's language and conversation history.

        // 8. RAG Retrieval Engine (Single-Query Pass 1)
        val activeHashes = getActiveReplyHashes()
        val ragBaseThreshold = calculateRagBaseThreshold(stylePrefs.similarityThreshold)
        var candidates: List<RetrievalCandidate> = emptyList()
        var maxScore = 0f

        val queryEmbedding = embeddingEngine.embedForAutoReply(processed.normalizedText)
        if (queryEmbedding != null) {
            val (retrievedCandidates, bestScore) = semanticRetrievalRepository.retrieveCandidates(
                queryEmbedding = queryEmbedding,
                minimumSimilarityThreshold = ragBaseThreshold,
                queryText = processed.normalizedText,
                recentReplyHashes = activeHashes
            )
            candidates = retrievedCandidates.filter { it.similarityScore >= 0.38f }
            maxScore = bestScore
        }

        // 8.1. Context Assembly (Sources A, B)
        val contextStart = System.currentTimeMillis()
        
        // Source A: Chronological turns
        val chatHistory = chatHistoryDao.getRecentMessagesExcept(
            contactName = processed.contactName,
            excludeId = insertedId,
            limit = 6
        )
        
        // Source B: Active orders
        val activeOrders = orderDao.getActiveOrdersByContact(processed.contactName)
        
        val contextBuildMs = System.currentTimeMillis() - contextStart
        Log.d(TAG, "📊 Context assembly completed in ${contextBuildMs}ms: chatTurns=${chatHistory.size}, activeOrders=${activeOrders.size}")
        Log.i("PersonaMetrics", "metric=context_build_ms value=$contextBuildMs contact=${processed.contactName.hashCode()}")

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
            personalChunks = emptyList(),
            maxTokens = 600
        )
        if (rawLlmOutput.isNullOrBlank()) {
            Log.d(TAG, "LLM returned null/blank — all models failed. HITL fallback.")
            triggerHitlAlarm(processed.contactName, processed.originalText, maxScore)
            Log.i("PersonaMetrics", "metric=hitl_rate value=1 contact=${processed.contactName.hashCode()}")
            sendHitlHoldingReply(processed)
            return
        }

        // 10b. Parse structured JSON (with code-block stripping & unescaped quote repair)
        val agenticResponse = try {
            decodeAgenticResponseSafely(rawLlmOutput)
        } catch (e: Exception) {
            // Malformed JSON / plain-text raw output must never reach the
            // customer (broken JSON snippets like {"action":"REPLY"... are
            // unacceptable UX). All decode failures degrade safely to HITL.
            if (isLeakedReasoningText(rawLlmOutput)) {
                Log.w(TAG, "Raw LLM output contains reasoning leakage — degrading safely to HITL: ${rawLlmOutput.take(200)}", e)
            } else {
                Log.w(TAG, "LLM output is not valid JSON — degrading safely to HITL (no raw-text fallback): ${rawLlmOutput.take(200)}", e)
            }
            buildUnsafeOutputHitlResponse()
        }.let { response ->
            // Guard against well-formed JSON whose final_reply_text itself
            // leaks English chain-of-thought (e.g. free-tier reasoning models).
            if (isLeakedReasoningText(response.finalReplyText)) {
                PersonaLogger.w(PersonaLogTags.LLM, "Reasoning leakage in parsed final_reply_text — routing to HITL guard")
                Log.w(TAG, "Reasoning leakage in parsed final_reply_text — overriding with HITL guard.")
                buildUnsafeOutputHitlResponse()
            } else {
                response
            }
        }

        // 5.1–5.3 Agentic Order Actions & Ingestion Routing.
        // `order_action` turns spoken intent into explicit DB state transitions
        // (CANCEL / UPDATE merge) so replies never claim changes the DB disagrees
        // with; extraction without an action keeps the legacy dedup/pivot path.
        var overriddenReplyText: String? = null

        // Known actions only: any other string the model emits must stay inert
        // instead of silently meaning something.
        val orderAction = agenticResponse.orderAction?.trim()?.uppercase()
            ?.takeIf { it == "CREATE" || it == "CANCEL" || it == "UPDATE" }

        Log.i("PersonaMetrics", "metric=order_extraction_rate value=${if (agenticResponse.extractedOrder != null) 1 else 0} contact=${processed.contactName.hashCode()}")
        Log.i("PersonaMetrics", "metric=order_action_usage value=${orderAction ?: if (agenticResponse.orderAction != null) "UNKNOWN" else "NONE"} contact=${processed.contactName.hashCode()}")

        if (agenticResponse.extractedOrder != null) {
            val extracted = agenticResponse.extractedOrder

            // Deduplication / order-state lookup
            val existingOrder = orderDao.getActiveMatchingOrder(processed.contactName)

            when {
                // Agentic CANCEL — the customer is stopping the order: transition
                // the persisted state and never resurrect the extraction as a new row.
                orderAction == "CANCEL" -> {
                    if (!executeCancelOrderAction(processed)) {
                        // No active order existed: the model may still have claimed
                        // a cancellation — override the prose with the real state.
                        overriddenReplyText = NO_ACTIVE_ORDER_REPLY
                    }
                }

                // Agentic UPDATE — merge the customer's new details into the
                // active pending order and advance it for the merchant dashboard.
                orderAction == "UPDATE" && existingOrder != null -> {
                    applyOrderUpdates(existingOrder, extracted)
                    if (existingOrder.status == OrderStatus.PENDING.name) {
                        orderDao.updateStatus(existingOrder.id, OrderStatus.IN_PREPARATION.name)
                        Log.i(TAG, "📈 Order id=${existingOrder.id} advanced PENDING → IN_PREPARATION after customer confirm/update")
                    }
                }

                // CREATE (explicit or implied) — and the UPDATE fallback when no
                // active order exists to merge into (degrade to insert so details
                // are never dropped). This is also the legacy dedup/pivot path
                // that keeps pre-order_action LLM outputs working.
                else -> {
                    val result = handleOrderCreation(
                        extractedOrder = agenticResponse.extractedOrder,
                        contactName = processed.contactName
                    )
                    if (result is OrderIngestionResult.DuplicatePivot) {
                        overriddenReplyText = result.pivotReplyText
                    }
                }
            }
        } else {
            // Agentic actions can arrive without an extraction payload (the
            // customer only confirms or cancels, with no new details).
            when (orderAction) {
                "CANCEL" -> {
                    if (!executeCancelOrderAction(processed)) {
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
            "UPDATE", "CREATE" -> "REPLY" // Treat order updates/creations with valid reply text as normal replies
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
                // Case 0: Handover to human representative (LLM requested HITL)
                effectiveAction == "HITL" -> {
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
                        CANONICAL_HITL_MESSAGE
                    }
                    Log.i("PersonaMetrics", "metric=hitl_rate value=1 contact=${processed.contactName.hashCode()}")
                    sendImmediateReply(processed, ensureHitlSuffix(hitlReply))
                    return
                }

                // Case 1: Valid REPLY action
                effectiveAction == "REPLY" && !agenticResponse.finalReplyText.isNullOrBlank() -> {
                    Log.i(TAG, "LLM action=REPLY. Dispatching final reply.")
                    agenticResponse.finalReplyText
                }

                // Case 2: RE_QUERY — execute secondary retrieval & second LLM pass
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

                    // 10e. Second LLM pass — REPLY-only mode (no further RE_QUERY allowed)
                    // ALWAYS invoke generateFinalReply even if mergedChunks is empty
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
                            decodeAgenticResponseSafely(finalOutput)
                        } catch (e: Exception) {
                            Log.w(TAG, "LLM final output is not valid JSON — degrading safely to HITL: ${finalOutput.take(200)}", e)
                            buildUnsafeOutputHitlResponse()
                        }.let { response ->
                            if (isLeakedReasoningText(response.finalReplyText)) {
                                PersonaLogger.w(PersonaLogTags.LLM, "Reasoning leakage in final-pass final_reply_text — routing to HITL guard")
                                Log.w(TAG, "Reasoning leakage in final-pass final_reply_text — overriding with HITL guard.")
                                buildUnsafeOutputHitlResponse()
                            } else {
                                response
                            }
                        }

                        val finalReplyOverride = handleFinalPassOrderActions(finalParsed, processed)

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
                                CANONICAL_HITL_MESSAGE
                            }
                            Log.i("PersonaMetrics", "metric=hitl_rate value=1 contact=${processed.contactName.hashCode()}")
                            sendImmediateReply(processed, ensureHitlSuffix(hitlReply))
                            return
                        }

                        finalReplyOverride
                            ?: finalParsed.finalReplyText
                            ?: buildUnsafeOutputHitlResponse().finalReplyText
                    }
                }

                // Case 3: removed — with the decode-failure HITL guard above,
                // raw LLM text is never dispatched as a reply. Any parsed but
                // unroutable response falls through to `null`, which triggers
                // the standard HITL fallback below.

                else -> null
            }
        }

        if (rawReplyText.isNullOrBlank()) {
            Log.d(TAG, "Agentic loop produced no reply — HITL fallback.")
            triggerHitlAlarm(processed.contactName, processed.originalText, maxScore)
            Log.i("PersonaMetrics", "metric=hitl_rate value=1 contact=${processed.contactName.hashCode()}")
            sendHitlHoldingReply(processed)
            return
        }

        // 11. Style post-processing (emoji rate is 0f since it's professional B2B)
        val styledReplyText = stylePostProcessor.process(
            rawReply = rawReplyText,
            prefs = stylePrefs,
            context = processed.context,
            userEmojiRate = 0f
        )

        // Anti-echo guard: if reply text is identical to a recent preceding assistant message for the same contact
        // within the current session window (last 2 hours), do NOT send a verbatim duplicate — hand off cleanly to HITL.
        if (isEchoRepetition(styledReplyText, processed.originalText, chatHistory)) {
            Log.w(TAG, "Anti-echo guard triggered: LLM produced verbatim duplicate of recent assistant reply for ${processed.contactName}. Degrading to HITL.")
            triggerHitlAlarm(processed.contactName, processed.originalText, maxScore)
            sendHitlHoldingReply(processed)
            return
        }

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
        val trimmed = text.trim()
        if (trimmed.isEmpty() || trimmed == CANONICAL_HITL_MESSAGE) {
            return CANONICAL_HITL_MESSAGE
        }
        if (trimmed.contains(CANONICAL_HITL_MESSAGE) || trimmed.contains("ثواني وهيكون مع حضرتك")) {
            return trimmed
        }
        return "$trimmed\n$CANONICAL_HITL_MESSAGE"
    }

    private suspend fun sendHitlHoldingReply(processed: ProcessedNotification) {
        sendImmediateReply(processed, CANONICAL_HITL_MESSAGE)
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
     * English chain-of-thought markers indicating reasoning-token leakage in
     * LLM output. Checked both on raw output (when JSON decoding fails) and
     * on parsed `final_reply_text` from well-formed JSON, as a last line of
     * defense before anything would reach the customer on WhatsApp.
     */
    private val REASONING_LEAK_MARKERS = listOf(
        "the user is asking",
        "looking at the knowledge",
        "as an ai",
        "context provided",
        "thinking process",
        "i need to"
    )

    /**
     * Detects English chain-of-thought / reasoning leakage in any LLM text
     * (raw output or a parsed `final_reply_text`). Case-insensitive.
     */
    private fun isLeakedReasoningText(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        val lowered = text.lowercase()
        return REASONING_LEAK_MARKERS.any { lowered.contains(it) }
    }

    /**
     * Deterministic safe degradation for unparseable / reasoning-leaking LLM
     * output: an apologetic HITL hand-off. Uses the exact canonical suffix
     * checked by [ensureHitlSuffix] so the phrase is never appended twice.
     */
    private fun buildUnsafeOutputHitlResponse(): AgenticLlmResponse = AgenticLlmResponse(
        action = "HITL",
        finalReplyText = CANONICAL_HITL_MESSAGE
    )

    /**
     * Executes the agentic CANCEL order action: deterministically transitions
     * the contact's **newest unfulfilled order** ([OrderStatus.PENDING] or
     * [OrderStatus.IN_PREPARATION], via [OrderDao.getActiveMatchingOrder]) to
     * CANCELLED so the bot's spoken claim ("تم الإلغاء") always matches the
     * persisted state.
     *
     * Scope is deliberately one order, not the whole active set: the prompt only
     * ever shows the model a single active order, while a contact may also have
     * an [OrderStatus.SHIPPING_IN_PROGRESS] delivery in flight — cancelling that
     * too would silently revert a state the customer never mentioned and no
     * merchant ever confirmed.
     *
     * Timing note: like the pre-existing insert/merge ingestion, the mutation
     * happens before reply dispatch; status moves are dashboard-reversible.
     *
     * @return true when a DB write occurred; false when no active order existed,
     * in which case the caller must override the LLM prose with the real state
     * (see [NO_ACTIVE_ORDER_REPLY]) — a verbal-only fake cancellation is exactly
     * the f3.log regression class this guard closes at the code level.
     */
    private suspend fun executeCancelOrderAction(processed: ProcessedNotification): Boolean {
        val activeOrder = orderDao.getActiveMatchingOrder(processed.contactName)
        if (activeOrder == null) {
            Log.i(TAG, "order_action=CANCEL but no active order for ${processed.contactName} — no state mutation")
            return false
        }

        orderDao.updateStatus(activeOrder.id, OrderStatus.CANCELLED.name)
        Log.i(TAG, "🚫 Agentic CANCEL: order id=${activeOrder.id} (${activeOrder.status} → ${OrderStatus.CANCELLED.name}) for ${processed.contactName}")
        return true
    }

    /**
     * Ingests a new order or triggers the duplicate-order verification pivot.
     */
    internal suspend fun handleOrderCreation(
        extractedOrder: ExtractedOrderDto?,
        contactName: String
    ): OrderIngestionResult {
        if (extractedOrder == null) return OrderIngestionResult.Ignored

        val normalizedCustomerName = normalizeField(extractedOrder.customerName)
        val normalizedDeliveryAddress = normalizeField(extractedOrder.deliveryAddress)
        val normalizedOrderDetails = normalizeField(extractedOrder.orderDetails)
        val normalizedCustomNotes = normalizeField(extractedOrder.customNotes)

        val existingOrder = orderDao.getActiveMatchingOrder(contactName)
        return if (existingOrder != null) {
            val recentChatTurns = chatHistoryDao.getRecentMessages(contactName, limit = 6)
            val latestPivotMsg = recentChatTurns.firstOrNull { !it.isFromUser && it.messageText.contains(PIVOT_MARKER) }

            val pivotSeen = isPivotStillOpen(
                latestPivotTimestamp = latestPivotMsg?.timestamp,
                consumedPivotTimestamps = consumedPivotTimestamps[contactName] ?: emptySet()
            )

            if (pivotSeen && latestPivotMsg != null) {
                applyOrderUpdates(existingOrder, extractedOrder)
                consumedPivotTimestamps
                    .computeIfAbsent(contactName) { ConcurrentHashMap.newKeySet() }
                    .add(latestPivotMsg.timestamp)
                OrderIngestionResult.Merged(existingOrder.id)
            } else {
                Log.i(TAG, "🔄 Duplicate order detected for $contactName. Sending verification pivot.")
                val pivotText = "أنا شايف إنك لسه عامل أوردر مشابه من شوية 😊 تحب نعدل على الأوردر القديم ونضيف عليه التفاصيل الجديدة دي، ولا ده أوردر جديد خالص يا فندم؟"
                OrderIngestionResult.DuplicatePivot(pivotText)
            }
        } else {
            val newOrder = OrderEntity(
                contactName = contactName,
                customerNameFromLlm = normalizedCustomerName,
                deliveryAddress = normalizedDeliveryAddress,
                orderDetails = normalizedOrderDetails,
                customNotes = normalizedCustomNotes,
                status = OrderStatus.PENDING.name
            )
            val insertedOrderId = orderDao.insert(newOrder)
            Log.i(TAG, "📦 New order ingested for $contactName: orderId=$insertedOrderId")
            OrderIngestionResult.Created(insertedOrderId)
        }
    }

    private fun buildNotesFromExtractedOrder(extracted: ExtractedOrder?): String {
        if (extracted == null) return ""
        val normalizedCustomerName = normalizeField(extracted.customerName)?.removePrefix("الاسم:")?.trim()
        val normalizedDeliveryAddress = normalizeField(extracted.deliveryAddress)?.removePrefix("العنوان:")?.trim()
        val normalizedOrderDetails = normalizeField(extracted.orderDetails)?.removePrefix("التفاصيل:")?.trim()
        val normalizedCustomNotes = normalizeField(extracted.customNotes)?.removePrefix("ملاحظات:")?.trim()

        return buildString {
            normalizedCustomerName?.takeIf { it.isNotEmpty() }?.let { append("الاسم: $it\n") }
            normalizedDeliveryAddress?.takeIf { it.isNotEmpty() }?.let { append("العنوان: $it\n") }
            normalizedOrderDetails?.takeIf { it.isNotEmpty() }?.let { append("التفاصيل: $it\n") }
            normalizedCustomNotes?.takeIf { it.isNotEmpty() }?.let { append("ملاحظات: $it\n") }
        }.trim()
    }

    internal suspend fun applyOrderUpdates(existingOrder: OrderEntity, extracted: ExtractedOrder?) {
        if (extracted == null) return

        val normalizedCustomerName = normalizeField(extracted.customerName)?.removePrefix("الاسم:")?.trim()
        if (!normalizedCustomerName.isNullOrBlank() && normalizedCustomerName != existingOrder.customerNameFromLlm) {
            orderDao.updateCustomerName(existingOrder.id, normalizedCustomerName)
            Log.i(TAG, "Updated customer name for order ${existingOrder.id}: $normalizedCustomerName")
        }

        val normalizedDeliveryAddress = normalizeField(extracted.deliveryAddress)?.removePrefix("العنوان:")?.trim()
        if (!normalizedDeliveryAddress.isNullOrBlank() && normalizedDeliveryAddress != existingOrder.deliveryAddress) {
            orderDao.updateDeliveryAddress(existingOrder.id, normalizedDeliveryAddress)
            Log.i(TAG, "Updated delivery address for order ${existingOrder.id}: $normalizedDeliveryAddress")
        }

        val rawNewNotes = buildNotesFromExtractedOrder(extracted)
        val deduplicatedNotes = filterDuplicateNoteLines(
            existingNotes = existingOrder.customNotes,
            newNotes = rawNewNotes,
            existingDeliveryAddress = normalizedDeliveryAddress ?: existingOrder.deliveryAddress,
            existingCustomerName = normalizedCustomerName ?: existingOrder.customerNameFromLlm
        )

        if (deduplicatedNotes.isNotEmpty()) {
            orderDao.appendCustomNotes(existingOrder.id, deduplicatedNotes)
            Log.i(TAG, "🔁 Deduplicated notes appended to order id=${existingOrder.id}: '$deduplicatedNotes'")
        }
    }

    internal suspend fun handleFinalPassOrderActions(
        finalParsed: AgenticLlmResponse,
        processed: ProcessedNotification
    ): String? {
        var finalReplyOverride: String? = null
        val finalOrderAction = finalParsed.orderAction?.trim()?.uppercase()
            ?.takeIf { it == "CREATE" || it == "CANCEL" || it == "UPDATE" }

        Log.i("PersonaMetrics", "metric=final_pass_order_action value=${finalOrderAction ?: "NONE"} contact=${processed.contactName.hashCode()}")

        if (finalParsed.extractedOrder != null) {
            val extracted = finalParsed.extractedOrder
            val existingOrder = orderDao.getActiveMatchingOrder(processed.contactName)

            when {
                finalOrderAction == "CANCEL" -> {
                    if (!executeCancelOrderAction(processed)) {
                        finalReplyOverride = NO_ACTIVE_ORDER_REPLY
                    }
                }
                finalOrderAction == "UPDATE" && existingOrder != null -> {
                    applyOrderUpdates(existingOrder, extracted)
                    if (existingOrder.status == OrderStatus.PENDING.name) {
                        orderDao.updateStatus(existingOrder.id, OrderStatus.IN_PREPARATION.name)
                        Log.i(TAG, "📈 Order id=${existingOrder.id} advanced PENDING → IN_PREPARATION (final-pass UPDATE)")
                    }
                }
                else -> {
                    val result = handleOrderCreation(
                        extractedOrder = finalParsed.extractedOrder,
                        contactName = processed.contactName
                    )
                    if (result is OrderIngestionResult.DuplicatePivot) {
                        finalReplyOverride = result.pivotReplyText
                    }
                }
            }
        } else {
            when (finalOrderAction) {
                "CANCEL" -> {
                    if (!executeCancelOrderAction(processed)) {
                        finalReplyOverride = NO_ACTIVE_ORDER_REPLY
                    }
                }
                "UPDATE" -> {
                    val existingOrder = orderDao.getActiveMatchingOrder(processed.contactName)
                    if (existingOrder == null) {
                        Log.i(TAG, "order_action=UPDATE (final pass) but no active order for ${processed.contactName} — nothing to update")
                    } else if (existingOrder.status == OrderStatus.PENDING.name) {
                        orderDao.updateStatus(existingOrder.id, OrderStatus.IN_PREPARATION.name)
                        Log.i(TAG, "📈 Order id=${existingOrder.id} advanced PENDING → IN_PREPARATION (final-pass UPDATE)")
                    }
                }
                "CREATE" -> {
                    val result = handleOrderCreation(
                        extractedOrder = null,
                        contactName = processed.contactName
                    )
                    if (result is OrderIngestionResult.DuplicatePivot) {
                        finalReplyOverride = result.pivotReplyText
                    }
                }
                else -> Unit
            }
        }
        return finalReplyOverride
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
