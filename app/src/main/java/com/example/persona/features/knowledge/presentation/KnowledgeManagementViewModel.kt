package com.example.persona.features.knowledge.presentation

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.persona.core.data.repository.UserPreferencesRepository
import com.example.persona.core.database.dao.FailedQueryDao
import com.example.persona.core.database.entity.ChatHistoryEntity
import com.example.persona.core.database.entity.FailedQueryEntity
import com.example.persona.core.network.dto.AgenticLlmResponse
import com.example.persona.core.network.dto.ExtractedOrder
import com.example.persona.features.auto_reply.data.diagnostics.NotificationSourceClass
import com.example.persona.features.auto_reply.data.gatekeeper.CloudflareClefClient
import com.example.persona.features.auto_reply.data.gatekeeper.LiquidDecisionClient
import com.example.persona.features.auto_reply.data.repository.PromptContextBuilder
import com.example.persona.features.auto_reply.data.repository.SemanticRetrievalRepository
import com.example.persona.features.auto_reply.data.service.WhatsAppMediaExtractor
import com.example.persona.features.auto_reply.domain.gatekeeper.DecisionResult
import com.example.persona.features.auto_reply.domain.gatekeeper.InboundIntent
import com.example.persona.features.auto_reply.domain.gatekeeper.LocalDecisionClassifier
import com.example.persona.features.auto_reply.domain.model.PersonaTone
import com.example.persona.features.auto_reply.domain.model.ProcessedNotification
import com.example.persona.features.auto_reply.domain.model.RetrievalCandidate
import com.example.persona.features.auto_reply.domain.orchestrator.AutoReplyOrchestrator
import com.example.persona.features.auto_reply.domain.orchestrator.ReasoningLeakSanitizer
import com.example.persona.features.auto_reply.domain.repository.ReplyGenerationRepository
import com.example.persona.features.chat_import.data.embedding.EmbeddingEngine
import com.example.persona.features.chat_import.domain.model.ConversationContext
import com.example.persona.features.chat_import.domain.model.IntentType
import com.example.persona.features.chat_import.domain.model.SentimentType
import com.example.persona.features.chat_import.domain.model.TopicType
import com.example.persona.features.knowledge.data.repository.KnowledgeRepository
import com.example.persona.features.knowledge.domain.model.KnowledgeEntry
import com.example.persona.features.knowledge.domain.usecase.AddManualKnowledgeUseCase
import com.example.persona.features.knowledge.domain.usecase.DeleteKnowledgeEntryUseCase
import com.example.persona.features.knowledge.domain.usecase.UpdateKnowledgeEntryUseCase
import com.example.persona.features.knowledge.presentation.model.ExecutionTrace
import com.example.persona.features.knowledge.presentation.model.SimulatorMediaType
import com.example.persona.features.knowledge.presentation.model.SimulatorMessage
import com.example.persona.features.knowledge.presentation.model.SimulatorSender
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class KnowledgeManagementUiState(
    val entries: List<KnowledgeEntry> = emptyList(),
    val unansweredQueries: List<FailedQueryEntity> = emptyList(),
    val inputQuery: String = "",
    val isTemporaryOffer: Boolean = false,
    val isProcessing: Boolean = false,
    val errorMessage: String? = null,
    // Search & Category Filter
    val searchQuery: String = "",
    val selectedFilter: String = "ALL", // "ALL", "PRODUCT", "POLICY", "OFFER"
    // Dialog states
    val editingEntry: KnowledgeEntry? = null,
    val answeringQuery: FailedQueryEntity? = null,
    // Bot Simulator state
    val isSimulatorOpen: Boolean = false,
    val isSimulating: Boolean = false,
    val simulatedResponse: String? = null,
    val simulationResults: List<RetrievalCandidate> = emptyList(),
    val simulationBestScore: Float = 0f,
    val simulationError: String? = null,
    val simulatorMessages: List<SimulatorMessage> = emptyList(),
    val selectedMediaUri: String? = null,
    val selectedMediaType: SimulatorMediaType? = null
) {
    val filteredEntries: List<KnowledgeEntry>
        get() {
            var list = entries
            if (searchQuery.isNotBlank()) {
                val q = searchQuery.trim().lowercase()
                list = list.filter {
                    it.chunkText.lowercase().contains(q) ||
                        (it.headerContext?.lowercase()?.contains(q) == true)
                }
            }
            return when (selectedFilter) {
                "PRODUCT" -> list.filter {
                    it.chunkText.contains("[منتج]") ||
                        it.headerContext?.contains("منتج") == true ||
                        it.chunkText.contains("ج.م") ||
                        it.chunkText.contains("جنيه")
                }
                "POLICY" -> list.filter {
                    it.headerContext?.contains("شحن") == true ||
                        it.headerContext?.contains("سياس") == true ||
                        it.headerContext?.contains("استرجاع") == true ||
                        it.sourceType == "CORE_DOCUMENT"
                }
                "OFFER" -> list.filter { it.sourceType == "TEMPORARY_OFFER" }
                else -> list
            }
        }
}

