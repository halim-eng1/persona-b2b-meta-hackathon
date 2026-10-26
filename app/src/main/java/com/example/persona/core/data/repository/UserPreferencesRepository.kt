package com.example.persona.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
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

        // KEY_OWNER_NAME removed — no longer required in B2B architecture.
    }

    // ── Flows ─────────────────────────────────────────────────────────────────

    val isAutoReplyEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_AUTO_REPLY_ENABLED] ?: false
    }

    val stylePreferences: Flow<StylePreferences> = dataStore.data.map { prefs ->
        StylePreferences(
            styleProfile = StyleProfile.fromString(prefs[KEY_STYLE_PROFILE] ?: StyleProfile.CASUAL.name),
            similarityThreshold = prefs[KEY_SIMILARITY_THRESHOLD] ?: 0.75f,
            cooldownMins = prefs[KEY_COOLDOWN_MINS] ?: 5,
            replyDelaySeconds = prefs[KEY_REPLY_DELAY_SECONDS] ?: 2
        )
    }

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

    // ── Suspend setters ───────────────────────────────────────────────────────

    suspend fun setAutoReplyEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[KEY_AUTO_REPLY_ENABLED] = enabled }
    }

    suspend fun setStylePreferences(stylePreferences: StylePreferences) {
        dataStore.edit { prefs ->
            prefs[KEY_STYLE_PROFILE] = stylePreferences.styleProfile.name
            prefs[KEY_SIMILARITY_THRESHOLD] = stylePreferences.similarityThreshold
            prefs[KEY_COOLDOWN_MINS] = stylePreferences.cooldownMins
            prefs[KEY_REPLY_DELAY_SECONDS] = stylePreferences.replyDelaySeconds
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

    // ── Sync readers (for worker/service contexts) ────────────────────────────

    suspend fun isAutoReplyEnabledSync(): Boolean =
        dataStore.data.first()[KEY_AUTO_REPLY_ENABLED] ?: false

    suspend fun getStylePreferencesSync(): StylePreferences {
        val prefs = dataStore.data.first()
        return StylePreferences(
            styleProfile = StyleProfile.fromString(prefs[KEY_STYLE_PROFILE] ?: StyleProfile.CASUAL.name),
            similarityThreshold = prefs[KEY_SIMILARITY_THRESHOLD] ?: 0.75f,
            cooldownMins = prefs[KEY_COOLDOWN_MINS] ?: 5,
            replyDelaySeconds = prefs[KEY_REPLY_DELAY_SECONDS] ?: 2
        )
    }

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
}

/** Snapshot of app-routing preferences. Immutable value object. */
data class RoutingPrefs(
    val listenMainApp: Boolean,
    val listenBusinessApp: Boolean,
    val listenCloneApp: Boolean
)
