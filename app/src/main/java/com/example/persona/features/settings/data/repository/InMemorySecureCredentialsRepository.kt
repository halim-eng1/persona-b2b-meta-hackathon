package com.example.persona.features.settings.data.repository

import com.example.persona.features.settings.domain.repository.SecureCredentialsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory fallback implementation of [SecureCredentialsRepository] for unit tests
 * and platforms without Android KeyStore support.
 */
class InMemorySecureCredentialsRepository(
    initialNvidiaApiKey: String? = null,
    initialQwenApiKey: String? = null
) : SecureCredentialsRepository {

    private val _nvidiaFlow = MutableStateFlow(initialNvidiaApiKey?.takeIf { it.isNotBlank() })
    private val _qwenFlow = MutableStateFlow(initialQwenApiKey?.takeIf { it.isNotBlank() })

    override fun getNvidiaApiKey(): String = _nvidiaFlow.value.orEmpty()

    override fun setNvidiaApiKey(key: String?) {
        _nvidiaFlow.value = key?.trim()?.takeIf { it.isNotBlank() }
    }

    override fun getQwenApiKey(): String = _qwenFlow.value.orEmpty()

    override fun setQwenApiKey(key: String?) {
        _qwenFlow.value = key?.trim()?.takeIf { it.isNotBlank() }
    }

    override val nvidiaApiKeyFlow: Flow<String?> = _nvidiaFlow.asStateFlow()

    override val qwenApiKeyFlow: Flow<String?> = _qwenFlow.asStateFlow()
}
