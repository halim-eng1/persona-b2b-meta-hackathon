package com.example.persona.features.settings.presentation

import com.example.persona.core.data.repository.BusinessHoursSchedule
import com.example.persona.core.data.repository.UserPreferencesRepository
import com.example.persona.features.auto_reply.domain.model.PersonaTone
import com.example.persona.features.auto_reply.domain.model.StylePreferences
import com.example.persona.features.auto_reply.domain.model.StyleProfile
import com.example.persona.features.settings.data.repository.InMemorySecureCredentialsRepository
import com.example.persona.features.settings.domain.repository.ModelProviders
import io.mockk.coEvery
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
    private val secureCredentialsRepository = InMemorySecureCredentialsRepository()
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

        every { userPreferencesRepository.providerOrderFlow } returns flowOf(UserPreferencesRepository.DEFAULT_PROVIDER_ORDER)
        every { userPreferencesRepository.businessHoursScheduleFlow } returns flowOf(BusinessHoursSchedule())
        every { userPreferencesRepository.ignoreGroupsFlow } returns flowOf(true)
        every { userPreferencesRepository.blacklistedNumbersFlow } returns flowOf(emptySet())
        every { userPreferencesRepository.isAutoStartConfirmedFlow } returns flowOf(false)
        coEvery { userPreferencesRepository.isAutoStartConfirmedSync() } returns false
        coEvery { userPreferencesRepository.getProviderModelsSync(any()) } answers {
            val provider = firstArg<String>()
            UserPreferencesRepository.DEFAULT_PROVIDER_MODELS[provider] ?: emptyList()
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `default auto_pause_on_hitl is false in uiState and StateFlow`() = runTest(testDispatcher) {
        val viewModel = SettingsViewModel(userPreferencesRepository, secureCredentialsRepository)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isAutoPauseOnHitl)
        assertFalse(viewModel.isAutoPauseOnHitl.value)
    }

    @Test
    fun `when isAutoPauseOnHitlEnabledFlow emits true, uiState updates`() = runTest(testDispatcher) {
        val viewModel = SettingsViewModel(userPreferencesRepository, secureCredentialsRepository)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isAutoPauseOnHitl)

        autoPauseFlow.value = true
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isAutoPauseOnHitl)
    }

    @Test
    fun `onAutoPauseOnHitlToggled updates state and calls repository`() = runTest(testDispatcher) {
        val viewModel = SettingsViewModel(userPreferencesRepository, secureCredentialsRepository)
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
        val viewModel = SettingsViewModel(userPreferencesRepository, secureCredentialsRepository)
        advanceUntilIdle()

        assertEquals(PersonaTone.WITTY_EGYPTIAN, viewModel.uiState.value.personaTone)
    }

    @Test
    fun `setPersonaTone updates uiState and calls repository with new tone`() = runTest(testDispatcher) {
        val viewModel = SettingsViewModel(userPreferencesRepository, secureCredentialsRepository)
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
        val viewModel = SettingsViewModel(userPreferencesRepository, secureCredentialsRepository)
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

    @Test
    fun `tab navigation and provider selection updates uiState`() = runTest(testDispatcher) {
        val viewModel = SettingsViewModel(userPreferencesRepository, secureCredentialsRepository)
        advanceUntilIdle()

        assertEquals(0, viewModel.uiState.value.selectedTab)

        viewModel.selectTab(2)
        assertEquals(2, viewModel.uiState.value.selectedTab)

        viewModel.selectProviderForEdit(ModelProviders.GEMINI)
        advanceUntilIdle()
        assertEquals(ModelProviders.GEMINI, viewModel.uiState.value.selectedProviderForEdit)
    }

    @Test
    fun `add and remove api keys per provider delegates to secure repository`() = runTest(testDispatcher) {
        val viewModel = SettingsViewModel(userPreferencesRepository, secureCredentialsRepository)
        advanceUntilIdle()

        viewModel.addApiKey(ModelProviders.QWEN, "sk-test-qwen-key-1")
        advanceUntilIdle()

        assertEquals(listOf("sk-test-qwen-key-1"), secureCredentialsRepository.getApiKeys(ModelProviders.QWEN))
        assertEquals(listOf("sk-test-qwen-key-1"), viewModel.uiState.value.providerConfigs[ModelProviders.QWEN]?.keys)

        viewModel.removeApiKey(ModelProviders.QWEN, "sk-test-qwen-key-1")
        advanceUntilIdle()

        assertTrue(secureCredentialsRepository.getApiKeys(ModelProviders.QWEN).isEmpty())
        assertTrue(viewModel.uiState.value.providerConfigs[ModelProviders.QWEN]?.keys.orEmpty().isEmpty())
    }

    @Test
    fun `moving provider up and down reorders fallback chain`() = runTest(testDispatcher) {
        val viewModel = SettingsViewModel(userPreferencesRepository, secureCredentialsRepository)
        advanceUntilIdle()

        val initialOrder = viewModel.uiState.value.providerOrder
        val secondProvider = initialOrder[1]

        viewModel.moveProviderUp(secondProvider)
        advanceUntilIdle()

        assertEquals(secondProvider, viewModel.uiState.value.providerOrder[0])
        coVerify(atLeast = 1) { userPreferencesRepository.setProviderOrder(any()) }

        viewModel.moveProviderDown(secondProvider)
        advanceUntilIdle()

        assertEquals(secondProvider, viewModel.uiState.value.providerOrder[1])
    }

    @Test
    fun `business hours and blacklist delegates to userPreferencesRepository`() = runTest(testDispatcher) {
        val viewModel = SettingsViewModel(userPreferencesRepository, secureCredentialsRepository)
        advanceUntilIdle()

        viewModel.setBusinessHoursEnabled(true)
        advanceUntilIdle()
        coVerify(exactly = 1) { userPreferencesRepository.setBusinessHoursEnabled(true) }

        viewModel.setWorkingDays(setOf("MONDAY", "TUESDAY"))
        advanceUntilIdle()
        coVerify(exactly = 1) { userPreferencesRepository.setWorkingDays(setOf("MONDAY", "TUESDAY")) }

        viewModel.setWorkingHours("09:00", "18:00")
        advanceUntilIdle()
        coVerify(exactly = 1) { userPreferencesRepository.setWorkingHours("09:00", "18:00") }

        viewModel.setIgnoreWhatsAppGroups(false)
        advanceUntilIdle()
        coVerify(exactly = 1) { userPreferencesRepository.setIgnoreGroups(false) }

        viewModel.addBlacklistedNumber("+201012345678")
        advanceUntilIdle()
        coVerify(exactly = 1) { userPreferencesRepository.addBlacklistedNumber("+201012345678") }

        viewModel.removeBlacklistedNumber("+201012345678")
        advanceUntilIdle()
        coVerify(exactly = 1) { userPreferencesRepository.removeBlacklistedNumber("+201012345678") }
    }

    @Test
    fun `system maintenance actions set feedback messages`() = runTest(testDispatcher) {
        val viewModel = SettingsViewModel(userPreferencesRepository, secureCredentialsRepository)
        advanceUntilIdle()

        viewModel.exportLocalBackup()
        assertTrue(viewModel.uiState.value.maintenanceMessage?.contains("تصدير") == true)

        viewModel.restoreLocalBackup()
        assertTrue(viewModel.uiState.value.maintenanceMessage?.contains("استيراد") == true)

        viewModel.clearCache()
        assertTrue(viewModel.uiState.value.maintenanceMessage?.contains("مسح") == true)

        viewModel.clearMaintenanceMessage()
        assertFalse(viewModel.uiState.value.maintenanceMessage != null)
    }

    @Test
    fun `testConnection validates blank and short keys`() = runTest(testDispatcher) {
        val viewModel = SettingsViewModel(userPreferencesRepository, secureCredentialsRepository, testDispatcher)
        advanceUntilIdle()

        viewModel.testConnection(ModelProviders.QWEN, "")
        advanceUntilIdle()

        val qwenConfig = viewModel.uiState.value.providerConfigs[ModelProviders.QWEN]
        assertTrue(qwenConfig?.verificationStates?.get("") is VerificationState.Error)

        viewModel.testConnection(ModelProviders.QWEN, "short")
        advanceUntilIdle()

        val shortState = viewModel.uiState.value.providerConfigs[ModelProviders.QWEN]?.verificationStates?.get("short")
        assertTrue(shortState is VerificationState.Error)

        // Providers without remote endpoint succeed on format validation
        viewModel.testConnection(ModelProviders.LIQUID, "liquid-key-123456789")
        advanceUntilIdle()

        val liquidState = viewModel.uiState.value.providerConfigs[ModelProviders.LIQUID]?.verificationStates?.get("liquid-key-123456789")
        assertTrue(liquidState is VerificationState.Success)
    }

    @Test
    fun `model management operations delegate to userPreferencesRepository`() = runTest(testDispatcher) {
        val viewModel = SettingsViewModel(userPreferencesRepository, secureCredentialsRepository)
        advanceUntilIdle()

        viewModel.addProviderModel(ModelProviders.GEMINI, "gemini-ultra-custom")
        advanceUntilIdle()
        coVerify(exactly = 1) { userPreferencesRepository.addProviderModel(ModelProviders.GEMINI, "gemini-ultra-custom") }

        viewModel.removeProviderModel(ModelProviders.GEMINI, "gemini-1.5-flash")
        advanceUntilIdle()
        coVerify(exactly = 1) { userPreferencesRepository.removeProviderModel(ModelProviders.GEMINI, "gemini-1.5-flash") }

        viewModel.moveModelUp(ModelProviders.GEMINI, "gemini-2.5-pro")
        advanceUntilIdle()
        coVerify(atLeast = 1) { userPreferencesRepository.setProviderModels(ModelProviders.GEMINI, any()) }
    }
}
