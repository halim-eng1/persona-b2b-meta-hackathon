package com.example.persona.features.settings.data.repository

import com.example.persona.core.data.repository.UserPreferencesRepository
import com.example.persona.features.settings.domain.repository.ModelProviders
import com.example.persona.features.settings.domain.repository.SecureCredentialsRepository
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SecureCredentialsRepositoryTest {

    private lateinit var secureCredentialsRepository: SecureCredentialsRepository

    @Before
    fun setUp() {
        secureCredentialsRepository = InMemorySecureCredentialsRepository()
    }

    @Test
    fun `initial API keys are blank and flows emit null`() = runTest {
        assertEquals("", secureCredentialsRepository.getNvidiaApiKey())
        assertEquals("", secureCredentialsRepository.getQwenApiKey())
        assertNull(secureCredentialsRepository.nvidiaApiKeyFlow.first())
        assertNull(secureCredentialsRepository.qwenApiKeyFlow.first())

        ModelProviders.ALL.forEach { provider ->
            assertTrue(secureCredentialsRepository.getApiKeys(provider).isEmpty())
            assertTrue(secureCredentialsRepository.getApiKeysFlow(provider).first().isEmpty())
        }
    }

    @Test
    fun `setting Nvidia API key updates getter and flow`() = runTest {
        secureCredentialsRepository.setNvidiaApiKey("nvapi-test-secret-123")

        assertEquals("nvapi-test-secret-123", secureCredentialsRepository.getNvidiaApiKey())
        assertEquals("nvapi-test-secret-123", secureCredentialsRepository.nvidiaApiKeyFlow.first())

        // Clearing key
        secureCredentialsRepository.setNvidiaApiKey("")
        assertEquals("", secureCredentialsRepository.getNvidiaApiKey())
        assertNull(secureCredentialsRepository.nvidiaApiKeyFlow.first())
    }

    @Test
    fun `setting Qwen API key updates getter and flow`() = runTest {
        secureCredentialsRepository.setQwenApiKey("sk-qwen-secret-456")

        assertEquals("sk-qwen-secret-456", secureCredentialsRepository.getQwenApiKey())
        assertEquals("sk-qwen-secret-456", secureCredentialsRepository.qwenApiKeyFlow.first())

        // Setting blank/null
        secureCredentialsRepository.setQwenApiKey(null)
        assertEquals("", secureCredentialsRepository.getQwenApiKey())
        assertNull(secureCredentialsRepository.qwenApiKeyFlow.first())
    }

    @Test
    fun `unlimited API keys per provider can be added, retrieved, and removed`() = runTest {
        val providers = listOf(
            ModelProviders.QWEN,
            ModelProviders.GEMINI,
            ModelProviders.GROQ,
            ModelProviders.NVIDIA,
            ModelProviders.OPENROUTER,
            ModelProviders.CLOUDFLARE,
            ModelProviders.LIQUID,
            ModelProviders.NARA
        )

        providers.forEach { provider ->
            // Add keys
            secureCredentialsRepository.addApiKey(provider, "key-1-$provider")
            secureCredentialsRepository.addApiKey(provider, "key-2-$provider")
            secureCredentialsRepository.addApiKey(provider, "key-3-$provider")

            val keys = secureCredentialsRepository.getApiKeys(provider)
            assertEquals(3, keys.size)
            assertEquals(listOf("key-1-$provider", "key-2-$provider", "key-3-$provider"), keys)
            assertEquals(keys, secureCredentialsRepository.getApiKeysFlow(provider).first())

            // Duplicate key should not be re-added
            secureCredentialsRepository.addApiKey(provider, "key-2-$provider")
            assertEquals(3, secureCredentialsRepository.getApiKeys(provider).size)

            // Remove a key
            secureCredentialsRepository.removeApiKey(provider, "key-2-$provider")
            val afterRemoval = secureCredentialsRepository.getApiKeys(provider)
            assertEquals(2, afterRemoval.size)
            assertEquals(listOf("key-1-$provider", "key-3-$provider"), afterRemoval)

            // Overwrite with setApiKeys
            secureCredentialsRepository.setApiKeys(provider, listOf("new-key-a", "new-key-b"))
            assertEquals(listOf("new-key-a", "new-key-b"), secureCredentialsRepository.getApiKeys(provider))
        }
    }

    @Test
    fun `UserPreferencesRepository delegates API keys to secure repository`() = runTest {
        val mockDataStore = mockk<androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>>(relaxed = true)
        val userPrefs = UserPreferencesRepository(
            dataStore = mockDataStore,
            secureCredentialsRepository = secureCredentialsRepository
        )

        userPrefs.setNvidiaApiKey("nv-secured-token")
        assertEquals("nv-secured-token", userPrefs.getNvidiaApiKeySync())
        assertEquals("nv-secured-token", userPrefs.nvidiaApiKeyFlow.first())

        userPrefs.setQwenApiKey("qwen-secured-token")
        assertEquals("qwen-secured-token", userPrefs.getQwenApiKeySync())
        assertEquals("qwen-secured-token", userPrefs.qwenApiKeyFlow.first())
    }
}
