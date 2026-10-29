package com.example.persona.features.onboarding.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    private val toggleAutoReply: ToggleAutoReplyUseCase
) : ViewModel() {

    private val _permissionState = MutableStateFlow(PermissionState())
    val permissionState: StateFlow<PermissionState> = _permissionState.asStateFlow()

    val wizardState: StateFlow<OnboardingWizardUiState> = _permissionState.map { state ->
        val currentStep = OnboardingStep.fromPermissionState(state)
        val requiredSteps = OnboardingStep.getRequiredSteps(state)
        val currentIndex = requiredSteps.indexOf(currentStep).coerceAtLeast(0)
        OnboardingWizardUiState(
            currentStep = currentStep,
            currentStepIndex = currentIndex,
            totalSteps = requiredSteps.size,
            permissionState = state,
            manufacturerName = autoStartHelper.getManufacturerName(),
            canProceed = state.allCriticalGranted
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = OnboardingWizardUiState()
    )

    init {
        refreshPermissions()
    }

    fun refreshPermissions() {
        val currentCheckerState = permissionChecker.getFullState()
        // Retain the manual AutoStart selection by the user if it exists
        val prevAutoStart = _permissionState.value.isAutoStartEnabled
        _permissionState.value = currentCheckerState.copy(
            isAutoStartEnabled = prevAutoStart ?: currentCheckerState.isAutoStartEnabled
        )
    }

    fun toggleAutoStartConfirmed(confirmed: Boolean) {
        _permissionState.value = _permissionState.value.copy(
            isAutoStartEnabled = confirmed
        )
    }

    fun getManufacturerName(): String = autoStartHelper.getManufacturerName()
    
    fun launchAutoStart(): Boolean = autoStartHelper.launchAutoStartSettings()

    fun onAllPermissionsGranted() {
        viewModelScope.launch {
            toggleAutoReply(enable = true)
        }
    }
}
