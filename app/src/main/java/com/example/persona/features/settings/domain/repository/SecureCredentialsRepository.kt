package com.example.persona.features.settings.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for hardware-keystore protected credentials (API keys).
 * Decouples sensitive token storage from plaintext DataStore preferences.
 */
interface SecureCredentialsRepository {
    fun getNvidiaApiKey(): String
    fun setNvidiaApiKey(key: String?)

    fun getQwenApiKey(): String
    fun setQwenApiKey(key: String?)

    val nvidiaApiKeyFlow: Flow<String?>
    val qwenApiKeyFlow: Flow<String?>
}
