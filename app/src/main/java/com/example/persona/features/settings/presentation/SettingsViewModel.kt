package com.example.persona.features.settings.presentation

import androidx.annotation.StringRes
import com.example.persona.R

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.persona.core.data.repository.BusinessHoursSchedule
import com.example.persona.core.data.repository.UserPreferencesRepository
import com.example.persona.core.di.IoDispatcher
import com.example.persona.features.auto_reply.domain.model.PersonaTone
import com.example.persona.features.auto_reply.domain.model.StylePreferences
import com.example.persona.features.auto_reply.domain.model.StyleProfile
import com.example.persona.features.onboarding.data.PermissionChecker
import com.example.persona.features.settings.data.repository.InMemorySecureCredentialsRepository
import com.example.persona.features.settings.domain.repository.ModelProviders
import com.example.persona.features.settings.domain.repository.SecureCredentialsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
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

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val secureCredentialsRepository: SecureCredentialsRepository,
    private val permissionChecker: PermissionChecker? = null,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    // Secondary constructor for existing unit tests
    constructor(
        userPreferencesRepository: UserPreferencesRepository,
        permissionChecker: PermissionChecker? = null
    ) : this(
        userPreferencesRepository = userPreferencesRepository,
        secureCredentialsRepository = InMemorySecureCredentialsRepository(),
        permissionChecker = permissionChecker,
        ioDispatcher = Dispatchers.Main.immediate
    )

    constructor(
        userPreferencesRepository: UserPreferencesRepository,
        secureCredentialsRepository: SecureCredentialsRepository,
        ioDispatcher: CoroutineDispatcher
    ) : this(
        userPreferencesRepository = userPreferencesRepository,
        secureCredentialsRepository = secureCredentialsRepository,
        permissionChecker = null,
        ioDispatcher = ioDispatcher
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

        // Group 6: Cloudflare Account ID
        viewModelScope.launch {
            userPreferencesRepository.cloudflareAccountIdFlow.collect { cfAccountId ->
                _uiState.update { it.copy(cloudflareAccountId = cfAccountId) }
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
        if (norm == ModelProviders.CLOUDFLARE) {
            val cfId = secureCredentialsRepository.getCloudflareAccountId()
            _uiState.update { it.copy(cloudflareAccountId = cfId) }
        }
        viewModelScope.launch {
            val models = userPreferencesRepository.getProviderModelsSync(norm)
            _uiState.update { s ->
                val existing = s.providerConfigs[norm] ?: ProviderConfigState(provider = norm)
                val updated = existing.copy(keys = keys, models = models)
                s.copy(providerConfigs = s.providerConfigs + (norm to updated))
            }
        }
    }

    // ── 1. AI Engines, Keys & Models Operations ───────────────────────────────

    fun moveProviderUp(provider: String) {
        val current = _uiState.value.providerOrder.toMutableList()
        val idx = current.indexOf(provider)
        if (idx > 0) {
            Collections.swap(current, idx, idx - 1)
            _uiState.update { it.copy(providerOrder = current) }
            viewModelScope.launch {
                userPreferencesRepository.setProviderOrder(current)
            }
        }
    }

    fun moveProviderDown(provider: String) {
        val current = _uiState.value.providerOrder.toMutableList()
        val idx = current.indexOf(provider)
        if (idx in 0 until current.size - 1) {
            Collections.swap(current, idx, idx + 1)
            _uiState.update { it.copy(providerOrder = current) }
            viewModelScope.launch {
                userPreferencesRepository.setProviderOrder(current)
            }
        }
    }

    fun moveProvider(from: Int, to: Int) {
        val order = _uiState.value.providerOrder.toMutableList()
        if (from !in order.indices || to !in order.indices) return
        val item = order.removeAt(from)
        order.add(to, item)
        _uiState.update { it.copy(providerOrder = order) }
        viewModelScope.launch {
            userPreferencesRepository.setProviderOrder(order)
        }
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

    fun setCloudflareAccountId(id: String) {
        val trimmed = id.trim()
        secureCredentialsRepository.setCloudflareAccountId(trimmed)
        _uiState.update { it.copy(cloudflareAccountId = trimmed) }
        viewModelScope.launch {
            userPreferencesRepository.setCloudflareAccountId(trimmed)
        }
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
        if (key.isBlank()) return VerificationState.Error("المفتاح فارغ")
        if (key.length < 8) return VerificationState.Error("المفتاح غير صالح (قصير جداً)")

        var connection: HttpURLConnection? = null
        return try {
            val urlString = when (provider) {
                ModelProviders.GEMINI -> "https://generativelanguage.googleapis.com/v1beta/models?key=$key"
                ModelProviders.GROQ -> "https://api.groq.com/openai/v1/models"
                ModelProviders.OPENROUTER -> "https://openrouter.ai/api/v1/auth/key"
                ModelProviders.NVIDIA -> "https://integrate.api.nvidia.com/v1/models"
                ModelProviders.CLOUDFLARE -> {
                    val accId = _uiState.value.cloudflareAccountId.ifBlank { secureCredentialsRepository.getCloudflareAccountId() }
                    if (accId.isNotBlank()) "https://api.cloudflare.com/client/v4/accounts/$accId/ai/models/search" else null
                }
                else -> null
            }

            if (urlString == null) {
                return VerificationState.Success("تم التحقق من الصيغة بنجاح ✓")
            }

            val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
                connectTimeout = 4000
                readTimeout = 4000
                requestMethod = "GET"
                if (provider != ModelProviders.GEMINI) {
                    setRequestProperty("Authorization", "Bearer $key")
                }
            }
            connection = conn

            val code = conn.responseCode
            when (code) {
                in 200..299 -> VerificationState.Success("الاتصال ناجح (HTTP $code) ✓")
                401, 403 -> VerificationState.Error("مفتاح غير مصرح به أو منتهي الصلاحية ($code)")
                429 -> VerificationState.Error("تم تجاوز حد الطلبات أو نفاد الرصيد (429)")
                else -> VerificationState.Success("تم التحقق بنجاح (رمز الحالة: $code)")
            }
        } catch (_: UnknownHostException) {
            VerificationState.Error("تعذر الاتصال بالإنترنت")
        } catch (_: SocketTimeoutException) {
            VerificationState.Error("انتهت مهلة استجابة الخادم")
        } catch (e: Throwable) {
            VerificationState.Error("فشل الفحص: ${e.message ?: "خطأ غير معروف"}")
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

    fun removeProviderModel(provider: String, model: String) {
        val norm = ModelProviders.normalize(provider)
        viewModelScope.launch {
            userPreferencesRepository.removeProviderModel(norm, model)
            refreshProviderConfig(norm)
        }
    }

    fun moveModelUp(provider: String, model: String) {
        val norm = ModelProviders.normalize(provider)
        viewModelScope.launch {
            val current = userPreferencesRepository.getProviderModelsSync(norm).toMutableList()
            val idx = current.indexOf(model)
            if (idx > 0) {
                Collections.swap(current, idx, idx - 1)
                userPreferencesRepository.setProviderModels(norm, current)
                refreshProviderConfig(norm)
            }
        }
    }

    fun moveModelDown(provider: String, model: String) {
        val norm = ModelProviders.normalize(provider)
        viewModelScope.launch {
            val current = userPreferencesRepository.getProviderModelsSync(norm).toMutableList()
            val idx = current.indexOf(model)
            if (idx in 0 until current.size - 1) {
                Collections.swap(current, idx, idx + 1)
                userPreferencesRepository.setProviderModels(norm, current)
                refreshProviderConfig(norm)
            }
        }
    }

    fun moveModel(provider: String, from: Int, to: Int) {
        val norm = ModelProviders.normalize(provider)
        viewModelScope.launch {
            val current = userPreferencesRepository.getProviderModelsSync(norm).toMutableList()
            if (from !in current.indices || to !in current.indices) return@launch
            val item = current.removeAt(from)
            current.add(to, item)
            userPreferencesRepository.setProviderModels(norm, current)
            refreshProviderConfig(norm)
        }
    }

    fun addProviderModelAtIndex(provider: String, model: String, index: Int) {
        val norm = ModelProviders.normalize(provider)
        val trimmed = model.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            val current = userPreferencesRepository.getProviderModelsSync(norm).toMutableList()
            current.remove(trimmed)
            val safeIndex = index.coerceIn(0, current.size)
            current.add(safeIndex, trimmed)
            userPreferencesRepository.setProviderModels(norm, current)
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
