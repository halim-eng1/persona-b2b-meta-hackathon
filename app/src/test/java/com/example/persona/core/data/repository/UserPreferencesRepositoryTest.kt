package com.example.persona.core.data.repository

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.example.persona.features.auto_reply.domain.model.PersonaTone
import com.example.persona.features.settings.data.repository.InMemorySecureCredentialsRepository
import com.example.persona.features.settings.domain.model.AccountMode
import com.example.persona.features.settings.domain.repository.ModelCapability
import com.example.persona.features.settings.domain.repository.ModelConfig
import com.example.persona.features.settings.domain.repository.ModelProviders
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class UserPreferencesRepositoryTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var repository: UserPreferencesRepository
    private lateinit var dataStoreFile: java.io.File

    @Before
    fun setUp() {
        dataStoreFile = tmpFolder.newFile("test_user_preferences.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(
            scope = testScope,
            produceFile = { dataStoreFile }
        )
        repository = UserPreferencesRepository(
            dataStore = dataStore,
            secureCredentialsRepository = InMemorySecureCredentialsRepository()
        )
    }

    @Test
    fun `meta provider pre-seeded models include spark reasoning and voice transcribe`() = runTest(testDispatcher) {
        val metaConfigs = repository.getProviderModelConfigsSync(ModelProviders.META)
        assertTrue(metaConfigs.isNotEmpty())
        val modelIds = metaConfigs.map { it.modelId }
        assertTrue(modelIds.contains("muse-spark-1.3-contributor"))
        assertTrue(modelIds.contains("muse-spark-1.3"))
        assertTrue(modelIds.contains("muse-spark-1.2-contributor"))
        assertTrue(modelIds.contains("muse-spark-1.2"))
        assertTrue(modelIds.contains("muse-voice-transcribe-1.0"))

        val voiceConfig = metaConfigs.first { it.modelId == "muse-voice-transcribe-1.0" }
        assertEquals(setOf(ModelCapability.AUDIO), voiceConfig.capabilities)

        val sparkConfig = metaConfigs.first { it.modelId == "muse-spark-1.3" }
        assertTrue(sparkConfig.capabilities.contains(ModelCapability.TEXT))
        assertTrue(sparkConfig.capabilities.contains(ModelCapability.VISION))
        assertTrue(sparkConfig.capabilities.contains(ModelCapability.DECISION))
    }

    @Test
    fun `per-capability provider priority defaults to META`() = runTest(testDispatcher) {
        val defaultText = repository.getProviderPrioritySync(ModelCapability.TEXT)
        assertEquals(listOf(ModelProviders.META), defaultText)

        val defaultDecision = repository.getProviderPrioritySync(ModelCapability.DECISION)
        assertEquals(listOf(ModelProviders.META), defaultDecision)

        val defaultAudio = repository.getProviderPrioritySync(ModelCapability.AUDIO)
        assertEquals(listOf(ModelProviders.META), defaultAudio)
    }

    @Test
    fun `isModelPrepared defaults to true for 0-second instant startup`() = runTest(testDispatcher) {
        assertEquals(true, repository.isModelPrepared())
        assertEquals(true, repository.isModelPreparedSync())
        assertEquals(true, repository.isModelPreparedFlow.first())

        repository.setModelPrepared(false)

        assertEquals(false, repository.isModelPrepared())
        assertEquals(false, repository.isModelPreparedSync())
        assertEquals(false, repository.isModelPreparedFlow.first())
    }

    @Test
    fun `hasSeenOnboarding defaults to false and persists true when set`() = runTest(testDispatcher) {
        assertEquals(false, repository.hasSeenOnboardingSync())
        assertEquals(false, repository.hasSeenOnboardingFlow.first())

        repository.setHasSeenOnboarding(true)

        assertEquals(true, repository.hasSeenOnboardingSync())
        assertEquals(true, repository.hasSeenOnboardingFlow.first())
    }

    @Test
    fun `default theme mode is light`() = runTest(testDispatcher) {
        assertEquals(UserPreferencesRepository.THEME_MODE_LIGHT, UserPreferencesRepository.DEFAULT_THEME_MODE)
        assertEquals(UserPreferencesRepository.THEME_MODE_LIGHT, repository.getThemeModeSync())
    }

    @Test
    fun `default app language is english`() = runTest(testDispatcher) {
        assertEquals("en", UserPreferencesRepository.DEFAULT_APP_LANGUAGE)
        assertEquals("en", repository.getAppLanguageSync())
        assertEquals("en", repository.appLanguageFlow.first())
    }

    @Test
    fun `default account mode is BUSINESS and persists updates`() = runTest(testDispatcher) {
        assertEquals(AccountMode.BUSINESS, repository.getAccountModeSync())
        assertEquals(AccountMode.BUSINESS, repository.accountModeFlow.first())

        repository.setAccountMode(AccountMode.PERSONAL)
        assertEquals(AccountMode.PERSONAL, repository.getAccountModeSync())
        assertEquals(AccountMode.PERSONAL, repository.accountModeFlow.first())
    }

    @Test
    fun `default persona tone is STANDARD_POLITE`() = runTest(testDispatcher) {
        assertEquals(PersonaTone.STANDARD_POLITE, repository.getPersonaToneSync())
        assertEquals(PersonaTone.STANDARD_POLITE, repository.personaToneFlow.first())
    }

    @Test
    fun `system messages fallback to English defaults when language is English`() = runTest(testDispatcher) {
        repository.setAppLanguage("en")
        assertEquals(UserPreferencesRepository.DEFAULT_HITL_HOLDING_MESSAGE_EN, repository.getHitlHoldingMessageSync())
        assertEquals(UserPreferencesRepository.DEFAULT_MEDIA_FALLBACK_MESSAGE_EN, repository.getMediaFallbackMessageSync())
        assertEquals(UserPreferencesRepository.DEFAULT_BLANK_REPLY_FALLBACK_TEMPLATE_EN, repository.getBlankReplyFallbackTemplateSync())
    }

    @Test
    fun `system messages fallback to Arabic defaults when language is Arabic`() = runTest(testDispatcher) {
        repository.setAppLanguage("ar")
        assertEquals(UserPreferencesRepository.DEFAULT_HITL_HOLDING_MESSAGE_AR, repository.getHitlHoldingMessageSync())
        assertEquals(UserPreferencesRepository.DEFAULT_MEDIA_FALLBACK_MESSAGE_AR, repository.getMediaFallbackMessageSync())
        assertEquals(UserPreferencesRepository.DEFAULT_BLANK_REPLY_FALLBACK_TEMPLATE_AR, repository.getBlankReplyFallbackTemplateSync())
    }

    @Test
    fun `default provider order has META as sole provider`() = runTest(testDispatcher) {
        val defaultOrder = repository.getProviderOrderSync()
        assertEquals(listOf(ModelProviders.META), defaultOrder)
        assertEquals(listOf(ModelProviders.META), UserPreferencesRepository.DEFAULT_PROVIDER_ORDER)
    }

    @Test
    fun `alwaysShowOnboarding overrides hasSeenOnboarding properly`() = runTest(testDispatcher) {
        repository.setHasSeenOnboarding(true)
        assertTrue(repository.hasSeenOnboarding())

        repository.setAlwaysShowOnboarding(true)
        assertFalse(repository.hasSeenOnboarding())
        assertFalse(repository.hasSeenOnboardingSync())

        repository.setAlwaysShowOnboarding(false)
        assertTrue(repository.hasSeenOnboarding())
        assertTrue(repository.hasSeenOnboardingSync())
    }

    @Test
    fun `per-capability model ordering persistence and retrieval works for Meta`() = runTest(testDispatcher) {
        val reorderedIds = listOf("muse-spark-1.3", "muse-spark-1.3-contributor")
        repository.setProviderModelOrderForCapability(ModelProviders.META, ModelCapability.TEXT, reorderedIds)

        val retrievedText = repository.getProviderModelConfigsSync(ModelProviders.META, ModelCapability.TEXT)
        assertEquals("muse-spark-1.3", retrievedText[0].modelId)
        assertEquals("muse-spark-1.3-contributor", retrievedText[1].modelId)
    }
}
