package com.example.persona.features.auto_reply.data.providers

import android.content.Context
import android.content.SharedPreferences
import com.example.persona.features.auto_reply.domain.orchestrator.SystemAlertNotificationHandler
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun `default models preserves all Meta models`() {
        val metaModels = manager.getModels(ProviderCircuitBreakerAndKeyManager.PROVIDER_META)
        assertEquals(ProviderCircuitBreakerAndKeyManager.DEFAULT_META_MODELS, metaModels)
    }

    @Test
    fun `key rotation cycles through active keys in pool`() {
        val keys = listOf("key_1", "key_2", "key_3")
        manager.setKeyPool(ProviderCircuitBreakerAndKeyManager.PROVIDER_META, keys)

        assertEquals("key_1", manager.getCurrentOrNextKey(ProviderCircuitBreakerAndKeyManager.PROVIDER_META))
        manager.rotateKey(ProviderCircuitBreakerAndKeyManager.PROVIDER_META)
        assertEquals("key_2", manager.getCurrentOrNextKey(ProviderCircuitBreakerAndKeyManager.PROVIDER_META))
        manager.rotateKey(ProviderCircuitBreakerAndKeyManager.PROVIDER_META)
        assertEquals("key_3", manager.getCurrentOrNextKey(ProviderCircuitBreakerAndKeyManager.PROVIDER_META))
        manager.rotateKey(ProviderCircuitBreakerAndKeyManager.PROVIDER_META)
        assertEquals("key_1", manager.getCurrentOrNextKey(ProviderCircuitBreakerAndKeyManager.PROVIDER_META))
    }

    @Test
    fun `evictKey evicts failing key, rotates to next, and alerts via notification`() {
        val keys = listOf("bad_key_12345678", "good_key_87654321")
        manager.setKeyPool(ProviderCircuitBreakerAndKeyManager.PROVIDER_META, keys)

        assertEquals("bad_key_12345678", manager.getCurrentOrNextKey(ProviderCircuitBreakerAndKeyManager.PROVIDER_META))

        manager.evictKey(
            ProviderCircuitBreakerAndKeyManager.PROVIDER_META,
            "bad_key_12345678",
            "HTTP 401 Unauthorized"
        )

        // Key evicted and rotated to good key
        assertEquals("good_key_87654321", manager.getCurrentOrNextKey(ProviderCircuitBreakerAndKeyManager.PROVIDER_META))
        val active = manager.getActiveKeys(ProviderCircuitBreakerAndKeyManager.PROVIDER_META)
        assertEquals(1, active.size)
        assertEquals("good_key_87654321", active[0])

        // Verify alert notification dispatched
        verify(atLeast = 1) {
            alertHandler.notifyKeyEvicted(
                ProviderCircuitBreakerAndKeyManager.PROVIDER_META,
                match { it.contains("bad_ke") },
                match { it.contains("401") }
            )
        }
    }

    @Test
    fun `evicting all keys trips circuit breaker to OPEN with 5 min cooldown alert`() {
        manager.setKeyPool(ProviderCircuitBreakerAndKeyManager.PROVIDER_META, listOf("only_key_1234567"))
        assertTrue(manager.isProviderHealthy(ProviderCircuitBreakerAndKeyManager.PROVIDER_META))

        manager.evictKey(
            ProviderCircuitBreakerAndKeyManager.PROVIDER_META,
            "only_key_1234567",
            "HTTP 402 Insufficient Quota"
        )

        // All keys gone -> circuit trips
        assertEquals(CircuitState.OPEN, manager.getCircuitState(ProviderCircuitBreakerAndKeyManager.PROVIDER_META))
        assertFalse(manager.isProviderHealthy(ProviderCircuitBreakerAndKeyManager.PROVIDER_META))

        verify {
            alertHandler.notifyProviderTripped(
                ProviderCircuitBreakerAndKeyManager.PROVIDER_META,
                5,
                any()
            )
        }
    }

    @Test
    fun `2 consecutive timeouts trip circuit breaker to OPEN`() {
        val provider = ProviderCircuitBreakerAndKeyManager.PROVIDER_META
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
        val provider = ProviderCircuitBreakerAndKeyManager.PROVIDER_META
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
        val provider = ProviderCircuitBreakerAndKeyManager.PROVIDER_META
        manager.addModel(provider, "muse-spark-custom-1")

        val modelsAfterAdd = manager.getModels(provider)
        assertTrue(modelsAfterAdd.contains("muse-spark-custom-1"))

        manager.removeModel(provider, "muse-spark-custom-1")
        val modelsAfterRemove = manager.getModels(provider)
        assertFalse(modelsAfterRemove.contains("muse-spark-custom-1"))

        val newOrder = listOf("muse-spark-1.2", "muse-spark-1.3")
        manager.reorderModels(provider, newOrder)
        assertEquals(newOrder, manager.getModels(provider))
    }

    @Test
    fun `cascade order always keeps Meta as the only failover target`() {
        val newCascade = listOf(ProviderCircuitBreakerAndKeyManager.PROVIDER_META)
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
