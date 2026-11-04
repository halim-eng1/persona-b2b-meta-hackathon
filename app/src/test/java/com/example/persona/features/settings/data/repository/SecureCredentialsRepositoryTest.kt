package com.example.persona.features.settings.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.persona.core.data.repository.UserPreferencesRepository
import com.example.persona.features.settings.domain.repository.ModelProviders
import com.example.persona.features.settings.domain.repository.SecureCredentialsRepository
import io.mockk.every
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
    fun `initial API keys are blank`() = runTest {
        ModelProviders.ALL.forEach { provider ->
            assertTrue(secureCredentialsRepository.getApiKeys(provider).isEmpty())
            assertTrue(secureCredentialsRepository.getApiKeysFlow(provider).first().isEmpty())
        }
    }

    @Test
    fun `unlimited API keys per provider can be added, retrieved, and removed`() = runTest {
        val provider = ModelProviders.META

        // Add keys
        secureCredentialsRepository.addApiKey(provider, "meta-key-1")
        secureCredentialsRepository.addApiKey(provider, "meta-key-2")
        secureCredentialsRepository.addApiKey(provider, "meta-key-3")

        val keys = secureCredentialsRepository.getApiKeys(provider)
        assertEquals(3, keys.size)
        assertEquals(listOf("meta-key-1", "meta-key-2", "meta-key-3"), keys)
        assertEquals(keys, secureCredentialsRepository.getApiKeysFlow(provider).first())

        // Duplicate key should not be re-added
        secureCredentialsRepository.addApiKey(provider, "meta-key-2")
        assertEquals(3, secureCredentialsRepository.getApiKeys(provider).size)

        // Remove a key
        secureCredentialsRepository.removeApiKey(provider, "meta-key-2")
        val afterRemoval = secureCredentialsRepository.getApiKeys(provider)
        assertEquals(2, afterRemoval.size)
        assertEquals(listOf("meta-key-1", "meta-key-3"), afterRemoval)

        // Overwrite with setApiKeys
        secureCredentialsRepository.setApiKeys(provider, listOf("new-key-a", "new-key-b"))
        assertEquals(listOf("new-key-a", "new-key-b"), secureCredentialsRepository.getApiKeys(provider))
    }

    @Test
    fun `hasConfiguredKeys returns false when empty and true when keys added`() = runTest {
        val provider = ModelProviders.META
        assertEquals(false, secureCredentialsRepository.hasConfiguredKeys(provider))

        secureCredentialsRepository.addApiKey(provider, "meta-test-key")
        assertEquals(true, secureCredentialsRepository.hasConfiguredKeys(provider))

        secureCredentialsRepository.removeApiKey(provider, "meta-test-key")
        assertEquals(false, secureCredentialsRepository.hasConfiguredKeys(provider))
    }

    @Test
    fun `blank strings are rejected and not counted in hasConfiguredKeys`() = runTest {
        val provider = ModelProviders.META
        secureCredentialsRepository.addApiKey(provider, "   ")
        assertEquals(false, secureCredentialsRepository.hasConfiguredKeys(provider))
        assertTrue(secureCredentialsRepository.getApiKeys(provider).isEmpty())

        secureCredentialsRepository.setApiKeys(provider, listOf("", "  ", "\t"))
        assertEquals(false, secureCredentialsRepository.hasConfiguredKeys(provider))
        assertTrue(secureCredentialsRepository.getApiKeys(provider).isEmpty())
    }

    @Test
    fun `SecureCredentialsRepositoryImpl stores and retrieves Meta keys`() = runTest {
        val mockPrefs = mockk<SharedPreferences>(relaxed = true)
        val mockEditor = mockk<SharedPreferences.Editor>(relaxed = true)
        every { mockPrefs.edit() } returns mockEditor
        every { mockEditor.putString(any(), any()) } returns mockEditor
        every { mockEditor.remove(any()) } returns mockEditor

        val mockContext = mockk<Context>(relaxed = true)
        every { mockContext.getSharedPreferences(any(), any()) } returns mockPrefs

        val repo = SecureCredentialsRepositoryImpl(mockContext)

        // Custom merchant keys are preserved
        repo.setApiKeys(ModelProviders.META, listOf("custom-merchant-meta-key"))
        assertEquals(listOf("custom-merchant-meta-key"), repo.getApiKeys(ModelProviders.META))
    }
}
