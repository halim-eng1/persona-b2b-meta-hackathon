package com.example.persona.features.chat_import.presentation

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import com.example.persona.core.common.Resource
import com.example.persona.features.chat_import.data.repository.ChatImportRepository
import com.example.persona.features.chat_import.data.worker.ChatImportWorker
import com.example.persona.features.chat_import.domain.usecase.StartChatImportUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatImportViewModel @Inject constructor(
    private val startImportUseCase: StartChatImportUseCase,
    private val repository: ChatImportRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatImportUiState())
    val uiState: StateFlow<ChatImportUiState> = _uiState.asStateFlow()

    fun onFilePicked(uri: Uri) {
        _uiState.update { it.copy(selectedFileUri = uri, error = null) }
    }

    fun startImport(userName: String? = null) {
        val uri = _uiState.value.selectedFileUri ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true, error = null, isComplete = false, isCancelled = false) }

            when (val result = startImportUseCase(uri, userName)) {
                is Resource.Success -> {
                    result.data.collect { workInfo ->
                        workInfo?.let { updateFromWorkInfo(it) }
                    }
                }
                is Resource.Error -> {
                    _uiState.update {
                        it.copy(isImporting = false, error = result.exception.message)
                    }
                }
                is Resource.Loading -> { /* no-op */ }
            }
        }
    }

    fun cancelImport() {
        repository.cancelImport()
        _uiState.update { it.copy(isImporting = false, isCancelled = true) }
    }

    private fun updateFromWorkInfo(workInfo: WorkInfo) {
        when (workInfo.state) {
            WorkInfo.State.RUNNING -> {
                val progress = workInfo.progress
                _uiState.update {
                    it.copy(
                        isImporting = true,
                        currentPhase = progress.getString(ChatImportWorker.KEY_PHASE) ?: "",
                        currentBatch = progress.getInt(ChatImportWorker.KEY_CURRENT_BATCH, 0),
                        totalBatches = progress.getInt(ChatImportWorker.KEY_TOTAL_BATCHES, 0),
                        messagesProcessed = progress.getInt(ChatImportWorker.KEY_MESSAGES_PROCESSED, 0),
                        totalMessages = progress.getInt(ChatImportWorker.KEY_TOTAL_MESSAGES, 0)
                    )
                }
            }
            WorkInfo.State.SUCCEEDED -> {
                val output = workInfo.outputData
                _uiState.update {
                    it.copy(
                        isImporting = false,
                        isComplete = true,
                        resultContact = output.getString(ChatImportWorker.KEY_RESULT_CONTACT),
                        resultTotal = output.getInt(ChatImportWorker.KEY_RESULT_TOTAL, 0),
                        resultUserMsgs = output.getInt(ChatImportWorker.KEY_RESULT_USER_MSGS, 0),
                        resultFormat = output.getString(ChatImportWorker.KEY_RESULT_FORMAT)
                    )
                }
            }
            WorkInfo.State.FAILED -> {
                val error = workInfo.outputData.getString(ChatImportWorker.KEY_ERROR_MESSAGE)
                _uiState.update {
                    it.copy(isImporting = false, error = error ?: "Import failed")
                }
            }
            WorkInfo.State.CANCELLED -> {
                _uiState.update {
                    it.copy(isImporting = false, isCancelled = true)
                }
            }
            else -> { /* ENQUEUED, BLOCKED — no UI change */ }
        }
    }
}

data class ChatImportUiState(
    val selectedFileUri: Uri? = null,
    val isImporting: Boolean = false,
    val isComplete: Boolean = false,
    val isCancelled: Boolean = false,
    val error: String? = null,
    // Progress
    val currentPhase: String = "",
    val currentBatch: Int = 0,
    val totalBatches: Int = 0,
    val messagesProcessed: Int = 0,
    val totalMessages: Int = 0,
    // Result
    val resultContact: String? = null,
    val resultTotal: Int = 0,
    val resultUserMsgs: Int = 0,
    val resultFormat: String? = null
) {
    val progressFraction: Float
        get() = if (totalMessages > 0) messagesProcessed.toFloat() / totalMessages else 0f
}
