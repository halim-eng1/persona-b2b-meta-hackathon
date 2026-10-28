package com.example.persona.features.settings.presentation

import com.example.persona.core.data.repository.UserPreferencesRepository
import com.example.persona.features.auto_reply.domain.model.PersonaTone
import com.example.persona.features.auto_reply.domain.model.StylePreferences
import com.example.persona.features.auto_reply.domain.model.StyleProfile
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
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
class SettingsViewModelTest {

    private val userPreferencesRepository = mockk<UserPreferencesRepository>(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()

    private val autoPauseFlow = MutableStateFlow(false)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { userPreferencesRepository.stylePreferences } returns flowOf(
            StylePreferences(StyleProfile.CASUAL, 0.75f, 5, 2)
        )
        every { userPreferencesRepository.listenMainApp } returns flowOf(true)
        every { userPreferencesRepository.listenBusinessApp } returns flowOf(true)
        every { userPreferencesRepository.listenCloneApp } returns flowOf(false)
        every { userPreferencesRepository.greetingTemplate } returns flowOf("")
        every { userPreferencesRepository.farewellTemplate } returns flowOf("")
        every { userPreferencesRepository.botName } returns flowOf("مساعد المبيعات")
        every { userPreferencesRepository.storeName } returns flowOf("متجرنا")
        every { userPreferencesRepository.isAutoPauseOnHitlEnabledFlow } returns autoPauseFlow
        every { userPreferencesRepository.hitlHoldingMessageFlow } returns flowOf(UserPreferencesRepository.DEFAULT_HITL_HOLDING_MESSAGE)
        every { userPreferencesRepository.mediaFallbackMessageFlow } returns flowOf(UserPreferencesRepository.DEFAULT_MEDIA_FALLBACK_MESSAGE)
        every { userPreferencesRepository.blankReplyFallbackTemplateFlow } returns flowOf(UserPreferencesRepository.DEFAULT_BLANK_REPLY_FALLBACK_TEMPLATE)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `default auto_pause_on_hitl is false in uiState and StateFlow`() = runTest(testDispatcher) {
        val viewModel = SettingsViewModel(userPreferencesRepository)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isAutoPauseOnHitl)
        assertFalse(viewModel.isAutoPauseOnHitl.value)
    }

    @Test
    fun `when isAutoPauseOnHitlEnabledFlow emits true, uiState updates`() = runTest(testDispatcher) {
        val viewModel = SettingsViewModel(userPreferencesRepository)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isAutoPauseOnHitl)

        autoPauseFlow.value = true
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isAutoPauseOnHitl)
    }

    @Test
    fun `onAutoPauseOnHitlToggled updates state and calls repository`() = runTest(testDispatcher) {
        val viewModel = SettingsViewModel(userPreferencesRepository)
        advanceUntilIdle()

        viewModel.onAutoPauseOnHitlToggled(true)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isAutoPauseOnHitl)
        coVerify(exactly = 1) { userPreferencesRepository.setAutoPauseOnHitl(true) }

        viewModel.onAutoPauseOnHitlToggled(false)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isAutoPauseOnHitl)
        coVerify(exactly = 1) { userPreferencesRepository.setAutoPauseOnHitl(false) }
    }

    @Test
    fun `default personaTone in uiState is WITTY_EGYPTIAN`() = runTest(testDispatcher) {
        val viewModel = SettingsViewModel(userPreferencesRepository)
        advanceUntilIdle()

        assertEquals(PersonaTone.WITTY_EGYPTIAN, viewModel.uiState.value.personaTone)
    }

    @Test
    fun `setPersonaTone updates uiState and calls repository with new tone`() = runTest(testDispatcher) {
        val viewModel = SettingsViewModel(userPreferencesRepository)
        advanceUntilIdle()

        viewModel.setPersonaTone(PersonaTone.STANDARD_POLITE)
        advanceUntilIdle()

        assertEquals(PersonaTone.STANDARD_POLITE, viewModel.uiState.value.personaTone)
        coVerify(exactly = 1) {
            userPreferencesRepository.setStylePreferences(match {
                it.personaTone == PersonaTone.STANDARD_POLITE
            })
        }
    }

    @Test
    fun `fallback messages have default values in uiState and can be updated`() = runTest(testDispatcher) {
        val viewModel = SettingsViewModel(userPreferencesRepository)
        advanceUntilIdle()

        assertEquals(UserPreferencesRepository.DEFAULT_HITL_HOLDING_MESSAGE, viewModel.uiState.value.hitlHoldingMessage)
        assertEquals(UserPreferencesRepository.DEFAULT_MEDIA_FALLBACK_MESSAGE, viewModel.uiState.value.mediaFallbackMessage)
        assertEquals(UserPreferencesRepository.DEFAULT_BLANK_REPLY_FALLBACK_TEMPLATE, viewModel.uiState.value.blankReplyFallbackTemplate)

        viewModel.setHitlHoldingMessage("تحويل لممثل خدمة العملاء")
        advanceUntilIdle()
        assertEquals("تحويل لممثل خدمة العملاء", viewModel.uiState.value.hitlHoldingMessage)
        coVerify(exactly = 1) { userPreferencesRepository.setHitlHoldingMessage("تحويل لممثل خدمة العملاء") }

        viewModel.setMediaFallbackMessage("لا يمكن معالجة الملف")
        advanceUntilIdle()
        assertEquals("لا يمكن معالجة الملف", viewModel.uiState.value.mediaFallbackMessage)
        coVerify(exactly = 1) { userPreferencesRepository.setMediaFallbackMessage("لا يمكن معالجة الملف") }

        viewModel.setBlankReplyFallbackTemplate("أهلاً بك في %s")
        advanceUntilIdle()
        assertEquals("أهلاً بك في %s", viewModel.uiState.value.blankReplyFallbackTemplate)
        coVerify(exactly = 1) { userPreferencesRepository.setBlankReplyFallbackTemplate("أهلاً بك في %s") }
    }
}
