package com.example.persona.core.data.repository

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.persona.features.settings.data.repository.InMemorySecureCredentialsRepository
import com.example.persona.features.settings.domain.repository.ModelCapability
import com.example.persona.features.settings.domain.repository.ModelConfig
import com.example.persona.features.settings.domain.repository.ModelProviders
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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
    fun `cloudflare and liquid models default ONLY to DECISION capability`() = runTest(testDispatcher) {
        val cfConfigs = repository.getProviderModelConfigsSync(ModelProviders.CLOUDFLARE)
        assertTrue(cfConfigs.isNotEmpty())
        cfConfigs.forEach { config ->
            assertEquals(setOf(ModelCapability.DECISION), config.capabilities)
        }

        val liquidConfigs = repository.getProviderModelConfigsSync(ModelProviders.LIQUID)
        assertTrue(liquidConfigs.isNotEmpty())
        liquidConfigs.forEach { config ->
            assertEquals(setOf(ModelCapability.DECISION), config.capabilities)
        }
    }

    @Test
    fun `auto-backfill on read appends missing multimodal models for Qwen non-destructively`() = runTest(testDispatcher) {
        // Simulate existing user with older stored Qwen models (missing fun-asr-flash, qwen-vl-max, qwen3.8-omni-flash)
        val initialStored = listOf("deepseek-v4.1-flash", "qwen3.8-max")
        repository.setProviderModels(ModelProviders.QWEN, initialStored)

        val resolved = repository.getProviderModelConfigsSync(ModelProviders.QWEN)
        val resolvedIds = resolved.map { it.modelId }

        // Existing models must be preserved at original positions
        assertEquals("deepseek-v4.1-flash", resolvedIds[0])
        assertEquals("qwen3.8-max", resolvedIds[1])

        // Missing pre-seeded models must be non-destructively appended
        assertTrue(resolvedIds.contains("qwen3.8-omni-flash"))
        assertTrue(resolvedIds.contains("qwen-vl-max"))
        assertTrue(resolvedIds.contains("fun-asr-flash-2026-06-15"))

        // Check capabilities
        val asrConfig = resolved.first { it.modelId == "fun-asr-flash-2026-06-15" }
        assertEquals(setOf(ModelCapability.AUDIO), asrConfig.capabilities)

        val vlConfig = resolved.first { it.modelId == "qwen-vl-max" }
        assertEquals(setOf(ModelCapability.VISION), vlConfig.capabilities)

        val omniConfig = resolved.first { it.modelId == "qwen3.8-omni-flash" }
        assertEquals(setOf(ModelCapability.TEXT, ModelCapability.VISION), omniConfig.capabilities)
    }

    @Test
    fun `auto-backfill on read appends missing nemotron vl for OpenRouter`() = runTest(testDispatcher) {
        // Simulate existing user with only text models for OpenRouter
        val initialStored = listOf("openrouter/free")
        repository.setProviderModels(ModelProviders.OPENROUTER, initialStored)

        val resolved = repository.getProviderModelConfigsSync(ModelProviders.OPENROUTER)
        val resolvedIds = resolved.map { it.modelId }

        assertTrue(resolvedIds.contains("openrouter/free"))
        assertTrue(resolvedIds.contains("nvidia/nemotron-3.5-super-vl:free"))

        val vlConfig = resolved.first { it.modelId == "nvidia/nemotron-3.5-super-vl:free" }
        assertEquals(setOf(ModelCapability.TEXT, ModelCapability.VISION), vlConfig.capabilities)
    }

    @Test
    fun `user-deleted backfilled models are not resurrected on subsequent reads`() = runTest(testDispatcher) {
        // User starts with some Qwen models, backfill runs on initial migration
        val initialStored = listOf("deepseek-v4.1-flash", "qwen3.8-max")
        repository.setProviderModels(ModelProviders.QWEN, initialStored)

        val firstRead = repository.getProviderModelConfigsSync(ModelProviders.QWEN)
        assertTrue(firstRead.any { it.modelId == "fun-asr-flash-2026-06-15" })

        // User explicitly deletes fun-asr-flash and saves configs
        val filtered = firstRead.filter { it.modelId != "fun-asr-flash-2026-06-15" }
        repository.setProviderModelConfigs(ModelProviders.QWEN, filtered)

        // Subsequent read MUST NOT auto-resurrect fun-asr-flash
        val secondRead = repository.getProviderModelConfigsSync(ModelProviders.QWEN)
        val secondIds = secondRead.map { it.modelId }
        assertTrue("deepseek-v4.1-flash should remain", secondIds.contains("deepseek-v4.1-flash"))
        assertTrue("fun-asr-flash must NOT be resurrected", !secondIds.contains("fun-asr-flash-2026-06-15"))
    }

    @Test
    fun `custom user capabilities for cloudflare and liquid are preserved`() = runTest(testDispatcher) {
        val customCfConfig = listOf(
            ModelConfig("@cf/cloudflare/custom-agent", setOf(ModelCapability.TEXT, ModelCapability.DECISION))
        )
        repository.setProviderModelConfigs(ModelProviders.CLOUDFLARE, customCfConfig)

        val retrieved = repository.getProviderModelConfigsSync(ModelProviders.CLOUDFLARE)
        assertEquals(1, retrieved.size)
        assertEquals("@cf/cloudflare/custom-agent", retrieved[0].modelId)
        assertEquals(setOf(ModelCapability.TEXT, ModelCapability.DECISION), retrieved[0].capabilities)
    }

    @Test
    fun `per-capability provider priority persistence and retrieval works`() = runTest(testDispatcher) {
        // Default check
        val defaultText = repository.getProviderPrioritySync(ModelCapability.TEXT)
        assertEquals(
            listOf(
                ModelProviders.QWEN,
                ModelProviders.GEMINI,
                ModelProviders.GROQ,
                ModelProviders.NVIDIA,
                ModelProviders.OPENROUTER,
                ModelProviders.NARA
            ),
            defaultText
        )

        val defaultDecision = repository.getProviderPrioritySync(ModelCapability.DECISION)
        assertEquals(
            listOf(
                ModelProviders.CLOUDFLARE,
                ModelProviders.LIQUID,
                ModelProviders.OPENROUTER
            ),
            defaultDecision
        )

        // Custom order for DECISION
        val customDecisionOrder = listOf(ModelProviders.LIQUID, ModelProviders.CLOUDFLARE, ModelProviders.OPENROUTER)
        repository.setProviderPriority(ModelCapability.DECISION, customDecisionOrder)

        val persistedDecision = repository.getProviderPrioritySync(ModelCapability.DECISION)
        assertEquals(customDecisionOrder, persistedDecision)

        // Flow check
        val emittedDecision = repository.getProviderPriority(ModelCapability.DECISION).first()
        assertEquals(customDecisionOrder, emittedDecision)

        // TEXT remains unaffected
        assertEquals(defaultText, repository.getProviderPrioritySync(ModelCapability.TEXT))
    }

    @Test
    fun `isModelPrepared defaults to false and persists true when set`() = runTest(testDispatcher) {
        assertEquals(false, repository.isModelPrepared())
        assertEquals(false, repository.isModelPreparedSync())
        assertEquals(false, repository.isModelPreparedFlow.first())

        repository.setModelPrepared(true)

        assertEquals(true, repository.isModelPrepared())
        assertEquals(true, repository.isModelPreparedSync())
        assertEquals(true, repository.isModelPreparedFlow.first())
    }
}
