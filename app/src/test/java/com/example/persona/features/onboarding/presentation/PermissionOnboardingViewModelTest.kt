package com.example.persona.features.onboarding.presentation

import com.example.persona.core.data.repository.UserPreferencesRepository
import com.example.persona.features.auto_reply.domain.usecase.ToggleAutoReplyUseCase
import com.example.persona.features.onboarding.data.AutoStartHelper
import com.example.persona.features.onboarding.data.PermissionChecker
import com.example.persona.features.onboarding.domain.model.OnboardingStep
import com.example.persona.features.onboarding.domain.model.PermissionState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PermissionOnboardingViewModelTest {

    private val permissionChecker = mockk<PermissionChecker>(relaxed = true)
    private val autoStartHelper = mockk<AutoStartHelper>(relaxed = true)
    private val toggleAutoReply = mockk<ToggleAutoReplyUseCase>(relaxed = true)
    private val userPreferencesRepository = mockk<UserPreferencesRepository>(relaxed = true)

    private val testDispatcher = StandardTestDispatcher()
    private val autoStartFlow = MutableStateFlow(false)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { autoStartHelper.getManufacturerName() } returns "Xiaomi"
        every { userPreferencesRepository.isAutoStartConfirmedFlow } returns autoStartFlow
        coEvery { userPreferencesRepository.isAutoStartConfirmedSync() } returns false

        every { permissionChecker.getFullState() } returns PermissionState(
            isNotificationListenerGranted = false,
            isBatteryOptimizationIgnored = false,
            isPostNotificationsGranted = false,
            isPostNotificationsRequired = false,
            isAutoStartEnabled = false
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial wizardState reflects notification access as first step`() = runTest(testDispatcher) {
        val viewModel = PermissionOnboardingViewModel(
            permissionChecker = permissionChecker,
            autoStartHelper = autoStartHelper,
            toggleAutoReply = toggleAutoReply,
            userPreferencesRepository = userPreferencesRepository
        )
        advanceUntilIdle()

        val wizard = viewModel.wizardState.value
        assertEquals(OnboardingStep.NotificationAccess, wizard.currentStep)
        assertFalse(wizard.canProceed)
        assertEquals("Xiaomi", wizard.manufacturerName)
    }

    @Test
    fun `toggleAutoStartConfirmed persists to repository and updates wizardState`() = runTest(testDispatcher) {
        val viewModel = PermissionOnboardingViewModel(
            permissionChecker = permissionChecker,
            autoStartHelper = autoStartHelper,
            toggleAutoReply = toggleAutoReply,
            userPreferencesRepository = userPreferencesRepository
        )
        advanceUntilIdle()

        viewModel.toggleAutoStartConfirmed(true)
        advanceUntilIdle()

        assertTrue(viewModel.permissionState.value.isAutoStartEnabled == true)
        coVerify(exactly = 1) { userPreferencesRepository.setAutoStartConfirmed(true) }
    }

    @Test
    fun `when all critical permissions are granted, canProceed becomes true`() = runTest(testDispatcher) {
        every { permissionChecker.getFullState() } returns PermissionState(
            isNotificationListenerGranted = true,
            isBatteryOptimizationIgnored = true,
            isPostNotificationsGranted = true,
            isPostNotificationsRequired = false,
            isAutoStartEnabled = true
        )

        val viewModel = PermissionOnboardingViewModel(
            permissionChecker = permissionChecker,
            autoStartHelper = autoStartHelper,
            toggleAutoReply = toggleAutoReply,
            userPreferencesRepository = userPreferencesRepository
        )
        advanceUntilIdle()

        val wizard = viewModel.wizardState.value
        assertTrue(wizard.canProceed)
        assertEquals(OnboardingStep.Ready, wizard.currentStep)

        viewModel.onAllPermissionsGranted()
        advanceUntilIdle()
        coVerify(exactly = 1) { toggleAutoReply(enable = true) }
    }
}
