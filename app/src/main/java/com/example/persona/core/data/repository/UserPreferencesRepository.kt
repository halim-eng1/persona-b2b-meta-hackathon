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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserPreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
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
        /** Listen to notifications from the main WhatsApp app (com.whatsapp). */
        val KEY_LISTEN_MAIN_APP = booleanPreferencesKey("listen_main_app")

        /** Listen to notifications from WhatsApp Business (com.whatsapp.w4b). */
        val KEY_LISTEN_BUSINESS_APP = booleanPreferencesKey("listen_business_app")

        /**
         * Listen to notifications from a cloned/dual-SIM WhatsApp instance.
         * Clones are identified by [UserHandle.identifier] != 0 on the MAIN_APP package.
         * Off by default — requires explicit user opt-in since clone detection is
         * device-manufacturer-specific and may produce false positives.
         */
        val KEY_LISTEN_CLONE_APP = booleanPreferencesKey("listen_clone_app")

        // ── Greeting / Farewell response templates ────────────────────────────
        /** Response text sent when the ContextDetector classifies the query as GREETING. */
        val KEY_GREETING_TEMPLATE = stringPreferencesKey("greeting_template")

        /** Response text sent when the ContextDetector classifies the query as CLOSING. */
        val KEY_FAREWELL_TEMPLATE = stringPreferencesKey("farewell_template")

        // ── Bot & Store Brand Identity ────────────────────────────────────────
        val KEY_BOT_NAME = stringPreferencesKey("bot_name")
        val KEY_STORE_NAME = stringPreferencesKey("store_name")

        // ── Store Topics Index (hierarchical knowledge indexing) ──────────────
        /** Newline-joined list of Level-1 (`#`) headings extracted from the imported knowledge document. */
        private val STORE_KNOWLEDGE_TOPICS_KEY = stringPreferencesKey("store_knowledge_topics")

        // ── Human Takeover & Safety ───────────────────────────────────────────
        val KEY_AUTO_PAUSE_ON_HITL = booleanPreferencesKey("auto_pause_on_hitl")
        private val AUTO_PAUSE_ON_HITL_KEY = KEY_AUTO_PAUSE_ON_HITL

        const val DEFAULT_BOT_NAME = "مساعد المبيعات"
        const val DEFAULT_STORE_NAME = "متجرنا"
    }

    // ── Flows ─────────────────────────────────────────────────────────────────

    val isAutoReplyEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_AUTO_REPLY_ENABLED] ?: false
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

    /**
     * Store Topics Index: the Level-1 (`#`) section headings of the last imported
     * knowledge document, exposed as a reactive list. Consumed by [PromptContextBuilder]
     * to inject the `<store_topics>` block into the system prompt.
     */
    val storeKnowledgeTopicsFlow: Flow<List<String>> = dataStore.data.map { preferences ->
        val raw = preferences[STORE_KNOWLEDGE_TOPICS_KEY] ?: ""
        if (raw.isBlank()) emptyList() else raw.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
    }

    val isAutoPauseOnHitlEnabledFlow: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[AUTO_PAUSE_ON_HITL_KEY] ?: false // Default is FALSE
    }

    // ── Suspend setters ───────────────────────────────────────────────────────

    suspend fun setAutoReplyEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[KEY_AUTO_REPLY_ENABLED] = enabled }
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

    /**
     * Atomically persists the Store Topics Index (one topic per line).
     * Overwrites any previous list — re-importing a knowledge document supersedes it.
     */
    suspend fun setStoreKnowledgeTopics(topics: List<String>) {
        dataStore.edit { preferences ->
            preferences[STORE_KNOWLEDGE_TOPICS_KEY] = topics.joinToString("\n")
        }
    }

    /** Removes the Store Topics Index (called when the merchant clears/resets the Knowledge Base). */
    suspend fun clearStoreKnowledgeTopics() {
        dataStore.edit { preferences ->
            preferences.remove(STORE_KNOWLEDGE_TOPICS_KEY)
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

    /**
     * Reads all routing toggles and template strings in a single DataStore read.
     * Intended for use in the NotificationListenerService hot path.
     */
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
}

/** Snapshot of app-routing preferences. Immutable value object. */
data class RoutingPrefs(
    val listenMainApp: Boolean,
    val listenBusinessApp: Boolean,
    val listenCloneApp: Boolean
)