@HiltViewModel
class KnowledgeManagementViewModel @Inject constructor(
    private val repository: KnowledgeRepository,
    private val failedQueryDao: FailedQueryDao,
    private val addManualKnowledgeUseCase: AddManualKnowledgeUseCase,
    private val deleteKnowledgeEntryUseCase: DeleteKnowledgeEntryUseCase,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val updateKnowledgeEntryUseCase: UpdateKnowledgeEntryUseCase? = null,
    private val semanticRetrievalRepository: SemanticRetrievalRepository? = null,
    private val embeddingEngine: EmbeddingEngine? = null,
    private val replyGenerationRepository: ReplyGenerationRepository? = null,
    private val promptContextBuilder: PromptContextBuilder? = null,
    private val reasoningLeakSanitizer: ReasoningLeakSanitizer? = null,
    private val cloudflareClefClient: CloudflareClefClient? = null,
    private val liquidDecisionClient: LiquidDecisionClient? = null,
    private val localDecisionClassifier: LocalDecisionClassifier? = null,
    private val mediaExtractor: WhatsAppMediaExtractor? = null,
    @ApplicationContext private val context: Context? = null
) : ViewModel() {

    private val replyRepo = replyGenerationRepository
    private val localClassifier = localDecisionClassifier ?: LocalDecisionClassifier()
    private val extractor = mediaExtractor

    private val _uiState = MutableStateFlow(KnowledgeManagementUiState())
    val uiState: StateFlow<KnowledgeManagementUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            launch {
                repository.observeAllChunks()
                    .map { entities ->
                        entities.map { entity ->
                            KnowledgeEntry(
                                chunkId = entity.chunkId,
                                headerContext = entity.headerContext,
                                chunkText = entity.chunkText,
                                sourceType = entity.sourceType,
                                timestamp = entity.timestamp
                            )
                        }
                    }
                    .collect { knowledgeEntries ->
                        _uiState.update { it.copy(entries = knowledgeEntries) }
                    }
            }
            launch {
                failedQueryDao.observeUnreviewedQueries().collect { queries ->
                    _uiState.update { it.copy(unansweredQueries = queries) }
                }
            }
        }
    }

    fun onInputQueryChanged(text: String) {
        _uiState.update { it.copy(inputQuery = text, errorMessage = null) }
    }

    fun onTemporaryOfferToggled(isTemporary: Boolean) {
        _uiState.update { it.copy(isTemporaryOffer = isTemporary) }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun onFilterSelected(filter: String) {
        _uiState.update { it.copy(selectedFilter = filter) }
    }

    fun addManualEntry() {
        val query = _uiState.value.inputQuery
        val isTemp = _uiState.value.isTemporaryOffer
        if (query.isBlank()) return

        _uiState.update { it.copy(isProcessing = true, errorMessage = null) }

        viewModelScope.launch {
            val success = addManualKnowledgeUseCase(query, isTemp)
            if (success) {
                _uiState.update {
                    it.copy(
                        inputQuery = "",
                        isTemporaryOffer = false,
                        isProcessing = false
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        errorMessage = "تعذر توليد التضمين الذكي أو حفظ البيان."
                    )
                }
            }
        }
    }

    fun deleteEntry(chunkId: String) {
        viewModelScope.launch {
            deleteKnowledgeEntryUseCase(chunkId)
        }
    }

    fun onEditEntry(entry: KnowledgeEntry) {
        _uiState.update { it.copy(editingEntry = entry) }
    }

    fun onDismissEditDialog() {
        _uiState.update { it.copy(editingEntry = null) }
    }

    fun onSaveEditedEntry(chunkId: String, newText: String) {
        if (newText.isBlank()) return
        val currentEntry = _uiState.value.editingEntry
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, errorMessage = null) }
            val success = if (updateKnowledgeEntryUseCase != null) {
                updateKnowledgeEntryUseCase.invoke(
                    chunkId = chunkId,
                    newText = newText,
                    sourceType = currentEntry?.sourceType ?: "MANUAL_ENTRY",
                    headerContext = currentEntry?.headerContext
                )
            } else {
                repository.updateKnowledge(
                    chunkId = chunkId,
                    newText = newText,
                    sourceType = currentEntry?.sourceType ?: "MANUAL_ENTRY",
                    headerContext = currentEntry?.headerContext
                )
            }
            if (success) {
                _uiState.update { it.copy(isProcessing = false, editingEntry = null) }
            } else {
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        errorMessage = "فشل في حفظ التعديل وتحديث المتجه الشعاعي."
                    )
                }
            }
        }
    }

    fun markQueryReviewed(id: Long) {
        viewModelScope.launch {
            failedQueryDao.markReviewed(id)
        }
    }

    fun useQueryAsInput(queryText: String) {
        _uiState.update { it.copy(inputQuery = queryText, errorMessage = null) }
    }

    fun onStartAnsweringQuery(query: FailedQueryEntity) {
        _uiState.update { it.copy(answeringQuery = query) }
    }

    fun onDismissAnswerDialog() {
        _uiState.update { it.copy(answeringQuery = null) }
    }

    fun saveAnswerForFailedQuery(
        queryId: Long,
        queryText: String,
        answerText: String,
        isTemporaryOffer: Boolean = false
    ) {
        if (answerText.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, errorMessage = null) }
            val formattedKnowledge = "سؤال: ${queryText.trim()}\nإجابة: ${answerText.trim()}"
            val success = addManualKnowledgeUseCase(formattedKnowledge, isTemporaryOffer)
            if (success) {
                failedQueryDao.markReviewed(queryId)
                _uiState.update { it.copy(isProcessing = false, answeringQuery = null) }
            } else {
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        errorMessage = "تعذر توليد التضمين الذكي أو حفظ إجابة الاستفسار."
                    )
                }
            }
        }
    }

    fun deleteQuery(id: Long) {
        viewModelScope.launch {
            failedQueryDao.deleteById(id)
        }
    }

    // ── Bot Simulator / Sandbox ──────────────────────────────────────────────
    fun onOpenSimulator() {
        _uiState.update {
            it.copy(
                isSimulatorOpen = true,
                simulationError = null
            )
        }
    }

    fun onCloseSimulator() {
        _uiState.update { it.copy(isSimulatorOpen = false) }
    }

    fun onClearSimulatorHistory() {
        _uiState.update {
            it.copy(
                simulatorMessages = emptyList(),
                simulatedResponse = null,
                simulationResults = emptyList(),
                simulationBestScore = 0f,
                simulationError = null,
                selectedMediaUri = null,
                selectedMediaType = null
            )
        }
    }

    fun onSimulatorMediaSelected(uri: String?, type: SimulatorMediaType?) {
        _uiState.update {
            it.copy(
                selectedMediaUri = uri,
                selectedMediaType = type
            )
        }
    }

    fun onClearSelectedMedia() {
        _uiState.update {
            it.copy(
                selectedMediaUri = null,
                selectedMediaType = null
            )
        }
    }

    fun runSimulation(
        query: String,
        mediaUri: String? = null,
        mediaType: SimulatorMediaType? = null
    ) {
        val effectiveMediaUri = mediaUri ?: _uiState.value.selectedMediaUri
        val effectiveMediaType = mediaType ?: _uiState.value.selectedMediaType
        val trimmedQuery = query.trim()
        val sanitizedQuery = if (trimmedQuery.length > 3000) trimmedQuery.take(3000) else trimmedQuery

        if (sanitizedQuery.isBlank() && effectiveMediaUri == null) return

        val userMessage = SimulatorMessage(
            sender = SimulatorSender.USER,
            text = sanitizedQuery,
            mediaUri = effectiveMediaUri,
            mediaType = effectiveMediaType,
            timestamp = System.currentTimeMillis()
        )

        _uiState.update {
            it.copy(
                isSimulating = true,
                simulationError = null,
                selectedMediaUri = null,
                selectedMediaType = null,
                simulatorMessages = it.simulatorMessages + userMessage
            )
        }

        viewModelScope.launch {
            try {
                executeSimulationTurn(
                    query = sanitizedQuery,
                    mediaUri = effectiveMediaUri,
                    mediaType = effectiveMediaType
                )
            } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isSimulating = false,
                        simulationError = "خطأ أثناء المحاكاة: ${e.message}"
                    )
                }
            }
        }
    }

    private suspend fun executeSimulationTurn(
        query: String,
        mediaUri: String?,
        mediaType: SimulatorMediaType?
    ) {
        val startTime = System.currentTimeMillis()
        var effectiveText = query.trim()
        var asrTranscription: String? = null
        var visionDescription: String? = null

        // 1. Audio ASR transcription step
        val ext = extractor
        if (mediaType == SimulatorMediaType.AUDIO && !mediaUri.isNullOrBlank()) {
            val audioFile = resolveFileFromUri(mediaUri, SimulatorMediaType.AUDIO)
            if (audioFile != null && ext != null) {
                val transcribed = runCatching { ext.transcribeVoiceNote(audioFile) }.getOrNull()
                if (!transcribed.isNullOrBlank()) {
                    asrTranscription = transcribed
                    effectiveText = if (effectiveText.isNotBlank()) {
                        "$effectiveText\n[رسالة صوتية من العميل]: $transcribed"
                    } else {
                        "[رسالة صوتية من العميل]: $transcribed"
                    }
                } else {
                    asrTranscription = "(فشل تحويل الرسالة الصوتية أو لا يتوفر مفتاح API)"
                }
            } else {
                asrTranscription = "(وضع محاكاة الصوتيات / الملف المحلي)"
            }
        }

        // 2. Image vision step
        val isImagePresent = mediaType == SimulatorMediaType.IMAGE && !mediaUri.isNullOrBlank()
        var imageFilePath: String? = null
        if (isImagePresent && mediaUri != null) {
            val imgFile = resolveFileFromUri(mediaUri, SimulatorMediaType.IMAGE)
            imageFilePath = imgFile?.absolutePath ?: mediaUri
        }

        // 3. Gatekeeper Cascade (Tier 1 -> Tier 2 -> Tier 3)
        val tGatekeeper = System.currentTimeMillis()
        val (decision, resolvedTier) = if (isImagePresent) {
            DecisionResult(intent = InboundIntent.CATALOG_INQUIRY, confidence = 1.0f, latencyMs = 0L) to "FastPath: Image Present"
        } else {
            val lastAssistantText = _uiState.value.simulatorMessages.lastOrNull { it.sender == SimulatorSender.BOT }?.text?.trim().orEmpty()
            val gatekeeperInput = if (lastAssistantText.isNotBlank()) {
                "[الرد السابق]: $lastAssistantText\n[رسالة العميل]: $effectiveText"
            } else {
                effectiveText
            }

            val cfIntent = cloudflareClefClient?.let { client ->
                runCatching {
                    client.decide(gatekeeperInput, timeoutMs = 1150L)
                }.getOrNull()
            }

            if (cfIntent != null) {
                val latency = System.currentTimeMillis() - tGatekeeper
                DecisionResult(intent = cfIntent, confidence = 0.95f, latencyMs = latency) to "Tier 1: Cloudflare Clef"
            } else {
                val liquidIntent = liquidDecisionClient?.let { client ->
                    runCatching {
                        client.decide(gatekeeperInput, timeoutMs = 800L)
                    }.getOrNull()
                }

                if (liquidIntent != null) {
                    val latency = System.currentTimeMillis() - tGatekeeper
                    DecisionResult(intent = liquidIntent, confidence = 0.95f, latencyMs = latency) to "Tier 2: Liquid AI"
                } else {
                    val queryEmbedding = embeddingEngine?.embed(effectiveText)
                    val localDecision = runCatching {
                        localClassifier.classify(effectiveText, queryEmbedding)
                    }.getOrElse {
                        DecisionResult(intent = InboundIntent.CATALOG_INQUIRY, confidence = 0.5f, latencyMs = 0L)
                    }
                    val latency = System.currentTimeMillis() - tGatekeeper
                    localDecision.copy(latencyMs = latency) to "Tier 3: LocalDecisionClassifier (On-Device Fallback)"
                }
            }
        }

        // 4. RAG Retrieval if intent is CATALOG_INQUIRY or RAG needed
        var candidates: List<RetrievalCandidate> = emptyList()
        var bestScore = 0f
        val needsRag = decision.intent == InboundIntent.CATALOG_INQUIRY || isImagePresent
        if (needsRag) {
            val engine = embeddingEngine
            val repo = semanticRetrievalRepository
            if (engine != null && repo != null) {
                val queryEmbedding = engine.embed(effectiveText)
                if (queryEmbedding != null) {
                    val (retrieved, score) = repo.retrieveCandidates(
                        queryEmbedding = queryEmbedding,
                        limit = 5,
                        minimumSimilarityThreshold = 0.25f,
                        queryText = effectiveText
                    )
                    candidates = retrieved
                    bestScore = score
                }
            } else {
                // In-memory matching against knowledge entries
                val matches = _uiState.value.entries.filter {
                    it.chunkText.contains(effectiveText, ignoreCase = true)
                }
                if (matches.isNotEmpty()) {
                    candidates = matches.take(5).map {
                        RetrievalCandidate(
                            chunkId = it.chunkId,
                            chunkText = it.chunkText,
                            headerContext = it.headerContext,
                            similarityScore = 0.85f
                        )
                    }
                    bestScore = 0.85f
                }
            }
        }

        // 5. Assemble System Prompt with PromptContextBuilder
        val botName = runCatching { userPreferencesRepository.getBotNameSync() }.getOrDefault(UserPreferencesRepository.DEFAULT_BOT_NAME)
        val storeName = runCatching { userPreferencesRepository.getStoreNameSync() }.getOrDefault(UserPreferencesRepository.DEFAULT_STORE_NAME)
        val tone = runCatching { userPreferencesRepository.getPersonaToneSync() }.getOrDefault(PersonaTone.WITTY_EGYPTIAN)
        val storeTopics = runCatching { userPreferencesRepository.storeKnowledgeTopicsFlow.first() }.getOrDefault(emptyList())
        val storeWebsite = runCatching { userPreferencesRepository.storeWebsiteFlow.first() }.getOrNull()

        val staticPrompt = PromptContextBuilder.buildFirstPassSystemPrompt(
            botName = botName,
            storeName = storeName,
            tone = tone,
            isMultimodal = isImagePresent
        )
        val fullSystemPrompt = PromptContextBuilder.buildSystemPromptWithContext(
            staticSystemPrompt = staticPrompt,
            candidates = candidates,
            activeOrders = emptyList(),
            storeTopics = storeTopics,
            storeWebsite = storeWebsite,
            lastOrder = null,
            statusCounts = emptyList()
        )

        // 6. Call ReplyGenerationRepository.generateReply
        val priorMessages = _uiState.value.simulatorMessages.dropLast(1)
        val rawChatHistory = priorMessages.reversed().mapIndexed { index, msg ->
            val text = when {
                msg.sender == SimulatorSender.USER && msg.mediaType == SimulatorMediaType.IMAGE -> {
                    val nextBotMsg = priorMessages.getOrNull(priorMessages.indexOf(msg) + 1)
                    val visDesc = nextBotMsg?.trace?.visionDescription
                    if (!visDesc.isNullOrBlank()) {
                        if (msg.text.isNotBlank()) "[صورة مرفقة من العميل: $visDesc]\n${msg.text}" else "[صورة مرفقة من العميل: $visDesc]"
                    } else {
                        if (msg.text.isNotBlank()) "[صورة مرفقة من العميل]\n${msg.text}" else "[صورة مرفقة من العميل]"
                    }
                }
                msg.sender == SimulatorSender.USER && msg.mediaType == SimulatorMediaType.AUDIO -> {
                    val nextBotMsg = priorMessages.getOrNull(priorMessages.indexOf(msg) + 1)
                    val asrDesc = nextBotMsg?.trace?.asrTranscription
                    if (!asrDesc.isNullOrBlank()) {
                        if (msg.text.isNotBlank()) "[رسالة صوتية من العميل: $asrDesc]\n${msg.text}" else "[رسالة صوتية من العميل: $asrDesc]"
                    } else {
                        if (msg.text.isNotBlank()) "[رسالة صوتية من العميل]\n${msg.text}" else "[رسالة صوتية من العميل]"
                    }
                }
                else -> msg.text
            }
            ChatHistoryEntity(
                id = (index + 1).toLong(),
                contactName = "SimulatorCustomer",
                messageText = text,
                isFromUser = msg.sender == SimulatorSender.USER,
                timestamp = msg.timestamp,
                senderType = if (msg.sender == SimulatorSender.USER) "CUSTOMER" else "BOT"
            )
        }
        val isOngoing = priorMessages.isNotEmpty()

        val notification = ProcessedNotification(
            notificationKey = "sim_${System.currentTimeMillis()}",
            contactName = "SimulatorCustomer",
            originalText = effectiveText,
            normalizedText = effectiveText,
            context = ConversationContext(
                intent = IntentType.QUESTION,
                topic = TopicType.GENERAL,
                sentiment = SentimentType.NEUTRAL,
                confidence = 1.0f
            ),
            postTime = System.currentTimeMillis(),
            sourceClass = NotificationSourceClass.MAIN_APP,
            mediaFilePath = imageFilePath
        )

        val llmStartTime = System.currentTimeMillis()
        var llmLatencyMs = 0L
        var reQueryTriggered = false
        var refinedKeyword: String? = null
        var pass2Candidates: List<RetrievalCandidate> = emptyList()
        var finalParsedResponse: AgenticLlmResponse? = null
        var llmProviderName = "OpenRouter / Gemini / Qwen"

        val rawOutput = runCatching {
            replyRepo?.generateReply(
                notification = notification,
                candidates = if (isImagePresent) emptyList() else candidates,
                isOngoingConversation = isOngoing,
                chatHistory = if (isImagePresent) emptyList() else rawChatHistory,
                activeOrders = emptyList(),
                personalChunks = emptyList(),
                maxTokens = if (isImagePresent) 500 else 600,
                lastOrder = null,
                orderStatusCounts = emptyList(),
                inboundIntent = decision.intent
            )
        }.getOrNull()
        llmLatencyMs = System.currentTimeMillis() - llmStartTime

        if (!rawOutput.isNullOrBlank()) {
            val firstPassParsed = runCatching {
                ReasoningLeakSanitizer.decodeAgenticResponseSafely(rawOutput)
            }.getOrElse {
                ReasoningLeakSanitizer.buildUnsafeOutputHitlResponse()
            }

            visionDescription = firstPassParsed.visualProductDescription

            val rawAction = firstPassParsed.action.trim().uppercase()
            val normalizedAction = when (rawAction) {
                "UPDATE", "CREATE" -> "REPLY"
                else -> rawAction
            }
            val effectiveAction = when {
                normalizedAction == "HITL" -> "HITL"
                isImagePresent -> "RE_QUERY"
                normalizedAction == "REPLY" && !firstPassParsed.refinedSearchKeyword.isNullOrBlank() -> "RE_QUERY"
                normalizedAction == "RE_QUERY" && firstPassParsed.refinedSearchKeyword.isNullOrBlank() && !firstPassParsed.finalReplyText.isNullOrBlank() -> "REPLY"
                else -> normalizedAction
            }

            if (effectiveAction == "RE_QUERY") {
                reQueryTriggered = true
                refinedKeyword = firstPassParsed.refinedSearchKeyword?.trim()
                val visDesc = firstPassParsed.visualProductDescription?.trim()
                if (visDesc != null) visionDescription = visDesc

                val combinedQuery = listOfNotNull(refinedKeyword, visDesc)
                    .filter { it.isNotBlank() }
                    .joinToString(" ")

                if (combinedQuery.isNotBlank() && semanticRetrievalRepository != null && embeddingEngine != null) {
                    val p2Embedding = embeddingEngine.embed(combinedQuery)
                    if (p2Embedding != null) {
                        val (p2Retrieved, _) = semanticRetrievalRepository.retrieveCandidates(
                            queryEmbedding = p2Embedding,
                            limit = 5,
                            minimumSimilarityThreshold = 0.25f,
                            queryText = combinedQuery
                        )
                        pass2Candidates = p2Retrieved
                    }
                }

                val merged = AutoReplyOrchestrator.mergeCandidates(
                    pass1 = candidates,
                    pass2 = pass2Candidates,
                    limit = 8,
                    minScore = 0.25f
                )

                val pass2Query = if (!visionDescription.isNullOrBlank()) {
                    "$effectiveText\n[وصف الصورة المرفقة من العميل]: $visionDescription"
                } else {
                    effectiveText
                }

                val finalOutput = runCatching {
                    replyRepo?.generateFinalReply(
                        queryText = pass2Query,
                        chunks = merged,
                        isOngoingConversation = isOngoing,
                        chatHistory = rawChatHistory,
                        activeOrders = emptyList(),
                        personalChunks = emptyList(),
                        maxTokens = if (imageFilePath != null && visionDescription.isNullOrBlank()) 1500 else 750,
                        imageUrl = if (visionDescription.isNullOrBlank()) imageFilePath else null,
                        lastOrder = null,
                        orderStatusCounts = emptyList()
                    )
                }.getOrNull()
                llmLatencyMs = System.currentTimeMillis() - llmStartTime

                if (!finalOutput.isNullOrBlank()) {
                    finalParsedResponse = runCatching {
                        ReasoningLeakSanitizer.decodeAgenticResponseSafely(finalOutput)
                    }.getOrElse {
                        ReasoningLeakSanitizer.buildUnsafeOutputHitlResponse()
                    }
                } else {
                    finalParsedResponse = firstPassParsed
                }
            } else {
                finalParsedResponse = firstPassParsed
            }
        } else {
            // Local sandbox fallback response when cloud keys are unavailable
            llmProviderName = "محاكي الاسترجاع المحلي (بدون اتصال)"
            val fallbackText = if (candidates.isNotEmpty()) {
                candidates.first().chunkText
            } else {
                "لم يتم العثور على رد مطابق في كتالوج المتجر."
            }
            finalParsedResponse = AgenticLlmResponse(
                action = "REPLY",
                refinedSearchKeyword = null,
                visualProductDescription = null,
                orderAction = null,
                finalReplyText = fallbackText,
                extractedOrder = null
            )
        }

        val isHitl = finalParsedResponse?.action.equals("HITL", ignoreCase = true)
        val finalReply = finalParsedResponse?.finalReplyText?.takeIf { it.isNotBlank() }
            ?: when {
                isHitl -> "⚠️ تم توجيه الاستفسار للمراجعة البشرية لعدم توفر رد مؤكد أو لحاجة الإجراء لتدخل يدوي."
                candidates.isNotEmpty() -> candidates.first().chunkText
                else -> "لم يتم العثور على معلومات كافية للإجابة في كتالوج المتجر."
            }

        val totalDurationMs = System.currentTimeMillis() - startTime

        val telemetry = replyRepo?.getLastExecutionTelemetry()
        val effectiveProvider = telemetry?.providerModel?.takeIf { it.isNotBlank() } ?: llmProviderName
        val effectiveLatency = telemetry?.latencyMs?.takeIf { it > 0 } ?: llmLatencyMs
        val promptTokens = if ((telemetry?.promptTokens ?: 0) > 0) {
            telemetry!!.promptTokens
        } else {
            PromptContextBuilder.estimateTokens(fullSystemPrompt + effectiveText)
        }
        val completionTokens = if ((telemetry?.completionTokens ?: 0) > 0) {
            telemetry!!.completionTokens
        } else {
            PromptContextBuilder.estimateTokens(finalReply)
        }
        val totalTokens = promptTokens + completionTokens

        val trace = ExecutionTrace(
            totalDurationMs = totalDurationMs,
            intent = decision.intent,
            intentSourceTier = resolvedTier,
            intentConfidence = decision.confidence,
            intentLatencyMs = decision.latencyMs,
            ragCandidates = candidates,
            ragBestScore = bestScore,
            reQueryTriggered = reQueryTriggered,
            reQueryKeyword = refinedKeyword,
            reQueryVisualDescription = visionDescription,
            reQueryCandidates = pass2Candidates,
            llmProvider = effectiveProvider,
            llmLatencyMs = effectiveLatency,
            llmAction = finalParsedResponse?.action ?: "REPLY",
            promptTokens = promptTokens,
            completionTokens = completionTokens,
            totalTokens = totalTokens,
            orderAction = finalParsedResponse?.orderAction?.uppercase() ?: "NONE",
            extractedOrder = finalParsedResponse?.extractedOrder,
            systemPrompt = "", // Removed from UI as requested
            asrTranscription = asrTranscription,
            visionDescription = visionDescription
        )

        val botMessage = SimulatorMessage(
            sender = SimulatorSender.BOT,
            text = finalReply,
            timestamp = System.currentTimeMillis(),
            trace = trace
        )

        _uiState.update {
            it.copy(
                isSimulating = false,
                simulatedResponse = finalReply,
                simulationResults = candidates,
                simulationBestScore = bestScore,
                simulatorMessages = it.simulatorMessages + botMessage
            )
        }
    }

    private fun resolveFileFromUri(uriString: String, mediaType: SimulatorMediaType? = null): File? {
        if (uriString.isBlank()) return null
        val ctx = context ?: return null
        return runCatching {
            val uri = Uri.parse(uriString)
            if (uri.scheme == "file") {
                val path = uri.path ?: return null
                val file = File(path)
                if (file.exists()) return file
            }
            val mimeType = ctx.contentResolver.getType(uri)
            val extension = when {
                mimeType != null && mimeType.contains("jpeg", ignoreCase = true) -> ".jpg"
                mimeType != null && mimeType.contains("png", ignoreCase = true) -> ".png"
                mimeType != null && mimeType.contains("webp", ignoreCase = true) -> ".webp"
                mimeType != null && mimeType.contains("ogg", ignoreCase = true) -> ".ogg"
                mimeType != null && mimeType.contains("opus", ignoreCase = true) -> ".opus"
                mimeType != null && mimeType.contains("audio", ignoreCase = true) -> ".m4a"
                uriString.contains(".jpg", true) || uriString.contains("jpeg", true) -> ".jpg"
                uriString.contains(".png", true) -> ".png"
                uriString.contains(".webp", true) -> ".webp"
                uriString.contains(".ogg", true) -> ".ogg"
                uriString.contains(".opus", true) -> ".opus"
                uriString.contains(".mp3", true) -> ".mp3"
                uriString.contains(".m4a", true) -> ".m4a"
                uriString.contains(".wav", true) -> ".wav"
                mediaType == SimulatorMediaType.IMAGE -> ".jpg"
                mediaType == SimulatorMediaType.AUDIO -> ".ogg"
                else -> ".tmp"
            }
            val stream = ctx.contentResolver.openInputStream(uri) ?: return null
            val tempFile = File(ctx.cacheDir, "sim_upload_${System.currentTimeMillis()}$extension")
            stream.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            tempFile
        }.getOrNull()
    }

    /**
     * Wipes the entire knowledge base:
     * 1. Deletes all chunks and embeddings from Room tables via repository.
     * 2. Clears store knowledge topics from DataStore preferences.
     * 3. Clears active UI state list.
     */
    fun clearAllKnowledge() {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, errorMessage = null) }
            repository.clearAllKnowledge()
            userPreferencesRepository.clearStoreKnowledgeTopics()
            userPreferencesRepository.clearStoreWebsite()
            _uiState.update {
                it.copy(
                    entries = emptyList(),
                    isProcessing = false
                )
            }
        }
    }
}
