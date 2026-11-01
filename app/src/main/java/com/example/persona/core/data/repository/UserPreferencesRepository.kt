package com.example.persona.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.persona.features.auto_reply.domain.model.PersonaTone
import com.example.persona.features.auto_reply.domain.model.StylePreferences
import com.example.persona.features.auto_reply.domain.model.StyleProfile
import com.example.persona.features.settings.data.repository.InMemorySecureCredentialsRepository
import com.example.persona.features.settings.domain.repository.ModelCapability
import com.example.persona.features.settings.domain.repository.ModelConfig
import com.example.persona.features.settings.domain.repository.ModelProviders
import com.example.persona.features.settings.domain.repository.SecureCredentialsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Value object representing merchant's configured business hours and automated schedule.
 */
data class BusinessHoursSchedule(
    val isEnabled: Boolean = false,
    val workingDays: Set<String> = DEFAULT_WORKING_DAYS,
    val startTime: String = "10:00",
    val endTime: String = "23:00",
    val outOfHoursMessage: String = DEFAULT_OUT_OF_HOURS_MESSAGE
) {
    companion object {
        val DEFAULT_WORKING_DAYS = setOf(
            "SATURDAY", "SUNDAY", "MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY"
        )
        const val DEFAULT_OUT_OF_HOURS_MESSAGE =
            "أهلاً بك في متجرنا! مواعيد العمل الرسمية من 10:00 صباحاً إلى 11:00 مساءً. تم استلام رسالتك وسنرد عليك فور بدء ساعات العمل الرسمية."
    }
}

