package com.example.persona.features.onboarding.domain.model

/**
 * Isolated finite state machine model for onboarding progression and permission resolution.
 * Pure Kotlin, decoupled from Android framework APIs.
 */
sealed interface OnboardingStep {
    data object Welcome : OnboardingStep
    data object NotificationAccess : OnboardingStep
    data object BatteryOptimization : OnboardingStep
    data object PostNotifications : OnboardingStep
    data object AutoStartConfig : OnboardingStep
    data object StorageAccess : OnboardingStep
    data object Ready : OnboardingStep

    companion object {
        /**
         * Computes the current active onboarding step based on the provided [PermissionState].
         */
        fun fromPermissionState(state: PermissionState): OnboardingStep = when {
            !state.isNotificationListenerGranted -> NotificationAccess
            !state.isBatteryOptimizationIgnored -> BatteryOptimization
            state.isPostNotificationsRequired && !state.isPostNotificationsGranted -> PostNotifications
            state.isAutoStartEnabled == false -> AutoStartConfig
            else -> Ready
        }

        /**
         * Returns the linear sequence of required onboarding steps for a given permission state.
         */
        fun getRequiredSteps(state: PermissionState): List<OnboardingStep> {
            val steps = mutableListOf<OnboardingStep>(
                NotificationAccess,
                BatteryOptimization
            )
            if (state.isPostNotificationsRequired) {
                steps.add(PostNotifications)
            }
            if (state.isAutoStartEnabled != null) {
                steps.add(AutoStartConfig)
            }
            steps.add(Ready)
            return steps
        }
    }
}

/**
 * UI State container for linear onboarding wizard progression.
 */
data class OnboardingWizardUiState(
    val currentStep: OnboardingStep = OnboardingStep.Welcome,
    val currentStepIndex: Int = 0,
    val totalSteps: Int = 4,
    val permissionState: PermissionState = PermissionState(),
    val manufacturerName: String = "",
    val canProceed: Boolean = false
)
