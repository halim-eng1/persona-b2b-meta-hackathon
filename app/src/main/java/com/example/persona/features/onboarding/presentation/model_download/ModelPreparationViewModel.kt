package com.example.persona.features.onboarding.presentation.model_download

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.persona.features.onboarding.data.model_download.ModelDownloadManager
import com.example.persona.features.onboarding.domain.model_download.DownloadProgress
import com.example.persona.features.onboarding.domain.model_download.ModelDownloadState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

sealed interface ModelPreparationUiState {
    data object Idle : ModelPreparationUiState
    data object CheckingStorage : ModelPreparationUiState
    data class InsufficientStorage(val requiredMb: Long, val availableMb: Long) : ModelPreparationUiState
    data class CellularConfirmationRequired(val totalMb: Long) : ModelPreparationUiState
    data class Downloading(val progress: DownloadProgress) : ModelPreparationUiState
    data class Verifying(val statusText: String) : ModelPreparationUiState
    data class NetworkDisconnected(val progress: DownloadProgress) : ModelPreparationUiState
    data class Failed(val errorMessage: String, val canRetry: Boolean) : ModelPreparationUiState
    data object Success : ModelPreparationUiState
}

@HiltViewModel
class ModelPreparationViewModel @Inject constructor(
    private val modelDownloadManager: ModelDownloadManager
) : ViewModel() {

    val uiState: StateFlow<ModelPreparationUiState> = modelDownloadManager.state
        .map { domainState ->
            when (domainState) {
                is ModelDownloadState.Idle -> ModelPreparationUiState.Idle
                is ModelDownloadState.CheckingStorage -> ModelPreparationUiState.CheckingStorage
                is ModelDownloadState.InsufficientStorage -> ModelPreparationUiState.InsufficientStorage(
                    requiredMb = domainState.requiredBytes / (1024 * 1024),
                    availableMb = domainState.availableBytes / (1024 * 1024)
                )
                is ModelDownloadState.CellularConfirmationRequired -> ModelPreparationUiState.CellularConfirmationRequired(
                    totalMb = domainState.totalBytes / (1024 * 1024)
                )
                is ModelDownloadState.Downloading -> ModelPreparationUiState.Downloading(domainState.progress)
                is ModelDownloadState.Verifying -> ModelPreparationUiState.Verifying(
                    statusText = "جاري التأكد من سلامة ملفات المساعد..."
                )
                is ModelDownloadState.NetworkDisconnected -> ModelPreparationUiState.NetworkDisconnected(domainState.progress)
                is ModelDownloadState.Failed -> ModelPreparationUiState.Failed(
                    errorMessage = domainState.errorMessage,
                    canRetry = domainState.canRetry
                )
                is ModelDownloadState.Success -> ModelPreparationUiState.Success
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ModelPreparationUiState.Idle
        )

    init {
        // Kick off preflight / download on initial composition
        modelDownloadManager.startDownload()
    }

    fun proceedOnCellular() {
        modelDownloadManager.proceedOnCellular()
    }

    fun retry() {
        modelDownloadManager.retry()
    }

    fun openStorageSettings(context: Context) {
        val intent = Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            // Fallback to general settings
            val fallback = Intent(Settings.ACTION_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(fallback)
        }
    }
}

