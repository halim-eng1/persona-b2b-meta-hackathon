package com.example.persona.features.onboarding.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingStepTest {

    @Test
    fun `when NLS is missing, step is NotificationAccess`() {
        val state = PermissionState(isNotificationListenerGranted = false)
        assertEquals(OnboardingStep.NotificationAccess, OnboardingStep.fromPermissionState(state))
    }

    @Test
    fun `when NLS is granted but battery optimization not ignored, step is BatteryOptimization`() {
        val state = PermissionState(
            isNotificationListenerGranted = true,
            isBatteryOptimizationIgnored = false
        )
        assertEquals(OnboardingStep.BatteryOptimization, OnboardingStep.fromPermissionState(state))
    }

    @Test
    fun `when battery optimization ignored and post notification required, step is PostNotifications`() {
        val state = PermissionState(
            isNotificationListenerGranted = true,
            isBatteryOptimizationIgnored = true,
            isPostNotificationsRequired = true,
            isPostNotificationsGranted = false
        )
        assertEquals(OnboardingStep.PostNotifications, OnboardingStep.fromPermissionState(state))
    }

    @Test
    fun `when OEM auto-start is unconfirmed, step is AutoStartConfig`() {
        val state = PermissionState(
            isNotificationListenerGranted = true,
            isBatteryOptimizationIgnored = true,
            isPostNotificationsRequired = false,
            isAutoStartEnabled = false
        )
        assertEquals(OnboardingStep.AutoStartConfig, OnboardingStep.fromPermissionState(state))
    }

    @Test
    fun `when all critical permissions granted, step is Ready`() {
        val state = PermissionState(
            isNotificationListenerGranted = true,
            isBatteryOptimizationIgnored = true,
            isPostNotificationsRequired = true,
            isPostNotificationsGranted = true,
            isAutoStartEnabled = true
        )
        assertEquals(OnboardingStep.Ready, OnboardingStep.fromPermissionState(state))
    }

    @Test
    fun `required steps list includes only applicable steps`() {
        val stateStock = PermissionState(
            isPostNotificationsRequired = false,
            isAutoStartEnabled = null
        )
        val stepsStock = OnboardingStep.getRequiredSteps(stateStock)
        assertEquals(listOf(OnboardingStep.NotificationAccess, OnboardingStep.BatteryOptimization, OnboardingStep.Ready), stepsStock)

        val stateOemTiramisu = PermissionState(
            isPostNotificationsRequired = true,
            isAutoStartEnabled = false
        )
        val stepsOem = OnboardingStep.getRequiredSteps(stateOemTiramisu)
        assertEquals(
            listOf(
                OnboardingStep.NotificationAccess,
                OnboardingStep.BatteryOptimization,
                OnboardingStep.PostNotifications,
                OnboardingStep.AutoStartConfig,
                OnboardingStep.Ready
            ),
            stepsOem
        )
    }
}
