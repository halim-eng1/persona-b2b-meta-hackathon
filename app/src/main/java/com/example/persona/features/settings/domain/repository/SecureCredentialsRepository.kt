package com.example.persona.features.settings.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * Supported LLM / AI inference providers in Persona.
 */
object ModelProviders {
    const val META = "meta"
    const val OPENROUTER = "openrouter"
    const val QWEN = "qwen"
    const val GEMINI = "gemini"
    const val GROQ = "groq"
    const val NVIDIA = "nvidia"
    const val CLOUDFLARE = "cloudflare"
    const val LIQUID = "liquid"
    const val NARA = "nara"

    val ALL = listOf(META, OPENROUTER, QWEN, GEMINI, GROQ, NVIDIA, CLOUDFLARE, LIQUID, NARA)

    fun normalize(provider: String): String = provider.trim().lowercase()

    val DEFAULT_MODELS: Map<String, List<String>> = mapOf(
        META to listOf(
            "muse-spark-1.3-contributor",
            "muse-spark-1.3",
            "muse-spark-1.2-contributor",
            "muse-spark-1.2",
            "muse-voice-transcribe-1.0"
        ),
        QWEN to listOf(
            "deepseek-v4.1-flash",
            "qwen3.8-omni-flash",
            "qwen-vl-max",
            "fun-asr-flash-2026-06-15",
            "qwen3.8-max",
            "qwen3.8-flash",
            "deepseek-v4-pro-0813",
            "qwen3.7-plus",
            "qwen3.7-flash",
            "qwen3.6-flash",
            "qwen3.5-flash",
            "qwen3-coder-flash",
            "qwen3.6-27b"
        ),
        GEMINI to listOf(
            "gemini-3.8-flash",
            "gemini-3.5-flash-lite",
            "gemini-3.1-flash-lite"
        ),
        NVIDIA to listOf(
            "nvidia/nemotron-3-ultra-550b-a55b",
            "nvidia/nemotron-3.5-bolt-e4b",
            "nvidia/nemotron-3.5-lightning-30b-a3b"
        ),
        GROQ to listOf(
            "llama-3.3-70b-versatile",
            "whisper-large-v3",
            "llama-3.2-11b-vision-preview",
            "openai/gpt-oss-120b",
            "openai/gpt-oss-20b",
            "qwen/qwen3.8-27b"
        ),
        OPENROUTER to listOf(
            "nvidia/nemotron-3.5-super-vl:free",
            "nvidia/nemotron-3-ultra-550b-a55b:free",
            "liquid/lfm-2.5-2.6b:free",
            "openrouter/free",
            "liquid/d1.1",
            "cloudflare/clef-flash",
            "openai/whisper-large-v3",
            "z-ai/glm-5.3-flash"
        ),
        NARA to listOf(
            "laguna-s-2.1"
        ),
        CLOUDFLARE to listOf(
            "@cf/cloudflare/clef-flash",
            "@cf/cloudflare/clef-omni"
        ),
        LIQUID to listOf(
            "d1:latest",
            "d1"
        )
    )

    val DEFAULT_CAPABILITY_PROVIDERS: Map<ModelCapability, List<String>> = mapOf(
        ModelCapability.TEXT to listOf(META, OPENROUTER, QWEN, GEMINI, GROQ, NVIDIA, NARA),
        ModelCapability.VISION to listOf(META, OPENROUTER, GROQ, GEMINI, NVIDIA),
        ModelCapability.AUDIO to listOf(META, OPENROUTER, QWEN, GEMINI, GROQ),
        ModelCapability.DECISION to listOf(OPENROUTER, CLOUDFLARE, LIQUID)
    )

    val DEFAULT_CAPABILITIES_BY_PROVIDER: Map<String, Set<ModelCapability>> = mapOf(
        META to setOf(ModelCapability.TEXT, ModelCapability.VISION, ModelCapability.AUDIO),
        QWEN to setOf(ModelCapability.TEXT, ModelCapability.VISION, ModelCapability.AUDIO),
        GEMINI to setOf(ModelCapability.TEXT, ModelCapability.VISION, ModelCapability.AUDIO),
        GROQ to setOf(ModelCapability.TEXT, ModelCapability.VISION, ModelCapability.AUDIO),
        NVIDIA to setOf(ModelCapability.TEXT, ModelCapability.VISION),
        OPENROUTER to setOf(ModelCapability.TEXT, ModelCapability.VISION, ModelCapability.AUDIO, ModelCapability.DECISION),
        NARA to setOf(ModelCapability.TEXT),
        CLOUDFLARE to setOf(ModelCapability.DECISION),
        LIQUID to setOf(ModelCapability.DECISION)
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
     * Returns all configured API keys for [provider] (e.g. "qwen", "gemini", "groq", "nvidia").
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

    /**
     * Returns the configured Cloudflare Account ID, falling back to [com.example.persona.BuildConfig.CLOUDFLARE_ACCOUNT_ID].
     */
    fun getCloudflareAccountId(): String

    /**
     * Updates the configured Cloudflare Account ID.
     */
    fun setCloudflareAccountId(accountId: String?)

    /**
     * Observes the Cloudflare Account ID as a reactive flow.
     */
    val cloudflareAccountIdFlow: Flow<String?>

    // ── Convenience backward-compatibility wrappers ───────────────────────────
    fun getNvidiaApiKey(): String
    fun setNvidiaApiKey(key: String?)

    fun getQwenApiKey(): String
    fun setQwenApiKey(key: String?)

    val nvidiaApiKeyFlow: Flow<String?>
    val qwenApiKeyFlow: Flow<String?>
}
