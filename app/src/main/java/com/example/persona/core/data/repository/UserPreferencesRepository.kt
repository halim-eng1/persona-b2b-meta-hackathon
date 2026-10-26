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
        val KEY_AUTO_REPLY_ENABLED = booleanPreferencesKey("auto_reply_enabled")
        val KEY_STYLE_PROFILE = stringPreferencesKey("style_profile")
        val KEY_SIMILARITY_THRESHOLD = floatPreferencesKey("similarity_threshold")
        val KEY_COOLDOWN_MINS = intPreferencesKey("cooldown_mins")
        val KEY_REPLY_DELAY_SECONDS = intPreferencesKey("reply_delay_seconds")
        val KEY_OWNER_NAME = stringPreferencesKey("owner_whatsapp_name")
    }

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

    val ownerName: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_OWNER_NAME] ?: ""
    }

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

    suspend fun setOwnerName(name: String) {
        dataStore.edit { prefs -> prefs[KEY_OWNER_NAME] = name }
    }

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

    suspend fun getOwnerNameSync(): String =
        dataStore.data.first()[KEY_OWNER_NAME] ?: ""
}
