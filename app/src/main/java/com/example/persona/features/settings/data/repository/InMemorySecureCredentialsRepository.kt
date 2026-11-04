package com.example.persona.features.settings.data.repository

import com.example.persona.features.settings.domain.repository.ModelProviders
import com.example.persona.features.settings.domain.repository.SecureCredentialsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory implementation of [SecureCredentialsRepository] for unit tests
 * and platforms without Android KeyStore support.
 */
class InMemorySecureCredentialsRepository : SecureCredentialsRepository {

    private val providerKeyStore = ConcurrentHashMap<String, MutableStateFlow<List<String>>>()

    init {
        ModelProviders.ALL.forEach { provider ->
            getOrCreateFlow(provider)
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
}
