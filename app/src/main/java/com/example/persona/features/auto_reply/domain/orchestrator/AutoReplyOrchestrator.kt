package com.example.persona.features.auto_reply.domain.orchestrator

import android.util.Log
import com.example.persona.core.database.dao.LinkedContactDao
import com.example.persona.core.data.repository.UserPreferencesRepository
import com.example.persona.core.di.ApplicationScope
import com.example.persona.features.auto_reply.data.service.IncomingMessageDispatcher
import com.example.persona.features.auto_reply.data.repository.NotificationProcessorImpl
import com.example.persona.features.auto_reply.data.repository.SemanticRetrievalRepository
import com.example.persona.features.auto_reply.domain.repository.ReplyGenerationRepository
import com.example.persona.features.auto_reply.domain.repository.AuditLogRepository
import com.example.persona.features.auto_reply.domain.usecase.DirectReplyExecutionUseCase
import com.example.persona.features.auto_reply.domain.usecase.ReplyExecutionResult
import com.example.persona.features.auto_reply.domain.model.WhatsAppNotification
import com.example.persona.features.chat_import.data.embedding.EmbeddingEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
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
    private val linkedContactDao: LinkedContactDao,
    private val prefsRepository: UserPreferencesRepository,
    @ApplicationScope private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "AutoReplyOrchestrator"
        private const val COOLDOWN_MS = 5 * 60 * 1000L // 5 minutes
        private val SAFETY_KEYWORDS = listOf("ضروري", "مشكلة", "مستشفى", "الحقني", "مصيبة", "urgent", "help")
    }

    // Channel for sequential processing of incoming messages to prevent concurrent ONNX executions (OOM prevention)
    private val channel = Channel<WhatsAppNotification>(Channel.UNLIMITED)

    // Cooldown map: contact name -> last reply timestamp
    private val cooldownMap = ConcurrentHashMap<String, Long>()

    private var isCollecting = false

    init {
        // Start consuming notifications from the channel sequentially
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

    /**
     * Start observing incoming message flow from the dispatcher.
     * Thread-safe and guarantees a single collection job.
     */
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
                channel.send(notification)
            }
        }
        Log.i(TAG, "AutoReplyOrchestrator started listening to IncomingMessageDispatcher")
    }

    /**
     * Sequential execution pipeline for a single notification.
     */
    private suspend fun processNotificationSequentially(rawNotification: WhatsAppNotification) {
        // 1. Strict Switch Check: Check if auto-reply is globally enabled
        if (!prefsRepository.isAutoReplyEnabledSync()) {
            Log.d(TAG, "Auto-reply is globally disabled. Skipping message from ${rawNotification.title}")
            return
        }

        // 2. Pre-process notification
        val processed = processor.process(rawNotification)

        // 3. Contact Validation: Ignore if not a linked active contact
        if (!linkedContactDao.isLinkedContact(processed.contactName)) {
            Log.d(TAG, "Ignoring message: '${processed.contactName}' is not a linked active contact")
            return
        }

        // 4. Cooldown Policy: Max 1 auto-reply per 5 minutes per contact to prevent spam loops
        val now = System.currentTimeMillis()
        val lastReplyTime = cooldownMap[processed.contactName] ?: 0L
        if (now - lastReplyTime < COOLDOWN_MS) {
            Log.d(TAG, "Ignoring message from ${processed.contactName}: Contact is currently in cooldown")
            return
        }

        // 5. Human Safety Filter: Simple rule-based filter before generation
        val hasUrgentKeyword = SAFETY_KEYWORDS.any { keyword ->
            processed.originalText.contains(keyword, ignoreCase = true) ||
            processed.normalizedText.contains(keyword, ignoreCase = true)
        }
        if (hasUrgentKeyword) {
            Log.w(TAG, "Safety filter triggered for message from ${processed.contactName}. Aborting auto-reply and routing to queue.")
            val alertMessage = "[Safety Alert] urgent keyword detected. Message: ${processed.originalText}"
            auditLogRepository.queuePendingReply(processed, alertMessage)
            return
        }

        // 6. Embed search query using priority-aware realtime inference
        val queryEmbedding = embeddingEngine.embedForAutoReply(processed.normalizedText)
        if (queryEmbedding == null) {
            Log.w(TAG, "Failing gracefully: Failed to generate embedding for auto-reply path")
            return
        }

        // 7. Semantic Vector Retrieval
        val candidates = semanticRetrievalRepository.retrieveCandidates(
            contactName = processed.contactName,
            queryEmbedding = queryEmbedding,
            queryContext = processed.context
        )
        if (candidates.isEmpty()) {
            Log.d(TAG, "No relevant candidate matches found above similarity threshold for message from ${processed.contactName}")
            return
        }

        // 8. Generate Reply (Strategy A - Exact Historical)
        val replyText = replyGenerationRepository.generateReply(processed, candidates)
        if (replyText.isNullOrBlank()) {
            Log.d(TAG, "Failed to generate reply text from retrieval candidates")
            return
        }

        // 9. Execute direct reply via system RemoteInput action
        val executionResult = directReplyExecutionUseCase.execute(processed.notificationKey, replyText)

        // 10. Audit Logging with rich metrics
        when (executionResult) {
            is ReplyExecutionResult.Success -> {
                // Update cooldown reset time upon successful sending
                cooldownMap[processed.contactName] = System.currentTimeMillis()
                auditLogRepository.logExecutedReply(processed, candidates.first(), replyText)
                Log.i(TAG, "Successfully auto-replied to ${processed.contactName} with: '$replyText'")
            }
            is ReplyExecutionResult.Failed -> {
                // Fall back to queueing if direct reply execution fails
                auditLogRepository.queuePendingReply(processed, replyText)
                Log.w(TAG, "Failed to execute auto-reply to ${processed.contactName}: ${executionResult.reason}. Routed to queue.")
            }
        }
    }
}
