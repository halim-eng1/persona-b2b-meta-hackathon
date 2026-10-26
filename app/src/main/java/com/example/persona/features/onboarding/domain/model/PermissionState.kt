package com.example.persona.features.onboarding.domain.model

import android.os.Build

data class PermissionState(
    val isNotificationListenerGranted: Boolean = false,
    val isBatteryOptimizationIgnored: Boolean = false,
    val isPostNotificationsGranted: Boolean = false,  // Android 13+ only
    val isAutoStartEnabled: Boolean? = null            // null = not applicable, false = applicable but not confirmed, true = confirmed
) {
    val allCriticalGranted: Boolean
        get() = isNotificationListenerGranted &&
                isBatteryOptimizationIgnored &&
                (Build.VERSION.SDK_INT < 33 || isPostNotificationsGranted) &&
                (isAutoStartEnabled != false)
}
