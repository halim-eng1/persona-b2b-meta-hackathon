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
import com.example.persona.features.auto_reply.data.repository.PromptContextBuilder
import com.example.persona.features.auto_reply.data.repository.SemanticRetrievalRepository
import com.example.persona.features.auto_reply.data.service.WhatsAppMediaExtractor
import com.example.persona.features.auto_reply.domain.gatekeeper.InboundIntent
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
import com.example.persona.features.settings.domain.model.AccountMode
import com.example.persona.features.knowledge.presentation.model.ExecutionTrace
import com.example.persona.features.knowledge.presentation.model.SimulatorMediaType
import com.example.persona.features.knowledge.presentation.model.SimulatorMessage
import com.example.persona.features.knowledge.presentation.model.SimulatorSender
import com.example.persona.core.di.IoDispatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

data class KnowledgeManagementUiState(
    val entries: List<KnowledgeEntry> = emptyList(),
    val filteredEntries: List<KnowledgeEntry> = emptyList(),
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
    val selectedMediaType: SimulatorMediaType? = null,
    val suggestedFaqChips: List<String> = emptyList(),
    val isRecordingAudio: Boolean = false,
    val recordingDurationSeconds: Int = 0,
    val recordingAmplitude: Float = 0f,
    val showAlreadyPopulatedDialog: Boolean = false
)

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
    private val mediaExtractor: WhatsAppMediaExtractor? = null,
    @ApplicationContext private val context: Context? = null,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val replyRepo = replyGenerationRepository
    private val extractor = mediaExtractor

    private val _uiState = MutableStateFlow(KnowledgeManagementUiState())
    val uiState: StateFlow<KnowledgeManagementUiState> = _uiState.asStateFlow()

    companion object {
        /** Minimum time the blocking vectorisation overlay stays visible. */
        private const val MIN_INDEXING_OVERLAY_MS = 2_000L
    }

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
                        val faqs = extractDynamicFaqChips(knowledgeEntries)
                        _uiState.update { current ->
                            val filtered = computeFilteredEntries(knowledgeEntries, current.searchQuery, current.selectedFilter)
                            current.copy(
                                entries = knowledgeEntries,
                                filteredEntries = filtered,
                                suggestedFaqChips = faqs
                            )
                        }
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
        _uiState.update { current ->
            val filtered = computeFilteredEntries(current.entries, query, current.selectedFilter)
            current.copy(searchQuery = query, filteredEntries = filtered)
        }
    }

    fun onFilterSelected(filter: String) {
        _uiState.update { current ->
            val filtered = computeFilteredEntries(current.entries, current.searchQuery, filter)
            current.copy(selectedFilter = filter, filteredEntries = filtered)
        }
    }

    fun addManualEntry() {
        val query = _uiState.value.inputQuery
        val isTemp = _uiState.value.isTemporaryOffer
        if (query.isBlank()) return

        _uiState.update { it.copy(isProcessing = true, errorMessage = null) }

        viewModelScope.launch {
            val success = withMinimumIndexingDuration { addManualKnowledgeUseCase(query, isTemp) }
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

    fun loadBusinessSampleKnowledge() {
        loadSampleKnowledge(
            mode = AccountMode.BUSINESS,
            englishAsset = "knowledge_store_en.md",
            arabicAsset = "knowledge_store_ar.md",
            sourceType = "SAMPLE_BUSINESS",
            errorPrefix = "فشل تحميل نموذج بيانات المتجر"
        )
    }

    fun loadPersonalSampleKnowledge() {
        loadSampleKnowledge(
            mode = AccountMode.PERSONAL,
            englishAsset = "knowledge_personal_en.md",
            arabicAsset = "knowledge_personal_ar.md",
            sourceType = "SAMPLE_PERSONAL",
            errorPrefix = "فشل تحميل نموذج البيانات الشخصية"
        )
    }

    fun dismissAlreadyPopulatedDialog() {
        _uiState.update { it.copy(showAlreadyPopulatedDialog = false) }
    }

    /**
     * Loads a bundled knowledge sample matching the current app language and switches the
     * account mode so the assistant adopts the right persona. The sample UI is only offered
     * while the knowledge base is empty; if a sample import is requested after chunks exist,
     * an informational dialog is surfaced instead of duplicating content.
     */
    private fun loadSampleKnowledge(
        mode: AccountMode,
        englishAsset: String,
        arabicAsset: String,
        sourceType: String,
        errorPrefix: String
    ) {
        if (_uiState.value.entries.isNotEmpty()) {
            _uiState.update { it.copy(showAlreadyPopulatedDialog = true) }
            return
        }
        val ctx = context ?: return
        viewModelScope.launch(ioDispatcher) {
            _uiState.update { it.copy(isProcessing = true, errorMessage = null) }
            try {
                val isArabic = runCatching { userPreferencesRepository.appLanguageFlow.first() }
                    .getOrDefault("")
                    .startsWith("ar", ignoreCase = true)
                val assetName = if (isArabic) arabicAsset else englishAsset
                val markdown = ctx.assets.open(assetName).bufferedReader().use { it.readText() }
                withMinimumIndexingDuration {
                    repository.ingestMarkdownDocument(
                        text = markdown,
                        sourceFileName = assetName,
                        sourceType = sourceType
                    )
                    runCatching { userPreferencesRepository.setAccountMode(mode) }
                }
                _uiState.update { it.copy(isProcessing = false) }
            } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(isProcessing = false, errorMessage = "$errorPrefix: ${e.message}") }
            }
        }
    }

    /**
     * Keeps the blocking vectorisation overlay visible for at least [MIN_INDEXING_OVERLAY_MS]
     * so users perceive that chunks were chunked and embedded, even on fast devices.
     */
    private suspend fun <T> withMinimumIndexingDuration(block: suspend () -> T): T {
        val start = System.currentTimeMillis()
        val result = block()
        val elapsed = System.currentTimeMillis() - start
        if (elapsed < MIN_INDEXING_OVERLAY_MS) {
            delay(MIN_INDEXING_OVERLAY_MS - elapsed)
        }
        return result
    }

    private fun extractDynamicFaqChips(entries: List<KnowledgeEntry>): List<String> {
        // No knowledge yet: let the simulator fall back to its localized sample queries.
        if (entries.isEmpty()) return emptyList()

        val combinedText = entries.joinToString(" ") { "${it.headerContext.orEmpty()} ${it.chunkText}" }
        val isArabic = combinedText.any { it in '\u0600'..'\u06FF' }

        data class FaqSignal(val keywords: List<String>, val ar: String, val en: String)

        val signals = listOf(
            FaqSignal(
                listOf("سعر", "أسعار", "منتج", "price", "product", "cost"),
                "ما هي المنتجات والأسعار المتاحة؟",
                "What products and prices are available?"
            ),
            FaqSignal(
                listOf("شحن", "توصيل", "shipping", "delivery"),
                "ما هي مصاريف ومدة الشحن؟",
                "What are the shipping fees and delivery times?"
            ),
            FaqSignal(
                listOf("استبدال", "استرجاع", "refund", "return", "exchange"),
                "ما هي سياسة الاستبدال والاسترجاع؟",
                "What is the exchange and returns policy?"
            ),
            FaqSignal(
                listOf("اجتماع", "موعد", "meeting", "schedule", "appointment"),
                "كيف يمكنني حجز موعد اجتماع؟",
                "How can I schedule a meeting?"
            ),
            FaqSignal(
                listOf("عنوان", "فرع", "مقر", "location", "address", "showroom"),
                "أين يقع الفرع أو المقر الرئيسي؟",
                "Where is the main office or showroom located?"
            ),
            FaqSignal(
                listOf("هاتف", "رقم", "phone", "contact", "whatsapp"),
                "ما هو رقم التواصل المباشر؟",
                "What is the direct contact number?"
            ),
            FaqSignal(
                listOf("دفع", "payment", "card", "wallet"),
                "ما هي طرق الدفع المتاحة؟",
                "Which payment methods are accepted?"
            ),
            FaqSignal(
                listOf("ساعات", "مواعيد العمل", "hours", "working hours"),
                "ما هي ساعات العمل؟",
                "What are the working hours?"
            )
        )

        return signals
            .filter { signal -> signal.keywords.any { combinedText.contains(it, ignoreCase = true) } }
            .map { if (isArabic) it.ar else it.en }
            .take(6)
    }

    private var lastDeletedEntry: KnowledgeEntry? = null

    fun deleteEntry(entry: KnowledgeEntry) {
        lastDeletedEntry = entry
        viewModelScope.launch {
            deleteKnowledgeEntryUseCase(entry.chunkId)
        }
    }

    fun deleteEntry(chunkId: String) {
        val entry = _uiState.value.entries.find { it.chunkId == chunkId }
        if (entry != null) {
            lastDeletedEntry = entry
        }
        viewModelScope.launch {
            deleteKnowledgeEntryUseCase(chunkId)
        }
    }

    fun restoreLastDeletedEntry() {
        val entry = lastDeletedEntry ?: return
        viewModelScope.launch {
            repository.updateKnowledge(
                chunkId = entry.chunkId,
                newText = entry.chunkText,
                sourceType = entry.sourceType,
                headerContext = entry.headerContext
            )
            lastDeletedEntry = null
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
            val success = withMinimumIndexingDuration {
                addManualKnowledgeUseCase(formattedKnowledge, isTemporaryOffer)
            }
            if (success) {
                failedQueryDao.markReviewed(queryId)
                _uiState.update { it.copy(isProcessing = false, answeringQuery = null) }
            } else {
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        errorMessage = "فشل في حفظ إجابة السؤال في قاعدة المعرفة."
                    )
                }
            }
        }
    }

    fun onOpenSimulator() {
        _uiState.update {
            it.copy(
                isSimulatorOpen = true,
                simulatedResponse = null,
                simulationResults = emptyList(),
                simulationBestScore = 0f,
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

    fun onSelectMedia(uri: String?, type: SimulatorMediaType?) {
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

    fun onSimulatorMediaSelected(uri: String?, type: SimulatorMediaType?) {
        onSelectMedia(uri, type)
    }

    fun clearAllKnowledge() {
        viewModelScope.launch(ioDispatcher) {
            repository.clearAllKnowledge()
            runCatching { userPreferencesRepository.clearStoreKnowledgeTopics() }
            runCatching { userPreferencesRepository.clearStoreWebsite() }
            _uiState.update {
                it.copy(
                    entries = emptyList(),
                    filteredEntries = emptyList(),
                    suggestedFaqChips = emptyList()
                )
            }
        }
    }

    fun deleteQuery(id: Long) {
        viewModelScope.launch(ioDispatcher) {
            failedQueryDao.deleteById(id)
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

        // 1. Audio ASR transcription via Meta Voice Transcribe
        val ext = extractor
        var audioTempFile: File? = null
        var imageTempFile: File? = null
        if (mediaType == SimulatorMediaType.AUDIO && !mediaUri.isNullOrBlank()) {
            val audioFile = resolveFileFromUri(mediaUri, SimulatorMediaType.AUDIO)
            audioTempFile = audioFile
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
        if (isImagePresent) {
            val imgFile = resolveFileFromUri(mediaUri!!, SimulatorMediaType.IMAGE)
            imageTempFile = imgFile
            imageFilePath = imgFile?.absolutePath ?: mediaUri
        }

        // =========================================================================
        // UNIFIED SINGLE-STAGE REASONING (Meta Mandate)
        // Retrieve top 8 context chunks directly and pass to Muse Spark 1.3
        // =========================================================================
        val resolvedTier = "Unified Meta Muse Spark 1.3"

        var candidates: List<RetrievalCandidate> = emptyList()
        var bestScore = 0f

        val engine = embeddingEngine
        val repo = semanticRetrievalRepository
        if (engine != null && repo != null) {
            val queryEmbedding = engine.embed(effectiveText)
            if (queryEmbedding != null) {
                val (retrieved, score) = repo.retrieveCandidates(
                    queryEmbedding = queryEmbedding,
                    limit = 8,
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
                candidates = matches.take(8).map {
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

        // Assemble System Prompt with PromptContextBuilder
        val botName = runCatching { userPreferencesRepository.getBotNameSync() }.getOrDefault(UserPreferencesRepository.DEFAULT_BOT_NAME)
        val storeName = runCatching { userPreferencesRepository.getStoreNameSync() }.getOrDefault(UserPreferencesRepository.DEFAULT_STORE_NAME)
        val tone = runCatching { userPreferencesRepository.getPersonaToneSync() }.getOrDefault(PersonaTone.STANDARD_POLITE)
        val storeTopics = runCatching {
            kotlinx.coroutines.withTimeoutOrNull(250L) {
                userPreferencesRepository.storeKnowledgeTopicsFlow.first()
            }
        }.getOrNull().orEmpty()
        val storeWebsite = runCatching {
            kotlinx.coroutines.withTimeoutOrNull(250L) {
                userPreferencesRepository.storeWebsiteFlow.first()
            }
        }.getOrNull()

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

        // Call ReplyGenerationRepository.generateReply
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
        var llmLatencyMs: Long
        var reQueryTriggered = false
        var refinedKeyword: String? = null
        var pass2Candidates: List<RetrievalCandidate> = emptyList()
        var finalParsedResponse: AgenticLlmResponse
        var llmProviderName = "Meta Muse Spark 1.3"

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
                inboundIntent = InboundIntent.CATALOG_INQUIRY
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
                            limit = 8,
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
            // Local fallback response when cloud keys are unavailable
            llmProviderName = "محاكي الاسترجاع المحلي (بدون اتصال)"
            val fallbackText = if (candidates.isNotEmpty()) {
                candidates.first().chunkText
            } else {
                "لم يتم العثور على رد مطابق في كتالوج المعرفة."
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

        val isHitl = finalParsedResponse.action.equals("HITL", ignoreCase = true)
        val finalReply = finalParsedResponse.finalReplyText?.takeIf { it.isNotBlank() }
            ?: when {
                isHitl -> "⚠️ تم توجيه الاستفسار للمراجعة البشرية لعدم توفر رد مؤكد أو لحاجة الإجراء لتدخل يدوي."
                candidates.isNotEmpty() -> candidates.first().chunkText
                else -> "لم يتم العثور على معلومات كافية للإجابة في كتالوج المعرفة."
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
            intent = InboundIntent.CATALOG_INQUIRY,
            intentSourceTier = resolvedTier,
            intentConfidence = 1.0f,
            intentLatencyMs = 0L,
            ragCandidates = candidates,
            ragBestScore = bestScore,
            reQueryTriggered = reQueryTriggered,
            reQueryKeyword = refinedKeyword,
            reQueryVisualDescription = visionDescription,
            reQueryCandidates = pass2Candidates,
            llmProvider = effectiveProvider,
            llmLatencyMs = effectiveLatency,
            llmAction = finalParsedResponse.action,
            promptTokens = promptTokens,
            completionTokens = completionTokens,
            totalTokens = totalTokens,
            systemPrompt = fullSystemPrompt,
            extractedOrder = finalParsedResponse.extractedOrder,
            orderAction = finalParsedResponse.orderAction ?: "NONE",
            visionDescription = visionDescription,
            asrTranscription = asrTranscription
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

        // Release transient media copies created for this turn. Only files this ViewModel
        // materialised into the cache directory are removed; user-owned originals are untouched.
        cleanupTempMedia(audioTempFile)
        cleanupTempMedia(imageTempFile)
    }

    /**
     * Deletes a simulator cache copy (`sim_input_*`) once the turn that needed it is finished.
     * Files supplied by the user are never touched.
     */
    private fun cleanupTempMedia(file: File?) {
        if (file == null) return
        if (!file.name.startsWith("sim_input_")) return
        runCatching { if (file.exists()) file.delete() }
    }

    private fun resolveFileFromUri(uriString: String, type: SimulatorMediaType): File? {
        val trimmed = uriString.trim()
        // Bare absolute paths (e.g. from the live recorder) have no URI scheme.
        if (trimmed.startsWith("/")) {
            val direct = File(trimmed)
            if (direct.exists()) return direct
        }
        val uri = runCatching { Uri.parse(trimmed) }.getOrNull()
        if (uri?.scheme == "file") {
            val f = File(uri.path ?: "")
            if (f.exists()) return f
        }
        val ctx = context ?: return null
        val parsedUri = uri ?: return null
        return try {
            val extension = if (type == SimulatorMediaType.IMAGE) ".jpg" else ".wav"
            val tempFile = File.createTempFile("sim_input_", extension, ctx.cacheDir)
            val stream = ctx.contentResolver.openInputStream(parsedUri) ?: run {
                tempFile.delete()
                return null
            }
            stream.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            tempFile
        } catch (e: Exception) {
            null
        }
    }

    private fun computeFilteredEntries(
        entries: List<KnowledgeEntry>,
        query: String,
        filter: String
    ): List<KnowledgeEntry> {
        val queryFiltered = if (query.isBlank()) {
            entries
        } else {
            entries.filter { entry ->
                entry.chunkText.contains(query, ignoreCase = true) ||
                (entry.headerContext?.contains(query, ignoreCase = true) == true)
            }
        }

        return when (filter.uppercase()) {
            "ALL" -> queryFiltered
            "PRODUCT" -> queryFiltered.filter { it.sourceType.contains("PRODUCT", ignoreCase = true) || it.chunkText.contains("سعر") || it.chunkText.contains("جنيه") }
            "POLICY" -> queryFiltered.filter { it.sourceType.contains("POLICY", ignoreCase = true) || it.chunkText.contains("شحن") || it.chunkText.contains("استرجاع") }
            "OFFER" -> queryFiltered.filter { it.sourceType.contains("OFFER", ignoreCase = true) || it.sourceType.contains("TEMP", ignoreCase = true) }
            else -> queryFiltered
        }
    }
}
