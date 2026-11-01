package com.example.persona.features.settings.presentation

import androidx.annotation.StringRes
import com.example.persona.R
import com.example.persona.core.data.repository.BusinessHoursSchedule
import com.example.persona.core.data.repository.UserPreferencesRepository
import com.example.persona.features.auto_reply.domain.model.PersonaTone
import com.example.persona.features.auto_reply.domain.model.StyleProfile
import com.example.persona.features.settings.domain.repository.ModelCapability
import com.example.persona.features.settings.domain.repository.ModelConfig
import com.example.persona.features.settings.domain.repository.ModelProviders

enum class ThresholdLevel(
    val displayName: String,
    val threshold: Float,
    @StringRes val labelResId: Int = R.string.settings_threshold_balanced,
    @StringRes val descResId: Int = R.string.settings_threshold_balanced_desc
) {
    CONSERVATIVE("Conservative", 0.85f, R.string.settings_threshold_conservative, R.string.settings_threshold_conservative_desc),
    BALANCED("Balanced", 0.75f, R.string.settings_threshold_balanced, R.string.settings_threshold_balanced_desc),
    AGGRESSIVE("Aggressive", 0.65f, R.string.settings_threshold_aggressive, R.string.settings_threshold_aggressive_desc);

    companion object {
        fun fromThreshold(value: Float): ThresholdLevel = when {
            value >= 0.80f -> CONSERVATIVE
            value >= 0.70f -> BALANCED
            else           -> AGGRESSIVE
        }
    }
}

sealed interface VerificationState {
    data object Idle : VerificationState
    data object Checking : VerificationState
    data class Success(val message: String = "الاتصال ناجح ✓") : VerificationState
    data class Error(val error: String) : VerificationState
}

data class ProviderConfigState(
    val provider: String = "",
    val keys: List<String> = emptyList(),
    val models: List<String> = emptyList(),
    /** Capability-tagged model configs. Parallel to [models] list. */
    val modelConfigs: List<ModelConfig> = emptyList(),
    /** Per-capability ordered model configs. */
    val capabilityModelConfigs: Map<ModelCapability, List<ModelConfig>> = emptyMap(),
    val verificationStates: Map<String, VerificationState> = emptyMap()
) {
    val verification: Map<String, VerificationState> get() = verificationStates

    /** Returns models that have a given capability, ordered by that capability's priority. */
    fun modelsWithCapability(cap: ModelCapability): List<String> =
        (capabilityModelConfigs[cap] ?: modelConfigs.filter { cap in it.capabilities }).map { it.modelId }

    fun modelConfigsWithCapability(cap: ModelCapability): List<ModelConfig> =
        capabilityModelConfigs[cap] ?: modelConfigs.filter { cap in it.capabilities }
}

data class SettingsUiState(
    // ── Tab selection (kept for backward compatibility with unit tests)
    val selectedTab: Int = 0,

    // ── App Language & Locale ("ar" or "en")
    val appLanguage: String = UserPreferencesRepository.DEFAULT_APP_LANGUAGE,

    // ── Theme Mode ("dark", "light", "system")
    val themeMode: String = UserPreferencesRepository.DEFAULT_THEME_MODE,

    // ── 1. AI Engines, Keys & Models
    val providerOrder: List<String> = UserPreferencesRepository.DEFAULT_PROVIDER_ORDER,
    val selectedCapabilityFilter: ModelCapability = ModelCapability.TEXT,
    val capabilityProviderPriorities: Map<ModelCapability, List<String>> = emptyMap(),
    val providerConfigs: Map<String, ProviderConfigState> = emptyMap(),
    val selectedProviderForEdit: String = ModelProviders.OPENROUTER,
    val cloudflareAccountId: String = "",

    // ── 2. Store Identity & Bot Persona
    val botName: String = UserPreferencesRepository.DEFAULT_BOT_NAME,
    val storeName: String = UserPreferencesRepository.DEFAULT_STORE_NAME,
    val personaTone: PersonaTone = PersonaTone.WITTY_EGYPTIAN,
    val styleProfile: StyleProfile = StyleProfile.CASUAL,
    val thresholdLevel: ThresholdLevel = ThresholdLevel.BALANCED,
    val cooldownMins: Int = 5,
    val replyDelaySeconds: Int = 2,
    val replyDelayInput: String = "2",
    val replyDelayError: String? = null,
    val greetingTemplate: String = "",
    val farewellTemplate: String = "",

    // ── 3. Business Hours & Blacklist
    val businessHoursSchedule: BusinessHoursSchedule = BusinessHoursSchedule(),
    val ignoreWhatsAppGroups: Boolean = true,
    val blacklistedNumbers: Set<String> = emptySet(),

    // ── 4. WhatsApp App Routing & HITL
    val listenMainApp: Boolean = true,
    val listenBusinessApp: Boolean = true,
    val listenCloneApp: Boolean = false,
    val isAutoPauseOnHitl: Boolean = false,
    val hitlHoldingMessage: String = UserPreferencesRepository.DEFAULT_HITL_HOLDING_MESSAGE,
    val mediaFallbackMessage: String = UserPreferencesRepository.DEFAULT_MEDIA_FALLBACK_MESSAGE,
    val blankReplyFallbackTemplate: String = UserPreferencesRepository.DEFAULT_BLANK_REPLY_FALLBACK_TEMPLATE,

    // ── 5. System Maintenance & Health
    val isStorageAccessGranted: Boolean = false,
    val isNotificationListenerGranted: Boolean = false,
    val isBatteryOptimizationIgnored: Boolean = false,
    val isAutoStartConfirmed: Boolean = false,
    val isContactsPermissionGranted: Boolean = false,
    val maintenanceMessage: String? = null,

    val isLoading: Boolean = true
) {
    /**
     * Returns the prioritized list of providers supporting [cap], combining
     * default supported providers with any provider possessing custom models for [cap].
     */
    fun filteredProviderOrder(cap: ModelCapability): List<String> {
        val baseDefaults = ModelProviders.DEFAULT_CAPABILITY_PROVIDERS[cap] ?: emptyList()
        val customProviders = providerConfigs.filter { (_, cfg) ->
            cfg.modelConfigs.any { cap in it.capabilities }
        }.keys.map { it.lowercase() }
        val eligibleProviders = (baseDefaults + customProviders).distinct().toSet()
        val savedOrder = capabilityProviderPriorities[cap] ?: baseDefaults
        val ordered = savedOrder.filter { it in eligibleProviders }
        val missing = eligibleProviders.filter { it !in ordered }
        return ordered + missing
    }

    /** Active provider list filtered to currently selected capability. */
    val currentFilteredProviderOrder: List<String>
        get() = filteredProviderOrder(selectedCapabilityFilter)
}
