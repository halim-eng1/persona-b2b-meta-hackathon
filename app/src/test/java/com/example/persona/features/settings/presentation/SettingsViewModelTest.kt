package com.example.persona.features.settings.presentation

import com.example.persona.core.data.repository.BusinessHoursSchedule
import com.example.persona.core.data.repository.UserPreferencesRepository
import com.example.persona.features.auto_reply.domain.model.PersonaTone
import com.example.persona.features.auto_reply.domain.model.StylePreferences
import com.example.persona.features.auto_reply.domain.model.StyleProfile
import com.example.persona.features.settings.data.repository.InMemorySecureCredentialsRepository
import com.example.persona.features.settings.domain.model.AccountMode
import com.example.persona.features.settings.domain.repository.ModelCapability
import com.example.persona.features.settings.domain.repository.ModelConfig
import com.example.persona.features.settings.domain.repository.ModelProviders
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
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
    private val appLanguageFlow = MutableStateFlow("ar")
    private val themeModeFlow = MutableStateFlow("dark")

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { userPreferencesRepository.appLanguageFlow } returns appLanguageFlow
        every { userPreferencesRepository.themeModeFlow } returns themeModeFlow
        every { userPreferencesRepository.accountModeFlow } returns flowOf(AccountMode.BUSINESS)
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
        every { userPreferencesRepository.getProviderPriority(any()) } answers {
            val cap = firstArg<ModelCapability>()
            flowOf(ModelProviders.DEFAULT_CAPABILITY_PROVIDERS[cap] ?: emptyList())
        }
        every { userPreferencesRepository.businessHoursScheduleFlow } returns flowOf(BusinessHoursSchedule())
        every { userPreferencesRepository.ignoreGroupsFlow } returns flowOf(true)
        every { userPreferencesRepository.blacklistedNumbersFlow } returns flowOf(emptySet())
        every { userPreferencesRepository.isAutoStartConfirmedFlow } returns flowOf(false)
        coEvery { userPreferencesRepository.isAutoStartConfirmedSync() } returns false
        coEvery { userPreferencesRepository.getProviderModelsSync(any()) } answers {
            val provider = firstArg<String>()
            UserPreferencesRepository.DEFAULT_PROVIDER_MODELS[provider] ?: emptyList()
        }
        coEvery { userPreferencesRepository.getProviderModelConfigsSync(any()) } answers {
            val provider = firstArg<String>()
            (UserPreferencesRepository.DEFAULT_PROVIDER_MODELS[provider] ?: emptyList())
                .map { ModelConfig(it, ModelConfig.inferCapabilities(it)) }
        }
        coEvery { userPreferencesRepository.getProviderModelConfigsSync(any(), any()) } answers {
            val provider = firstArg<String>()
            val cap = secondArg<ModelCapability?>()
            val all = (UserPreferencesRepository.DEFAULT_PROVIDER_MODELS[provider] ?: emptyList())
                .map { ModelConfig(it, ModelConfig.inferCapabilities(it)) }
            if (cap != null) all.filter { cap in it.capabilities } else all
        }
        coEvery { userPreferencesRepository.setProviderModelOrderForCapability(any(), any(), any()) } just Runs
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
    fun `default personaTone in uiState is STANDARD_POLITE`() = runTest(testDispatcher) {
        val viewModel = SettingsViewModel(userPreferencesRepository, secureCredentialsRepository)
        advanceUntilIdle()

        assertEquals(PersonaTone.STANDARD_POLITE, viewModel.uiState.value.personaTone)
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

        viewModel.selectProviderForEdit(ModelProviders.META)
        advanceUntilIdle()
        assertEquals(ModelProviders.META, viewModel.uiState.value.selectedProviderForEdit)
    }

    @Test
    fun `add and remove api keys per provider delegates to secure repository`() = runTest(testDispatcher) {
        val viewModel = SettingsViewModel(userPreferencesRepository, secureCredentialsRepository)
        advanceUntilIdle()

        viewModel.addApiKey(ModelProviders.META, "sk-test-meta-key-1")
        advanceUntilIdle()

        assertEquals(listOf("sk-test-meta-key-1"), secureCredentialsRepository.getApiKeys(ModelProviders.META))
        assertEquals(listOf("sk-test-meta-key-1"), viewModel.uiState.value.providerConfigs[ModelProviders.META]?.keys)

        viewModel.removeApiKey(ModelProviders.META, "sk-test-meta-key-1")
        advanceUntilIdle()

        assertTrue(secureCredentialsRepository.getApiKeys(ModelProviders.META).isEmpty())
        assertTrue(viewModel.uiState.value.providerConfigs[ModelProviders.META]?.keys.orEmpty().isEmpty())
    }

    @Test
    fun `moving the only provider keeps a stable fallback chain`() = runTest(testDispatcher) {
        val viewModel = SettingsViewModel(userPreferencesRepository, secureCredentialsRepository)
        advanceUntilIdle()

        val initialOrder = viewModel.uiState.value.providerOrder
        assertEquals(listOf(ModelProviders.META), initialOrder)

        viewModel.moveProviderUp(ModelProviders.META)
        advanceUntilIdle()
        assertEquals(listOf(ModelProviders.META), viewModel.uiState.value.providerOrder)

        viewModel.moveProviderDown(ModelProviders.META)
        advanceUntilIdle()
        assertEquals(listOf(ModelProviders.META), viewModel.uiState.value.providerOrder)
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

        viewModel.testConnection(ModelProviders.META, "")
        advanceUntilIdle()

        val metaConfig = viewModel.uiState.value.providerConfigs[ModelProviders.META]
        assertTrue(metaConfig?.verificationStates?.get("") is VerificationState.Error)

        viewModel.testConnection(ModelProviders.META, "short")
        advanceUntilIdle()

        val shortState = viewModel.uiState.value.providerConfigs[ModelProviders.META]?.verificationStates?.get("short")
        assertTrue(shortState is VerificationState.Error)
    }

    @Test
    fun `model management operations delegate to userPreferencesRepository`() = runTest(testDispatcher) {
        val viewModel = SettingsViewModel(userPreferencesRepository, secureCredentialsRepository)
        advanceUntilIdle()

        viewModel.addProviderModel(ModelProviders.META, "muse-spark-custom")
        advanceUntilIdle()
        coVerify(exactly = 1) { userPreferencesRepository.addProviderModel(ModelProviders.META, "muse-spark-custom") }

        viewModel.removeProviderModel(ModelProviders.META, "muse-spark-1.2")
        advanceUntilIdle()
        coVerify(exactly = 1) { userPreferencesRepository.removeProviderModel(ModelProviders.META, "muse-spark-1.2") }

        viewModel.moveModelUp(ModelProviders.META, "muse-spark-1.3")
        advanceUntilIdle()
        // moveModelUp now persists via setProviderModelConfigs (capability-aware API)
        coVerify(atLeast = 1) { userPreferencesRepository.setProviderModelConfigs(ModelProviders.META, any()) }
    }

    @Test
    fun `default appLanguage in uiState is ar and setAppLanguage updates repository`() = runTest(testDispatcher) {
        val viewModel = SettingsViewModel(userPreferencesRepository, secureCredentialsRepository)
        advanceUntilIdle()

        assertEquals("ar", viewModel.uiState.value.appLanguage)

        viewModel.setAppLanguage("en")
        advanceUntilIdle()

        assertEquals("en", viewModel.uiState.value.appLanguage)
        coVerify { userPreferencesRepository.setAppLanguage("en") }
    }

    @Test
    fun `when appLanguageFlow emits new language, uiState updates`() = runTest(testDispatcher) {
        val viewModel = SettingsViewModel(userPreferencesRepository, secureCredentialsRepository)
        advanceUntilIdle()

        assertEquals("ar", viewModel.uiState.value.appLanguage)

        appLanguageFlow.value = "en"
        advanceUntilIdle()

        assertEquals("en", viewModel.uiState.value.appLanguage)
    }

    @Test
    fun `default themeMode in uiState is dark and setThemeMode updates repository`() = runTest(testDispatcher) {
        coEvery { userPreferencesRepository.setThemeMode(any()) } just Runs
        val viewModel = SettingsViewModel(userPreferencesRepository, secureCredentialsRepository)
        advanceUntilIdle()

        assertEquals("dark", viewModel.uiState.value.themeMode)

        viewModel.setThemeMode("light")
        advanceUntilIdle()

        assertEquals("light", viewModel.uiState.value.themeMode)
        coVerify { userPreferencesRepository.setThemeMode("light") }
    }

    @Test
    fun `setBotName with empty string falls back to default`() = runTest(testDispatcher) {
        val viewModel = SettingsViewModel(userPreferencesRepository, secureCredentialsRepository)
        advanceUntilIdle()

        viewModel.setBotName("   ")
        advanceUntilIdle()

        assertEquals(UserPreferencesRepository.DEFAULT_BOT_NAME, viewModel.uiState.value.botName)
        coVerify { userPreferencesRepository.setBotName(UserPreferencesRepository.DEFAULT_BOT_NAME) }
    }

    @Test
    fun `setStoreName with empty string falls back to default`() = runTest(testDispatcher) {
        val viewModel = SettingsViewModel(userPreferencesRepository, secureCredentialsRepository)
        advanceUntilIdle()

        viewModel.setStoreName("")
        advanceUntilIdle()

        assertEquals(UserPreferencesRepository.DEFAULT_STORE_NAME, viewModel.uiState.value.storeName)
        coVerify { userPreferencesRepository.setStoreName(UserPreferencesRepository.DEFAULT_STORE_NAME) }
    }

    @Test
    fun `reordering API keys with moveApiKeyUp moveApiKeyDown and moveApiKey`() = runTest(testDispatcher) {
        val viewModel = SettingsViewModel(userPreferencesRepository, secureCredentialsRepository)
        advanceUntilIdle()

        secureCredentialsRepository.setApiKeys(ModelProviders.META, listOf("key-1", "key-2", "key-3"))
        viewModel.selectProviderForEdit(ModelProviders.META)
        advanceUntilIdle()

        // Move key-2 up
        viewModel.moveApiKeyUp(ModelProviders.META, "key-2")
        advanceUntilIdle()
        assertEquals(listOf("key-2", "key-1", "key-3"), secureCredentialsRepository.getApiKeys(ModelProviders.META))

        // Move key-2 down
        viewModel.moveApiKeyDown(ModelProviders.META, "key-2")
        advanceUntilIdle()
        assertEquals(listOf("key-1", "key-2", "key-3"), secureCredentialsRepository.getApiKeys(ModelProviders.META))

        // Move key-3 to index 0 (drag from 2 to 0)
        viewModel.moveApiKey(ModelProviders.META, 2, 0)
        advanceUntilIdle()
        assertEquals(listOf("key-3", "key-1", "key-2"), secureCredentialsRepository.getApiKeys(ModelProviders.META))
    }

    @Test
    fun `reordering models with moveModel updates repository and config`() = runTest(testDispatcher) {
        val viewModel = SettingsViewModel(userPreferencesRepository, secureCredentialsRepository)
        advanceUntilIdle()

        val initialModels = listOf("muse-spark-1.3", "muse-spark-1.3-contributor")
        val initialConfigs = initialModels.map { ModelConfig(it, setOf(ModelCapability.TEXT)) }
        coEvery { userPreferencesRepository.getProviderModelConfigsSync(ModelProviders.META) } returns initialConfigs
        viewModel.selectProviderForEdit(ModelProviders.META)
        advanceUntilIdle()

        // Move muse-spark-1.3 (index 0) to index 1
        viewModel.moveModel(ModelProviders.META, 0, 1)
        advanceUntilIdle()

        val expectedConfigs = listOf("muse-spark-1.3-contributor", "muse-spark-1.3")
            .map { ModelConfig(it, setOf(ModelCapability.TEXT)) }
        coVerify { userPreferencesRepository.setProviderModelConfigs(ModelProviders.META, expectedConfigs) }
    }

    @Test
    fun `exact production models are configured for Meta`() {
        val models = UserPreferencesRepository.DEFAULT_PROVIDER_MODELS

        assertEquals(
            listOf(
                "muse-spark-1.3-contributor",
                "muse-spark-1.3",
                "muse-spark-1.2-contributor",
                "muse-spark-1.2",
                "muse-voice-transcribe-1.0"
            ),
            models[ModelProviders.META]
        )
    }

    @Test
    fun `per-capability filtering restricts providers correctly`() = runTest(testDispatcher) {
        val viewModel = SettingsViewModel(userPreferencesRepository, secureCredentialsRepository)
        advanceUntilIdle()

        // Text
        assertEquals(
            listOf(ModelProviders.META),
            viewModel.uiState.value.filteredProviderOrder(ModelCapability.TEXT)
        )

        // Vision
        assertEquals(
            listOf(ModelProviders.META),
            viewModel.uiState.value.filteredProviderOrder(ModelCapability.VISION)
        )

        // Audio
        assertEquals(
            listOf(ModelProviders.META),
            viewModel.uiState.value.filteredProviderOrder(ModelCapability.AUDIO)
        )

        // Decision
        assertEquals(
            listOf(ModelProviders.META),
            viewModel.uiState.value.filteredProviderOrder(ModelCapability.DECISION)
        )
    }

    @Test
    fun `moveModelForCapability delegates to userPreferencesRepository and refreshes`() = runTest(testDispatcher) {
        val viewModel = SettingsViewModel(userPreferencesRepository, secureCredentialsRepository)
        advanceUntilIdle()

        val initialConfigs = listOf(
            ModelConfig("muse-spark-1.3", setOf(ModelCapability.TEXT)),
            ModelConfig("muse-spark-1.3-contributor", setOf(ModelCapability.TEXT))
        )
        coEvery { userPreferencesRepository.getProviderModelConfigsSync(ModelProviders.META, ModelCapability.TEXT) } returns initialConfigs

        viewModel.moveModelForCapability(ModelProviders.META, ModelCapability.TEXT, 0, 1)
        advanceUntilIdle()

        coVerify {
            userPreferencesRepository.setProviderModelOrderForCapability(
                ModelProviders.META,
                ModelCapability.TEXT,
                listOf("muse-spark-1.3-contributor", "muse-spark-1.3")
            )
        }
    }

    @Test
    fun `Meta-only provider list makes reordering a safe no-op`() = runTest(testDispatcher) {
        val mockCircuitBreaker = mockk<com.example.persona.features.auto_reply.data.providers.ProviderCircuitBreakerAndKeyManager>(relaxed = true)
        val viewModel = SettingsViewModel(
            userPreferencesRepository = userPreferencesRepository,
            secureCredentialsRepository = secureCredentialsRepository,
            circuitBreakerAndKeyManager = mockCircuitBreaker
        )
        advanceUntilIdle()

        viewModel.selectCapabilityFilter(ModelCapability.TEXT)
        advanceUntilIdle()

        // Runtime mandate: Meta is the only provider, so the TEXT chain has a single entry.
        assertEquals(
            listOf(ModelProviders.META),
            viewModel.uiState.value.filteredProviderOrder(ModelCapability.TEXT)
        )

        viewModel.moveProvider(0, 1)
        advanceUntilIdle()

        // No reorder is possible, so no cascade update should be attempted.
        verify(exactly = 0) { mockCircuitBreaker.setProviderCascadeOrder(any()) }
    }

    @Test
    fun `recheckPermissions updates all permissions in uiState`() = runTest(testDispatcher) {
        val mockPermissionChecker = mockk<com.example.persona.features.onboarding.data.PermissionChecker>()
        every { mockPermissionChecker.isManageStorageGranted() } returns true
        every { mockPermissionChecker.isNotificationListenerEnabled() } returns true
        every { mockPermissionChecker.isBatteryOptimizationIgnored() } returns true
        every { mockPermissionChecker.isContactsPermissionGranted() } returns true
        coEvery { userPreferencesRepository.isAutoStartConfirmedSync() } returns true

        val viewModel = SettingsViewModel(
            userPreferencesRepository = userPreferencesRepository,
            secureCredentialsRepository = secureCredentialsRepository,
            permissionChecker = mockPermissionChecker,
            ioDispatcher = testDispatcher
        )
        advanceUntilIdle()

        viewModel.recheckPermissions()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.isStorageAccessGranted)
        assertTrue(state.isNotificationListenerGranted)
        assertTrue(state.isBatteryOptimizationIgnored)
        assertTrue(state.isContactsPermissionGranted)
        assertTrue(state.isAutoStartConfirmed)
    }

    @Test
    fun `setAutoStartConfirmed persists value to repository and updates uiState`() = runTest(testDispatcher) {
        coEvery { userPreferencesRepository.setAutoStartConfirmed(true) } returns Unit
        coEvery { userPreferencesRepository.isAutoStartConfirmedSync() } returns true

        val viewModel = SettingsViewModel(
            userPreferencesRepository = userPreferencesRepository,
            secureCredentialsRepository = secureCredentialsRepository,
            ioDispatcher = testDispatcher
        )
        advanceUntilIdle()

        viewModel.setAutoStartConfirmed(true)
        advanceUntilIdle()

        coVerify { userPreferencesRepository.setAutoStartConfirmed(true) }
        assertTrue(viewModel.uiState.value.isAutoStartConfirmed)
    }

    @Test
    fun `openAutoStartSettings delegates to AutoStartHelper and confirms on success`() = runTest(testDispatcher) {
        val mockAutoStartHelper = mockk<com.example.persona.features.onboarding.data.AutoStartHelper>()
        val mockContext = mockk<android.content.Context>(relaxed = true)
        every { mockAutoStartHelper.launchAutoStartSettings(mockContext) } returns true
        coEvery { userPreferencesRepository.setAutoStartConfirmed(true) } returns Unit
        coEvery { userPreferencesRepository.isAutoStartConfirmedSync() } returns true

        val viewModel = SettingsViewModel(
            userPreferencesRepository = userPreferencesRepository,
            secureCredentialsRepository = secureCredentialsRepository,
            permissionChecker = null,
            autoStartHelper = mockAutoStartHelper,
            ioDispatcher = testDispatcher
        )
        advanceUntilIdle()

        viewModel.openAutoStartSettings(mockContext)
        advanceUntilIdle()

        verify { mockAutoStartHelper.launchAutoStartSettings(mockContext) }
        coVerify { userPreferencesRepository.setAutoStartConfirmed(true) }
        assertTrue(viewModel.uiState.value.isAutoStartConfirmed)
    }

    @Test
    fun `openAutoStartSettings does not confirm auto-start if launch fails`() = runTest(testDispatcher) {
        val mockAutoStartHelper = mockk<com.example.persona.features.onboarding.data.AutoStartHelper>()
        val mockContext = mockk<android.content.Context>(relaxed = true)
        every { mockAutoStartHelper.launchAutoStartSettings(mockContext) } returns false
        coEvery { userPreferencesRepository.isAutoStartConfirmedSync() } returns false

        val viewModel = SettingsViewModel(
            userPreferencesRepository = userPreferencesRepository,
            secureCredentialsRepository = secureCredentialsRepository,
            permissionChecker = null,
            autoStartHelper = mockAutoStartHelper,
            ioDispatcher = testDispatcher
        )
        advanceUntilIdle()

        viewModel.openAutoStartSettings(mockContext)
        advanceUntilIdle()

        verify { mockAutoStartHelper.launchAutoStartSettings(mockContext) }
        coVerify(exactly = 0) { userPreferencesRepository.setAutoStartConfirmed(true) }
        assertFalse(viewModel.uiState.value.isAutoStartConfirmed)
    }
}
