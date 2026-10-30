package com.example.persona.features.settings.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * Supported LLM / AI inference providers in Persona.
 */
object ModelProviders {
    const val QWEN = "qwen"
    const val GEMINI = "gemini"
    const val GROQ = "groq"
    const val NVIDIA = "nvidia"
    const val OPENROUTER = "openrouter"
    const val CLOUDFLARE = "cloudflare"
    const val LIQUID = "liquid"
    const val NARA = "nara"

    val ALL = listOf(QWEN, GEMINI, GROQ, NVIDIA, OPENROUTER, CLOUDFLARE, LIQUID, NARA)

    fun normalize(provider: String): String = provider.trim().lowercase()
}

/**
 * Repository interface for hardware-keystore protected credentials (API keys).
 * Decouples sensitive token storage from plaintext DataStore preferences.
 * Supports unlimited API keys per provider with reactive flow observation and
 * backward-compatible convenience methods for single keys.
 */
interface SecureCredentialsRepository {
    /**
     * Returns all configured API keys for [provider] (e.g. "qwen", "gemini", "groq", "nvidia").
     */
    fun getApiKeys(provider: String): List<String>

    /**
     * Atomically replaces the list of API keys for [provider].
     */
    fun setApiKeys(provider: String, keys: List<String>)

    /**
     * Adds an API key to [provider] if not already present.
     */
    fun addApiKey(provider: String, key: String)

    /**
     * Removes an API key from [provider].
     */
    fun removeApiKey(provider: String, key: String)

    /**
     * Observes the list of API keys for [provider] as a reactive flow.
     */
    fun getApiKeysFlow(provider: String): Flow<List<String>>

    // ── Convenience backward-compatibility wrappers ───────────────────────────
    fun getNvidiaApiKey(): String
    fun setNvidiaApiKey(key: String?)

    fun getQwenApiKey(): String
    fun setQwenApiKey(key: String?)

    val nvidiaApiKeyFlow: Flow<String?>
    val qwenApiKeyFlow: Flow<String?>
}
