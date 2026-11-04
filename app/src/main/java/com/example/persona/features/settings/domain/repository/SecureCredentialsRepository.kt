package com.example.persona.features.settings.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * Supported AI inference providers in Persona.
 *
 * Runtime mandate: Meta models only. The provider abstraction is retained so the
 * circuit-breaker and settings plumbing stay generic, but Meta is the only value.
 */
object ModelProviders {
    const val META = "meta"

    val ALL = listOf(META)

    fun normalize(provider: String): String = provider.trim().lowercase()

    val DEFAULT_MODELS: Map<String, List<String>> = mapOf(
        META to listOf(
            "muse-spark-1.3-contributor",
            "muse-spark-1.3",
            "muse-spark-1.2-contributor",
            "muse-spark-1.2",
            "muse-voice-transcribe-1.0"
        )
    )

    val DEFAULT_CAPABILITY_PROVIDERS: Map<ModelCapability, List<String>> = mapOf(
        ModelCapability.TEXT to listOf(META),
        ModelCapability.VISION to listOf(META),
        ModelCapability.AUDIO to listOf(META),
        ModelCapability.DECISION to listOf(META)
    )

    val DEFAULT_CAPABILITIES_BY_PROVIDER: Map<String, Set<ModelCapability>> = mapOf(
        META to setOf(
            ModelCapability.TEXT,
            ModelCapability.VISION,
            ModelCapability.AUDIO,
            ModelCapability.DECISION
        )
    )
}

/**
 * Repository interface for hardware-keystore protected credentials (API keys).
 * Decouples sensitive token storage from plaintext DataStore preferences.
 * Supports unlimited API keys per provider with reactive flow observation and
 * backward-compatible convenience methods for single keys.
 */
interface SecureCredentialsRepository {
    /**
     * Returns all configured API keys for [provider] (e.g. "meta").
     */
    fun getApiKeys(provider: String): List<String>

    /**
     * Checks if at least one valid API key is configured for [provider].
     */
    fun hasConfiguredKeys(provider: String): Boolean = getApiKeys(provider).isNotEmpty()

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
}
