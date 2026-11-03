package com.example.persona.features.chat_import.presentation

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import com.example.persona.core.common.AppException
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
    private val repository: ChatImportRepository,
    private val knowledgeRepository: com.example.persona.features.knowledge.data.repository.KnowledgeRepository? = null,
    private val markdownChunker: com.example.persona.features.chat_import.data.parser.MarkdownChunker = com.example.persona.features.chat_import.data.parser.MarkdownChunker(),
    private val userPreferencesRepository: com.example.persona.core.data.repository.UserPreferencesRepository? = null
) : ViewModel() {

    constructor(
        startImportUseCase: StartChatImportUseCase,
        repository: ChatImportRepository
    ) : this(
        startImportUseCase = startImportUseCase,
        repository = repository,
        knowledgeRepository = null,
        markdownChunker = com.example.persona.features.chat_import.data.parser.MarkdownChunker(),
        userPreferencesRepository = null
    )

    private val _uiState = MutableStateFlow(ChatImportUiState())
    val uiState: StateFlow<ChatImportUiState> = _uiState.asStateFlow()

    fun onDirectInputTextChanged(text: String) {
        _uiState.update { it.copy(directInputText = text, directIndexError = null, directIndexSuccessMessage = null) }
    }

    fun clearDirectIndexMessages() {
        _uiState.update { it.copy(directIndexError = null, directIndexSuccessMessage = null) }
    }

    fun ingestDirectText() {
        val text = _uiState.value.directInputText.trim()
        if (text.isBlank()) {
            _uiState.update {
                it.copy(directIndexError = "يرجى لصق نص المعرفة أولاً")
            }
            return
        }

        val kRepo = knowledgeRepository
        if (kRepo == null) {
            _uiState.update {
                it.copy(directIndexError = "محرك المعرفة غير مهيأ")
            }
            return
        }

        if (_uiState.value.isDirectIndexing) {
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isDirectIndexing = true,
                    directIndexError = null,
                    directIndexSuccessMessage = null
                )
            }

            try {
                val result = kRepo.ingestMarkdownDocument(
                    text = text,
                    chunker = markdownChunker,
                    sourceFileName = "direct_paste.md",
                    sourceType = "CORE_DOCUMENT"
                )

                result.fold(
                    onSuccess = { chunkCount ->
                        userPreferencesRepository?.let { prefs ->
                            try {
                                val topics = com.example.persona.features.chat_import.data.parser.StoreTopicsExtractor.extract(text)
                                prefs.setStoreKnowledgeTopics(topics)
                                val website = com.example.persona.features.chat_import.data.parser.StoreWebsiteExtractor.extract(text)
                                prefs.setStoreWebsite(website)
                            } catch (_: Exception) {}
                        }

                        _uiState.update {
                            it.copy(
                                isDirectIndexing = false,
                                directIndexSuccessMessage = "تمت فهرسة $chunkCount مقطعاً بنجاح!",
                                directInputText = ""
                            )
                        }
                    },
                    onFailure = { error ->
                        android.util.Log.e("ChatImportViewModel", "Direct indexing failed", error)
                        _uiState.update {
                            it.copy(
                                isDirectIndexing = false,
                                directIndexError = sanitizeErrorMessage(error)
                            )
                        }
                    }
                )
            } catch (e: Exception) {
                android.util.Log.e("ChatImportViewModel", "Unexpected exception in direct indexing", e)
                _uiState.update {
                    it.copy(
                        isDirectIndexing = false,
                        directIndexError = sanitizeErrorMessage(e)
                    )
                }
            }
        }
    }

    private fun sanitizeErrorMessage(error: Throwable): String {
        val msg = error.message.orEmpty()
        val isTechnicalError = error is android.database.SQLException ||
            msg.contains("SQLITE", ignoreCase = true) ||
            msg.contains("FOREIGN KEY", ignoreCase = true) ||
            msg.contains("constraint", ignoreCase = true) ||
            msg.contains("table", ignoreCase = true) ||
            msg.contains("database", ignoreCase = true) ||
            msg.contains("cursor", ignoreCase = true) ||
            msg.contains("NullPointerException", ignoreCase = true) ||
            msg.contains("Exception", ignoreCase = true)

        return if (isTechnicalError || msg.isBlank()) {
            "حدث خطأ أثناء حفظ وفهرسة المعرفة. يرجى التحقق من النص والمحاولة مرة أخرى."
        } else {
            msg
        }
    }

    fun onFilePicked(uri: Uri, displayName: String? = null) {
        _uiState.update {
            it.copy(
                selectedFileUri = uri,
                selectedFileName = displayName,
                error = null,
                isOwnerNameMissing = false
            )
        }
    }

    fun clearSelectedFile() {
        _uiState.update {
            it.copy(
                selectedFileUri = null,
                selectedFileName = null,
                error = null,
                isOwnerNameMissing = false
            )
        }
    }

    /** Called when the picked file has an unsupported extension (must be .txt or .md). */
    fun onUnsupportedFilePicked() {
        _uiState.update {
            it.copy(
                selectedFileUri = null,
                selectedFileName = null,
                error = "نوع الملف غير مدعوم. اختر ملف بنسق .txt أو .md (Unsupported file type. Please pick a .txt or .md file.)",
                isOwnerNameMissing = false
            )
        }
    }

    fun startImport() {
        val uri = _uiState.value.selectedFileUri ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true, error = null, isComplete = false, isCancelled = false, isOwnerNameMissing = false) }

            when (val result = startImportUseCase(uri)) {
                is Resource.Success -> {
                    result.data.collect { workInfo ->
                        workInfo?.let { updateFromWorkInfo(it) }
                    }
                }
                is Resource.Error -> {
                    _uiState.update {
                        it.copy(
                            isImporting = false,
                            error = result.exception.message,
                            isOwnerNameMissing = result.exception is AppException.OwnerNameNotConfigured
                        )
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
    val selectedFileName: String? = null,
    val isImporting: Boolean = false,
    val isComplete: Boolean = false,
    val isCancelled: Boolean = false,
    val error: String? = null,
    val isOwnerNameMissing: Boolean = false,
    // Direct text ingestion state
    val directInputText: String = "",
    val isDirectIndexing: Boolean = false,
    val directIndexSuccessMessage: String? = null,
    val directIndexError: String? = null,
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
