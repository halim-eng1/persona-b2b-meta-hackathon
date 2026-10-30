package com.example.persona.features.auto_reply.data.providers

import android.content.Context
import android.content.SharedPreferences
import com.example.persona.features.auto_reply.domain.orchestrator.SystemAlertNotificationHandler
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ProviderCircuitBreakerAndKeyManagerTest {

    private lateinit var context: Context
    private lateinit var prefs: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor
    private lateinit var alertHandler: SystemAlertNotificationHandler
    private lateinit var manager: ProviderCircuitBreakerAndKeyManager

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        prefs = mockk(relaxed = true)
        editor = mockk(relaxed = true)
        alertHandler = mockk(relaxed = true)

        every { context.getSharedPreferences(any(), any()) } returns prefs
        every { prefs.edit() } returns editor
        every { editor.putString(any(), any()) } returns editor
        every { editor.apply() } returns Unit
        every { prefs.getString(any(), any()) } returns null

        manager = ProviderCircuitBreakerAndKeyManager(context, alertHandler)
    }

    @Test
    fun `default models preserves all 10 Qwen models without pruning`() {
        val qwenModels = manager.getModels(ProviderCircuitBreakerAndKeyManager.PROVIDER_QWEN)
        assertEquals(10, qwenModels.size)
        assertTrue(qwenModels.contains("qwen-max"))
        assertTrue(qwenModels.contains("qwen-plus"))
        assertTrue(qwenModels.contains("qwen-turbo"))
        assertTrue(qwenModels.contains("qwen2.5-72b-instruct"))
        assertTrue(qwenModels.contains("qwen2.5-32b-instruct"))
        assertTrue(qwenModels.contains("qwen2.5-14b-instruct"))
        assertTrue(qwenModels.contains("qwen2.5-7b-instruct"))
        assertTrue(qwenModels.contains("qwen-long"))
        assertTrue(qwenModels.contains("deepseek-r1"))
        assertTrue(qwenModels.contains("deepseek-v3"))
    }

    @Test
    fun `key rotation cycles through active keys in pool`() {
        val keys = listOf("key_1", "key_2", "key_3")
        manager.setKeyPool(ProviderCircuitBreakerAndKeyManager.PROVIDER_GROQ, keys)

        assertEquals("key_1", manager.getCurrentOrNextKey(ProviderCircuitBreakerAndKeyManager.PROVIDER_GROQ))
        manager.rotateKey(ProviderCircuitBreakerAndKeyManager.PROVIDER_GROQ)
        assertEquals("key_2", manager.getCurrentOrNextKey(ProviderCircuitBreakerAndKeyManager.PROVIDER_GROQ))
        manager.rotateKey(ProviderCircuitBreakerAndKeyManager.PROVIDER_GROQ)
        assertEquals("key_3", manager.getCurrentOrNextKey(ProviderCircuitBreakerAndKeyManager.PROVIDER_GROQ))
        manager.rotateKey(ProviderCircuitBreakerAndKeyManager.PROVIDER_GROQ)
        assertEquals("key_1", manager.getCurrentOrNextKey(ProviderCircuitBreakerAndKeyManager.PROVIDER_GROQ))
    }

    @Test
    fun `evictKey evicts failing key, rotates to next, and alerts via notification`() {
        val keys = listOf("bad_key_12345678", "good_key_87654321")
        manager.setKeyPool(ProviderCircuitBreakerAndKeyManager.PROVIDER_GEMINI, keys)

        assertEquals("bad_key_12345678", manager.getCurrentOrNextKey(ProviderCircuitBreakerAndKeyManager.PROVIDER_GEMINI))

        manager.evictKey(
            ProviderCircuitBreakerAndKeyManager.PROVIDER_GEMINI,
            "bad_key_12345678",
            "HTTP 401 Unauthorized"
        )

        // Key evicted and rotated to good key
        assertEquals("good_key_87654321", manager.getCurrentOrNextKey(ProviderCircuitBreakerAndKeyManager.PROVIDER_GEMINI))
        val active = manager.getActiveKeys(ProviderCircuitBreakerAndKeyManager.PROVIDER_GEMINI)
        assertEquals(1, active.size)
        assertEquals("good_key_87654321", active[0])

        // Verify alert notification dispatched
        verify(atLeast = 1) {
            alertHandler.notifyKeyEvicted(
                ProviderCircuitBreakerAndKeyManager.PROVIDER_GEMINI,
                match { it.contains("bad_ke") },
                match { it.contains("401") }
            )
        }
    }

    @Test
    fun `evicting all keys trips circuit breaker to OPEN with 5 min cooldown alert`() {
        manager.setKeyPool(ProviderCircuitBreakerAndKeyManager.PROVIDER_OPENROUTER, listOf("only_key_1234567"))
        assertTrue(manager.isProviderHealthy(ProviderCircuitBreakerAndKeyManager.PROVIDER_OPENROUTER))

        manager.evictKey(
            ProviderCircuitBreakerAndKeyManager.PROVIDER_OPENROUTER,
            "only_key_1234567",
            "HTTP 402 Insufficient Quota"
        )

        // All keys gone -> circuit trips
        assertEquals(CircuitState.OPEN, manager.getCircuitState(ProviderCircuitBreakerAndKeyManager.PROVIDER_OPENROUTER))
        assertFalse(manager.isProviderHealthy(ProviderCircuitBreakerAndKeyManager.PROVIDER_OPENROUTER))

        verify {
            alertHandler.notifyProviderTripped(
                ProviderCircuitBreakerAndKeyManager.PROVIDER_OPENROUTER,
                5,
                any()
            )
        }
    }

    @Test
    fun `2 consecutive timeouts trip circuit breaker to OPEN`() {
        val provider = ProviderCircuitBreakerAndKeyManager.PROVIDER_NVIDIA
        assertTrue(manager.isProviderHealthy(provider))

        // Timeout 1
        manager.recordTimeout(provider)
        assertEquals(CircuitState.CLOSED, manager.getCircuitState(provider))
        assertTrue(manager.isProviderHealthy(provider))

        // Timeout 2 -> trips to OPEN
        manager.recordTimeout(provider)
        assertEquals(CircuitState.OPEN, manager.getCircuitState(provider))
        assertFalse(manager.isProviderHealthy(provider)) // 0ms fast failover

        verify {
            alertHandler.notifyProviderTripped(provider, 5, match { it.contains("2 consecutive timeouts") })
        }
    }

    @Test
    fun `trial call success in HALF_OPEN resets circuit breaker to CLOSED`() {
        val provider = ProviderCircuitBreakerAndKeyManager.PROVIDER_QWEN
        manager.tripCircuitBreaker(provider, "Forced test trip")
        assertEquals(CircuitState.OPEN, manager.getCircuitState(provider))

        // Reset to HALF_OPEN
        manager.resetCircuitBreaker(provider)
        assertEquals(CircuitState.CLOSED, manager.getCircuitState(provider))

        // Test recordSuccess resets counters
        manager.recordSuccess(provider)
        assertTrue(manager.isProviderHealthy(provider))
    }

    @Test
    fun `dynamic model management allows adding, removing, and reordering models`() {
        val provider = ProviderCircuitBreakerAndKeyManager.PROVIDER_GROQ
        manager.addModel(provider, "custom-groq-model-1")

        val modelsAfterAdd = manager.getModels(provider)
        assertTrue(modelsAfterAdd.contains("custom-groq-model-1"))

        manager.removeModel(provider, "custom-groq-model-1")
        val modelsAfterRemove = manager.getModels(provider)
        assertFalse(modelsAfterRemove.contains("custom-groq-model-1"))

        val newOrder = listOf("llama-3.1-8b-instant", "llama-3.3-70b-versatile")
        manager.reorderModels(provider, newOrder)
        assertEquals(newOrder, manager.getModels(provider))
    }

    @Test
    fun `dynamic cascade priority reorders failover sequence`() {
        val newCascade = listOf(
            ProviderCircuitBreakerAndKeyManager.PROVIDER_NARA,
            ProviderCircuitBreakerAndKeyManager.PROVIDER_GROQ,
            ProviderCircuitBreakerAndKeyManager.PROVIDER_GEMINI
        )
        manager.setCascadeOrder(newCascade)
        assertEquals(newCascade, manager.getCascadeOrder())
    }

    @Test
    fun `maskKey hides key body but exposes head and tail for debug identification`() {
        assertEquals("sk-ant...1234", ProviderCircuitBreakerAndKeyManager.maskKey("sk-ant12345678901234"))
        assertEquals("...1234", ProviderCircuitBreakerAndKeyManager.maskKey("key1234"))
        assertEquals("[BLANK]", ProviderCircuitBreakerAndKeyManager.maskKey("   "))
    }
}
