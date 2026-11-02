package com.example.persona.features.auto_reply.domain.orchestrator

import android.content.Context
import android.util.Log
import com.example.persona.BuildConfig
import com.example.persona.R
import com.example.persona.core.data.repository.UserPreferencesRepository
import com.example.persona.core.database.Converters
import com.example.persona.core.database.dao.BusinessKnowledgeDao
import com.example.persona.core.database.dao.ChatHistoryDao
import com.example.persona.core.database.dao.ContactDao
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
import com.example.persona.features.contacts.data.resolver.ThreadIdentityResolver
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
import com.example.persona.features.settings.domain.repository.ModelCapability
import com.example.persona.features.settings.domain.repository.ModelProviders
import com.example.persona.features.settings.domain.repository.SecureCredentialsRepository
import com.example.persona.features.chat_import.data.embedding.EmbeddingEngine
import com.example.persona.features.chat_import.data.normalizer.ArabicTextNormalizer
import com.example.persona.core.network.dto.AgenticLlmResponse
import com.example.persona.core.network.dto.ExtractedOrder
import com.example.persona.features.auto_reply.data.gatekeeper.CloudflareClefClient
import com.example.persona.features.auto_reply.data.gatekeeper.LiquidDecisionClient
import com.example.persona.features.auto_reply.data.providers.OpenRouterClient
import com.example.persona.features.auto_reply.data.repository.ReplyGenerationRepositoryImpl
import com.example.persona.features.auto_reply.domain.gatekeeper.DecisionResult
import com.example.persona.features.auto_reply.domain.gatekeeper.InboundIntent
import com.example.persona.features.auto_reply.domain.gatekeeper.LocalDecisionClassifier
import com.example.persona.core.network.dto.ChatMessage
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import com.example.persona.features.auto_reply.data.repository.PersistentDebounceBuffer
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import kotlinx.coroutines.flow.update
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
    private val contactDao: ContactDao? = null,
    @ApplicationContext private val context: Context,
    @ApplicationScope private val scope: CoroutineScope,
    private val mediaExtractor: WhatsAppMediaExtractor? = null,
    private val orderIngestionManager: OrderIngestionManager = OrderIngestionManager(orderDao, chatHistoryDao),
    private val hitlNotificationHandler: HitlNotificationHandler = HitlNotificationHandler(context, conversationMetadataDao, prefsRepository),
    private val antiEchoGuard: AntiEchoGuard = AntiEchoGuard(),
    private val reasoningLeakSanitizer: ReasoningLeakSanitizer = ReasoningLeakSanitizer(),
    private val cloudflareClefClient: CloudflareClefClient = CloudflareClefClient(),
    private val liquidDecisionClient: LiquidDecisionClient = LiquidDecisionClient(),
    private val localDecisionClassifier: LocalDecisionClassifier = LocalDecisionClassifier(),
    private val openRouterClient: OpenRouterClient = OpenRouterClient(HttpClient()),
    private val persistentDebounceBuffer: PersistentDebounceBuffer = PersistentDebounceBuffer(context),
    private val threadIdentityResolver: ThreadIdentityResolver? = null,
    private val secureCredentialsRepository: SecureCredentialsRepository? = null
) {
    companion object {
        private const val TAG = "AutoReplyOrchestrator"
        const val ALARM_CHANNEL_ID = HitlNotificationHandler.ALARM_CHANNEL_ID
        const val CANONICAL_HITL_MESSAGE = ReasoningLeakSanitizer.CANONICAL_HITL_MESSAGE
        const val DEBOUNCE_MICRO_EXTENSION_MS = 280L

        internal fun isAudioFilePath(path: String?): Boolean {
            if (path.isNullOrBlank()) return false
            val lower = path.lowercase()
            return lower.endsWith(".ogg") || lower.endsWith(".opus") ||
                   lower.endsWith(".mp3") || lower.endsWith(".m4a") ||
                   lower.endsWith(".wav") || lower.endsWith(".aac") ||
                   lower.endsWith(".amr")
        }

        private val _activeProcessingContacts = kotlinx.coroutines.flow.MutableStateFlow<Set<String>>(emptySet())
        val activeProcessingContacts: kotlinx.coroutines.flow.StateFlow<Set<String>> = _activeProcessingContacts

        fun setContactProcessing(contactName: String, isProcessing: Boolean) {
            val key = contactName.trim().lowercase()
            _activeProcessingContacts.update { current ->
                if (isProcessing) current + key else current - key
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

        private val SAFETY_KEYWORDS = setOf(
            "مستشفى", "الحقني", "مصيبة", "كارثة", "نجدة", "emergency", "suicide", "police"
        )

        internal fun isPivotStillOpen(
            latestPivotTimestamp: Long?,
            consumedPivotTimestamps: Set<Long>,
            nowMs: Long = System.currentTimeMillis()
        ): Boolean = OrderIngestionManager.isPivotStillOpen(latestPivotTimestamp, consumedPivotTimestamps, nowMs)

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
        const val MAX_CONTACT_MUTEXES = 100
    }

    // Per-contact mutexes and active jobs for non-blocking multi-contact parallelism
    private val contactMutexes = ConcurrentHashMap<String, Mutex>()
    private val activeContactJobs = ConcurrentHashMap<String, Job>()
    private val mutexGate = Any()
    // Dedicated mutex guarding on-device ONNX embedding inference to eliminate lock contention
    private val onnxMutex = Mutex()

    private suspend fun safeEmbedForAutoReply(text: String): FloatArray? = onnxMutex.withLock {
        if (!embeddingEngine.isEngineReady()) {
            Log.d(TAG, "On-device embedding engine not prepared yet. Skipping local embedding generation.")
            return@withLock null
        }
        embeddingEngine.embedForAutoReply(text)
    }

    private fun getOrCreateContactMutex(contactKey: String): Mutex {
        synchronized(mutexGate) {
            if (contactMutexes.size > MAX_CONTACT_MUTEXES) {
                val iterator = contactMutexes.entries.iterator()
                while (iterator.hasNext()) {
                    val entry = iterator.next()
                    if (!entry.value.isLocked && !activeContactJobs.containsKey(entry.key)) {
                        iterator.remove()
                    }
                }
            }
            return contactMutexes.computeIfAbsent(contactKey) { Mutex() }
        }
    }

    fun hasAnyActiveApiKeys(): Boolean {
        val providers = ModelProviders.ALL
        val secRepo = secureCredentialsRepository
        if (secRepo != null && providers.any { secRepo.hasConfiguredKeys(it) }) return true
        if (providers.any { prefsRepository.hasConfiguredKeys(it) }) return true
        if (providers.any { replyGenerationRepository.hasConfiguredKeys(it) }) return true
        return false
    }

    fun cancelInFlightProcessing(contactKey: String) {
        val contactName = contactKey.substringAfter('_', "")
        if (contactName.isNotBlank()) {
            setContactProcessing(contactName, false)
        }
        activeContactJobs.remove(contactKey)?.let { job ->
            if (job.isActive) {
                Log.i(TAG, "Cancelling in-flight processing job for contactKey: $contactKey")
                job.cancel()
            }
        }
    }

    /**
     * Cancels any in-flight AI turn, active job, or debounce buffer for [contactName].
     * Invoked when the merchant sends a manual reply or manually pauses AI.
     */
    fun cancelInFlightTurn(contactName: String) {
        val trimmed = contactName.trim().lowercase()
        setContactProcessing(contactName, false)

        // Cancel matching active processing jobs
        activeContactJobs.entries.filter { (key, _) ->
            val k = key.lowercase()
            k.endsWith("_$trimmed") || k == trimmed
        }.forEach { (key, job) ->
            if (job.isActive) {
                Log.i(TAG, "Cancelling in-flight turn job for $key")
                job.cancel()
            }
            activeContactJobs.remove(key)
        }

        // Cancel pending debounce turn
        debounceBuffer.entries.filter { (key, _) ->
            val k = key.lowercase()
            k.endsWith("_$trimmed") || k == trimmed
        }.forEach { (key, turn) ->
            synchronized(debounceLock) {
                turn.debounceJob?.cancel()
                debounceBuffer.remove(key)
            }
        }
    }

    internal fun dispatchContactProcessing(notification: WhatsAppNotification) {
        val contactName = notification.title.trim().ifBlank { "unknown" }
        val contactKey = "${notification.sourceClass.name}_${contactName.lowercase()}"
        cancelInFlightProcessing(contactKey)
        val contactMutex = getOrCreateContactMutex(contactKey)

        val job = scope.launch(Dispatchers.Default) {
            contactMutex.withLock {
                try {
                    processNotificationSequentially(notification)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    Log.i(TAG, "Processing cancelled for contactKey: $contactKey")
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing notification for contactKey: $contactKey", e)
                } finally {
                    activeContactJobs.remove(contactKey, coroutineContext[Job])
                    synchronized(mutexGate) {
                        if (contactMutexes.size > MAX_CONTACT_MUTEXES && !contactMutex.isLocked && !activeContactJobs.containsKey(contactKey)) {
                            contactMutexes.remove(contactKey, contactMutex)
                        }
                    }
                }
            }
        }
        activeContactJobs[contactKey] = job
    }

    // Queue for context window calculation with aggressive memory safety
    private val receivedNotifications = ConcurrentLinkedQueue<WhatsAppNotification>()

    // Cooldown map: contact name -> last reply timestamp (ms)
    private val cooldownMap = ConcurrentHashMap<String, Long>()

    // Session tracker for greeting suppression (last auto-reply timestamp per sender)
    private val lastReplyTimestamps = ConcurrentHashMap<String, Long>()

    internal data class BufferedTurn(
        val notifications: MutableList<WhatsAppNotification> = mutableListOf(),
        val insertedIds: MutableList<Long> = mutableListOf(),
        var debounceJob: kotlinx.coroutines.Job? = null,
        val firstArrivalMs: Long = System.currentTimeMillis(),
        var targetDeadlineMs: Long = 0L
    )

    private val debounceBuffer = ConcurrentHashMap<String, BufferedTurn>()
    private val debounceLock = Any()

    private var isCollecting = false

    init {
        hitlNotificationHandler.createAlarmNotificationChannel()
    }

    fun start() {
        synchronized(this) {
            if (isCollecting) {
                Log.d(TAG, "Orchestrator is already collecting notifications")
                return
            }
            isCollecting = true
        }

        // Recover pending debounce turns surviving process restart
        scope.launch(Dispatchers.Default) {
            val pendingTurns = persistentDebounceBuffer.recoverPendingTurns()
            if (pendingTurns.isNotEmpty()) {
                Log.i(TAG, "Recovered ${pendingTurns.size} pending debounce turns after restart. Dispatching...")
                for ((_, notification) in pendingTurns) {
                    dispatchContactProcessing(notification)
                }
            }
        }

        scope.launch {
            incomingMessageDispatcher.notifications.collect { notification ->
                enqueueInboundNotification(notification)
            }
        }
        Log.i(TAG, "AutoReplyOrchestrator started listening to IncomingMessageDispatcher")
    }

    internal suspend fun enqueueInboundNotification(notification: WhatsAppNotification) {
        receivedNotifications.add(notification)
        while (receivedNotifications.size > MAX_CONTEXT_QUEUE_SIZE) {
            receivedNotifications.poll()
        }
        val cutOff = System.currentTimeMillis() - 10 * 60 * 1000L
        receivedNotifications.removeIf { it.postTime < cutOff }

        // 1. Resolve contact identity & perform cascade thread merge across title/phone transitions
        val resolver = threadIdentityResolver ?: if (contactDao != null) {
            ThreadIdentityResolver(contactDao, conversationMetadataDao, chatHistoryDao, orderDao)
        } else null
        val resolvedIdentity = resolver?.resolveAndMerge(notification)
        val effectiveTitle = resolvedIdentity?.unifiedContactName ?: notification.title
        val effectiveNotification = notification.copy(
            title = effectiveTitle,
            resolvedPhoneNumber = resolvedIdentity?.resolvedPhoneNumber ?: notification.resolvedPhoneNumber
        )

        // 2. Immediate persistence for zero data loss & live UI responsiveness
        val processed = processor.process(effectiveNotification)
        val sourceClassStr = processed.sourceClass.name
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
        Log.d(TAG, "Chat history row inserted immediately for contact=${processed.contactName}, id=$insertedId")

        conversationMetadataDao.recordMessageTurn(
            contactName = processed.contactName,
            sourceClass = sourceClassStr,
            messageText = processed.originalText,
            senderType = "CUSTOMER",
            timestamp = processed.postTime,
            notificationKey = processed.notificationKey,
            incrementUnread = true
        )

        // 3. Gate check: allowed if not individually paused AND (Global is ON or explicitly overridden ON for this specific chat)
        val isGlobalAutoReplyEnabled = prefsRepository.isAutoReplyEnabledSync()
        val convMeta = conversationMetadataDao.getConversation(processed.contactName, sourceClassStr)
        val isContactPaused = convMeta?.isAiPaused == true
        val isContactOverride = convMeta?.isAiOverrideEnabled == true

        val isAutoReplyAllowedForContact = !isContactPaused && (isGlobalAutoReplyEnabled || isContactOverride)
        if (!isAutoReplyAllowedForContact) {
            PersonaLogger.d(
                PersonaLogTags.ORCHESTRATOR,
                "Auto-reply suppressed for ${processed.contactName} (Global=$isGlobalAutoReplyEnabled, Paused=$isContactPaused, Override=$isContactOverride)"
            )
            return
        }

        // 4. Sliding Debounce Window with Micro-Extension
        val stylePrefs = prefsRepository.getStylePreferencesSync()
        val delaySec = stylePrefs.replyDelaySeconds
        if (delaySec <= 0) {
            val singlePrePersisted = effectiveNotification.copy(
                isPrePersisted = true,
                burstMessageIds = listOf(insertedId)
            )
            dispatchContactProcessing(singlePrePersisted)
            return
        }

        val baseDelayMs = delaySec.coerceIn(1, 10) * 1000L
        val maxWaitMs = maxOf(baseDelayMs + 2500L, 8000L)
        val contactKey = "${processed.sourceClass.name}_${processed.contactName.trim().lowercase()}"
        val now = System.currentTimeMillis()

        synchronized(debounceLock) {
            val isNewTurn = !debounceBuffer.containsKey(contactKey)
            val turn = debounceBuffer.getOrPut(contactKey) {
                BufferedTurn(
                    firstArrivalMs = now,
                    targetDeadlineMs = now + baseDelayMs
                )
            }
            turn.notifications.add(effectiveNotification)
            turn.insertedIds.add(insertedId)

            if (!isNewTurn) {
                // Micro-extension per message (adds 280ms), strictly bounded by maxWaitMs
                val maxAllowedDeadline = turn.firstArrivalMs + maxWaitMs
                turn.targetDeadlineMs = minOf(turn.targetDeadlineMs + DEBOUNCE_MICRO_EXTENSION_MS, maxAllowedDeadline)
            }

            // Persist turn to disk for crash recovery
            persistentDebounceBuffer.persistTurn(contactKey, turn.notifications, turn.insertedIds, turn.targetDeadlineMs)

            if (turn.debounceJob == null || !turn.debounceJob!!.isActive) {
                turn.debounceJob = scope.launch {
                    while (true) {
                        val currentNow = System.currentTimeMillis()
                        val remaining = synchronized(debounceLock) { turn.targetDeadlineMs } - currentNow
                        if (remaining <= 0L) {
                            break
                        }
                        delay(remaining)
                    }
                    flushDebounceBuffer(contactKey)
                }
            }
        }
    }

    internal suspend fun flushDebounceBuffer(contactKey: String) {
        val turnToFlush = synchronized(debounceLock) {
            debounceBuffer.remove(contactKey)
        } ?: return

        persistentDebounceBuffer.removeTurn(contactKey)

        val batch = turnToFlush.notifications
        if (batch.isEmpty()) return

        val coalesced = coalesceNotifications(batch, turnToFlush.insertedIds)
        dispatchContactProcessing(coalesced)
    }

    internal fun coalesceNotifications(
        batch: List<WhatsAppNotification>,
        insertedIds: List<Long> = emptyList()
    ): WhatsAppNotification {
        if (batch.isEmpty()) {
            throw IllegalArgumentException("Cannot coalesce empty notification batch")
        }
        if (batch.size == 1) {
            return batch[0].copy(
                isPrePersisted = true,
                burstMessageIds = insertedIds.ifEmpty { emptyList() }
            )
        }

        val latest = batch.last()
        // Distinct non-empty lines preserving chronological order
        val combinedText = batch
            .map { it.text.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString("\n")

        // Retain media file if any message in burst carried media (prioritize image files over audio files)
        val imageMediaPath = batch.mapNotNull { it.mediaFilePath }.firstOrNull { it.isNotBlank() && !isAudioFilePath(it) }
        val mediaPath = imageMediaPath ?: batch.mapNotNull { it.mediaFilePath }.firstOrNull { it.isNotBlank() }

        // Audio failure only if all failed and no valid text exists
        val voiceFailed = batch.all { it.isVoiceTranscriptionFailed } && combinedText.isBlank()

        return latest.copy(
            text = combinedText,
            mediaFilePath = mediaPath,
            isVoiceTranscriptionFailed = voiceFailed,
            isPrePersisted = true,
            burstMessageIds = insertedIds
        )
    }

    internal suspend fun processNotificationSequentially(rawNotification: WhatsAppNotification) {
        // 1. Pre-process notification FIRST
        val processed = processor.process(rawNotification)
        val sourceClassStr = processed.sourceClass.name

        Log.i(PersonaLogTags.LLM, "📩 Incoming message from [${processed.contactName}] ($sourceClassStr): \"${processed.originalText}\"")

        // 2. Persist inbound message immediately to chat_history
        val insertedId = if (rawNotification.isPrePersisted) {
            rawNotification.burstMessageIds.lastOrNull() ?: runCatching {
                chatHistoryDao.getRecentMessagesExceptForSource(
                    contactName = processed.contactName,
                    sourceClass = sourceClassStr,
                    excludeId = -1L,
                    limit = 1
                ).firstOrNull()?.id ?: 0L
            }.getOrDefault(0L)
        } else {
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
            val id = chatHistoryDao.insert(chatHistoryEntity)
            Log.d(TAG, "Chat history row inserted for contact=${processed.contactName}, id=$id")

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
            id
        }

        // 4. Sequential single-embedding pipeline is executed at Step 11 to prevent ONNX lock contention.

        // 5. Execution gatekeeper: allowed if not individually paused AND (Global is ON or explicitly overridden ON for this specific chat)
        val isGlobalAutoReplyEnabled = prefsRepository.isAutoReplyEnabledSync()
        val convMeta = conversationMetadataDao.getConversation(processed.contactName, sourceClassStr)
        val isContactPaused = convMeta?.isAiPaused == true
        val isContactOverride = convMeta?.isAiOverrideEnabled == true

        val isAutoReplyAllowedForContact = !isContactPaused && (isGlobalAutoReplyEnabled || isContactOverride)
        if (!isAutoReplyAllowedForContact) {
            PersonaLogger.d(
                PersonaLogTags.ORCHESTRATOR,
                "Auto-reply suppressed for ${processed.contactName} (Global=$isGlobalAutoReplyEnabled, Paused=$isContactPaused, Override=$isContactOverride)"
            )
            Log.d(TAG, "Auto-reply suppressed for ${processed.contactName} (Global=$isGlobalAutoReplyEnabled, Paused=$isContactPaused, Override=$isContactOverride)")
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

        // 9.5. Active API Keys Guard: graceful degradation if no provider has configured keys
        if (!hasAnyActiveApiKeys()) {
            val warningMsg = "تعذر الرد التلقائي: لم يتم تسجيل مفتاح API نشط في الإعدادات."
            Log.w(TAG, warningMsg)
            PersonaLogger.w(PersonaLogTags.ORCHESTRATOR, warningMsg)

            val alertMessage = "[API Key Missing Alert] $warningMsg"
            auditLogRepository.queuePendingReply(processed, alertMessage)
            hitlNotificationHandler.handleHitlEscalation(processed, 1.0f)
            hitlNotificationHandler.triggerHitlAlarm(
                contactName = processed.contactName,
                queryText = warningMsg,
                score = 1.0f,
                sourceClass = sourceClassStr
            )
            sendHitlHoldingReply(processed)
            return
        }

        // 10. Multimodal Media Processing & Fallback handling
        // Fast path: Voice transcription already attempted and failed at listener stage
        if (processed.isVoiceTranscriptionFailed) {
            Log.i(TAG, "Voice transcription failed at listener stage for ${processed.contactName}. Immediately dispatching media fallback.")
            Log.i("PersonaMetrics", "metric=notification_drop_rate value=1 contact=${processed.contactName.hashCode()}")
            val fallbackMsg = runCatching { prefsRepository.getMediaFallbackMessageSync() }.getOrNull()?.takeIf { it.isNotBlank() }
                ?: context.getString(R.string.media_fallback_message)
            sendImmediateReply(processed, fallbackMsg)
            return
        }

        val hasTranscribedVoice = processed.originalText.contains("[رسالة صوتية من العميل]:")
        var currentMediaFilePath = if (hasTranscribedVoice && isAudioFilePath(processed.mediaFilePath)) null else processed.mediaFilePath
        var effectiveProcessed = if (hasTranscribedVoice && isAudioFilePath(processed.mediaFilePath)) {
            processed.copy(mediaFilePath = null)
        } else {
            processed
        }

        val rawText = processed.originalText
        val hasImage = mediaExtractor?.isImageIndicator(rawText) ?: com.example.persona.features.auto_reply.data.repository.NotificationProcessorImpl.isImageIndicator(rawText)
        val hasAudio = mediaExtractor?.isVoiceMessageIndicator(rawText) ?: com.example.persona.features.auto_reply.data.repository.NotificationProcessorImpl.isVoiceMessageIndicator(rawText)
        if (hasImage || hasAudio || containsMediaIndicators(rawText)) {
            Log.d("PersonaMedia", "📥 Inbound media turn detected: text='$rawText', hasImageIndicator=$hasImage, hasAudioIndicator=$hasAudio")
        }

        if (!hasTranscribedVoice && containsMediaIndicators(processed.originalText)) {
            if (mediaExtractor != null) {
                if (mediaExtractor.isVoiceMessageIndicator(processed.originalText)) {
                    val durationSeconds = mediaExtractor.extractDurationSeconds(processed.originalText)
                    val voiceFile = mediaExtractor.captureLatestVoiceNote(processed.sourceClass, processed.postTime, durationSeconds)
                    if (voiceFile != null) {
                        val audioModels = runCatching {
                            prefsRepository.getProviderModelConfigsSync(ModelProviders.QWEN)
                                .filter { ModelCapability.AUDIO in it.capabilities }
                                .map { it.modelId }
                        }.getOrNull()
                        val openRouterAudioModels = runCatching {
                            prefsRepository.getProviderModelConfigsSync(ModelProviders.OPENROUTER)
                                .filter { ModelCapability.AUDIO in it.capabilities }
                                .map { it.modelId }
                        }.getOrNull()
                        val audioPriority = runCatching {
                            prefsRepository.getProviderPrioritySync(ModelCapability.AUDIO)
                        }.getOrNull()
                        val transcribed = mediaExtractor.transcribeVoiceNote(voiceFile, audioModels, openRouterAudioModels, audioPriority)
                        if (!transcribed.isNullOrBlank()) {
                            val updatedText = "[رسالة صوتية من العميل]: $transcribed"
                            val retainMediaPath = if (isAudioFilePath(currentMediaFilePath)) null else currentMediaFilePath
                            currentMediaFilePath = retainMediaPath
                            effectiveProcessed = processed.copy(
                                originalText = updatedText,
                                normalizedText = normalizer.normalize(updatedText) ?: updatedText,
                                mediaFilePath = retainMediaPath
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

        val hasVoiceTranscribedNow = effectiveProcessed.originalText.contains("[رسالة صوتية من العميل]:")
        val isVoiceIndicator = mediaExtractor?.isVoiceMessageIndicator(effectiveProcessed.originalText)
            ?: com.example.persona.features.auto_reply.data.repository.NotificationProcessorImpl.isVoiceMessageIndicator(effectiveProcessed.originalText)

        // ONLY send media fallback if media indicator is present AND media extraction genuinely failed
        val shouldSendFallback = if (isVoiceIndicator && !hasVoiceTranscribedNow) {
            true
        } else {
            !hasVoiceTranscribedNow && currentMediaFilePath == null && containsMediaIndicators(effectiveProcessed.originalText)
        }

        if (shouldSendFallback) {
            Log.i(TAG, "Media indicator detected but extraction failed for ${effectiveProcessed.contactName}. Sending media fallback.")
            Log.i("PersonaMetrics", "metric=notification_drop_rate value=1 contact=${effectiveProcessed.contactName.hashCode()}")
            val fallbackMsg = runCatching { prefsRepository.getMediaFallbackMessageSync() }.getOrNull()?.takeIf { it.isNotBlank() }
                ?: context.getString(R.string.media_fallback_message)
            sendImmediateReply(effectiveProcessed, fallbackMsg)
            return
        }

        if (currentMediaFilePath != null && effectiveProcessed.mediaFilePath == null && !isAudioFilePath(currentMediaFilePath)) {
            effectiveProcessed = effectiveProcessed.copy(mediaFilePath = currentMediaFilePath)
        }

        setContactProcessing(effectiveProcessed.contactName, true)
        try {
            // 11. RAG Retrieval Engine (Single-Query Pass 1)
            val activeHashes = antiEchoGuard.getActiveReplyHashes(effectiveProcessed.contactName)
            val ragBaseThreshold = calculateRagBaseThreshold(stylePrefs.similarityThreshold)
            var candidates: List<RetrievalCandidate> = emptyList()
            var maxScore = 0f

            val queryTextForEmbedding = if (hasVoiceTranscribedNow) {
                effectiveProcessed.originalText.replace("[رسالة صوتية من العميل]:", "").trim().let {
                    normalizer.normalize(it) ?: it
                }
            } else {
                effectiveProcessed.normalizedText
            }

            val queryEmbedding = try {
                val startTime = System.currentTimeMillis()
                val emb = safeEmbedForAutoReply(queryTextForEmbedding)
                val latency = System.currentTimeMillis() - startTime
                Log.i("PersonaMetrics", "metric=embedding_latency_ms value=$latency contact=${processed.contactName.hashCode()}")
                emb
            } catch (e: Exception) {
                Log.w(TAG, "Embedding generation failed for chatHistory id=$insertedId", e)
                chatHistoryDao.updateEmbeddingState(insertedId, "FAILED")
                null
            }

            if (queryEmbedding != null) {
                val embeddingBlob = Converters().fromFloatArray(queryEmbedding)
                if (embeddingBlob != null) {
                    chatHistoryDao.updateEmbeddingRaw(insertedId, embeddingBlob)
                    Log.d(TAG, "Sequential embedding completed and saved for chatHistory id=$insertedId")
                } else {
                    chatHistoryDao.updateEmbeddingState(insertedId, "FAILED")
                }
            } else {
                chatHistoryDao.updateEmbeddingState(insertedId, "FAILED")
            }

            if (rawNotification.burstMessageIds.isNotEmpty()) {
                rawNotification.burstMessageIds.filter { it != insertedId }.forEach { oldId ->
                    chatHistoryDao.updateEmbeddingState(oldId, "SKIPPED")
                }
            }

            val isImagePresent = effectiveProcessed.mediaFilePath != null && !isAudioFilePath(effectiveProcessed.mediaFilePath)

            // =========================================================================
            // EXPERIMENTAL TEST SETUP: Evaluating On-Device Multilingual Classifier as Tier 1
            // Goal: Leverage ~1ms edge inference for instant intent triage before cloud calls.
            // Fallback: Cloudflare Clef (Tier 2) -> Liquid AI (Tier 3).
            // =========================================================================
            val tGatekeeper = System.currentTimeMillis()

            val (decision, resolvedTier) = if (isImagePresent) {
                DecisionResult(intent = InboundIntent.CATALOG_INQUIRY, confidence = 1.0f, latencyMs = 0L) to "FastPath: Image Present"
            } else {
                // Tier 1 (On-Device): Ultra-fast edge classification
                val localDecision = runCatching {
                    localDecisionClassifier.classify(queryTextForEmbedding, queryEmbedding)
                }.getOrElse { e ->
                    Log.e(TAG, "Gatekeeper Tier 1 (LocalDecisionClassifier) failed: ${e.message}", e)
                    DecisionResult(intent = InboundIntent.CATALOG_INQUIRY, confidence = 0.5f, latencyMs = 0L)
                }

                val isOnDeviceAccepted = localDecision.confidence >= 0.70f ||
                    localDecision.intent == InboundIntent.GREETING ||
                    localDecision.intent == InboundIntent.BANTER

                if (isOnDeviceAccepted) {
                    val latency = System.currentTimeMillis() - tGatekeeper
                    localDecision.copy(latencyMs = latency) to "Tier 1: LocalDecisionClassifier (On-Device Test)"
                } else {
                    Log.d(TAG, "Gatekeeper Tier 1 below confidence threshold (${localDecision.confidence} < 0.70) -> falling back to Cloudflare Clef (Tier 2)")
                    val lastAssistantText = runCatching {
                        chatHistoryDao.getRecentMessagesExceptForSource(
                            contactName = effectiveProcessed.contactName,
                            sourceClass = sourceClassStr,
                            excludeId = insertedId,
                            limit = 20
                        ).let { list ->
                            if (rawNotification.burstMessageIds.isNotEmpty()) {
                                list.filterNot { rawNotification.burstMessageIds.contains(it.id) }
                            } else {
                                list
                            }
                        }.firstOrNull { !it.isFromUser }?.messageText?.trim() ?: ""
                    }.getOrDefault("")

                    val gatekeeperInput = if (lastAssistantText.isNotBlank()) {
                        "[الرد السابق]: $lastAssistantText\n[رسالة العميل]: $queryTextForEmbedding"
                    } else {
                        queryTextForEmbedding
                    }

                    val openRouterDecisionModels = runCatching {
                        prefsRepository.getProviderModelConfigsSync(ModelProviders.OPENROUTER)
                            .filter { ModelCapability.DECISION in it.capabilities }
                            .map { it.modelId }
                    }.getOrElse { emptyList() }

                    val decisionPriority = runCatching {
                        prefsRepository.getProviderPrioritySync(ModelCapability.DECISION)
                    }.getOrNull()?.filter { it.isNotBlank() }?.ifEmpty { null }
                        ?: ModelProviders.DEFAULT_CAPABILITY_PROVIDERS[ModelCapability.DECISION]
                        ?: listOf(ModelProviders.CLOUDFLARE, ModelProviders.LIQUID, ModelProviders.OPENROUTER)

                    var resolvedOutcome: Pair<DecisionResult, String>? = null

                    for (provider in decisionPriority) {
                        when (provider.lowercase()) {
                            ModelProviders.CLOUDFLARE -> {
                                val tCloudflare = System.currentTimeMillis()
                                val cfTokens = secureCredentialsRepository?.getApiKeys(ModelProviders.CLOUDFLARE)?.filter { it.isNotBlank() } ?: emptyList()
                                if (cfTokens.isNotEmpty()) {
                                    cloudflareClefClient.setTokens(cfTokens)
                                }
                                val cfIntent = runCatching {
                                    cloudflareClefClient.decide(gatekeeperInput, timeoutMs = 1150L)
                                }.onFailure { e ->
                                    Log.w(TAG, "Gatekeeper (Cloudflare Clef) threw exception: ${e.message}")
                                }.getOrNull()

                                if (cfIntent != null) {
                                    val latency = System.currentTimeMillis() - tGatekeeper
                                    resolvedOutcome = DecisionResult(intent = cfIntent, confidence = 0.95f, latencyMs = latency) to "Cloudflare Clef"
                                    break
                                } else {
                                    val latencyStep = System.currentTimeMillis() - tCloudflare
                                    Log.w(TAG, "⚠️ Gatekeeper (Cloudflare Clef) failed or timed out (${latencyStep}ms). Falling back...")
                                    PersonaLogger.w(PersonaLogTags.ORCHESTRATOR, "⚠️ Gatekeeper (Cloudflare Clef) failed or timed out (${latencyStep}ms). Falling back...")
                                }
                            }
                            ModelProviders.LIQUID -> {
                                val tLiquid = System.currentTimeMillis()
                                val liquidKeys = secureCredentialsRepository?.getApiKeys(ModelProviders.LIQUID)?.filter { it.isNotBlank() } ?: emptyList()
                                if (liquidKeys.isNotEmpty()) {
                                    liquidDecisionClient.setKeys(liquidKeys)
                                }
                                val liquidIntent = runCatching {
                                    liquidDecisionClient.decide(gatekeeperInput, timeoutMs = 800L)
                                }.onFailure { e ->
                                    Log.w(TAG, "Gatekeeper (Liquid AI) threw exception: ${e.message}")
                                }.getOrNull()

                                if (liquidIntent != null) {
                                    val latency = System.currentTimeMillis() - tGatekeeper
                                    resolvedOutcome = DecisionResult(intent = liquidIntent, confidence = 0.95f, latencyMs = latency) to "Liquid AI"
                                    break
                                } else {
                                    val latencyStep = System.currentTimeMillis() - tLiquid
                                    Log.w(TAG, "⚠️ Gatekeeper (Liquid AI) failed or timed out (${latencyStep}ms). Falling back...")
                                    PersonaLogger.w(PersonaLogTags.ORCHESTRATOR, "⚠️ Gatekeeper (Liquid AI) failed or timed out (${latencyStep}ms). Falling back...")
                                }
                            }
                            ModelProviders.OPENROUTER -> {
                                if (openRouterDecisionModels.isNotEmpty()) {
                                    val tOr = System.currentTimeMillis()
                                    val orOutcome = try {
                                        decideWithOpenRouter(gatekeeperInput, openRouterDecisionModels, timeoutMs = 1500L)
                                    } catch (e: Exception) {
                                        Log.w(TAG, "Gatekeeper (OpenRouter) threw exception: ${e.message}")
                                        null
                                    }

                                    if (orOutcome != null) {
                                        val latency = System.currentTimeMillis() - tGatekeeper
                                        resolvedOutcome = DecisionResult(intent = orOutcome.first, confidence = orOutcome.second, latencyMs = latency) to "OpenRouter (${orOutcome.third})"
                                        break
                                    } else {
                                        val latencyStep = System.currentTimeMillis() - tOr
                                        Log.w(TAG, "⚠️ Gatekeeper (OpenRouter) failed or timed out (${latencyStep}ms). Falling back...")
                                        PersonaLogger.w(PersonaLogTags.ORCHESTRATOR, "⚠️ Gatekeeper (OpenRouter) failed or timed out (${latencyStep}ms). Falling back...")
                                    }
                                }
                            }
                        }
                    }

                    resolvedOutcome ?: run {
                        val latency = System.currentTimeMillis() - tGatekeeper
                        localDecision.copy(latencyMs = latency) to "Tier 1: LocalDecisionClassifier (On-Device Test Fallback)"
                    }
                }
            }

            PersonaLogger.d(
                PersonaLogTags.ORCHESTRATOR,
                "⚡ Gatekeeper resolved via $resolvedTier -> intent=${decision.intent} (conf=${decision.confidence}, latency=${decision.latencyMs}ms)"
            )

            val containsExplicitEntityOrUrl = hasExplicitEntityOrUrl(queryTextForEmbedding)
            var resolvedIntent = if (isImagePresent || containsExplicitEntityOrUrl) {
                InboundIntent.CATALOG_INQUIRY
            } else {
                decision.intent
            }
            Log.i(TAG, "🎯 Gatekeeper decision: intent=$resolvedIntent via $resolvedTier (conf=${decision.confidence}, latency=${decision.latencyMs}ms, forcedRag=$containsExplicitEntityOrUrl)")

            // Only retrieve RAG candidates if intent is CATALOG_INQUIRY or forced via entity/URL match
            if ((resolvedIntent == InboundIntent.CATALOG_INQUIRY || containsExplicitEntityOrUrl) && queryEmbedding != null) {
                val (retrievedCandidates, bestScore) = semanticRetrievalRepository.retrieveCandidates(
                    queryEmbedding = queryEmbedding,
                    minimumSimilarityThreshold = ragBaseThreshold,
                    queryText = queryTextForEmbedding,
                    recentReplyHashes = activeHashes
                )
                candidates = retrievedCandidates.filter { it.similarityScore >= 0.38f }
                maxScore = bestScore
                if (candidates.isNotEmpty()) {
                    resolvedIntent = InboundIntent.CATALOG_INQUIRY
                }
            } else {
                Log.d(TAG, "⚡ Bypassing RAG retrieval for intent $resolvedIntent (candidates=0)")
            }

            val inboundIntent = resolvedIntent

            val finalCandidates = candidates

            // Context Assembly
            val contextStart = System.currentTimeMillis()
            val rawChatHistory = chatHistoryDao.getRecentMessagesExceptForSource(
                contactName = effectiveProcessed.contactName,
                sourceClass = sourceClassStr,
                excludeId = insertedId,
                limit = 20
            )
            val chatHistory = if (rawNotification.burstMessageIds.isNotEmpty()) {
                rawChatHistory.filterNot { rawNotification.burstMessageIds.contains(it.id) }
            } else {
                rawChatHistory
            }
            val activeOrders = orderDao.getActiveOrdersByContact(effectiveProcessed.contactName)
            val latestOrder = runCatching { orderDao.getLatestOrderByContact(effectiveProcessed.contactName) }.getOrNull()
            val orderStatusCounts = runCatching { orderDao.getOrderStatusCounts(effectiveProcessed.contactName) }.getOrNull() ?: emptyList()
            val contextBuildMs = System.currentTimeMillis() - contextStart
            Log.d(TAG, "📊 Context assembly completed in ${contextBuildMs}ms: chatTurns=${chatHistory.size}, activeOrders=${activeOrders.size}, statusCounts=${orderStatusCounts.size}")
            Log.i("PersonaMetrics", "metric=context_build_ms value=$contextBuildMs contact=${effectiveProcessed.contactName.hashCode()}")

            // 12. Agentic LLM Generation Pass 1
            val lastSessionReplyTime = lastReplyTimestamps[effectiveProcessed.contactName] ?: 0L
            val isOngoing = (System.currentTimeMillis() - lastSessionReplyTime) < 300_000L

            val maxTokensForGeneration = if (isImagePresent) 500 else 600

            val rawLlmOutput = replyGenerationRepository.generateReply(
                notification = effectiveProcessed,
                candidates = if (isImagePresent) emptyList() else finalCandidates,
                isOngoingConversation = isOngoing,
                chatHistory = if (isImagePresent) emptyList() else chatHistory,
                activeOrders = if (isImagePresent) emptyList() else activeOrders,
                personalChunks = emptyList(),
                maxTokens = maxTokensForGeneration,
                lastOrder = if (isImagePresent) null else latestOrder,
                orderStatusCounts = if (isImagePresent) emptyList() else orderStatusCounts,
                inboundIntent = inboundIntent
            )
            if (rawLlmOutput.isNullOrBlank()) {
                Log.d(TAG, "LLM returned null/blank — all models failed. HITL fallback.")
                hitlNotificationHandler.handleHitlEscalation(processed, maxScore)
                Log.i("PersonaMetrics", "metric=hitl_rate value=1 contact=${processed.contactName.hashCode()}")
                sendHitlHoldingReply(processed)
                return
            }

            val hitlHoldingMessage = runCatching { prefsRepository.getHitlHoldingMessageSync() }.getOrNull()?.takeIf { it.isNotBlank() }
                ?: CANONICAL_HITL_MESSAGE

            // Parse structured JSON
            val agenticResponse = try {
                ReasoningLeakSanitizer.decodeAgenticResponseSafely(rawLlmOutput)
            } catch (e: Exception) {
                if (ReasoningLeakSanitizer.isLeakedReasoningText(rawLlmOutput)) {
                    Log.w(TAG, "Raw LLM output contains reasoning leakage — degrading safely to HITL: ${rawLlmOutput.take(200)}", e)
                } else {
                    Log.w(TAG, "LLM output is not valid JSON — degrading safely to HITL: ${rawLlmOutput.take(200)}", e)
                }
                ReasoningLeakSanitizer.buildUnsafeOutputHitlResponse(hitlHoldingMessage)
            }.let { response ->
                if (ReasoningLeakSanitizer.isLeakedReasoningText(response.finalReplyText)) {
                    PersonaLogger.w(PersonaLogTags.LLM, "Reasoning leakage in parsed final_reply_text — routing to HITL guard")
                    Log.w(TAG, "Reasoning leakage in parsed final_reply_text — overriding with HITL guard.")
                    ReasoningLeakSanitizer.buildUnsafeOutputHitlResponse(hitlHoldingMessage)
                } else {
                    response
                }
            }

            val rawAction = agenticResponse.action.trim().uppercase()
            val normalizedAction = when (rawAction) {
                "UPDATE", "CREATE" -> "REPLY"
                else -> rawAction
            }
            val effectiveAction = when {
                normalizedAction == "HITL" -> "HITL"
                isImagePresent -> "RE_QUERY"
                normalizedAction == "REPLY" && !agenticResponse.refinedSearchKeyword.isNullOrBlank() -> {
                    PersonaLogger.w(TAG, "Auto-promoting REPLY to RE_QUERY due to non-blank search keyword: ${agenticResponse.refinedSearchKeyword}")
                    Log.w(TAG, "Auto-promoting REPLY to RE_QUERY due to non-blank search keyword: ${agenticResponse.refinedSearchKeyword}")
                    "RE_QUERY"
                }
                normalizedAction == "RE_QUERY" && agenticResponse.refinedSearchKeyword.isNullOrBlank() && !agenticResponse.finalReplyText.isNullOrBlank() -> "REPLY"
                else -> normalizedAction
            }

            // Agentic Order Actions & Ingestion Routing
            var overriddenReplyText: String? = null
            val orderAction = OrderIngestionManager.normalizeOrderAction(agenticResponse.orderAction)

            Log.i("PersonaMetrics", "metric=order_extraction_rate value=${if (agenticResponse.extractedOrder != null) 1 else 0} contact=${processed.contactName.hashCode()}")
            Log.i("PersonaMetrics", "metric=order_action_usage value=${orderAction ?: if (agenticResponse.orderAction != null) "UNKNOWN" else "NONE"} contact=${processed.contactName.hashCode()}")

            // Defer order ingestion mutations on intermediate turns (RE_QUERY or image turns)
            if (effectiveAction == "REPLY" && !isImagePresent) {
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
                            hitlHoldingMessage
                        }
                        Log.i("PersonaMetrics", "metric=hitl_rate value=1 contact=${processed.contactName.hashCode()}")
                        sendImmediateReply(processed, ReasoningLeakSanitizer.ensureHitlSuffix(hitlReply, hitlHoldingMessage))
                        return
                    }

                    effectiveAction == "REPLY" -> {
                        Log.i(TAG, "LLM action=REPLY. Dispatching final reply.")
                        agenticResponse.finalReplyText
                    }

                    effectiveAction == "RE_QUERY" -> {
                        val refinedKeyword = agenticResponse.refinedSearchKeyword?.trim().orEmpty()
                        val visualDesc = agenticResponse.visualProductDescription?.trim().orEmpty()
                        Log.i(TAG, "LLM action=RE_QUERY. Refined keyword: '$refinedKeyword', Visual description: '$visualDesc'")
                        PersonaLogger.d(PersonaLogTags.LLM, "RE_QUERY loop triggered with keyword='$refinedKeyword', visualDesc='$visualDesc'")

                        val combinedSearchQuery = listOf(refinedKeyword, visualDesc)
                            .filter { it.isNotBlank() }
                            .joinToString(" ")

                        val retrievedPass2: List<RetrievalCandidate> = if (combinedSearchQuery.isNotBlank()) {
                            val pass2Embedding = safeEmbedForAutoReply(combinedSearchQuery)
                            if (pass2Embedding != null) {
                                val (retrieved, _) = semanticRetrievalRepository.retrieveCandidates(
                                    queryEmbedding = pass2Embedding,
                                    minimumSimilarityThreshold = ragBaseThreshold,
                                    queryText = combinedSearchQuery,
                                    recentReplyHashes = activeHashes
                                )
                                retrieved
                            } else {
                                emptyList()
                            }
                        } else {
                            emptyList()
                        }

                        val mergedChunks = mergeCandidates(
                            pass1 = candidates,
                            pass2 = retrievedPass2,
                            limit = 8,
                            minScore = 0.38f
                        )

                        Log.i(TAG, "RE_QUERY merged to ${mergedChunks.size} chunks (Pass1: ${candidates.size}, Pass2: ${retrievedPass2.size}). Executing final LLM pass.")

                        val pass2QueryText = if (visualDesc.isNotBlank()) {
                            "${effectiveProcessed.originalText}\n[وصف الصورة المرفقة من العميل]: $visualDesc"
                        } else {
                            effectiveProcessed.originalText
                        }

                        val pass2ImageUrl = if (visualDesc.isBlank() && isImagePresent) {
                            effectiveProcessed.mediaFilePath
                        } else {
                            null
                        }

                        val maxTokensPass2 = if (pass2ImageUrl != null) 1500 else 750
                        val finalOutput = replyGenerationRepository.generateFinalReply(
                            queryText = pass2QueryText,
                            chunks = mergedChunks,
                            isOngoingConversation = isOngoing,
                            chatHistory = chatHistory,
                            activeOrders = activeOrders,
                            personalChunks = emptyList(),
                            maxTokens = maxTokensPass2,
                            imageUrl = pass2ImageUrl,
                            lastOrder = latestOrder,
                            orderStatusCounts = orderStatusCounts
                        )

                        if (finalOutput.isNullOrBlank()) {
                            null
                        } else {
                            val finalParsed = try {
                                ReasoningLeakSanitizer.decodeAgenticResponseSafely(finalOutput)
                            } catch (e: Exception) {
                                Log.w(TAG, "LLM final output is not valid JSON — degrading safely to HITL: ${finalOutput.take(200)}", e)
                                ReasoningLeakSanitizer.buildUnsafeOutputHitlResponse(hitlHoldingMessage)
                            }.let { response ->
                                if (ReasoningLeakSanitizer.isLeakedReasoningText(response.finalReplyText)) {
                                    PersonaLogger.w(PersonaLogTags.LLM, "Reasoning leakage in final-pass final_reply_text — routing to HITL guard")
                                    Log.w(TAG, "Reasoning leakage in final-pass final_reply_text — overriding with HITL guard.")
                                    ReasoningLeakSanitizer.buildUnsafeOutputHitlResponse(hitlHoldingMessage)
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
                                    hitlHoldingMessage
                                }
                                Log.i("PersonaMetrics", "metric=hitl_rate value=1 contact=${processed.contactName.hashCode()}")
                                sendImmediateReply(processed, ReasoningLeakSanitizer.ensureHitlSuffix(hitlReply, hitlHoldingMessage))
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
                val template = runCatching { prefsRepository.getBlankReplyFallbackTemplateSync() }.getOrNull()
                    ?: UserPreferencesRepository.DEFAULT_BLANK_REPLY_FALLBACK_TEMPLATE
                String.format(template, storeName)
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
            if (!rawNotification.isPrePersisted && stylePrefs.replyDelaySeconds > 0) {
                delay(stylePrefs.replyDelaySeconds * 1000L)
            }

            // Pre-execution abort check: re-verify merchant hasn't suppressed auto-reply for this contact
            val preExecConv = conversationMetadataDao.getConversation(processed.contactName, processed.sourceClass.name)
            val isPreExecGlobalOn = prefsRepository.isAutoReplyEnabledSync()
            val isPreExecPaused = preExecConv?.isAiPaused == true
            val isPreExecOverride = preExecConv?.isAiOverrideEnabled == true
            val isPreExecAllowed = !isPreExecPaused && (isPreExecGlobalOn || isPreExecOverride)
            if (!isPreExecAllowed) {
                Log.i(TAG, "AI auto-reply turn aborted before execution: suppressed for ${processed.contactName} (Global=$isPreExecGlobalOn, Paused=$isPreExecPaused, Override=$isPreExecOverride)")
                return
            }

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
                    antiEchoGuard.addReplyToCache(styledReplyText, processed.contactName)

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
                                val embedding = safeEmbedForAutoReply(styledReplyText)
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
                    val baseTelemetry = replyGenerationRepository.getLastExecutionTelemetry()
                    val calculatedTokensIn = (effectiveProcessed.originalText.length * 2).coerceAtLeast(20)
                    val calculatedTokensOut = (styledReplyText.length * 2).coerceAtLeast(15)
                    val fullTelemetry = com.example.persona.features.auto_reply.domain.model.ExecutionTelemetry(
                        providerModel = baseTelemetry?.providerModel ?: "Unknown",
                        gatekeeperTier = resolvedTier,
                        latencyMs = baseTelemetry?.latencyMs ?: 0L,
                        promptTokens = calculatedTokensIn,
                        completionTokens = calculatedTokensOut,
                        decisionReason = "تم الرد تلقائياً بناءً على نية ($inboundIntent)"
                    )
                    auditLogRepository.logExecutedReply(processed, auditCandidate, styledReplyText, fullTelemetry)
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
        val preExecConv = conversationMetadataDao.getConversation(processed.contactName, processed.sourceClass.name)
        val isPreExecGlobalOn = prefsRepository.isAutoReplyEnabledSync()
        val isPreExecPaused = preExecConv?.isAiPaused == true
        val isPreExecOverride = preExecConv?.isAiOverrideEnabled == true
        val isPreExecAllowed = !isPreExecPaused && (isPreExecGlobalOn || isPreExecOverride)
        if (!isPreExecAllowed) {
            Log.i(TAG, "Immediate AI reply aborted before execution: suppressed for ${processed.contactName} (Global=$isPreExecGlobalOn, Paused=$isPreExecPaused, Override=$isPreExecOverride)")
            return
        }

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
                antiEchoGuard.addReplyToCache(text, processed.contactName)

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
                            val embedding = safeEmbedForAutoReply(text)
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
                val templateTelemetry = com.example.persona.features.auto_reply.domain.model.ExecutionTelemetry(
                    providerModel = "Rule Template",
                    gatekeeperTier = "RULE",
                    latencyMs = 50L,
                    promptTokens = (processed.originalText.length * 2).coerceAtLeast(20),
                    completionTokens = (text.length * 2).coerceAtLeast(15),
                    decisionReason = "تم الإرسال كقالب تدخّل / رد جاهز (Interception Template)"
                )
                auditLogRepository.logExecutedReply(processed, dummyCandidate, text, templateTelemetry)
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
        val hitlMsg = runCatching { prefsRepository.getHitlHoldingMessageSync() }.getOrNull()?.takeIf { it.isNotBlank() }
            ?: CANONICAL_HITL_MESSAGE
        sendImmediateReply(processed, hitlMsg)
    }

    internal fun containsMediaIndicators(text: String): Boolean =
        com.example.persona.features.auto_reply.data.repository.NotificationProcessorImpl.containsMediaIndicators(text)

    internal fun hasExplicitEntityOrUrl(text: String): Boolean {
        if (text.isBlank()) return false
        val lower = text.lowercase()
        if (lower.contains("http://") || lower.contains("https://") || lower.contains("www.") ||
            lower.contains(".com") || lower.contains(".net") || lower.contains(".org") ||
            lower.contains(".eg") || lower.contains(".shop") || lower.contains("@")
        ) {
            return true
        }
        val entityKeywords = listOf(
            "tiktok", "tik tok", "facebook", "fb", "instagram", "insta", "whatsapp",
            "تيك توك", "تيكتوك", "فيسبوك", "فيس بوك", "انستجرام", "انستقرام", "انستا", "واتساب",
            "موقع", "الموقع", "موقعكم", "ويب سايت", "ويبسايت", "لينك", "اللينك", "رابط", "الرابط",
            "صفحة", "صفحتكم", "حساب", "حسابكم", "أكونت", "اكونت", "رقم", "رقمكم", "ارقام", "أرقام",
            "خدمة العملاء", "خدمه العملاء"
        )
        return entityKeywords.any { lower.contains(it) }
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

    internal suspend fun decideWithOpenRouter(
        gatekeeperInput: String,
        models: List<String>,
        keys: List<String> = emptyList(),
        timeoutMs: Long = 1500L
    ): Triple<InboundIntent, Float, String>? = kotlinx.coroutines.withContext(Dispatchers.IO) {
        withTimeoutOrNull(timeoutMs) {
            val userKeys = if (keys.isNotEmpty()) {
                keys
            } else {
                (secureCredentialsRepository?.getApiKeys(ModelProviders.OPENROUTER)
                    ?: runCatching { prefsRepository.getApiKeys(ModelProviders.OPENROUTER) }.getOrNull())
                    ?.map { it.trim() }
                    ?.filter { it.isNotBlank() }
                    .orEmpty()
            }
            val validKeys = userKeys.ifEmpty {
                listOfNotNull(
                    runCatching { BuildConfig.OPENROUTER_API_KEY }.getOrNull()?.trim()?.takeIf { it.isNotBlank() },
                    runCatching { BuildConfig.OPENROUTER_API_KEY_V2 }.getOrNull()?.trim()?.takeIf { it.isNotBlank() }
                )
            }
            if (validKeys.isEmpty() || models.isEmpty()) {
                Log.w(TAG, "OpenRouter Gatekeeper: No valid keys or models configured")
                return@withTimeoutOrNull null
            }

            val systemPrompt = """
                You are an intent classification gatekeeper for an Egyptian WhatsApp customer service assistant.
                Classify the user message into exactly ONE of the following intents:
                - GREETING: Greetings like "السلام عليكم", "صباح الخير", "مرحبا", "ألو".
                - BANTER: Small talk, jokes, personal remarks, thank you / closing. (Never for questions about products, prices, store links, URLs, phone numbers).
                - ORDER_STATUS: Inquiring about previous or pending orders, delivery status, order cancellation or modification ("أوردر", "شحنة", "تعديل الطلب", "إلغاء").
                - CATALOG_INQUIRY: Inquiring about products, hoodies, t-shirts, prices, sizes, materials, shipping cost, store website/links, social media accounts, human agent requests, or containing URLs/handles.

                Respond ONLY with a JSON object:
                {"intent": "GREETING" | "BANTER" | "ORDER_STATUS" | "CATALOG_INQUIRY", "confidence": 0.95}
            """.trimIndent()

            val messages = listOf(
                ChatMessage(role = "system", content = systemPrompt),
                ChatMessage(role = "user", content = gatekeeperInput)
            )

            for (key in validKeys) {
                for (model in models) {
                    val outcome = openRouterClient.executeSingleCall(
                        model = model,
                        messages = messages,
                        maxTokens = 120,
                        apiKey = key
                    )
                    if (outcome is ReplyGenerationRepositoryImpl.CallResult.Success) {
                        val parsed = parseOpenRouterDecisionJson(outcome.content)
                        if (parsed != null) {
                            PersonaLogger.d(
                                PersonaLogTags.ORCHESTRATOR,
                                "⚡ OpenRouter Gatekeeper resolved ($model) -> intent=${parsed.first} (conf=${parsed.second})"
                            )
                            return@withTimeoutOrNull Triple(parsed.first, parsed.second, model)
                        }
                    }
                }
            }
            null
        }
    }

    internal fun parseOpenRouterDecisionJson(content: String): Pair<InboundIntent, Float>? {
        return runCatching {
            var raw = content.trim()
            if (raw.startsWith("```")) {
                raw = raw.substringAfter("\n")
                if (raw.endsWith("```")) {
                    raw = raw.substringBeforeLast("```").trim()
                }
            }
            val startIdx = raw.indexOf('{')
            val endIdx = raw.lastIndexOf('}')
            if (startIdx != -1 && endIdx > startIdx) {
                raw = raw.substring(startIdx, endIdx + 1)
            }
            val element = agenticJson.parseToJsonElement(raw).jsonObject
            val intentStr = element["intent"]?.jsonPrimitive?.content
                ?: element["category"]?.jsonPrimitive?.content
                ?: element["classification"]?.jsonPrimitive?.content
            val confidence = runCatching {
                element["confidence"]?.jsonPrimitive?.content?.toFloatOrNull()
            }.getOrNull() ?: 0.95f

            val mapped = when (intentStr?.trim()?.uppercase()) {
                "GREETING" -> InboundIntent.GREETING
                "BANTER", "CLOSING", "PRESENCE_CHECK" -> InboundIntent.BANTER
                "ORDER_STATUS", "ORDER_UPDATE", "ORDER_CANCEL" -> InboundIntent.ORDER_STATUS
                "CATALOG_INQUIRY", "STORE_INFO", "ORDER_CREATE", "GENUINE_COMPLAINT_HITL" -> InboundIntent.CATALOG_INQUIRY
                else -> null
            }
            if (mapped != null) mapped to confidence else null
        }.getOrNull()
    }
}

