package com.example.persona.features.onboarding.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionStateTest {

    @Test
    fun `when critical permissions are missing, allCriticalGranted is false`() {
        val state = PermissionState(
            isNotificationListenerGranted = false,
            isBatteryOptimizationIgnored = false
        )
        assertFalse(state.allCriticalGranted)
    }

    @Test
    fun `when NLS and Battery granted on legacy Android without post notifications required, allCriticalGranted is true`() {
        val state = PermissionState(
            isNotificationListenerGranted = true,
            isBatteryOptimizationIgnored = true,
            isPostNotificationsRequired = false,
            isPostNotificationsGranted = false,
            isAutoStartEnabled = null
        )
        assertTrue(state.allCriticalGranted)
    }

    @Test
    fun `when post notifications required on API 33+, allCriticalGranted requires post notifications granted`() {
        val pendingPostState = PermissionState(
            isNotificationListenerGranted = true,
            isBatteryOptimizationIgnored = true,
            isPostNotificationsRequired = true,
            isPostNotificationsGranted = false
        )
        assertFalse(pendingPostState.allCriticalGranted)

        val grantedPostState = pendingPostState.copy(isPostNotificationsGranted = true)
        assertTrue(grantedPostState.allCriticalGranted)
    }

    @Test
    fun `when OEM auto-start is unconfirmed (false), allCriticalGranted is false`() {
        val oemUnconfirmed = PermissionState(
            isNotificationListenerGranted = true,
            isBatteryOptimizationIgnored = true,
            isPostNotificationsRequired = false,
            isAutoStartEnabled = false
        )
        assertFalse(oemUnconfirmed.allCriticalGranted)

        val oemConfirmed = oemUnconfirmed.copy(isAutoStartEnabled = true)
        assertTrue(oemConfirmed.allCriticalGranted)
    }
}
