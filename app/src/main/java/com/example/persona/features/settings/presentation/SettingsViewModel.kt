package com.example.persona.features.settings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.persona.core.data.repository.UserPreferencesRepository
import com.example.persona.features.auto_reply.domain.model.StylePreferences
import com.example.persona.features.auto_reply.domain.model.StyleProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Human-friendly threshold levels mapped from the user's selection.
 * Conservative = strict matching, Aggressive = wide matching.
 */
enum class ThresholdLevel(val displayName: String, val threshold: Float) {
    CONSERVATIVE("Conservative", 0.85f),
    BALANCED("Balanced", 0.75f),
    AGGRESSIVE("Aggressive", 0.65f);

    companion object {
        fun fromThreshold(value: Float): ThresholdLevel = when {
            value >= 0.80f -> CONSERVATIVE
            value >= 0.70f -> BALANCED
            else           -> AGGRESSIVE
        }
    }
}

data class SettingsUiState(
    val styleProfile: StyleProfile = StyleProfile.CASUAL,
    val thresholdLevel: ThresholdLevel = ThresholdLevel.BALANCED,
    val cooldownMins: Int = 5,
    val replyDelaySeconds: Int = 2,
    val replyDelayInput: String = "2",
    val replyDelayError: String? = null,
    val ownerName: String = "",
    val isLoading: Boolean = true
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            launch {
                userPreferencesRepository.stylePreferences.collect { prefs ->
                    _uiState.update {
                        it.copy(
                            styleProfile = prefs.styleProfile,
                            thresholdLevel = ThresholdLevel.fromThreshold(prefs.similarityThreshold),
                            cooldownMins = prefs.cooldownMins,
                            replyDelaySeconds = prefs.replyDelaySeconds,
                            replyDelayInput = if (it.replyDelayError == null) prefs.replyDelaySeconds.toString() else it.replyDelayInput,
                            isLoading = false
                        )
                    }
                }
            }
            launch {
                userPreferencesRepository.ownerName.collect { name ->
                    _uiState.update {
                        it.copy(
                            ownerName = name,
                            isLoading = false
                        )
                    }
                }
            }
        }
    }

    fun setStyleProfile(profile: StyleProfile) {
        _uiState.update { it.copy(styleProfile = profile) }
        save()
    }

    fun setThresholdLevel(level: ThresholdLevel) {
        _uiState.update { it.copy(thresholdLevel = level) }
        save()
    }

    fun setCooldownMins(mins: Int) {
        _uiState.update { it.copy(cooldownMins = mins) }
        save()
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
            save()
        }
    }

    fun setOwnerName(name: String) {
        _uiState.update { it.copy(ownerName = name) }
        viewModelScope.launch {
            userPreferencesRepository.setOwnerName(name)
        }
    }

    private fun save() {
        val s = _uiState.value
        if (s.replyDelayError != null) return

        viewModelScope.launch {
            userPreferencesRepository.setStylePreferences(
                StylePreferences(
                    styleProfile = s.styleProfile,
                    similarityThreshold = s.thresholdLevel.threshold,
                    cooldownMins = s.cooldownMins,
                    replyDelaySeconds = s.replyDelaySeconds
                )
            )
        }
    }
}
