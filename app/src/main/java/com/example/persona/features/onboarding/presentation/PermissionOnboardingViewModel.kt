package com.example.persona.features.onboarding.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.persona.features.auto_reply.domain.usecase.ToggleAutoReplyUseCase
import com.example.persona.features.onboarding.data.AutoStartHelper
import com.example.persona.features.onboarding.data.PermissionChecker
import com.example.persona.features.onboarding.domain.model.PermissionState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
