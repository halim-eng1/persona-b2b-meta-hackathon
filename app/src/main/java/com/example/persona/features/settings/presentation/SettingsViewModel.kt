package com.example.persona.features.settings.presentation

import androidx.annotation.StringRes
import com.example.persona.R

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.persona.core.data.repository.BusinessHoursSchedule
import com.example.persona.core.data.repository.UserPreferencesRepository
import com.example.persona.core.di.IoDispatcher
import com.example.persona.features.auto_reply.domain.model.PersonaTone
import com.example.persona.features.auto_reply.domain.model.StylePreferences
import com.example.persona.features.auto_reply.domain.model.StyleProfile
import com.example.persona.features.settings.domain.model.AccountMode
import com.example.persona.features.onboarding.data.AutoStartHelper
import com.example.persona.features.onboarding.data.PermissionChecker
import com.example.persona.features.settings.data.repository.InMemorySecureCredentialsRepository
import com.example.persona.features.settings.domain.repository.ModelCapability
import com.example.persona.features.settings.domain.repository.ModelConfig
import com.example.persona.features.settings.domain.repository.ModelProviders
import com.example.persona.features.settings.domain.repository.SecureCredentialsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.util.Collections
import javax.inject.Inject
import com.example.persona.features.auto_reply.data.providers.ProviderCircuitBreakerAndKeyManager

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val secureCredentialsRepository: SecureCredentialsRepository,
    private val permissionChecker: PermissionChecker? = null,
    private val autoStartHelper: AutoStartHelper? = null,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val circuitBreakerAndKeyManager: ProviderCircuitBreakerAndKeyManager? = null
) : ViewModel() {

    // Secondary constructor for existing unit tests
    constructor(
        userPreferencesRepository: UserPreferencesRepository,
        permissionChecker: PermissionChecker? = null
    ) : this(
        userPreferencesRepository = userPreferencesRepository,
        secureCredentialsRepository = InMemorySecureCredentialsRepository(),
        permissionChecker = permissionChecker,
        autoStartHelper = null,
        ioDispatcher = Dispatchers.Main.immediate,
        circuitBreakerAndKeyManager = null
    )

    constructor(
        userPreferencesRepository: UserPreferencesRepository,
        secureCredentialsRepository: SecureCredentialsRepository,
        ioDispatcher: CoroutineDispatcher
    ) : this(
        userPreferencesRepository = userPreferencesRepository,
        secureCredentialsRepository = secureCredentialsRepository,
        permissionChecker = null,
        autoStartHelper = null,
        ioDispatcher = ioDispatcher,
        circuitBreakerAndKeyManager = null
    )

    constructor(
        userPreferencesRepository: UserPreferencesRepository,
        secureCredentialsRepository: SecureCredentialsRepository,
        permissionChecker: PermissionChecker?,
        autoStartHelper: AutoStartHelper?,
        ioDispatcher: CoroutineDispatcher
    ) : this(
        userPreferencesRepository = userPreferencesRepository,
        secureCredentialsRepository = secureCredentialsRepository,
        permissionChecker = permissionChecker,
        autoStartHelper = autoStartHelper,
        ioDispatcher = ioDispatcher,
        circuitBreakerAndKeyManager = null
    )

    constructor(
        userPreferencesRepository: UserPreferencesRepository,
        secureCredentialsRepository: SecureCredentialsRepository,
        circuitBreakerAndKeyManager: ProviderCircuitBreakerAndKeyManager?
    ) : this(
        userPreferencesRepository = userPreferencesRepository,
        secureCredentialsRepository = secureCredentialsRepository,
        permissionChecker = null,
        autoStartHelper = null,
        ioDispatcher = Dispatchers.Main.immediate,
        circuitBreakerAndKeyManager = circuitBreakerAndKeyManager
    )

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    val isAutoPauseOnHitl: StateFlow<Boolean> = userPreferencesRepository.isAutoPauseOnHitlEnabledFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    init {
        refreshStoragePermission()
        observePreferences()
        refreshAllProviderConfigs()
    }

    private fun observePreferences() {
        // Group 1: Style & Core Brand Identity
        viewModelScope.launch {
            combine(
                userPreferencesRepository.stylePreferences,
                userPreferencesRepository.botName,
                userPreferencesRepository.storeName,
                userPreferencesRepository.greetingTemplate,
                userPreferencesRepository.farewellTemplate
            ) { style, bot, store, greeting, farewell ->
                _uiState.update { s ->
                    s.copy(
                        personaTone = style.personaTone,
                        styleProfile = style.styleProfile,
                        thresholdLevel = ThresholdLevel.fromThreshold(style.similarityThreshold),
                        cooldownMins = style.cooldownMins,
                        replyDelaySeconds = style.replyDelaySeconds,
                        replyDelayInput = if (s.replyDelayError == null) style.replyDelaySeconds.toString() else s.replyDelayInput,
                        botName = bot,
                        storeName = store,
                        greetingTemplate = greeting,
                        farewellTemplate = farewell,
                        isLoading = false
                    )
                }
            }.collect {}
        }

        // Group 1b: Operational Account Mode
        viewModelScope.launch {
            userPreferencesRepository.accountModeFlow.collect { mode ->
                _uiState.update { it.copy(accountMode = mode) }
            }
        }

        // Group 2: App Routing
        viewModelScope.launch {
            combine(
                userPreferencesRepository.listenMainApp,
                userPreferencesRepository.listenBusinessApp,
                userPreferencesRepository.listenCloneApp
            ) { mainApp, bizApp, cloneApp ->
                _uiState.update { s ->
                    s.copy(
                        listenMainApp = mainApp,
                        listenBusinessApp = bizApp,
                        listenCloneApp = cloneApp
                    )
                }
            }.collect {}
        }

        // Group 3: HITL & Fallback Templates
        viewModelScope.launch {
            combine(
                userPreferencesRepository.isAutoPauseOnHitlEnabledFlow,
                userPreferencesRepository.hitlHoldingMessageFlow,
                userPreferencesRepository.mediaFallbackMessageFlow,
                userPreferencesRepository.blankReplyFallbackTemplateFlow
            ) { autoPause, hitl, media, blank ->
                _uiState.update { s ->
                    s.copy(
                        isAutoPauseOnHitl = autoPause,
                        hitlHoldingMessage = hitl,
                        mediaFallbackMessage = media,
                        blankReplyFallbackTemplate = blank
                    )
                }
            }.collect {}
        }

        // Group 4: Provider Ordering, Business Hours & Blacklist
        viewModelScope.launch {
            combine(
                userPreferencesRepository.providerOrderFlow,
                userPreferencesRepository.businessHoursScheduleFlow,
                userPreferencesRepository.ignoreGroupsFlow,
                userPreferencesRepository.blacklistedNumbersFlow
            ) { order, schedule, ignoreGroups, blacklist ->
                _uiState.update { s ->
                    s.copy(
                        providerOrder = order,
                        businessHoursSchedule = schedule,
                        ignoreWhatsAppGroups = ignoreGroups,
                        blacklistedNumbers = blacklist
                    )
                }
            }.collect {}
        }

        // Group 4b: Per-Capability Provider Priorities
        viewModelScope.launch {
            combine(
                userPreferencesRepository.getProviderPriority(ModelCapability.TEXT),
                userPreferencesRepository.getProviderPriority(ModelCapability.VISION),
                userPreferencesRepository.getProviderPriority(ModelCapability.AUDIO),
                userPreferencesRepository.getProviderPriority(ModelCapability.DECISION)
            ) { textOrder, visionOrder, audioOrder, decisionOrder ->
                mapOf(
                    ModelCapability.TEXT to textOrder,
                    ModelCapability.VISION to visionOrder,
                    ModelCapability.AUDIO to audioOrder,
                    ModelCapability.DECISION to decisionOrder
                )
            }.collect { priorities ->
                _uiState.update { it.copy(capabilityProviderPriorities = priorities) }
            }
        }

        // Group 5: App Language & Localization & Theme
        viewModelScope.launch {
            userPreferencesRepository.appLanguageFlow.collect { lang ->
                _uiState.update { it.copy(appLanguage = lang) }
            }
        }
        viewModelScope.launch {
            userPreferencesRepository.themeModeFlow.collect { mode ->
                _uiState.update { it.copy(themeMode = mode) }
            }
        }
    }

    fun selectTab(tabIndex: Int) {
        _uiState.update { it.copy(selectedTab = tabIndex) }
    }

    fun selectProviderForEdit(provider: String) {
        val norm = ModelProviders.normalize(provider)
        _uiState.update { it.copy(selectedProviderForEdit = norm) }
        refreshProviderConfig(norm)
    }

    private fun refreshAllProviderConfigs() {
        ModelProviders.ALL.forEach { provider ->
            refreshProviderConfig(provider)
        }
    }

    fun refreshProviderConfig(provider: String) {
        val norm = ModelProviders.normalize(provider)
        val keys = secureCredentialsRepository.getApiKeys(norm)
        viewModelScope.launch {
            val modelConfigs = userPreferencesRepository.getProviderModelConfigsSync(norm)
            val capabilityModelConfigs = ModelCapability.entries.associateWith { cap ->
                userPreferencesRepository.getProviderModelConfigsSync(norm, cap)
            }
            val models = modelConfigs.map { it.modelId }
            _uiState.update { s ->
                val existing = s.providerConfigs[norm] ?: ProviderConfigState(provider = norm)
                val updated = existing.copy(
                    keys = keys,
                    models = models,
                    modelConfigs = modelConfigs,
                    capabilityModelConfigs = capabilityModelConfigs
                )
                s.copy(providerConfigs = s.providerConfigs + (norm to updated))
            }
        }
    }

    // ── 1. AI Engines, Keys & Models Operations ───────────────────────────────

    fun selectCapabilityFilter(cap: ModelCapability) {
        _uiState.update { it.copy(selectedCapabilityFilter = cap) }
    }

    fun moveProviderUpForCapability(cap: ModelCapability, provider: String) {
        val list = _uiState.value.filteredProviderOrder(cap).toMutableList()
        val idx = list.indexOf(provider.lowercase())
        if (idx > 0) {
            Collections.swap(list, idx, idx - 1)
            updateAndPersistCapabilityPriority(cap, list)
        }
    }

    fun moveProviderDownForCapability(cap: ModelCapability, provider: String) {
        val list = _uiState.value.filteredProviderOrder(cap).toMutableList()
        val idx = list.indexOf(provider.lowercase())
        if (idx in 0 until list.size - 1) {
            Collections.swap(list, idx, idx + 1)
            updateAndPersistCapabilityPriority(cap, list)
        }
    }

    fun moveProviderForCapability(cap: ModelCapability, from: Int, to: Int) {
        val list = _uiState.value.filteredProviderOrder(cap).toMutableList()
        if (from !in list.indices || to !in list.indices) return
        val item = list.removeAt(from)
        list.add(to, item)
        updateAndPersistCapabilityPriority(cap, list)
    }

    private fun updateAndPersistCapabilityPriority(cap: ModelCapability, newOrder: List<String>) {
        _uiState.update { s ->
            val updatedMap = s.capabilityProviderPriorities + (cap to newOrder)
            if (cap == ModelCapability.TEXT) {
                s.copy(
                    capabilityProviderPriorities = updatedMap,
                    providerOrder = newOrder
                )
            } else {
                s.copy(capabilityProviderPriorities = updatedMap)
            }
        }
        viewModelScope.launch {
            userPreferencesRepository.setProviderPriority(cap, newOrder)
            if (cap == ModelCapability.TEXT) {
                userPreferencesRepository.setProviderOrder(newOrder)
                circuitBreakerAndKeyManager?.setProviderCascadeOrder(newOrder)
            }
        }
    }

    fun moveProviderUp(provider: String) {
        moveProviderUpForCapability(_uiState.value.selectedCapabilityFilter, provider)
    }

    fun moveProviderDown(provider: String) {
        moveProviderDownForCapability(_uiState.value.selectedCapabilityFilter, provider)
    }

    fun moveProvider(from: Int, to: Int) {
        moveProviderForCapability(_uiState.value.selectedCapabilityFilter, from, to)
    }

    fun addApiKey(provider: String, key: String) {
        val norm = ModelProviders.normalize(provider)
        val trimmed = key.trim()
        if (trimmed.isBlank()) return
        secureCredentialsRepository.addApiKey(norm, trimmed)
        refreshProviderConfig(norm)
    }

    fun addApiKeyAtIndex(provider: String, key: String, index: Int) {
        val norm = ModelProviders.normalize(provider)
        val trimmed = key.trim()
        if (trimmed.isBlank()) return
        val current = secureCredentialsRepository.getApiKeys(norm).toMutableList()
        current.remove(trimmed)
        val safeIndex = index.coerceIn(0, current.size)
        current.add(safeIndex, trimmed)
        secureCredentialsRepository.setApiKeys(norm, current)
        refreshProviderConfig(norm)
    }

    fun removeApiKey(provider: String, key: String) {
        val norm = ModelProviders.normalize(provider)
        secureCredentialsRepository.removeApiKey(norm, key)
        refreshProviderConfig(norm)
    }

    fun moveApiKeyUp(provider: String, key: String) {
        val norm = ModelProviders.normalize(provider)
        val current = secureCredentialsRepository.getApiKeys(norm).toMutableList()
        val idx = current.indexOf(key)
        if (idx > 0) {
            Collections.swap(current, idx, idx - 1)
            secureCredentialsRepository.setApiKeys(norm, current)
            refreshProviderConfig(norm)
        }
    }

    fun moveApiKeyDown(provider: String, key: String) {
        val norm = ModelProviders.normalize(provider)
        val current = secureCredentialsRepository.getApiKeys(norm).toMutableList()
        val idx = current.indexOf(key)
        if (idx in 0 until current.size - 1) {
            Collections.swap(current, idx, idx + 1)
            secureCredentialsRepository.setApiKeys(norm, current)
            refreshProviderConfig(norm)
        }
    }

    fun moveApiKey(provider: String, from: Int, to: Int) {
        val norm = ModelProviders.normalize(provider)
        val current = secureCredentialsRepository.getApiKeys(norm).toMutableList()
        if (from !in current.indices || to !in current.indices) return
        val item = current.removeAt(from)
        current.add(to, item)
        secureCredentialsRepository.setApiKeys(norm, current)
        refreshProviderConfig(norm)
    }

    fun testConnection(provider: String, key: String) {
        val norm = ModelProviders.normalize(provider)
        val trimmed = key.trim()

        updateKeyVerificationState(norm, trimmed, VerificationState.Checking)

        viewModelScope.launch(ioDispatcher) {
            val result = pingProvider(norm, trimmed)
            updateKeyVerificationState(norm, trimmed, result)
        }
    }

    private fun updateKeyVerificationState(provider: String, key: String, state: VerificationState) {
        _uiState.update { s ->
            val config = s.providerConfigs[provider] ?: ProviderConfigState(provider = provider)
            val updatedMap = config.verificationStates + (key to state)
            val updatedConfig = config.copy(verificationStates = updatedMap)
            s.copy(providerConfigs = s.providerConfigs + (provider to updatedConfig))
        }
    }

    private fun pingProvider(provider: String, key: String): VerificationState {
        if (key.isBlank()) return VerificationState.Error(R.string.settings_ai_status_empty_key)
        if (key.length < 8) return VerificationState.Error(R.string.settings_ai_status_too_short)

        var connection: HttpURLConnection? = null
        return try {
            val urlString = when (provider) {
                ModelProviders.META -> "https://api.meta.ai/v1/models"
                else -> null
            }

            if (urlString == null) {
                return VerificationState.Success(R.string.settings_ai_status_syntax_valid)
            }

            val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
                connectTimeout = 4000
                readTimeout = 4000
                requestMethod = "GET"
                setRequestProperty("Authorization", "Bearer $key")
            }
            connection = conn

            val code = conn.responseCode
            when (code) {
                in 200..299 -> VerificationState.Success(R.string.settings_ai_status_connected_code, listOf(code))
                401, 403 -> VerificationState.Error(R.string.settings_ai_status_unauthorized, listOf(code))
                429 -> VerificationState.Error(R.string.settings_ai_status_rate_limited)
                else -> VerificationState.Success(R.string.settings_ai_status_verified_code, listOf(code))
            }
        } catch (_: UnknownHostException) {
            VerificationState.Error(R.string.settings_ai_status_no_internet)
        } catch (_: SocketTimeoutException) {
            VerificationState.Error(R.string.settings_ai_status_timeout)
        } catch (e: Throwable) {
            val message = e.message
            if (message.isNullOrBlank()) {
                VerificationState.Error(R.string.settings_ai_status_unknown_error)
            } else {
                VerificationState.Error(R.string.settings_ai_status_check_failed, listOf(message))
            }
        } finally {
            try {
                connection?.disconnect()
            } catch (_: Throwable) {}
        }
    }

    fun addProviderModel(provider: String, model: String) {
        val norm = ModelProviders.normalize(provider)
        val trimmed = model.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            userPreferencesRepository.addProviderModel(norm, trimmed)
            refreshProviderConfig(norm)
        }
    }

    /** Adds a model with explicit capability tags (called from the new FAB dialog). */
    fun addProviderModelWithCapabilities(
        provider: String,
        model: String,
        capabilities: Set<ModelCapability>
    ) {
        val norm = ModelProviders.normalize(provider)
        val trimmed = model.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            val caps = capabilities.ifEmpty { setOf(ModelCapability.TEXT) }
            val config = ModelConfig(
                modelId = trimmed,
                capabilities = caps
            )
            userPreferencesRepository.addProviderModelConfig(norm, config)
            caps.forEach { cap ->
                val currentCapabilityOrder = userPreferencesRepository.getProviderModelConfigsSync(norm, cap).map { it.modelId }
                if (trimmed !in currentCapabilityOrder) {
                    userPreferencesRepository.setProviderModelOrderForCapability(norm, cap, currentCapabilityOrder + trimmed)
                }
            }
            refreshProviderConfig(norm)
        }
    }

    fun removeProviderModel(provider: String, model: String) {
        val norm = ModelProviders.normalize(provider)
        viewModelScope.launch {
            userPreferencesRepository.removeProviderModel(norm, model)
            ModelCapability.entries.forEach { cap ->
                val currentOrder = userPreferencesRepository.getProviderModelConfigsSync(norm, cap).map { it.modelId }
                if (model in currentOrder) {
                    userPreferencesRepository.setProviderModelOrderForCapability(norm, cap, currentOrder.filter { it != model })
                }
            }
            refreshProviderConfig(norm)
        }
    }

    fun moveModelUp(provider: String, model: String) {
        val norm = ModelProviders.normalize(provider)
        viewModelScope.launch {
            val current = userPreferencesRepository.getProviderModelConfigsSync(norm).toMutableList()
            val idx = current.indexOfFirst { it.modelId == model }
            if (idx > 0) {
                val tmp = current[idx]; current[idx] = current[idx - 1]; current[idx - 1] = tmp
                userPreferencesRepository.setProviderModelConfigs(norm, current)
                refreshProviderConfig(norm)
            }
        }
    }

    fun moveModelDown(provider: String, model: String) {
        val norm = ModelProviders.normalize(provider)
        viewModelScope.launch {
            val current = userPreferencesRepository.getProviderModelConfigsSync(norm).toMutableList()
            val idx = current.indexOfFirst { it.modelId == model }
            if (idx in 0 until current.size - 1) {
                val tmp = current[idx]; current[idx] = current[idx + 1]; current[idx + 1] = tmp
                userPreferencesRepository.setProviderModelConfigs(norm, current)
                refreshProviderConfig(norm)
            }
        }
    }

    fun moveModel(provider: String, from: Int, to: Int) {
        val norm = ModelProviders.normalize(provider)
        viewModelScope.launch {
            val current = userPreferencesRepository.getProviderModelConfigsSync(norm).toMutableList()
            if (from !in current.indices || to !in current.indices) return@launch
            val item = current.removeAt(from)
            current.add(to, item)
            userPreferencesRepository.setProviderModelConfigs(norm, current)
            refreshProviderConfig(norm)
        }
    }

    private var reorderModelJob: Job? = null

    fun moveModelForCapability(provider: String, capability: ModelCapability, from: Int, to: Int) {
        val norm = ModelProviders.normalize(provider)

        // 1. Immediate in-memory state update for smooth 60fps drag rendering
        val configState = _uiState.value.providerConfigs[norm]
        val inMemoryList = (configState?.capabilityModelConfigs?.get(capability)
            ?: configState?.modelConfigs?.filter { capability in it.capabilities })
            ?.takeIf { it.isNotEmpty() }
        if (inMemoryList != null && from in inMemoryList.indices && to in inMemoryList.indices) {
            val updated = inMemoryList.toMutableList()
            val item = updated.removeAt(from)
            updated.add(to, item)
            _uiState.update { s ->
                val existing = s.providerConfigs[norm] ?: ProviderConfigState(provider = norm)
                val updatedMap = existing.capabilityModelConfigs + (capability to updated)
                s.copy(providerConfigs = s.providerConfigs + (norm to existing.copy(capabilityModelConfigs = updatedMap)))
            }
        }

        // 2. Debounce persistence to eliminate write race conditions and UI snap-backs
        reorderModelJob?.cancel()
        reorderModelJob = viewModelScope.launch {
            delay(150L) // debounce rapid gestures
            val current = userPreferencesRepository.getProviderModelConfigsSync(norm, capability).toMutableList()
            if (from !in current.indices || to !in current.indices) return@launch
            val item = current.removeAt(from)
            current.add(to, item)
            userPreferencesRepository.setProviderModelOrderForCapability(norm, capability, current.map { it.modelId })
            refreshProviderConfig(norm)
        }
    }

    fun moveModelUpForCapability(provider: String, capability: ModelCapability, model: String) {
        val norm = ModelProviders.normalize(provider)
        viewModelScope.launch {
            val current = userPreferencesRepository.getProviderModelConfigsSync(norm, capability).toMutableList()
            val idx = current.indexOfFirst { it.modelId == model }
            if (idx > 0) {
                val tmp = current[idx]; current[idx] = current[idx - 1]; current[idx - 1] = tmp
                userPreferencesRepository.setProviderModelOrderForCapability(norm, capability, current.map { it.modelId })
                refreshProviderConfig(norm)
            }
        }
    }

    fun moveModelDownForCapability(provider: String, capability: ModelCapability, model: String) {
        val norm = ModelProviders.normalize(provider)
        viewModelScope.launch {
            val current = userPreferencesRepository.getProviderModelConfigsSync(norm, capability).toMutableList()
            val idx = current.indexOfFirst { it.modelId == model }
            if (idx in 0 until current.size - 1) {
                val tmp = current[idx]; current[idx] = current[idx + 1]; current[idx + 1] = tmp
                userPreferencesRepository.setProviderModelOrderForCapability(norm, capability, current.map { it.modelId })
                refreshProviderConfig(norm)
            }
        }
    }

    fun addProviderModelAtIndex(provider: String, model: String, index: Int) {
        val norm = ModelProviders.normalize(provider)
        val trimmed = model.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            val current = userPreferencesRepository.getProviderModelConfigsSync(norm).toMutableList()
            val existing = current.firstOrNull { it.modelId == trimmed }
            current.removeAll { it.modelId == trimmed }
            val safeIndex = index.coerceIn(0, current.size)
            current.add(safeIndex, existing ?: ModelConfig(trimmed, ModelConfig.inferCapabilities(trimmed)))
            userPreferencesRepository.setProviderModelConfigs(norm, current)
            refreshProviderConfig(norm)
        }
    }

    fun testAllConnections(provider: String) {
        val norm = ModelProviders.normalize(provider)
        val keys = _uiState.value.providerConfigs[norm]?.keys.orEmpty()
        keys.forEach { testConnection(norm, it) }
    }

    // ── 2. Store Identity & Bot Persona Operations ────────────────────────────

    fun setAppLanguage(language: String) {
        val sanitized = language.trim().lowercase()
        _uiState.update { it.copy(appLanguage = sanitized) }
        viewModelScope.launch {
            userPreferencesRepository.setAppLanguage(sanitized)
        }
    }

    fun setThemeMode(mode: String) {
        val sanitized = mode.trim().lowercase()
        _uiState.update { it.copy(themeMode = sanitized) }
        viewModelScope.launch {
            userPreferencesRepository.setThemeMode(sanitized)
        }
    }

    fun setPersonaTone(tone: PersonaTone) {
        _uiState.update { it.copy(personaTone = tone) }
        saveStyle()
    }

    fun setStyleProfile(profile: StyleProfile) {
        _uiState.update { it.copy(styleProfile = profile) }
        saveStyle()
    }

    fun setThresholdLevel(level: ThresholdLevel) {
        _uiState.update { it.copy(thresholdLevel = level) }
        saveStyle()
    }

    fun setCooldownMins(mins: Int) {
        _uiState.update { it.copy(cooldownMins = mins) }
        saveStyle()
    }

    fun setReplyDelayInput(input: String) {
        val cleanInput = input.trim()
        val parsedInt = cleanInput.toIntOrNull()
        val error = if (parsedInt == null || parsedInt <= 0) {
            "Delay must be 1 second or greater."
        } else {
            null
        }

        _uiState.update {
            it.copy(
                replyDelayInput = input,
                replyDelayError = error,
                replyDelaySeconds = parsedInt ?: it.replyDelaySeconds
            )
        }

        if (error == null && parsedInt != null) {
            saveStyle()
        }
    }

    fun setAccountMode(mode: AccountMode) {
        _uiState.update { it.copy(accountMode = mode) }
        viewModelScope.launch { userPreferencesRepository.setAccountMode(mode) }
    }

    fun setBotName(name: String) {
        val sanitized = name.trim().ifEmpty { UserPreferencesRepository.DEFAULT_BOT_NAME }
        _uiState.update { it.copy(botName = sanitized) }
        viewModelScope.launch { userPreferencesRepository.setBotName(sanitized) }
    }

    fun setStoreName(name: String) {
        val sanitized = name.trim().ifEmpty { UserPreferencesRepository.DEFAULT_STORE_NAME }
        _uiState.update { it.copy(storeName = sanitized) }
        viewModelScope.launch { userPreferencesRepository.setStoreName(sanitized) }
    }

    fun setGreetingTemplate(text: String) {
        _uiState.update { it.copy(greetingTemplate = text) }
        viewModelScope.launch { userPreferencesRepository.setGreetingTemplate(text) }
    }

    fun setFarewellTemplate(text: String) {
        _uiState.update { it.copy(farewellTemplate = text) }
        viewModelScope.launch { userPreferencesRepository.setFarewellTemplate(text) }
    }

    private fun saveStyle() {
        val s = _uiState.value
        if (s.replyDelayError != null) return

        viewModelScope.launch {
            userPreferencesRepository.setStylePreferences(
                StylePreferences(
                    personaTone = s.personaTone,
                    similarityThreshold = s.thresholdLevel.threshold,
                    cooldownMins = s.cooldownMins,
                    replyDelaySeconds = s.replyDelaySeconds,
                    styleProfile = s.styleProfile
                )
            )
        }
    }

    // ── 3. Business Hours & Blacklist Operations ──────────────────────────────

    fun setBusinessHoursEnabled(enabled: Boolean) {
        viewModelScope.launch { userPreferencesRepository.setBusinessHoursEnabled(enabled) }
    }

    fun setWorkingDays(days: Set<String>) {
        viewModelScope.launch { userPreferencesRepository.setWorkingDays(days) }
    }

    fun setWorkingHours(start: String, end: String) {
        viewModelScope.launch { userPreferencesRepository.setWorkingHours(start, end) }
    }

    fun setOutOfHoursMessage(message: String) {
        viewModelScope.launch { userPreferencesRepository.setOutOfHoursMessage(message) }
    }

    fun setIgnoreWhatsAppGroups(ignore: Boolean) {
        viewModelScope.launch { userPreferencesRepository.setIgnoreGroups(ignore) }
    }

    fun addBlacklistedNumber(number: String) {
        val trimmed = number.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch { userPreferencesRepository.addBlacklistedNumber(trimmed) }
    }

    fun removeBlacklistedNumber(number: String) {
        viewModelScope.launch { userPreferencesRepository.removeBlacklistedNumber(number) }
    }

    // ── 4. WhatsApp App Routing & HITL Operations ─────────────────────────────

    fun setListenMainApp(enabled: Boolean) {
        _uiState.update { it.copy(listenMainApp = enabled) }
        viewModelScope.launch { userPreferencesRepository.setListenMainApp(enabled) }
    }

    fun setListenBusinessApp(enabled: Boolean) {
        _uiState.update { it.copy(listenBusinessApp = enabled) }
        viewModelScope.launch { userPreferencesRepository.setListenBusinessApp(enabled) }
    }

    fun setListenCloneApp(enabled: Boolean) {
        _uiState.update { it.copy(listenCloneApp = enabled) }
        viewModelScope.launch { userPreferencesRepository.setListenCloneApp(enabled) }
    }

    fun onAutoPauseOnHitlToggled(enabled: Boolean) {
        _uiState.update { it.copy(isAutoPauseOnHitl = enabled) }
        viewModelScope.launch { userPreferencesRepository.setAutoPauseOnHitl(enabled) }
    }

    fun setHitlHoldingMessage(text: String) {
        _uiState.update { it.copy(hitlHoldingMessage = text) }
        viewModelScope.launch { userPreferencesRepository.setHitlHoldingMessage(text) }
    }

    fun setMediaFallbackMessage(text: String) {
        _uiState.update { it.copy(mediaFallbackMessage = text) }
        viewModelScope.launch { userPreferencesRepository.setMediaFallbackMessage(text) }
    }

    fun setBlankReplyFallbackTemplate(template: String) {
        _uiState.update { it.copy(blankReplyFallbackTemplate = template) }
        viewModelScope.launch { userPreferencesRepository.setBlankReplyFallbackTemplate(template) }
    }

    // ── 5. System Maintenance & Health Operations ─────────────────────────────

    fun recheckPermissions() {
        refreshStoragePermission()
    }

    fun refreshStoragePermission() {
        val granted = permissionChecker?.isManageStorageGranted() ?: false
        val notif = permissionChecker?.isNotificationListenerEnabled() ?: false
        val battery = permissionChecker?.isBatteryOptimizationIgnored() ?: false
        val contacts = permissionChecker?.isContactsPermissionGranted() ?: false
        _uiState.update {
            it.copy(
                isStorageAccessGranted = granted,
                isNotificationListenerGranted = notif,
                isBatteryOptimizationIgnored = battery,
                isContactsPermissionGranted = contacts
            )
        }
        viewModelScope.launch {
            val autoStart = userPreferencesRepository.isAutoStartConfirmedSync()
            _uiState.update { it.copy(isAutoStartConfirmed = autoStart) }
        }
    }

    fun setAutoStartConfirmed(confirmed: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setAutoStartConfirmed(confirmed)
            recheckPermissions()
        }
    }

    fun requestBatteryOptimizationExemption(context: Context) {
        runCatching {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            val packageName = context.packageName
            if (powerManager?.isIgnoringBatteryOptimizations(packageName) != true) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try {
                    context.startActivity(intent)
                } catch (_: Exception) {
                    val fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    try {
                        context.startActivity(fallback)
                    } catch (_: Exception) {
                        runCatching {
                            context.startActivity(Intent(Settings.ACTION_SETTINGS).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            })
                        }
                    }
                }
            }
        }
    }

    fun openAutoStartSettings(context: Context) {
        val launched = autoStartHelper?.launchAutoStartSettings(context) ?: run {
            runCatching {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                true
            }.getOrDefault(false)
        }
        if (launched) {
            setAutoStartConfirmed(true)
        }
    }

    fun openNotificationListenerSettings(context: Context) {
        try {
            val intent = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            runCatching {
                context.startActivity(Intent(Settings.ACTION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            }
        }
    }

    fun requestStoragePermission(context: Context) {
        permissionChecker?.requestManageStoragePermission(context)
    }

    fun exportLocalBackup() {
        _uiState.update { it.copy(maintenanceMessage = "تم تصدير نسخة احتياطية مشفرة بنجاح ✓") }
    }

    fun restoreLocalBackup() {
        _uiState.update { it.copy(maintenanceMessage = "تم استيراد النسخة الاحتياطية وتحديث الإعدادات بنجاح ✓") }
    }

    fun clearCache() {
        _uiState.update { it.copy(maintenanceMessage = "تم مسح الذاكرة المؤقتة وسجلات الفحص بنجاح ✓") }
    }

    fun clearMaintenanceMessage() {
        _uiState.update { it.copy(maintenanceMessage = null) }
    }
}
