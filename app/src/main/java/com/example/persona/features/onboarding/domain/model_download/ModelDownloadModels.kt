package com.example.persona.features.onboarding.domain.model_download

/**
 * Real-time progress metrics for asset streaming.
 */
data class DownloadProgress(
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val speedBytesPerSec: Long = 0L,
    val etaSeconds: Long = 0L,
    val currentFileName: String = ""
) {
    val progressFraction: Float
        get() = if (totalBytes > 0) (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f
}

/**
 * State machine for on-device AI asset download and validation.
 */
sealed interface ModelDownloadState {
    data object Idle : ModelDownloadState
    data object CheckingStorage : ModelDownloadState
    data class InsufficientStorage(val requiredBytes: Long, val availableBytes: Long) : ModelDownloadState
    data class CellularConfirmationRequired(val totalBytes: Long) : ModelDownloadState
    data class Downloading(val progress: DownloadProgress) : ModelDownloadState
    data class Verifying(val fileName: String) : ModelDownloadState
    data class NetworkDisconnected(val progress: DownloadProgress) : ModelDownloadState
    data class Failed(val errorMessage: String, val canRetry: Boolean) : ModelDownloadState
    data object Success : ModelDownloadState
}

/**
 * Immutable specification for an on-device AI asset to download and verify.
 */
data class ModelAssetSpec(
    val fileName: String,
    val url: String,
    val expectedBytes: Long,
    val expectedSha256: String,
    val relativePath: String
)

