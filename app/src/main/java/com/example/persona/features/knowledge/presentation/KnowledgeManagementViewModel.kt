package com.example.persona.features.knowledge.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.persona.core.data.repository.UserPreferencesRepository
import com.example.persona.core.database.dao.FailedQueryDao
import com.example.persona.core.database.entity.FailedQueryEntity
import com.example.persona.features.auto_reply.data.repository.SemanticRetrievalRepository
import com.example.persona.features.auto_reply.domain.model.RetrievalCandidate
import com.example.persona.features.chat_import.data.embedding.EmbeddingEngine
import com.example.persona.features.knowledge.data.repository.KnowledgeRepository
import com.example.persona.features.knowledge.domain.model.KnowledgeEntry
import com.example.persona.features.knowledge.domain.usecase.AddManualKnowledgeUseCase
import com.example.persona.features.knowledge.domain.usecase.DeleteKnowledgeEntryUseCase
import com.example.persona.features.knowledge.domain.usecase.UpdateKnowledgeEntryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
    val simulationError: String? = null
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
    private val embeddingEngine: EmbeddingEngine? = null
) : ViewModel() {

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

    fun runSimulation(query: String) {
        if (query.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSimulating = true, simulationError = null) }
            try {
                val engine = embeddingEngine
                val repo = semanticRetrievalRepository
                if (engine == null || repo == null) {
                    val match = _uiState.value.entries.firstOrNull {
                        it.chunkText.contains(query, ignoreCase = true)
                    }
                    _uiState.update {
                        it.copy(
                            isSimulating = false,
                            simulatedResponse = match?.chunkText
                                ?: "لم يتم العثور على رد مطابق في كتالوج المتجر.",
                            simulationResults = emptyList(),
                            simulationBestScore = if (match != null) 0.85f else 0.0f
                        )
                    }
                    return@launch
                }

                val embedding = engine.embed(query)
                if (embedding == null) {
                    _uiState.update {
                        it.copy(
                            isSimulating = false,
                            simulationError = "تعذر توليد التضمين الذكي للسؤال التجريبي."
                        )
                    }
                    return@launch
                }

                val (candidates, bestScore) = repo.retrieveCandidates(
                    queryEmbedding = embedding,
                    limit = 5,
                    minimumSimilarityThreshold = 0.25f,
                    queryText = query
                )

                val response = if (candidates.isNotEmpty()) {
                    candidates.first().chunkText
                } else {
                    "لم يتم العثور على معلومات كافية للإجابة على هذا السؤال في الكتالوج (أعلى نسبة تطابق: ${(bestScore * 100).toInt()}%)."
                }

                _uiState.update {
                    it.copy(
                        isSimulating = false,
                        simulatedResponse = response,
                        simulationResults = candidates,
                        simulationBestScore = bestScore
                    )
                }
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
