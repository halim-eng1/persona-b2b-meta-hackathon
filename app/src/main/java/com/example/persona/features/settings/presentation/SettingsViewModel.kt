package com.example.persona.features.settings.presentation

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.persona.core.data.repository.UserPreferencesRepository
import com.example.persona.features.auto_reply.domain.model.PersonaTone
import com.example.persona.features.auto_reply.domain.model.StylePreferences
import com.example.persona.features.auto_reply.domain.model.StyleProfile
import com.example.persona.features.onboarding.data.PermissionChecker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
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
    val personaTone: PersonaTone = PersonaTone.WITTY_EGYPTIAN,
    val styleProfile: StyleProfile = StyleProfile.CASUAL,
    val thresholdLevel: ThresholdLevel = ThresholdLevel.BALANCED,
    val cooldownMins: Int = 5,
    val replyDelaySeconds: Int = 2,
    val replyDelayInput: String = "2",
    val replyDelayError: String? = null,
    val listenMainApp: Boolean = true,
    val listenBusinessApp: Boolean = true,
    val listenCloneApp: Boolean = false,
    val greetingTemplate: String = "",
    val farewellTemplate: String = "",
    val botName: String = UserPreferencesRepository.DEFAULT_BOT_NAME,
    val storeName: String = UserPreferencesRepository.DEFAULT_STORE_NAME,
    val isAutoPauseOnHitl: Boolean = false,
    val isStorageAccessGranted: Boolean = false,
    val isLoading: Boolean = true
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val permissionChecker: PermissionChecker? = null
) : ViewModel() {

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
        viewModelScope.launch {
            launch {
                userPreferencesRepository.stylePreferences.collect { prefs ->
                    _uiState.update {
                        it.copy(
                            personaTone = prefs.personaTone,
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
                userPreferencesRepository.listenMainApp.collect { value ->
                    _uiState.update { it.copy(listenMainApp = value, isLoading = false) }
                }
            }
            launch {
                userPreferencesRepository.listenBusinessApp.collect { value ->
                    _uiState.update { it.copy(listenBusinessApp = value, isLoading = false) }
                }
            }
            launch {
                userPreferencesRepository.listenCloneApp.collect { value ->
                    _uiState.update { it.copy(listenCloneApp = value, isLoading = false) }
                }
            }
            launch {
                userPreferencesRepository.greetingTemplate.collect { value ->
                    _uiState.update { it.copy(greetingTemplate = value, isLoading = false) }
                }
            }
            launch {
                userPreferencesRepository.farewellTemplate.collect { value ->
                    _uiState.update { it.copy(farewellTemplate = value, isLoading = false) }
                }
            }
            launch {
                userPreferencesRepository.botName.collect { value ->
                    _uiState.update { it.copy(botName = value, isLoading = false) }
                }
            }
            launch {
                userPreferencesRepository.storeName.collect { value ->
                    _uiState.update { it.copy(storeName = value, isLoading = false) }
                }
            }
            launch {
                userPreferencesRepository.isAutoPauseOnHitlEnabledFlow.collect { value ->
                    _uiState.update { it.copy(isAutoPauseOnHitl = value, isLoading = false) }
                }
            }
        }
    }

    fun refreshStoragePermission() {
        val granted = permissionChecker?.isManageStorageGranted() ?: false
        _uiState.update { it.copy(isStorageAccessGranted = granted) }
    }

    fun requestStoragePermission(context: Context) {
        permissionChecker?.requestManageStoragePermission(context)
    }

    fun setPersonaTone(tone: PersonaTone) {
        _uiState.update { it.copy(personaTone = tone) }
        save()
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

    fun setListenMainApp(enabled: Boolean) {
        _uiState.update { it.copy(listenMainApp = enabled) }
        viewModelScope.launch {
            userPreferencesRepository.setListenMainApp(enabled)
        }
    }

    fun setListenBusinessApp(enabled: Boolean) {
        _uiState.update { it.copy(listenBusinessApp = enabled) }
        viewModelScope.launch {
            userPreferencesRepository.setListenBusinessApp(enabled)
        }
    }

    fun setListenCloneApp(enabled: Boolean) {
        _uiState.update { it.copy(listenCloneApp = enabled) }
        viewModelScope.launch {
            userPreferencesRepository.setListenCloneApp(enabled)
        }
    }

    fun setGreetingTemplate(text: String) {
        _uiState.update { it.copy(greetingTemplate = text) }
        viewModelScope.launch {
            userPreferencesRepository.setGreetingTemplate(text)
        }
    }

    fun setFarewellTemplate(text: String) {
        _uiState.update { it.copy(farewellTemplate = text) }
        viewModelScope.launch {
            userPreferencesRepository.setFarewellTemplate(text)
        }
    }

    fun setBotName(name: String) {
        _uiState.update { it.copy(botName = name) }
        viewModelScope.launch {
            userPreferencesRepository.setBotName(name)
        }
    }

    fun setStoreName(name: String) {
        _uiState.update { it.copy(storeName = name) }
        viewModelScope.launch {
            userPreferencesRepository.setStoreName(name)
        }
    }

    fun onAutoPauseOnHitlToggled(enabled: Boolean) {
        _uiState.update { it.copy(isAutoPauseOnHitl = enabled) }
        viewModelScope.launch {
            userPreferencesRepository.setAutoPauseOnHitl(enabled)
        }
    }

    private fun save() {
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
}
