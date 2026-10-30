package com.example.persona.features.onboarding.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.persona.core.data.repository.UserPreferencesRepository
import com.example.persona.features.auto_reply.domain.usecase.ToggleAutoReplyUseCase
import com.example.persona.features.onboarding.data.AutoStartHelper
import com.example.persona.features.onboarding.data.PermissionChecker
import com.example.persona.features.onboarding.domain.model.OnboardingStep
import com.example.persona.features.onboarding.domain.model.OnboardingWizardUiState
import com.example.persona.features.onboarding.domain.model.PermissionState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PermissionOnboardingViewModel @Inject constructor(
    private val permissionChecker: PermissionChecker,
    private val autoStartHelper: AutoStartHelper,
    private val toggleAutoReply: ToggleAutoReplyUseCase,
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    private val _permissionState = MutableStateFlow(PermissionState())
    val permissionState: StateFlow<PermissionState> = _permissionState.asStateFlow()

    private fun computeWizardState(state: PermissionState): OnboardingWizardUiState {
        val currentStep = OnboardingStep.fromPermissionState(state)
        val requiredSteps = OnboardingStep.getRequiredSteps(state)
        val currentIndex = requiredSteps.indexOf(currentStep).coerceAtLeast(0)
        return OnboardingWizardUiState(
            currentStep = currentStep,
            currentStepIndex = currentIndex,
            totalSteps = requiredSteps.size,
            permissionState = state,
            manufacturerName = autoStartHelper.getManufacturerName(),
            canProceed = state.allCriticalGranted
        )
    }

    val wizardState: StateFlow<OnboardingWizardUiState> = _permissionState.map { state ->
        computeWizardState(state)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = computeWizardState(_permissionState.value)
    )

    init {
        // Collect persisted auto-start confirmation
        viewModelScope.launch {
            userPreferencesRepository.isAutoStartConfirmedFlow.collect { persistedConfirmed ->
                val current = _permissionState.value
                if (current.isAutoStartEnabled != null && persistedConfirmed) {
                    _permissionState.value = current.copy(isAutoStartEnabled = true)
                }
            }
        }
        refreshPermissions()
    }

    fun refreshPermissions() {
        val currentCheckerState = permissionChecker.getFullState()
        val prevAutoStart = _permissionState.value.isAutoStartEnabled
        _permissionState.value = currentCheckerState.copy(
            isAutoStartEnabled = prevAutoStart ?: currentCheckerState.isAutoStartEnabled
        )

        viewModelScope.launch {
            val persisted = userPreferencesRepository.isAutoStartConfirmedSync()
            if (persisted && _permissionState.value.isAutoStartEnabled != null) {
                _permissionState.value = _permissionState.value.copy(isAutoStartEnabled = true)
            }
        }
    }

    fun toggleAutoStartConfirmed(confirmed: Boolean) {
        _permissionState.value = _permissionState.value.copy(
            isAutoStartEnabled = confirmed
        )
        viewModelScope.launch {
            userPreferencesRepository.setAutoStartConfirmed(confirmed)
        }
    }

    fun getManufacturerName(): String = autoStartHelper.getManufacturerName()

    fun launchAutoStart(): Boolean = autoStartHelper.launchAutoStartSettings()

    fun onAllPermissionsGranted() {
        viewModelScope.launch {
            toggleAutoReply(enable = true)
        }
    }
}
