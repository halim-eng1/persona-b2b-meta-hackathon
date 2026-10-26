package com.example.persona.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
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
    }

    val isAutoReplyEnabled: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[KEY_AUTO_REPLY_ENABLED] ?: false
    }

    suspend fun setAutoReplyEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[KEY_AUTO_REPLY_ENABLED] = enabled
        }
    }

    suspend fun isAutoReplyEnabledSync(): Boolean {
        return dataStore.data.first()[KEY_AUTO_REPLY_ENABLED] ?: false
    }
}
