package com.example.persona.features.knowledge.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.persona.core.database.dao.FailedQueryDao
import com.example.persona.core.database.entity.FailedQueryEntity
import com.example.persona.features.knowledge.domain.model.KnowledgeEntry
import com.example.persona.features.knowledge.domain.usecase.AddManualKnowledgeUseCase
import com.example.persona.features.knowledge.domain.usecase.DeleteKnowledgeEntryUseCase
import com.example.persona.features.knowledge.data.repository.KnowledgeRepository
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
    val errorMessage: String? = null
)

@HiltViewModel
class KnowledgeManagementViewModel @Inject constructor(
    private val repository: KnowledgeRepository,
    private val failedQueryDao: FailedQueryDao,
    private val addManualKnowledgeUseCase: AddManualKnowledgeUseCase,
    private val deleteKnowledgeEntryUseCase: DeleteKnowledgeEntryUseCase
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
                        errorMessage = "Failed to generate embedding or save entry."
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

    fun markQueryReviewed(id: Long) {
        viewModelScope.launch {
            failedQueryDao.markReviewed(id)
        }
    }

    fun useQueryAsInput(queryText: String) {
        _uiState.update { it.copy(inputQuery = queryText, errorMessage = null) }
    }

    fun deleteQuery(id: Long) {
        viewModelScope.launch {
            failedQueryDao.deleteById(id)
        }
    }
}
