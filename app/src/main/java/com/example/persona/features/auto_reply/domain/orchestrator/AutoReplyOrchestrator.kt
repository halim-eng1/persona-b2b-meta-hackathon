package com.example.persona.features.auto_reply.domain.orchestrator

import android.util.Log
import com.example.persona.core.database.dao.ChatMessageDao
import com.example.persona.core.database.dao.LinkedContactDao
import com.example.persona.core.data.repository.UserPreferencesRepository
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
import com.example.persona.features.chat_import.data.embedding.EmbeddingEngine
import com.example.persona.features.chat_import.data.normalizer.ArabicTextNormalizer
import com.example.persona.features.chat_import.domain.model.ConversationContext
import com.example.persona.features.chat_import.domain.model.IntentType
import com.example.persona.features.chat_import.domain.model.TopicType
import com.example.persona.features.chat_import.domain.model.SentimentType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
    private val linkedContactDao: LinkedContactDao,
    private val chatMessageDao: ChatMessageDao,
    private val prefsRepository: UserPreferencesRepository,
    private val stylePostProcessor: StylePostProcessor,
    private val normalizer: ArabicTextNormalizer,
    @ApplicationScope private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "AutoReplyOrchestrator"
        private val SAFETY_KEYWORDS = listOf(
            "ضروري", "مشكلة", "مستشفى", "الحقني", "مصيبة", "urgent", "help"
        )
        const val SHORT_MSG_FREQUENCY_WEIGHT = 1.0f
        const val SHORT_MSG_RECENCY_WEIGHT = 5.0f
        const val SHORT_MSG_MIN_MATCHES = 3
        const val SHORT_MSG_MAX_REPLY_LENGTH = 200
        const val SHORT_MSG_MAX_NEWLINES = 2
        const val CONTEXT_WINDOW_MINUTES = 5L
        const val SHORT_MSG_WORD_THRESHOLD = 2
        const val MAX_CONTEXT_QUEUE_SIZE = 50
    }

    // Channel for sequential processing — prevents concurrent ONNX executions (OOM prevention)
    private val channel = Channel<WhatsAppNotification>(Channel.UNLIMITED)

    // Queue for context window calculation with aggressive memory safety
    private val receivedNotifications = ConcurrentLinkedQueue<WhatsAppNotification>()

    // Cooldown map: contact name -> last reply timestamp (ms)
    private val cooldownMap = ConcurrentHashMap<String, Long>()

    private var isCollecting = false

    init {
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

        // 2. Load user style preferences (single DataStore read, sync)
        val stylePrefs = prefsRepository.getStylePreferencesSync()
        val cooldownMs = stylePrefs.cooldownMins * 60_000L

        // 3. Pre-process notification
        val processed = processor.process(rawNotification)

        // 4. Contact validation
        val isLinked = linkedContactDao.isLinkedContact(processed.contactName)
        PersonaLogger.d(PersonaLogTags.CONTACT, "Notification contact=${processed.contactName}")
        PersonaLogger.d(PersonaLogTags.CONTACT, "LinkedContact found=$isLinked")
        if (!isLinked) {
            PersonaLogger.d(PersonaLogTags.ORCHESTRATOR, "Notification rejected")
            Log.d(TAG, "Ignoring: '${processed.contactName}' is not a linked active contact")
            return
        }

        PersonaLogger.d(PersonaLogTags.ORCHESTRATOR, "Notification accepted")

        // 5. Cooldown policy (dynamic — reads from user prefs)
        val now = System.currentTimeMillis()
        val lastReplyTime = cooldownMap[processed.contactName] ?: 0L
        if (stylePrefs.cooldownMins != 0 && now - lastReplyTime < cooldownMs) {
            PersonaLogger.d(PersonaLogTags.ORCHESTRATOR, "Cooldown triggered")
            Log.d(TAG, "Ignoring '${processed.contactName}': in cooldown (${stylePrefs.cooldownMins} min)")
            return
        }

        // 6. Human safety filter
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
            return
        }

        // Word-count check for Short Message Mode
        val wordCount = processed.normalizedText.split("\\s+".toRegex()).filter { it.isNotEmpty() }.size
        var candidates: List<RetrievalCandidate> = emptyList()
        var usedShortMessageMode = false

        if (wordCount <= SHORT_MSG_WORD_THRESHOLD) {
            val normalizedTrigger = normalizer.normalize(processed.normalizedText) ?: processed.normalizedText
            val shortCandidate = semanticRetrievalRepository.retrieveShortMessageReply(
                contactName = processed.contactName,
                shortTrigger = normalizedTrigger
            )
            if (shortCandidate != null) {
                candidates = listOf(shortCandidate)
                usedShortMessageMode = true
            }
        }

        if (!usedShortMessageMode) {
            // Phase A: Context Window (Time-Guarded Context Concatenation)
            val priorMessages = getPriorMessages(rawNotification)
            val richContext = if (priorMessages.isNotEmpty()) {
                val normalizedPrior = priorMessages.map { normalizer.normalize(it) ?: it }
                normalizedPrior.joinToString("\n") + "\n" + processed.normalizedText
            } else {
                processed.normalizedText
            }

            // 7. Embed search query
            val queryEmbedding = embeddingEngine.embedForAutoReply(richContext)
            if (queryEmbedding == null) {
                Log.w(TAG, "Failed to generate embedding for ${processed.contactName}")
                return
            }

            // 8. Semantic retrieval — use dynamic similarity threshold from user prefs
            candidates = semanticRetrievalRepository.retrieveCandidates(
                contactName = processed.contactName,
                queryEmbedding = queryEmbedding,
                queryContext = processed.context,
                minimumSimilarityThreshold = stylePrefs.similarityThreshold
            )
        }

        if (candidates.isEmpty()) {
            Log.d(TAG, "No candidates found or above threshold for ${processed.contactName}")
            return
        }

        // 9. Generate reply
        val rawReplyText = replyGenerationRepository.generateReply(processed, candidates)
        if (rawReplyText.isNullOrBlank()) {
            Log.d(TAG, "Failed to generate reply text from candidates")
            return
        }

        // 10. Compute user emoji rate for style-aware post-processing
        val userEmojiRate = computeUserEmojiRate(processed.contactName)

        // 11. Apply style post-processing (pure function, right before execution)
        val styledReplyText = stylePostProcessor.process(
            rawReply = rawReplyText,
            prefs = stylePrefs,
            context = processed.context,
            userEmojiRate = userEmojiRate
        )

        // 12. Execute direct reply via RemoteInput
        delay(stylePrefs.replyDelaySeconds * 1000L)
        val executionResult = directReplyExecutionUseCase.execute(
            processed.notificationKey, styledReplyText
        )

        // 13. Audit logging
        when (executionResult) {
            is ReplyExecutionResult.Success -> {
                cooldownMap[processed.contactName] = System.currentTimeMillis()
                auditLogRepository.logExecutedReply(processed, candidates.first(), styledReplyText)
                PersonaLogger.d(PersonaLogTags.ORCHESTRATOR, "Reply executed")
                Log.i(TAG, "Auto-replied to ${processed.contactName}: '$styledReplyText'")
            }
            is ReplyExecutionResult.Failed -> {
                auditLogRepository.queuePendingReply(processed, styledReplyText)
                PersonaLogger.d(PersonaLogTags.ORCHESTRATOR, "Reply queued")
                Log.w(TAG, "Failed to send to ${processed.contactName}: ${executionResult.reason}. Queued.")
            }
        }
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

    /**
     * Derives the user's emoji usage rate from their stored chat messages for this contact.
     * Returns a value in [0.0, 1.0]. Lightweight — only scans message text, no embedding.
     */
    private suspend fun computeUserEmojiRate(contactName: String): Float {
        val emojiRegex = Regex(
            "[\uD83C\uDF00-\uD83D\uDDFF]|[\uD83D\uDE00-\uD83D\uDE4F]|[\uD83D\uDE80-\uD83D\uDEFF]" +
            "|[\uD83E\uDD00-\uD83E\uDDFF]|[\u2600-\u26FF]|[\u2700-\u27BF]"
        )
        return try {
            val userMessages = chatMessageDao.getUserMessagesForContact(contactName)
            if (userMessages.isEmpty()) return 0f
            val withEmoji = userMessages.count { emojiRegex.containsMatchIn(it.messageText) }
            withEmoji.toFloat() / userMessages.size
        } catch (e: Exception) {
            Log.w(TAG, "Could not compute emoji rate for $contactName", e)
            0f
        }
    }
}