@Singleton
class UserPreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val secureCredentialsRepository: SecureCredentialsRepository
) {
    /**
     * Backward-compatible constructor for unit tests or legacy callers
     * providing only [DataStore]. Defaults to [InMemorySecureCredentialsRepository].
     */
    constructor(dataStore: DataStore<Preferences>) : this(
        dataStore = dataStore,
        secureCredentialsRepository = InMemorySecureCredentialsRepository()
    )

    companion object {
        // ── Core toggles ──────────────────────────────────────────────────────
        val KEY_AUTO_REPLY_ENABLED = booleanPreferencesKey("auto_reply_enabled")

        // ── Style & thresholds ────────────────────────────────────────────────
        val KEY_STYLE_PROFILE = stringPreferencesKey("style_profile")
        val KEY_PERSONA_TONE = KEY_STYLE_PROFILE
        val KEY_SIMILARITY_THRESHOLD = floatPreferencesKey("similarity_threshold")
        val KEY_COOLDOWN_MINS = intPreferencesKey("cooldown_mins")
        val KEY_REPLY_DELAY_SECONDS = intPreferencesKey("reply_delay_seconds")

        // ── App routing toggles ────────────────────────────────────────────────
        val KEY_LISTEN_MAIN_APP = booleanPreferencesKey("listen_main_app")
        val KEY_LISTEN_BUSINESS_APP = booleanPreferencesKey("listen_business_app")
        val KEY_LISTEN_CLONE_APP = booleanPreferencesKey("listen_clone_app")

        // ── Greeting / Farewell response templates ────────────────────────────
        val KEY_GREETING_TEMPLATE = stringPreferencesKey("greeting_template")
        val KEY_FAREWELL_TEMPLATE = stringPreferencesKey("farewell_template")

        // ── Bot & Store Brand Identity ────────────────────────────────────────
        val KEY_BOT_NAME = stringPreferencesKey("bot_name")
        val KEY_STORE_NAME = stringPreferencesKey("store_name")

        // ── Store Topics Index (hierarchical knowledge indexing) ──────────────
        private val STORE_KNOWLEDGE_TOPICS_KEY = stringPreferencesKey("store_knowledge_topics")
        val KEY_STORE_WEBSITE = stringPreferencesKey("store_website")
        private val STORE_WEBSITE_KEY = KEY_STORE_WEBSITE

        // ── Human Takeover & Safety ───────────────────────────────────────────
        val KEY_AUTO_PAUSE_ON_HITL = booleanPreferencesKey("auto_pause_on_hitl")
        private val AUTO_PAUSE_ON_HITL_KEY = KEY_AUTO_PAUSE_ON_HITL

        // ── System Fallback Messages ──────────────────────────────────────────
        val KEY_HITL_HOLDING_MESSAGE = stringPreferencesKey("hitl_holding_message")
        val KEY_MEDIA_FALLBACK_MESSAGE = stringPreferencesKey("media_fallback_message")
        val KEY_BLANK_REPLY_FALLBACK_TEMPLATE = stringPreferencesKey("blank_reply_fallback_template")

        // ── LLM Credentials Overrides (Legacy keys migrated to SecureCredentialsRepository) ─
        val KEY_NVIDIA_API_KEY = stringPreferencesKey("nvidia_api_key")
        val KEY_QWEN_API_KEY = stringPreferencesKey("qwen_api_key")

        // ── Provider Ordering & Model Management ──────────────────────────────
        val KEY_PROVIDER_ORDER = stringPreferencesKey("provider_order")
        private fun providerModelsKey(provider: String) =
            stringPreferencesKey("provider_models_${provider.trim().lowercase()}")

        // ── Auto-Start Onboarding Confirmation ─────────────────────────────────
        val KEY_AUTO_START_CONFIRMED = booleanPreferencesKey("is_auto_start_confirmed")

        // ── App Language & Locale ──────────────────────────────────────────────
        val KEY_APP_LANGUAGE = stringPreferencesKey("app_language")
        const val DEFAULT_APP_LANGUAGE = "ar"

        // ── Theme Mode ("dark", "light", "system") ──────────────────────────────
        val KEY_THEME_MODE = stringPreferencesKey("app_theme_mode")
        const val THEME_MODE_DARK = "dark"
        const val THEME_MODE_LIGHT = "light"
        const val THEME_MODE_SYSTEM = "system"
        const val DEFAULT_THEME_MODE = THEME_MODE_DARK

        // ── Business Hours & Blacklist ────────────────────────────────────────
        val KEY_BUSINESS_HOURS_ENABLED = booleanPreferencesKey("business_hours_enabled")
        val KEY_BUSINESS_HOURS_DAYS = stringPreferencesKey("business_hours_days")
        val KEY_BUSINESS_HOURS_START = stringPreferencesKey("business_hours_start")
        val KEY_BUSINESS_HOURS_END = stringPreferencesKey("business_hours_end")
        val KEY_BUSINESS_HOURS_OUT_OF_HOURS_MSG = stringPreferencesKey("business_hours_out_of_hours_msg")

        val KEY_IGNORE_GROUPS = booleanPreferencesKey("ignore_whatsapp_groups")
        val KEY_BLACKLISTED_NUMBERS = stringPreferencesKey("blacklisted_phone_numbers")

        const val DEFAULT_BOT_NAME = "مساعد المبيعات"
        const val DEFAULT_STORE_NAME = "متجرنا"

        const val DEFAULT_HITL_HOLDING_MESSAGE =
            "تمام يا فندم، ثواني وهيكون مع حضرتك ممثل خدمة العملاء فوراً لمساعدتك في أقرب وقت متاح! 🙏"
        const val DEFAULT_MEDIA_FALLBACK_MESSAGE =
            "عفواً، لا يمكنني معالجة الوسائط. يرجى كتابة استفسارك في رسالة نصية."
        const val DEFAULT_BLANK_REPLY_FALLBACK_TEMPLATE =
            "أنا معاك وسامعك كويس يا فندم، بس قولّي نقدر نجهزلك إيه النهارده من منتجاتنا في %s؟ 💙"

        val DEFAULT_PROVIDER_ORDER = listOf(
            ModelProviders.QWEN,
            ModelProviders.GEMINI,
            ModelProviders.GROQ,
            ModelProviders.NVIDIA,
            ModelProviders.OPENROUTER,
            ModelProviders.CLOUDFLARE,
            ModelProviders.LIQUID,
            ModelProviders.NARA
        )

        val DEFAULT_PROVIDER_MODELS = ModelProviders.DEFAULT_MODELS
    }

    // ── Flows ─────────────────────────────────────────────────────────────────

    val isAutoReplyEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_AUTO_REPLY_ENABLED] ?: false
    }

    val appLanguageFlow: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_APP_LANGUAGE]?.takeIf { it.isNotBlank() } ?: DEFAULT_APP_LANGUAGE
    }

    val themeModeFlow: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_THEME_MODE]?.takeIf { it.isNotBlank() } ?: DEFAULT_THEME_MODE
    }

    val stylePreferences: Flow<StylePreferences> = dataStore.data.map { prefs ->
        val rawTone = prefs[KEY_PERSONA_TONE] ?: prefs[KEY_STYLE_PROFILE]
        StylePreferences(
            personaTone = PersonaTone.fromString(rawTone),
            styleProfile = StyleProfile.fromString(prefs[KEY_STYLE_PROFILE] ?: StyleProfile.CASUAL.name),
            similarityThreshold = prefs[KEY_SIMILARITY_THRESHOLD] ?: 0.75f,
            cooldownMins = prefs[KEY_COOLDOWN_MINS] ?: 5,
            replyDelaySeconds = prefs[KEY_REPLY_DELAY_SECONDS] ?: 2
        )
    }

    val personaToneFlow: Flow<PersonaTone> = stylePreferences.map { it.personaTone }

    val listenMainApp: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_LISTEN_MAIN_APP] ?: true
    }

    val listenBusinessApp: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_LISTEN_BUSINESS_APP] ?: true
    }

    val listenCloneApp: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_LISTEN_CLONE_APP] ?: false
    }

    val greetingTemplate: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_GREETING_TEMPLATE] ?: ""
    }

    val farewellTemplate: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_FAREWELL_TEMPLATE] ?: ""
    }

    val botName: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_BOT_NAME]?.takeIf { it.isNotBlank() } ?: DEFAULT_BOT_NAME
    }

    val storeName: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_STORE_NAME]?.takeIf { it.isNotBlank() } ?: DEFAULT_STORE_NAME
    }

    val storeKnowledgeTopicsFlow: Flow<List<String>> = dataStore.data.map { preferences ->
        val raw = preferences[STORE_KNOWLEDGE_TOPICS_KEY] ?: ""
        if (raw.isBlank()) emptyList() else raw.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
    }

    val storeWebsiteFlow: Flow<String?> = dataStore.data.map { preferences ->
        preferences[STORE_WEBSITE_KEY]?.takeIf { it.isNotBlank() }
    }

    val isAutoPauseOnHitlEnabledFlow: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[AUTO_PAUSE_ON_HITL_KEY] ?: false
    }

    val hitlHoldingMessageFlow: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_HITL_HOLDING_MESSAGE]?.takeIf { it.isNotBlank() } ?: DEFAULT_HITL_HOLDING_MESSAGE
    }

    val mediaFallbackMessageFlow: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_MEDIA_FALLBACK_MESSAGE]?.takeIf { it.isNotBlank() } ?: DEFAULT_MEDIA_FALLBACK_MESSAGE
    }

    val blankReplyFallbackTemplateFlow: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_BLANK_REPLY_FALLBACK_TEMPLATE]?.takeIf { it.isNotBlank() } ?: DEFAULT_BLANK_REPLY_FALLBACK_TEMPLATE
    }

    val nvidiaApiKeyFlow: Flow<String?> = secureCredentialsRepository.nvidiaApiKeyFlow
    val qwenApiKeyFlow: Flow<String?> = secureCredentialsRepository.qwenApiKeyFlow

    val cloudflareAccountIdFlow: Flow<String> = secureCredentialsRepository.cloudflareAccountIdFlow.map {
        it?.takeIf { id -> id.isNotBlank() } ?: secureCredentialsRepository.getCloudflareAccountId()
    }

    fun getCloudflareAccountId(): String = secureCredentialsRepository.getCloudflareAccountId()

    suspend fun getCloudflareAccountIdSync(): String = secureCredentialsRepository.getCloudflareAccountId()

    suspend fun setCloudflareAccountId(accountId: String?) {
        secureCredentialsRepository.setCloudflareAccountId(accountId)
    }

    // ── AutoStart Confirmation Persistence ─────────────────────────────────────

    val isAutoStartConfirmedFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_AUTO_START_CONFIRMED] ?: false
    }

    suspend fun setAutoStartConfirmed(confirmed: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_AUTO_START_CONFIRMED] = confirmed
        }
    }

    suspend fun isAutoStartConfirmedSync(): Boolean =
        dataStore.data.first()[KEY_AUTO_START_CONFIRMED] ?: false

    // ── Provider Ordering Management ──────────────────────────────────────────

    val providerOrderFlow: Flow<List<String>> = dataStore.data.map { prefs ->
        val raw = prefs[KEY_PROVIDER_ORDER]
        if (raw.isNullOrBlank()) {
            DEFAULT_PROVIDER_ORDER
        } else {
            raw.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }
        }
    }

    fun getProviderOrder(): Flow<List<String>> = providerOrderFlow

    suspend fun setProviderOrder(order: List<String>) {
        val sanitized = order.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.distinct()
        dataStore.edit { prefs ->
            prefs[KEY_PROVIDER_ORDER] = sanitized.joinToString(",")
        }
    }

    suspend fun getProviderOrderSync(): List<String> {
        val raw = dataStore.data.first()[KEY_PROVIDER_ORDER]
        return if (raw.isNullOrBlank()) {
            DEFAULT_PROVIDER_ORDER
        } else {
            raw.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }
        }
    }

    // ── Dynamic Provider Model Management (capability-aware) ──────────────────

    /**
     * Key where capability-annotated model configs are stored.
     * Format per line: "modelId|TEXT,VISION"  or legacy "modelId" (TEXT-only).
     * Kept separate from the legacy plain-model key to maintain full backward
     * compatibility: if the new key is blank we fall back to the legacy key.
     */
    private fun providerModelConfigsKey(provider: String) =
        stringPreferencesKey("provider_model_configs_${provider.trim().lowercase()}")

    // ── Capability-aware model config flows ───────────────────────────────────

    fun getProviderModelConfigs(provider: String): Flow<List<ModelConfig>> {
        val norm = ModelProviders.normalize(provider)
        return dataStore.data.map { prefs ->
            resolveModelConfigs(norm, prefs)
        }
    }

    private fun resolveModelConfigs(norm: String, prefs: Preferences): List<ModelConfig> {
        // Prefer new capability-annotated key
        val newRaw = prefs[providerModelConfigsKey(norm)]
        if (!newRaw.isNullOrBlank()) {
            return newRaw.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
                .map { ModelConfig.parse(it) }
        }
        // Fall back to legacy plain-model key (backward compat)
        val legacyRaw = prefs[providerModelsKey(norm)]
        val ids = if (!legacyRaw.isNullOrBlank()) {
            legacyRaw.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        } else {
            DEFAULT_PROVIDER_MODELS[norm] ?: emptyList()
        }
        return ids.map { id ->
            ModelConfig(modelId = id, capabilities = ModelConfig.inferCapabilities(id))
        }
    }

    suspend fun getProviderModelConfigsSync(provider: String): List<ModelConfig> {
        val norm = ModelProviders.normalize(provider)
        val prefs = dataStore.data.first()
        return resolveModelConfigs(norm, prefs)
    }

    suspend fun setProviderModelConfigs(provider: String, configs: List<ModelConfig>) {
        val norm = ModelProviders.normalize(provider)
        val sanitized = configs
            .filter { it.modelId.isNotBlank() }
            .distinctBy { it.modelId }
        dataStore.edit { prefs ->
            prefs[providerModelConfigsKey(norm)] = sanitized.joinToString("\n") { it.serialize() }
            // Keep legacy plain key in sync for backward compat with old callers
            prefs[providerModelsKey(norm)] = sanitized.joinToString("\n") { it.modelId }
        }
    }

    suspend fun addProviderModelConfig(provider: String, config: ModelConfig) {
        val norm = ModelProviders.normalize(provider)
        val current = getProviderModelConfigsSync(norm).toMutableList()
        if (current.none { it.modelId == config.modelId }) {
            current += config
            setProviderModelConfigs(norm, current)
        }
    }

    suspend fun removeProviderModelConfig(provider: String, modelId: String) {
        val norm = ModelProviders.normalize(provider)
        val current = getProviderModelConfigsSync(norm)
        setProviderModelConfigs(norm, current.filter { it.modelId != modelId })
    }

    // ── Legacy plain-string model API (preserved for backward compat) ──────────

    fun getProviderModels(provider: String): Flow<List<String>> =
        getProviderModelConfigs(provider).map { configs -> configs.map { it.modelId } }

    suspend fun setProviderModels(provider: String, models: List<String>) {
        val norm = ModelProviders.normalize(provider)
        // Merge with existing capability data so we don't lose capability tags on reorder/delete
        val existingConfigs = getProviderModelConfigsSync(norm).associateBy { it.modelId }
        val newConfigs = models
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .map { id ->
                existingConfigs[id] ?: ModelConfig(id, ModelConfig.inferCapabilities(id))
            }
        setProviderModelConfigs(norm, newConfigs)
    }

    suspend fun addProviderModel(provider: String, model: String) {
        val norm = ModelProviders.normalize(provider)
        val trimmed = model.trim()
        if (trimmed.isBlank()) return
        val config = ModelConfig(trimmed, ModelConfig.inferCapabilities(trimmed))
        addProviderModelConfig(norm, config)
    }

    suspend fun removeProviderModel(provider: String, model: String) {
        removeProviderModelConfig(provider, model.trim())
    }

    suspend fun getProviderModelsSync(provider: String): List<String> =
        getProviderModelConfigsSync(provider).map { it.modelId }

    // ── Business Hours Schedule Persistence ───────────────────────────────────

    val businessHoursScheduleFlow: Flow<BusinessHoursSchedule> = dataStore.data.map { prefs ->
        val isEnabled = prefs[KEY_BUSINESS_HOURS_ENABLED] ?: false
        val rawDays = prefs[KEY_BUSINESS_HOURS_DAYS]
        val days = if (rawDays.isNullOrBlank()) {
            BusinessHoursSchedule.DEFAULT_WORKING_DAYS
        } else {
            rawDays.split(",").map { it.trim().uppercase() }.filter { it.isNotEmpty() }.toSet()
        }
        val startTime = prefs[KEY_BUSINESS_HOURS_START] ?: "10:00"
        val endTime = prefs[KEY_BUSINESS_HOURS_END] ?: "23:00"
        val outOfHoursMsg = prefs[KEY_BUSINESS_HOURS_OUT_OF_HOURS_MSG]?.takeIf { it.isNotBlank() }
            ?: BusinessHoursSchedule.DEFAULT_OUT_OF_HOURS_MESSAGE

        BusinessHoursSchedule(
            isEnabled = isEnabled,
            workingDays = days,
            startTime = startTime,
            endTime = endTime,
            outOfHoursMessage = outOfHoursMsg
        )
    }

    suspend fun setBusinessHoursSchedule(schedule: BusinessHoursSchedule) {
        dataStore.edit { prefs ->
            prefs[KEY_BUSINESS_HOURS_ENABLED] = schedule.isEnabled
            prefs[KEY_BUSINESS_HOURS_DAYS] = schedule.workingDays.joinToString(",")
            prefs[KEY_BUSINESS_HOURS_START] = schedule.startTime
            prefs[KEY_BUSINESS_HOURS_END] = schedule.endTime
            prefs[KEY_BUSINESS_HOURS_OUT_OF_HOURS_MSG] = schedule.outOfHoursMessage
        }
    }

    suspend fun setBusinessHoursEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[KEY_BUSINESS_HOURS_ENABLED] = enabled }
    }

    suspend fun setWorkingDays(days: Set<String>) {
        dataStore.edit { prefs -> prefs[KEY_BUSINESS_HOURS_DAYS] = days.joinToString(",") }
    }

    suspend fun setWorkingHours(startTime: String, endTime: String) {
        dataStore.edit { prefs ->
            prefs[KEY_BUSINESS_HOURS_START] = startTime.trim()
            prefs[KEY_BUSINESS_HOURS_END] = endTime.trim()
        }
    }

    suspend fun setOutOfHoursMessage(message: String) {
        dataStore.edit { prefs -> prefs[KEY_BUSINESS_HOURS_OUT_OF_HOURS_MSG] = message.trim() }
    }

    suspend fun getBusinessHoursScheduleSync(): BusinessHoursSchedule =
        businessHoursScheduleFlow.first()

    // ── Blacklist & Privacy Persistence ───────────────────────────────────────

    val ignoreGroupsFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_IGNORE_GROUPS] ?: true // Ignore groups by default for privacy
    }

    val blacklistedNumbersFlow: Flow<Set<String>> = dataStore.data.map { prefs ->
        val raw = prefs[KEY_BLACKLISTED_NUMBERS]
        if (raw.isNullOrBlank()) {
            emptySet()
        } else {
            raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        }
    }

    suspend fun setIgnoreGroups(ignore: Boolean) {
        dataStore.edit { prefs -> prefs[KEY_IGNORE_GROUPS] = ignore }
    }

    suspend fun setBlacklistedNumbers(numbers: Set<String>) {
        val sanitized = numbers.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        dataStore.edit { prefs ->
            prefs[KEY_BLACKLISTED_NUMBERS] = sanitized.joinToString(",")
        }
    }

    suspend fun addBlacklistedNumber(number: String) {
        val trimmed = number.trim()
        if (trimmed.isBlank()) return
        val current = getBlacklistedNumbersSync()
        setBlacklistedNumbers(current + trimmed)
    }

    suspend fun removeBlacklistedNumber(number: String) {
        val trimmed = number.trim()
        val current = getBlacklistedNumbersSync()
        setBlacklistedNumbers(current.filter { it != trimmed }.toSet())
    }

    suspend fun isIgnoreGroupsSync(): Boolean =
        dataStore.data.first()[KEY_IGNORE_GROUPS] ?: true

    suspend fun getBlacklistedNumbersSync(): Set<String> {
        val raw = dataStore.data.first()[KEY_BLACKLISTED_NUMBERS]
        return if (raw.isNullOrBlank()) {
            emptySet()
        } else {
            raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        }
    }

    // ── Core Suspend Setters ──────────────────────────────────────────────────

    suspend fun setAutoReplyEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[KEY_AUTO_REPLY_ENABLED] = enabled }
    }

    suspend fun setAppLanguage(lang: String) {
        dataStore.edit { prefs ->
            prefs[KEY_APP_LANGUAGE] = lang.trim().lowercase()
        }
    }

    suspend fun setThemeMode(mode: String) {
        dataStore.edit { prefs ->
            prefs[KEY_THEME_MODE] = mode.trim().lowercase()
        }
    }

    suspend fun setStylePreferences(stylePreferences: StylePreferences) {
        dataStore.edit { prefs ->
            prefs[KEY_PERSONA_TONE] = stylePreferences.personaTone.name
            prefs[KEY_STYLE_PROFILE] = stylePreferences.personaTone.name
            prefs[KEY_SIMILARITY_THRESHOLD] = stylePreferences.similarityThreshold
            prefs[KEY_COOLDOWN_MINS] = stylePreferences.cooldownMins
            prefs[KEY_REPLY_DELAY_SECONDS] = stylePreferences.replyDelaySeconds
        }
    }

    suspend fun setPersonaTone(tone: PersonaTone) {
        dataStore.edit { prefs ->
            prefs[KEY_PERSONA_TONE] = tone.name
            prefs[KEY_STYLE_PROFILE] = tone.name
        }
    }

    suspend fun setListenMainApp(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[KEY_LISTEN_MAIN_APP] = enabled }
    }

    suspend fun setListenBusinessApp(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[KEY_LISTEN_BUSINESS_APP] = enabled }
    }

    suspend fun setListenCloneApp(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[KEY_LISTEN_CLONE_APP] = enabled }
    }

    suspend fun setGreetingTemplate(text: String) {
        dataStore.edit { prefs -> prefs[KEY_GREETING_TEMPLATE] = text }
    }

    suspend fun setFarewellTemplate(text: String) {
        dataStore.edit { prefs -> prefs[KEY_FAREWELL_TEMPLATE] = text }
    }

    suspend fun setBotName(name: String) {
        dataStore.edit { prefs -> prefs[KEY_BOT_NAME] = name.trim() }
    }

    suspend fun setStoreName(name: String) {
        dataStore.edit { prefs -> prefs[KEY_STORE_NAME] = name.trim() }
    }

    suspend fun setStoreKnowledgeTopics(topics: List<String>) {
        dataStore.edit { preferences ->
            preferences[STORE_KNOWLEDGE_TOPICS_KEY] = topics.joinToString("\n")
        }
    }

    suspend fun clearStoreKnowledgeTopics() {
        dataStore.edit { preferences ->
            preferences.remove(STORE_KNOWLEDGE_TOPICS_KEY)
        }
    }

    suspend fun setStoreWebsite(website: String?) {
        dataStore.edit { preferences ->
            if (website.isNullOrBlank()) {
                preferences.remove(STORE_WEBSITE_KEY)
            } else {
                preferences[STORE_WEBSITE_KEY] = website.trim()
            }
        }
    }

    suspend fun clearStoreWebsite() {
        dataStore.edit { preferences ->
            preferences.remove(STORE_WEBSITE_KEY)
        }
    }

    suspend fun setAutoPauseOnHitl(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[AUTO_PAUSE_ON_HITL_KEY] = enabled
        }
    }

    // ── Sync readers (for worker/service contexts) ────────────────────────────

    suspend fun isAutoReplyEnabledSync(): Boolean =
        dataStore.data.first()[KEY_AUTO_REPLY_ENABLED] ?: false

    suspend fun getAppLanguageSync(): String =
        dataStore.data.first()[KEY_APP_LANGUAGE]?.takeIf { it.isNotBlank() } ?: DEFAULT_APP_LANGUAGE

    suspend fun getThemeModeSync(): String =
        dataStore.data.first()[KEY_THEME_MODE]?.takeIf { it.isNotBlank() } ?: DEFAULT_THEME_MODE

    suspend fun getStylePreferencesSync(): StylePreferences {
        val prefs = dataStore.data.first()
        val rawTone = prefs[KEY_PERSONA_TONE] ?: prefs[KEY_STYLE_PROFILE]
        return StylePreferences(
            personaTone = PersonaTone.fromString(rawTone),
            styleProfile = StyleProfile.fromString(prefs[KEY_STYLE_PROFILE] ?: StyleProfile.CASUAL.name),
            similarityThreshold = prefs[KEY_SIMILARITY_THRESHOLD] ?: 0.75f,
            cooldownMins = prefs[KEY_COOLDOWN_MINS] ?: 5,
            replyDelaySeconds = prefs[KEY_REPLY_DELAY_SECONDS] ?: 2
        )
    }

    suspend fun getPersonaToneSync(): PersonaTone = getStylePreferencesSync().personaTone

    suspend fun getRoutingPrefsSync(): RoutingPrefs {
        val prefs = dataStore.data.first()
        return RoutingPrefs(
            listenMainApp = prefs[KEY_LISTEN_MAIN_APP] ?: true,
            listenBusinessApp = prefs[KEY_LISTEN_BUSINESS_APP] ?: true,
            listenCloneApp = prefs[KEY_LISTEN_CLONE_APP] ?: false
        )
    }

    suspend fun getGreetingTemplateSync(default: String): String =
        dataStore.data.first()[KEY_GREETING_TEMPLATE]?.takeIf { it.isNotBlank() } ?: default

    suspend fun getFarewellTemplateSync(default: String): String =
        dataStore.data.first()[KEY_FAREWELL_TEMPLATE]?.takeIf { it.isNotBlank() } ?: default

    suspend fun getBotNameSync(): String =
        dataStore.data.first()[KEY_BOT_NAME]?.takeIf { it.isNotBlank() } ?: DEFAULT_BOT_NAME

    suspend fun getStoreNameSync(): String =
        dataStore.data.first()[KEY_STORE_NAME]?.takeIf { it.isNotBlank() } ?: DEFAULT_STORE_NAME

    suspend fun isAutoPauseOnHitlEnabledSync(): Boolean {
        return isAutoPauseOnHitlEnabledFlow.first()
    }

    suspend fun setHitlHoldingMessage(message: String) {
        dataStore.edit { prefs -> prefs[KEY_HITL_HOLDING_MESSAGE] = message }
    }

    suspend fun setMediaFallbackMessage(message: String) {
        dataStore.edit { prefs -> prefs[KEY_MEDIA_FALLBACK_MESSAGE] = message }
    }

    suspend fun setBlankReplyFallbackTemplate(template: String) {
        dataStore.edit { prefs -> prefs[KEY_BLANK_REPLY_FALLBACK_TEMPLATE] = template }
    }

    suspend fun getHitlHoldingMessageSync(): String =
        dataStore.data.first()[KEY_HITL_HOLDING_MESSAGE]?.takeIf { it.isNotBlank() } ?: DEFAULT_HITL_HOLDING_MESSAGE

    suspend fun getMediaFallbackMessageSync(): String =
        dataStore.data.first()[KEY_MEDIA_FALLBACK_MESSAGE]?.takeIf { it.isNotBlank() } ?: DEFAULT_MEDIA_FALLBACK_MESSAGE

    suspend fun setNvidiaApiKey(key: String?) {
        secureCredentialsRepository.setNvidiaApiKey(key)
        dataStore.edit { prefs ->
            prefs.remove(KEY_NVIDIA_API_KEY)
        }
    }

    suspend fun getNvidiaApiKeySync(): String {
        val secureKey = secureCredentialsRepository.getNvidiaApiKey()
        if (secureKey.isNotBlank()) return secureKey
        val legacy = dataStore.data.first()[KEY_NVIDIA_API_KEY]?.takeIf { it.isNotBlank() } ?: ""
        if (legacy.isNotBlank()) {
            secureCredentialsRepository.setNvidiaApiKey(legacy)
        }
        return legacy
    }

    suspend fun setQwenApiKey(key: String?) {
        secureCredentialsRepository.setQwenApiKey(key)
        dataStore.edit { prefs ->
            prefs.remove(KEY_QWEN_API_KEY)
        }
    }

    suspend fun getQwenApiKeySync(): String {
        val secureKey = secureCredentialsRepository.getQwenApiKey()
        if (secureKey.isNotBlank()) return secureKey
        val legacy = dataStore.data.first()[KEY_QWEN_API_KEY]?.takeIf { it.isNotBlank() } ?: ""
        if (legacy.isNotBlank()) {
            secureCredentialsRepository.setQwenApiKey(legacy)
        }
        return legacy
    }

    suspend fun getBlankReplyFallbackTemplateSync(): String =
        dataStore.data.first()[KEY_BLANK_REPLY_FALLBACK_TEMPLATE]?.takeIf { it.isNotBlank() } ?: DEFAULT_BLANK_REPLY_FALLBACK_TEMPLATE
}

/** Snapshot of app-routing preferences. Immutable value object. */
data class RoutingPrefs(
    val listenMainApp: Boolean,
    val listenBusinessApp: Boolean,
    val listenCloneApp: Boolean
)
