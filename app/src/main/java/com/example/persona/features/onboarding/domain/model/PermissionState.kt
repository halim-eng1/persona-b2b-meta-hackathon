package com.example.persona.features.onboarding.domain.model

/**
 * Pure Kotlin domain model representing the application's permission state.
 * Fully decoupled from Android SDK / runtime framework dependencies.
 */
data class PermissionState(
    val isNotificationListenerGranted: Boolean = false,
    val isBatteryOptimizationIgnored: Boolean = false,
    val isPostNotificationsGranted: Boolean = false,
    val isPostNotificationsRequired: Boolean = false, // Injected by caller/platform checker (e.g. true on API 33+)
    val isAutoStartEnabled: Boolean? = null,          // null = not applicable, false = applicable but not confirmed, true = confirmed
    val isManageStorageGranted: Boolean = false
) {
    /**
     * Pure domain validation of critical permissions required to run the auto-reply engine.
     */
    val allCriticalGranted: Boolean
        get() = isNotificationListenerGranted &&
                isBatteryOptimizationIgnored &&
                (!isPostNotificationsRequired || isPostNotificationsGranted) &&
                (isAutoStartEnabled != false)
}
