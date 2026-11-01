package com.example.persona.features.settings.data.repository

import com.example.persona.features.settings.domain.repository.ModelProviders
import com.example.persona.features.settings.domain.repository.SecureCredentialsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory implementation of [SecureCredentialsRepository] for unit tests
 * and platforms without Android KeyStore support.
 */
class InMemorySecureCredentialsRepository(
    initialNvidiaApiKey: String? = null,
    initialQwenApiKey: String? = null,
    private val defaultCloudflareAccountId: String? = null
) : SecureCredentialsRepository {

    private val providerKeyStore = ConcurrentHashMap<String, MutableStateFlow<List<String>>>()
    private val cloudflareAccountIdState = MutableStateFlow<String?>(null)

    init {
        ModelProviders.ALL.forEach { provider ->
            getOrCreateFlow(provider)
        }
        if (!initialNvidiaApiKey.isNullOrBlank()) {
            setNvidiaApiKey(initialNvidiaApiKey)
        }
        if (!initialQwenApiKey.isNullOrBlank()) {
            setQwenApiKey(initialQwenApiKey)
        }
        if (!defaultCloudflareAccountId.isNullOrBlank()) {
            cloudflareAccountIdState.value = defaultCloudflareAccountId
        }
    }

    private fun getOrCreateFlow(provider: String): MutableStateFlow<List<String>> {
        val norm = ModelProviders.normalize(provider)
        return providerKeyStore.computeIfAbsent(norm) {
            MutableStateFlow(emptyList())
        }
    }

    override fun getApiKeys(provider: String): List<String> {
        val norm = ModelProviders.normalize(provider)
        return getOrCreateFlow(norm).value
    }

    override fun hasConfiguredKeys(provider: String): Boolean {
        return getApiKeys(provider).isNotEmpty()
    }

    override fun setApiKeys(provider: String, keys: List<String>) {
        val norm = ModelProviders.normalize(provider)
        val sanitized = keys.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        getOrCreateFlow(norm).value = sanitized
    }

    override fun addApiKey(provider: String, key: String) {
        val trimmed = key.trim()
        if (trimmed.isBlank()) return
        val current = getApiKeys(provider)
        if (!current.contains(trimmed)) {
            setApiKeys(provider, current + trimmed)
        }
    }

    override fun removeApiKey(provider: String, key: String) {
        val trimmed = key.trim()
        val current = getApiKeys(provider)
        setApiKeys(provider, current.filter { it != trimmed })
    }

    override fun getApiKeysFlow(provider: String): Flow<List<String>> {
        val norm = ModelProviders.normalize(provider)
        return getOrCreateFlow(norm).asStateFlow()
    }

    override fun getCloudflareAccountId(): String {
        val current = cloudflareAccountIdState.value
        if (!current.isNullOrBlank()) return current
        if (!defaultCloudflareAccountId.isNullOrBlank()) return defaultCloudflareAccountId
        return runCatching { com.example.persona.BuildConfig.CLOUDFLARE_ACCOUNT_ID }.getOrDefault("")
    }

    override fun setCloudflareAccountId(accountId: String?) {
        val trimmed = accountId?.trim()?.takeIf { it.isNotBlank() }
        cloudflareAccountIdState.value = trimmed
    }

    override val cloudflareAccountIdFlow: Flow<String?> = cloudflareAccountIdState.asStateFlow()

    override fun getNvidiaApiKey(): String =
        getApiKeys(ModelProviders.NVIDIA).firstOrNull().orEmpty()

    override fun setNvidiaApiKey(key: String?) {
        val trimmed = key?.trim()?.takeIf { it.isNotBlank() }
        if (trimmed == null) {
            setApiKeys(ModelProviders.NVIDIA, emptyList())
        } else {
            setApiKeys(ModelProviders.NVIDIA, listOf(trimmed))
        }
    }

    override fun getQwenApiKey(): String =
        getApiKeys(ModelProviders.QWEN).firstOrNull().orEmpty()

    override fun setQwenApiKey(key: String?) {
        val trimmed = key?.trim()?.takeIf { it.isNotBlank() }
        if (trimmed == null) {
            setApiKeys(ModelProviders.QWEN, emptyList())
        } else {
            setApiKeys(ModelProviders.QWEN, listOf(trimmed))
        }
    }

    override val nvidiaApiKeyFlow: Flow<String?> =
        getApiKeysFlow(ModelProviders.NVIDIA).map { it.firstOrNull() }

    override val qwenApiKeyFlow: Flow<String?> =
        getApiKeysFlow(ModelProviders.QWEN).map { it.firstOrNull() }
}
