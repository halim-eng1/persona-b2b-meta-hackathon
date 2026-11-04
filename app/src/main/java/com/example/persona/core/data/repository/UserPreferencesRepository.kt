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
import com.example.persona.features.settings.domain.model.AccountMode
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
    val secureCredentialsRepository: SecureCredentialsRepository
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

        // ── Provider Ordering & Model Management ──────────────────────────────
        val KEY_PROVIDER_ORDER = stringPreferencesKey("provider_order")
        val KEY_BACKFILL_APPLIED_V2 = booleanPreferencesKey("models_backfill_applied_v2")

        fun providerPriorityKey(capability: ModelCapability) =
            stringPreferencesKey("provider_priority_${capability.name}")

        fun providerModelOrderForCapabilityKey(provider: String, capability: ModelCapability) =
            stringPreferencesKey("provider_model_order_${provider.trim().lowercase()}_${capability.name.lowercase()}")

        private fun providerModelsKey(provider: String) =
            stringPreferencesKey("provider_models_${provider.trim().lowercase()}")

        // ── Auto-Start Onboarding Confirmation ─────────────────────────────────
        val KEY_AUTO_START_CONFIRMED = booleanPreferencesKey("is_auto_start_confirmed")
        val KEY_HAS_SEEN_ONBOARDING = booleanPreferencesKey("has_seen_onboarding")
        val KEY_ALWAYS_SHOW_ONBOARDING = booleanPreferencesKey("always_show_onboarding")

        // ── Operational Mode (Business vs. Personal) ───────────────────────────
        val KEY_ACCOUNT_MODE = stringPreferencesKey("account_mode")
        val DEFAULT_ACCOUNT_MODE = AccountMode.BUSINESS.name
        val KEY_PERSONAL_ASSISTANT_NAME = stringPreferencesKey("personal_assistant_name")
        val KEY_PERSONAL_OWNER_NAME = stringPreferencesKey("personal_owner_name")
        const val DEFAULT_PERSONAL_ASSISTANT_NAME = "Assistant"
        const val DEFAULT_PERSONAL_ASSISTANT_NAME_AR = "المساعد الشخصي"
        const val DEFAULT_PERSONAL_OWNER_NAME = "Personal Profile"
        const val DEFAULT_PERSONAL_OWNER_NAME_AR = "الملف الشخصي"

        // ── Model Preparation State ───────────────────────────────────────────
        val KEY_MODEL_PREPARED = booleanPreferencesKey("is_model_prepared")

        // ── App Language & Locale ──────────────────────────────────────────────
        val KEY_APP_LANGUAGE = stringPreferencesKey("app_language")
        const val DEFAULT_APP_LANGUAGE = "en"

        // ── Theme Mode ("dark", "light", "system") ──────────────────────────────
        val KEY_THEME_MODE = stringPreferencesKey("app_theme_mode")
        const val THEME_MODE_DARK = "dark"
        const val THEME_MODE_LIGHT = "light"
        const val THEME_MODE_SYSTEM = "system"
        const val DEFAULT_THEME_MODE = THEME_MODE_LIGHT

        // ── Business Hours & Blacklist ────────────────────────────────────────
        val KEY_BUSINESS_HOURS_ENABLED = booleanPreferencesKey("business_hours_enabled")
        val KEY_BUSINESS_HOURS_DAYS = stringPreferencesKey("business_hours_days")
        val KEY_BUSINESS_HOURS_START = stringPreferencesKey("business_hours_start")
        val KEY_BUSINESS_HOURS_END = stringPreferencesKey("business_hours_end")
        val KEY_BUSINESS_HOURS_OUT_OF_HOURS_MSG = stringPreferencesKey("business_hours_out_of_hours_msg")

        val KEY_IGNORE_GROUPS = booleanPreferencesKey("ignore_whatsapp_groups")
        val KEY_BLACKLISTED_NUMBERS = stringPreferencesKey("blacklisted_phone_numbers")

        const val DEFAULT_BOT_NAME = "مساعد المبيعات"
        const val DEFAULT_BOT_NAME_EN = "Sales Assistant"
        const val DEFAULT_STORE_NAME = "متجرنا"
        const val DEFAULT_STORE_NAME_EN = "Our Store"

        const val DEFAULT_HITL_HOLDING_MESSAGE_AR =
            "تمام يا فندم، ثواني وهيكون مع حضرتك ممثل خدمة العملاء فوراً لمساعدتك في أقرب وقت متاح! 🙏"
        const val DEFAULT_HITL_HOLDING_MESSAGE_EN =
            "One moment please, a customer support representative will be with you shortly to assist you! 🙏"
        const val DEFAULT_HITL_HOLDING_MESSAGE = DEFAULT_HITL_HOLDING_MESSAGE_EN

        const val DEFAULT_MEDIA_FALLBACK_MESSAGE_AR =
            "عفواً، لا يمكنني معالجة الوسائط. يرجى كتابة استفسارك في رسالة نصية."
        const val DEFAULT_MEDIA_FALLBACK_MESSAGE_EN =
            "Sorry, I cannot process media attachments. Please type your inquiry as a text message."
        const val DEFAULT_MEDIA_FALLBACK_MESSAGE = DEFAULT_MEDIA_FALLBACK_MESSAGE_EN

        const val DEFAULT_BLANK_REPLY_FALLBACK_TEMPLATE_AR =
            "أنا معاك وسامعك كويس يا فندم، بس قولّي نقدر نجهزلك إيه النهارده من منتجاتنا في %s؟ 💙"
        const val DEFAULT_BLANK_REPLY_FALLBACK_TEMPLATE_EN =
            "I am here and listening, but please let us know how we can assist you with our products today? 💙"
        const val DEFAULT_BLANK_REPLY_FALLBACK_TEMPLATE = DEFAULT_BLANK_REPLY_FALLBACK_TEMPLATE_EN

        val DEFAULT_PROVIDER_ORDER = listOf(
            ModelProviders.META
        )

        val DEFAULT_PROVIDER_MODELS = ModelProviders.DEFAULT_MODELS

        /**
         * Pre-seeded capability maps for known default models to ensure exact hydration
         * during legacy migration or fresh installations.
         */
        val PRESEEDED_CAPABILITIES: Map<String, Set<ModelCapability>> = mapOf(
            "muse-spark-1.3-contributor" to setOf(ModelCapability.TEXT, ModelCapability.VISION, ModelCapability.DECISION),
            "muse-spark-1.3" to setOf(ModelCapability.TEXT, ModelCapability.VISION, ModelCapability.DECISION),
            "muse-spark-1.2-contributor" to setOf(ModelCapability.TEXT, ModelCapability.VISION, ModelCapability.DECISION),
            "muse-spark-1.2" to setOf(ModelCapability.TEXT, ModelCapability.VISION, ModelCapability.DECISION),
            "muse-voice-transcribe-1.0" to setOf(ModelCapability.AUDIO)
        )

        /**
         * Multimodal models required to be backfilled non-destructively for existing users
         * who already have stored models for these providers.
         */
        val REQUIRED_MODELS_BACKFILL: Map<String, List<ModelConfig>> = mapOf(
            ModelProviders.META to listOf(
                ModelConfig("muse-spark-1.3-contributor", setOf(ModelCapability.TEXT, ModelCapability.VISION, ModelCapability.DECISION)),
                ModelConfig("muse-spark-1.3", setOf(ModelCapability.TEXT, ModelCapability.VISION, ModelCapability.DECISION)),
                ModelConfig("muse-spark-1.2-contributor", setOf(ModelCapability.TEXT, ModelCapability.VISION, ModelCapability.DECISION)),
                ModelConfig("muse-spark-1.2", setOf(ModelCapability.TEXT, ModelCapability.VISION, ModelCapability.DECISION)),
                ModelConfig("muse-voice-transcribe-1.0", setOf(ModelCapability.AUDIO))
            )
        )
    }

    // ── Flows ─────────────────────────────────────────────────────────────────

    val isAutoReplyEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_AUTO_REPLY_ENABLED] ?: false
    }

    @Volatile
    private var cachedModelPrepared: Boolean? = null

    val isModelPreparedFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        val prepared = prefs[KEY_MODEL_PREPARED] ?: true
        cachedModelPrepared = prepared
        prepared
    }

    val appLanguageFlow: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_APP_LANGUAGE]?.takeIf { it.isNotBlank() } ?: DEFAULT_APP_LANGUAGE
    }

    val accountModeFlow: Flow<AccountMode> = dataStore.data.map { prefs ->
        AccountMode.fromString(prefs[KEY_ACCOUNT_MODE])
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
        val saved = prefs[KEY_BOT_NAME]?.takeIf { it.isNotBlank() }
        if (saved != null) saved
        else {
            val lang = prefs[KEY_APP_LANGUAGE]?.takeIf { it.isNotBlank() } ?: DEFAULT_APP_LANGUAGE
            if (lang.startsWith("ar", ignoreCase = true)) DEFAULT_BOT_NAME else DEFAULT_BOT_NAME_EN
        }
    }

    val storeName: Flow<String> = dataStore.data.map { prefs ->
        val saved = prefs[KEY_STORE_NAME]?.takeIf { it.isNotBlank() }
        if (saved != null) saved
        else {
            val lang = prefs[KEY_APP_LANGUAGE]?.takeIf { it.isNotBlank() } ?: DEFAULT_APP_LANGUAGE
            if (lang.startsWith("ar", ignoreCase = true)) DEFAULT_STORE_NAME else DEFAULT_STORE_NAME_EN
        }
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
        val custom = prefs[KEY_HITL_HOLDING_MESSAGE]?.takeIf { it.isNotBlank() }
        val lang = prefs[KEY_APP_LANGUAGE]?.takeIf { it.isNotBlank() } ?: DEFAULT_APP_LANGUAGE
        val isArabic = lang.equals("ar", ignoreCase = true)
        val defaultMsg = if (isArabic) DEFAULT_HITL_HOLDING_MESSAGE_AR else DEFAULT_HITL_HOLDING_MESSAGE_EN
        if (custom != null) {
            if (!isArabic && custom == DEFAULT_HITL_HOLDING_MESSAGE_AR) {
                defaultMsg
            } else if (isArabic && custom == DEFAULT_HITL_HOLDING_MESSAGE_EN) {
                defaultMsg
            } else {
                custom
            }
        } else {
            defaultMsg
        }
    }

    val mediaFallbackMessageFlow: Flow<String> = dataStore.data.map { prefs ->
        val custom = prefs[KEY_MEDIA_FALLBACK_MESSAGE]?.takeIf { it.isNotBlank() }
        val lang = prefs[KEY_APP_LANGUAGE]?.takeIf { it.isNotBlank() } ?: DEFAULT_APP_LANGUAGE
        val isArabic = lang.equals("ar", ignoreCase = true)
        val defaultMsg = if (isArabic) DEFAULT_MEDIA_FALLBACK_MESSAGE_AR else DEFAULT_MEDIA_FALLBACK_MESSAGE_EN
        if (custom != null) {
            if (!isArabic && custom == DEFAULT_MEDIA_FALLBACK_MESSAGE_AR) {
                defaultMsg
            } else if (isArabic && custom == DEFAULT_MEDIA_FALLBACK_MESSAGE_EN) {
                defaultMsg
            } else {
                custom
            }
        } else {
            defaultMsg
        }
    }

    val blankReplyFallbackTemplateFlow: Flow<String> = dataStore.data.map { prefs ->
        val custom = prefs[KEY_BLANK_REPLY_FALLBACK_TEMPLATE]?.takeIf { it.isNotBlank() }
        val lang = prefs[KEY_APP_LANGUAGE]?.takeIf { it.isNotBlank() } ?: DEFAULT_APP_LANGUAGE
        val isArabic = lang.equals("ar", ignoreCase = true)
        val defaultMsg = if (isArabic) DEFAULT_BLANK_REPLY_FALLBACK_TEMPLATE_AR else DEFAULT_BLANK_REPLY_FALLBACK_TEMPLATE_EN
        if (custom != null) {
            if (!isArabic && custom == DEFAULT_BLANK_REPLY_FALLBACK_TEMPLATE_AR) {
                defaultMsg
            } else if (isArabic && custom == DEFAULT_BLANK_REPLY_FALLBACK_TEMPLATE_EN) {
                defaultMsg
            } else {
                custom
            }
        } else {
            defaultMsg
        }
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

    // ── Onboarding Persistence ────────────────────────────────────────────────
    @Volatile
    private var cachedHasSeenOnboarding: Boolean? = null

    val alwaysShowOnboardingFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_ALWAYS_SHOW_ONBOARDING] ?: false
    }

    suspend fun setAlwaysShowOnboarding(enabled: Boolean) {
        cachedHasSeenOnboarding = if (enabled) false else true
        dataStore.edit { prefs ->
            prefs[KEY_ALWAYS_SHOW_ONBOARDING] = enabled
            prefs[KEY_HAS_SEEN_ONBOARDING] = !enabled
        }
    }

    fun isAlwaysShowOnboardingSync(): Boolean {
        return runCatching {
            kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
                dataStore.data.first()[KEY_ALWAYS_SHOW_ONBOARDING] ?: false
            }
        }.getOrDefault(false)
    }

    val hasSeenOnboardingFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        val alwaysShow = prefs[KEY_ALWAYS_SHOW_ONBOARDING] ?: false
        if (alwaysShow) {
            cachedHasSeenOnboarding = false
            false
        } else {
            val seen = prefs[KEY_HAS_SEEN_ONBOARDING] ?: false
            cachedHasSeenOnboarding = seen
            seen
        }
    }

    suspend fun setHasSeenOnboarding(seen: Boolean) {
        cachedHasSeenOnboarding = seen
        dataStore.edit { prefs ->
            prefs[KEY_HAS_SEEN_ONBOARDING] = seen
        }
    }

    suspend fun hasSeenOnboarding(): Boolean {
        val prefs = dataStore.data.first()
        if (prefs[KEY_ALWAYS_SHOW_ONBOARDING] == true) return false
        return prefs[KEY_HAS_SEEN_ONBOARDING] ?: false
    }

    fun hasSeenOnboardingSync(): Boolean {
        return runCatching {
            kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
                val prefs = dataStore.data.first()
                if (prefs[KEY_ALWAYS_SHOW_ONBOARDING] == true) {
                    cachedHasSeenOnboarding = false
                    return@runBlocking false
                }
                cachedHasSeenOnboarding?.let { return@runBlocking it }
                val value = prefs[KEY_HAS_SEEN_ONBOARDING] ?: false
                cachedHasSeenOnboarding = value
                value
            }
        }.getOrDefault(false)
    }

    // ── Provider Ordering Management ──────────────────────────────────────────

    val providerOrderFlow: Flow<List<String>> = dataStore.data.map { prefs ->
        val raw = prefs[KEY_PROVIDER_ORDER]
        val stored = if (raw.isNullOrBlank()) {
            DEFAULT_PROVIDER_ORDER
        } else {
            val parsed = raw.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() && it in ModelProviders.ALL }.distinct()
            if (!parsed.contains(ModelProviders.META)) {
                listOf(ModelProviders.META) + parsed
            } else {
                parsed
            }
        }
        val missing = DEFAULT_PROVIDER_ORDER.filterNot { stored.contains(it) }
        (stored + missing).distinct().filter { it in ModelProviders.ALL }
    }

    fun getProviderOrder(): Flow<List<String>> = providerOrderFlow

    suspend fun setProviderOrder(order: List<String>) {
        val sanitized = order.map { it.trim().lowercase() }.filter { it.isNotEmpty() && it in ModelProviders.ALL }.distinct()
        dataStore.edit { prefs ->
            prefs[KEY_PROVIDER_ORDER] = sanitized.joinToString(",")
        }
    }

    suspend fun getProviderOrderSync(): List<String> {
        val raw = dataStore.data.first()[KEY_PROVIDER_ORDER]
        val stored = if (raw.isNullOrBlank()) {
            DEFAULT_PROVIDER_ORDER
        } else {
            val parsed = raw.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() && it in ModelProviders.ALL }.distinct()
            if (!parsed.contains(ModelProviders.META)) {
                listOf(ModelProviders.META) + parsed
            } else {
                parsed
            }
        }
        val missing = DEFAULT_PROVIDER_ORDER.filterNot { stored.contains(it) }
        return (stored + missing).distinct().filter { it in ModelProviders.ALL }
    }

    // ── Per-Capability Provider Priority Ordering ─────────────────────────────

    fun getProviderPriority(capability: ModelCapability): Flow<List<String>> =
        dataStore.data.map { prefs ->
            val raw = prefs[providerPriorityKey(capability)]
            if (raw.isNullOrBlank()) {
                ModelProviders.DEFAULT_CAPABILITY_PROVIDERS[capability] ?: listOf(ModelProviders.META)
            } else {
                val parsed = raw.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() && it in ModelProviders.ALL }.distinct()
                if (!parsed.contains(ModelProviders.META)) {
                    listOf(ModelProviders.META) + parsed
                } else {
                    parsed
                }
            }
        }

    suspend fun getProviderPrioritySync(capability: ModelCapability): List<String> {
        val raw = dataStore.data.first()[providerPriorityKey(capability)]
        return if (raw.isNullOrBlank()) {
            ModelProviders.DEFAULT_CAPABILITY_PROVIDERS[capability] ?: listOf(ModelProviders.META)
        } else {
            val parsed = raw.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() && it in ModelProviders.ALL }.distinct()
            if (!parsed.contains(ModelProviders.META)) {
                listOf(ModelProviders.META) + parsed
            } else {
                parsed
            }
        }
    }

    suspend fun setProviderPriority(capability: ModelCapability, order: List<String>) {
        val sanitized = order.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.distinct()
        dataStore.edit { prefs ->
            prefs[providerPriorityKey(capability)] = sanitized.joinToString(",")
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

    fun getProviderModelConfigs(provider: String, capability: ModelCapability? = null): Flow<List<ModelConfig>> {
        val norm = ModelProviders.normalize(provider)
        return dataStore.data.map { prefs ->
            resolveModelConfigs(norm, prefs, capability)
        }
    }

    fun getApiKeys(provider: String): List<String> = secureCredentialsRepository.getApiKeys(provider)
    fun hasConfiguredKeys(provider: String): Boolean = secureCredentialsRepository.hasConfiguredKeys(provider)

    private fun resolveModelConfigs(
        norm: String,
        prefs: Preferences,
        capability: ModelCapability? = null
    ): List<ModelConfig> {
        val newRaw = prefs[providerModelConfigsKey(norm)]
        val hasCustomConfigs = !newRaw.isNullOrBlank()

        val baseConfigs: List<ModelConfig> = if (!newRaw.isNullOrBlank()) {
            // User has explicitly customized/saved capability-annotated models:
            // Parse configs AS-IS, preserving custom capabilities (including any custom tags).
            newRaw.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
                .map { ModelConfig.parse(it) }
        } else {
            // Fall back to legacy plain-model key or defaults
            val legacyRaw = prefs[providerModelsKey(norm)]
            val ids = if (!legacyRaw.isNullOrBlank()) {
                legacyRaw.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
            } else {
                DEFAULT_PROVIDER_MODELS[norm] ?: emptyList()
            }
            ids.map { id ->
                val caps = PRESEEDED_CAPABILITIES[id] ?: ModelConfig.inferCapabilities(id)
                ModelConfig(modelId = id, capabilities = caps)
            }
        }

        // Auto-backfill strictly once for legacy users who haven't saved custom configs
        val backfillApplied = prefs[KEY_BACKFILL_APPLIED_V2] ?: false
        val backfillList = REQUIRED_MODELS_BACKFILL[norm]
        val fullList = if (!hasCustomConfigs && !backfillApplied && backfillList != null) {
            val existingIds = baseConfigs.map { it.modelId }.toSet()
            val missing = backfillList.filter { it.modelId !in existingIds }
            if (missing.isNotEmpty()) {
                baseConfigs + missing
            } else {
                baseConfigs
            }
        } else {
            baseConfigs
        }

        if (capability == null) {
            return fullList
        }

        // Filter to models matching the capability and sort by saved per-capability order
        val matching = fullList.filter { capability in it.capabilities }
        val savedOrderRaw = prefs[providerModelOrderForCapabilityKey(norm, capability)]
        if (savedOrderRaw.isNullOrBlank()) {
            return matching
        }

        val savedOrderIds = savedOrderRaw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val matchingMap = matching.associateBy { it.modelId }
        val orderedList = mutableListOf<ModelConfig>()
        savedOrderIds.forEach { id ->
            matchingMap[id]?.let { orderedList.add(it) }
        }
        matching.forEach { config ->
            if (config.modelId !in savedOrderIds) {
                orderedList.add(config)
            }
        }
        return orderedList
    }

    suspend fun getProviderModelConfigsSync(provider: String, capability: ModelCapability? = null): List<ModelConfig> {
        val norm = ModelProviders.normalize(provider)
        val prefs = dataStore.data.first()
        return resolveModelConfigs(norm, prefs, capability)
    }

    suspend fun setProviderModelOrderForCapability(
        provider: String,
        capability: ModelCapability,
        orderedModelIds: List<String>
    ) {
        val norm = ModelProviders.normalize(provider)
        val sanitized = orderedModelIds.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        dataStore.edit { prefs ->
            prefs[providerModelOrderForCapabilityKey(norm, capability)] = sanitized.joinToString(",")
        }
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
            // Record that backfill migration has been applied so deleted models are not resurrected
            prefs[KEY_BACKFILL_APPLIED_V2] = true
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
        val sanitized = models.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        dataStore.edit { preferences ->
            preferences[providerModelsKey(norm)] = sanitized.joinToString("\n")
        }
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

    suspend fun setAccountMode(mode: AccountMode) {
        dataStore.edit { prefs -> prefs[KEY_ACCOUNT_MODE] = mode.name }
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

    suspend fun getAccountModeSync(): AccountMode {
        val raw = dataStore.data.first()[KEY_ACCOUNT_MODE]
        return AccountMode.fromString(raw)
    }

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

    suspend fun getBotNameSync(): String {
        val prefs = dataStore.data.first()
        val saved = prefs[KEY_BOT_NAME]?.takeIf { it.isNotBlank() }
        if (saved != null) return saved
        val lang = prefs[KEY_APP_LANGUAGE]?.takeIf { it.isNotBlank() } ?: DEFAULT_APP_LANGUAGE
        return if (lang.startsWith("ar", ignoreCase = true)) DEFAULT_BOT_NAME else DEFAULT_BOT_NAME_EN
    }

    suspend fun getStoreNameSync(): String {
        val prefs = dataStore.data.first()
        val saved = prefs[KEY_STORE_NAME]?.takeIf { it.isNotBlank() }
        if (saved != null) return saved
        val lang = prefs[KEY_APP_LANGUAGE]?.takeIf { it.isNotBlank() } ?: DEFAULT_APP_LANGUAGE
        return if (lang.startsWith("ar", ignoreCase = true)) DEFAULT_STORE_NAME else DEFAULT_STORE_NAME_EN
    }

    val personalAssistantNameFlow: Flow<String> = dataStore.data.map { prefs ->
        val lang = prefs[KEY_APP_LANGUAGE]?.takeIf { it.isNotBlank() } ?: DEFAULT_APP_LANGUAGE
        val isArabic = lang.startsWith("ar", ignoreCase = true)
        prefs[KEY_PERSONAL_ASSISTANT_NAME]?.takeIf { it.isNotBlank() }
            ?: prefs[KEY_BOT_NAME]?.takeIf { it.isNotBlank() && it != DEFAULT_BOT_NAME && it != DEFAULT_BOT_NAME_EN }
            ?: if (isArabic) DEFAULT_PERSONAL_ASSISTANT_NAME_AR else DEFAULT_PERSONAL_ASSISTANT_NAME
    }

    val personalOwnerNameFlow: Flow<String> = dataStore.data.map { prefs ->
        val lang = prefs[KEY_APP_LANGUAGE]?.takeIf { it.isNotBlank() } ?: DEFAULT_APP_LANGUAGE
        val isArabic = lang.startsWith("ar", ignoreCase = true)
        prefs[KEY_PERSONAL_OWNER_NAME]?.takeIf { it.isNotBlank() }
            ?: prefs[KEY_STORE_NAME]?.takeIf { it.isNotBlank() && it != DEFAULT_STORE_NAME && it != DEFAULT_STORE_NAME_EN }
            ?: if (isArabic) DEFAULT_PERSONAL_OWNER_NAME_AR else DEFAULT_PERSONAL_OWNER_NAME
    }

    suspend fun getPersonalAssistantNameSync(): String {
        val prefs = dataStore.data.first()
        val lang = prefs[KEY_APP_LANGUAGE]?.takeIf { it.isNotBlank() } ?: DEFAULT_APP_LANGUAGE
        val isArabic = lang.startsWith("ar", ignoreCase = true)
        return prefs[KEY_PERSONAL_ASSISTANT_NAME]?.takeIf { it.isNotBlank() }
            ?: prefs[KEY_BOT_NAME]?.takeIf { it.isNotBlank() && it != DEFAULT_BOT_NAME && it != DEFAULT_BOT_NAME_EN }
            ?: if (isArabic) DEFAULT_PERSONAL_ASSISTANT_NAME_AR else DEFAULT_PERSONAL_ASSISTANT_NAME
    }

    suspend fun getPersonalOwnerNameSync(): String {
        val prefs = dataStore.data.first()
        val lang = prefs[KEY_APP_LANGUAGE]?.takeIf { it.isNotBlank() } ?: DEFAULT_APP_LANGUAGE
        val isArabic = lang.startsWith("ar", ignoreCase = true)
        return prefs[KEY_PERSONAL_OWNER_NAME]?.takeIf { it.isNotBlank() }
            ?: prefs[KEY_STORE_NAME]?.takeIf { it.isNotBlank() && it != DEFAULT_STORE_NAME && it != DEFAULT_STORE_NAME_EN }
            ?: if (isArabic) DEFAULT_PERSONAL_OWNER_NAME_AR else DEFAULT_PERSONAL_OWNER_NAME
    }

    suspend fun setPersonalAssistantName(name: String) {
        dataStore.edit { prefs -> prefs[KEY_PERSONAL_ASSISTANT_NAME] = name.trim() }
    }

    suspend fun setPersonalOwnerName(name: String) {
        dataStore.edit { prefs -> prefs[KEY_PERSONAL_OWNER_NAME] = name.trim() }
    }

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

    suspend fun getHitlHoldingMessageSync(): String {
        val prefs = dataStore.data.first()
        val custom = prefs[KEY_HITL_HOLDING_MESSAGE]?.takeIf { it.isNotBlank() }
        val lang = prefs[KEY_APP_LANGUAGE]?.takeIf { it.isNotBlank() } ?: DEFAULT_APP_LANGUAGE
        val isArabic = lang.equals("ar", ignoreCase = true)
        val defaultMsg = if (isArabic) DEFAULT_HITL_HOLDING_MESSAGE_AR else DEFAULT_HITL_HOLDING_MESSAGE_EN
        return if (custom != null) {
            if (!isArabic && custom == DEFAULT_HITL_HOLDING_MESSAGE_AR) defaultMsg
            else if (isArabic && custom == DEFAULT_HITL_HOLDING_MESSAGE_EN) defaultMsg
            else custom
        } else {
            defaultMsg
        }
    }

    suspend fun getMediaFallbackMessageSync(): String {
        val prefs = dataStore.data.first()
        val custom = prefs[KEY_MEDIA_FALLBACK_MESSAGE]?.takeIf { it.isNotBlank() }
        val lang = prefs[KEY_APP_LANGUAGE]?.takeIf { it.isNotBlank() } ?: DEFAULT_APP_LANGUAGE
        val isArabic = lang.equals("ar", ignoreCase = true)
        val defaultMsg = if (isArabic) DEFAULT_MEDIA_FALLBACK_MESSAGE_AR else DEFAULT_MEDIA_FALLBACK_MESSAGE_EN
        return if (custom != null) {
            if (!isArabic && custom == DEFAULT_MEDIA_FALLBACK_MESSAGE_AR) defaultMsg
            else if (isArabic && custom == DEFAULT_MEDIA_FALLBACK_MESSAGE_EN) defaultMsg
            else custom
        } else {
            defaultMsg
        }
    }



    suspend fun getBlankReplyFallbackTemplateSync(): String {
        val prefs = dataStore.data.first()
        val custom = prefs[KEY_BLANK_REPLY_FALLBACK_TEMPLATE]?.takeIf { it.isNotBlank() }
        val lang = prefs[KEY_APP_LANGUAGE]?.takeIf { it.isNotBlank() } ?: DEFAULT_APP_LANGUAGE
        val isArabic = lang.equals("ar", ignoreCase = true)
        val defaultMsg = if (isArabic) DEFAULT_BLANK_REPLY_FALLBACK_TEMPLATE_AR else DEFAULT_BLANK_REPLY_FALLBACK_TEMPLATE_EN
        return if (custom != null) {
            if (!isArabic && custom == DEFAULT_BLANK_REPLY_FALLBACK_TEMPLATE_AR) defaultMsg
            else if (isArabic && custom == DEFAULT_BLANK_REPLY_FALLBACK_TEMPLATE_EN) defaultMsg
            else custom
        } else {
            defaultMsg
        }
    }

    suspend fun setModelPrepared(prepared: Boolean) {
        cachedModelPrepared = prepared
        dataStore.edit { prefs ->
            prefs[KEY_MODEL_PREPARED] = prepared
        }
    }

    suspend fun isModelPrepared(): Boolean =
        dataStore.data.first()[KEY_MODEL_PREPARED] ?: true

    fun isModelPreparedSync(): Boolean {
        cachedModelPrepared?.let { return it }
        return runCatching {
            kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
                val value = dataStore.data.first()[KEY_MODEL_PREPARED] ?: true
                cachedModelPrepared = value
                value
            }
        }.getOrDefault(false)
    }
}

/** Snapshot of app-routing preferences. Immutable value object. */
data class RoutingPrefs(
    val listenMainApp: Boolean,
    val listenBusinessApp: Boolean,
    val listenCloneApp: Boolean
)
