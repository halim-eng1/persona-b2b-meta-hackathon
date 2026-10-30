package com.example.persona.features.auto_reply.data.providers

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.persona.core.logging.PersonaLogger
import com.example.persona.features.auto_reply.domain.orchestrator.SystemAlertNotificationHandler
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

enum class CircuitState {
    CLOSED,
    OPEN,
    HALF_OPEN
}

/**
 * Manages unlimited API key pools per LLM provider, key rotation, eviction on quota/credit errors,
 * 3-state circuit breaker protection with 5-minute cooldown, dynamic model list management,
 * and dynamic provider cascade priority.
 */
@Singleton
class ProviderCircuitBreakerAndKeyManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val systemAlertHandler: SystemAlertNotificationHandler
) {
    companion object {
        private const val TAG = "ProviderCircuitBreaker"
        const val COOLDOWN_DURATION_MS = 5 * 60 * 1000L // 5 minutes cooldown
        private const val PREFS_NAME = "provider_circuit_breaker_prefs"

        const val PROVIDER_QWEN = "qwen"
        const val PROVIDER_GEMINI = "gemini"
        const val PROVIDER_NVIDIA = "nvidia"
        const val PROVIDER_GROQ = "groq"
        const val PROVIDER_OPENROUTER = "openrouter"
        const val PROVIDER_NARA = "nara"
        const val PROVIDER_LIQUID = "liquid"
        const val PROVIDER_CLOUDFLARE = "cloudflare"

        /**
         * MANDATORY ARCHITECTURAL OVERRIDE:
         * Strictly FORBIDDEN to reduce experimental Qwen models!
         * All 10 default Qwen models MUST remain.
         */
        val DEFAULT_QWEN_MODELS = QwenCloudClient.QWEN_TEXT_MODELS
        val DEFAULT_GEMINI_MODELS = GeminiClient.GEMINI_TEXT_MODELS
        val DEFAULT_NVIDIA_MODELS = NvidiaNimClient.NVIDIA_TEXT_MODELS
        val DEFAULT_GROQ_MODELS = GroqClient.GROQ_MODELS
        val DEFAULT_OPENROUTER_MODELS = OpenRouterClient.OPENROUTER_FAILOVER_MODELS
        val DEFAULT_NARA_MODELS = NaraRouterClient.NARA_TEXT_MODELS
        val DEFAULT_CLOUDFLARE_MODELS = listOf("@cf/cloudflare/clef-flash", "@cf/cloudflare/clef-omni")
        val DEFAULT_LIQUID_MODELS = listOf("d1:latest", "d1")

        val DEFAULT_CASCADE_ORDER = listOf(
            PROVIDER_QWEN,
            PROVIDER_GEMINI,
            PROVIDER_NVIDIA,
            PROVIDER_GROQ,
            PROVIDER_OPENROUTER,
            PROVIDER_NARA
        )

        fun maskKey(key: String): String {
            val trimmed = key.trim()
            return if (trimmed.length >= 10) {
                "${trimmed.take(6)}...${trimmed.takeLast(4)}"
            } else if (trimmed.isNotEmpty()) {
                "...${trimmed.takeLast(minOf(4, trimmed.length))}"
            } else {
                "[BLANK]"
            }
        }
    }

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // Provider state tracking
    private val circuitStates = ConcurrentHashMap<String, CircuitState>()
    private val tripTimestamps = ConcurrentHashMap<String, Long>()
    private val consecutiveTimeouts = ConcurrentHashMap<String, AtomicInteger>()
    private val consecutiveFailures = ConcurrentHashMap<String, AtomicInteger>()

    // Key management per provider
    private val keyPools = ConcurrentHashMap<String, MutableList<String>>()
    private val evictedKeys = ConcurrentHashMap<String, MutableSet<String>>()
    private val keyPointers = ConcurrentHashMap<String, AtomicInteger>()

    // Dynamic model management per provider
    private val modelLists = ConcurrentHashMap<String, MutableList<String>>()

    // Dynamic provider cascade priority
    private val cascadePriority = mutableListOf<String>()

    init {
        loadPreferences()
    }

    private fun loadPreferences() {
        // Cascade order
        val savedOrder = prefs.getString("cascade_order", null)
        val order = if (!savedOrder.isNullOrBlank()) {
            savedOrder.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        } else {
            DEFAULT_CASCADE_ORDER
        }
        cascadePriority.clear()
        cascadePriority.addAll(order)

        // Initialize default models
        initModelsForProvider(PROVIDER_QWEN, DEFAULT_QWEN_MODELS)
        initModelsForProvider(PROVIDER_GEMINI, DEFAULT_GEMINI_MODELS)
        initModelsForProvider(PROVIDER_NVIDIA, DEFAULT_NVIDIA_MODELS)
        initModelsForProvider(PROVIDER_GROQ, DEFAULT_GROQ_MODELS)
        initModelsForProvider(PROVIDER_OPENROUTER, DEFAULT_OPENROUTER_MODELS)
        initModelsForProvider(PROVIDER_NARA, DEFAULT_NARA_MODELS)
        initModelsForProvider(PROVIDER_CLOUDFLARE, DEFAULT_CLOUDFLARE_MODELS)
        initModelsForProvider(PROVIDER_LIQUID, DEFAULT_LIQUID_MODELS)
    }

    private fun initModelsForProvider(providerId: String, defaults: List<String>) {
        val saved = prefs.getString("models_$providerId", null)
        val list = if (!saved.isNullOrBlank()) {
            saved.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        } else {
            defaults
        }
        modelLists[providerId] = list.toMutableList()
    }

    // ── Key Pool Management ──────────────────────────────────────────────────

    fun setKeyPool(providerId: String, keys: List<String>) {
        val cleaned = keys.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        keyPools[providerId] = cleaned.toMutableList()
        evictedKeys[providerId] = mutableSetOf()
        keyPointers[providerId] = AtomicInteger(0)
    }

    fun addKey(providerId: String, key: String) {
        val trimmed = key.trim()
        if (trimmed.isEmpty()) return
        val pool = keyPools.getOrPut(providerId) { mutableListOf() }
        synchronized(pool) {
            if (!pool.contains(trimmed)) {
                pool.add(trimmed)
            }
        }
        evictedKeys[providerId]?.remove(trimmed)
    }

    fun removeKey(providerId: String, key: String) {
        val trimmed = key.trim()
        keyPools[providerId]?.remove(trimmed)
        evictedKeys[providerId]?.remove(trimmed)
    }

    fun getActiveKeys(providerId: String): List<String> {
        val pool = keyPools[providerId] ?: return emptyList()
        val evicted = evictedKeys[providerId] ?: emptySet()
        return synchronized(pool) {
            pool.filterNot { evicted.contains(it) }
        }
    }

    fun getCurrentOrNextKey(providerId: String): String? {
        val active = getActiveKeys(providerId)
        if (active.isEmpty()) return null
        val pointer = keyPointers.getOrPut(providerId) { AtomicInteger(0) }
        val idx = pointer.get() % active.size
        return active[idx]
    }

    fun rotateKey(providerId: String) {
        val active = getActiveKeys(providerId)
        if (active.isNotEmpty()) {
            val pointer = keyPointers.getOrPut(providerId) { AtomicInteger(0) }
            pointer.incrementAndGet()
        }
    }

    /**
     * Evicts an API key on Quota/Credit error (400/401/402/403 or Quota Exhausted).
     * Rotates to next key in key pool. If all keys evicted, trips circuit breaker for 5 minutes.
     */
    fun evictKey(providerId: String, key: String, reason: String) {
        val trimmed = key.trim()
        if (trimmed.isEmpty()) return

        val evictedSet = evictedKeys.getOrPut(providerId) { mutableSetOf() }
        val newlyEvicted = synchronized(evictedSet) {
            evictedSet.add(trimmed)
        }

        val masked = maskKey(trimmed)
        if (newlyEvicted) {
            PersonaLogger.w(TAG, "Evicting key $masked for [$providerId] due to: $reason")
            Log.w(TAG, "Evicting key $masked for [$providerId] due to: $reason")
            systemAlertHandler.notifyKeyEvicted(providerId, masked, reason)
        }

        rotateKey(providerId)

        val remaining = getActiveKeys(providerId)
        if (remaining.isEmpty()) {
            PersonaLogger.e(TAG, "All keys for [$providerId] evicted! Tripping circuit breaker for 5 minutes cooldown.")
            Log.e(TAG, "All keys for [$providerId] evicted! Tripping circuit breaker for 5 minutes cooldown.")
            tripCircuitBreaker(providerId, "نفاد كافة مفاتيح التشغيل أو رصيد غير كافٍ ($reason)")
        }
    }

    // ── Circuit Breaker Core ────────────────────────────────────────────────

    fun isProviderHealthy(providerId: String): Boolean {
        val state = circuitStates.getOrDefault(providerId, CircuitState.CLOSED)
        if (state == CircuitState.CLOSED) return true

        if (state == CircuitState.OPEN) {
            val trippedAt = tripTimestamps[providerId] ?: 0L
            val now = System.currentTimeMillis()
            if (now - trippedAt >= COOLDOWN_DURATION_MS) {
                PersonaLogger.i(TAG, "Circuit breaker for [$providerId] cooldown elapsed (5 mins). Transitioning to HALF_OPEN.")
                Log.i(TAG, "Circuit breaker for [$providerId] cooldown elapsed. Transitioning to HALF_OPEN.")
                circuitStates[providerId] = CircuitState.HALF_OPEN
                return true
            }
            return false // 0ms instantaneous bypass
        }

        return true // HALF_OPEN allows a trial call
    }

    fun recordSuccess(providerId: String) {
        consecutiveTimeouts.getOrPut(providerId) { AtomicInteger(0) }.set(0)
        consecutiveFailures.getOrPut(providerId) { AtomicInteger(0) }.set(0)
        if (circuitStates[providerId] == CircuitState.HALF_OPEN) {
            PersonaLogger.i(TAG, "Trial call succeeded on [$providerId]. Resetting circuit breaker to CLOSED.")
            Log.i(TAG, "Trial call succeeded on [$providerId]. Resetting circuit breaker to CLOSED.")
            circuitStates[providerId] = CircuitState.CLOSED
        }
    }

    fun recordTimeout(providerId: String) {
        val timeouts = consecutiveTimeouts.getOrPut(providerId) { AtomicInteger(0) }.incrementAndGet()
        PersonaLogger.w(TAG, "Timeout recorded on [$providerId] (consecutive=$timeouts)")
        Log.w(TAG, "Timeout recorded on [$providerId] (consecutive=$timeouts)")
        if (timeouts >= 2) {
            tripCircuitBreaker(providerId, "تكرار انتهاء المهلة (2 consecutive timeouts)")
        }
    }

    fun recordFailure(providerId: String, isServerError: Boolean = false) {
        val failures = consecutiveFailures.getOrPut(providerId) { AtomicInteger(0) }.incrementAndGet()
        if (isServerError && failures >= 3) {
            tripCircuitBreaker(providerId, "تكرار أخطاء الخادم (3 server errors)")
        }
    }

    fun tripCircuitBreaker(providerId: String, reason: String) {
        circuitStates[providerId] = CircuitState.OPEN
        tripTimestamps[providerId] = System.currentTimeMillis()
        consecutiveTimeouts.getOrPut(providerId) { AtomicInteger(0) }.set(0)
        consecutiveFailures.getOrPut(providerId) { AtomicInteger(0) }.set(0)

        systemAlertHandler.notifyProviderTripped(providerId, 5, reason)
    }

    fun resetCircuitBreaker(providerId: String) {
        circuitStates[providerId] = CircuitState.CLOSED
        tripTimestamps.remove(providerId)
        consecutiveTimeouts.getOrPut(providerId) { AtomicInteger(0) }.set(0)
        consecutiveFailures.getOrPut(providerId) { AtomicInteger(0) }.set(0)
        evictedKeys[providerId]?.clear()
    }

    // ── Dynamic Model Management ─────────────────────────────────────────────

    fun getModelList(providerId: String): List<String> {
        val current = modelLists[providerId]
        if (!current.isNullOrEmpty()) return current.toList()
        return when (providerId) {
            PROVIDER_QWEN -> DEFAULT_QWEN_MODELS
            PROVIDER_GEMINI -> DEFAULT_GEMINI_MODELS
            PROVIDER_NVIDIA -> DEFAULT_NVIDIA_MODELS
            PROVIDER_GROQ -> DEFAULT_GROQ_MODELS
            PROVIDER_OPENROUTER -> DEFAULT_OPENROUTER_MODELS
            PROVIDER_NARA -> DEFAULT_NARA_MODELS
            PROVIDER_CLOUDFLARE -> DEFAULT_CLOUDFLARE_MODELS
            PROVIDER_LIQUID -> DEFAULT_LIQUID_MODELS
            else -> emptyList()
        }
    }

    fun setModelList(providerId: String, models: List<String>) {
        val cleaned = models.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        modelLists[providerId] = cleaned.toMutableList()
        prefs.edit().putString("models_$providerId", cleaned.joinToString(",")).apply()
    }

    fun addModel(providerId: String, model: String) {
        val trimmed = model.trim()
        if (trimmed.isEmpty()) return
        val list = modelLists.getOrPut(providerId) { mutableListOf() }
        synchronized(list) {
            if (!list.contains(trimmed)) {
                list.add(trimmed)
            }
        }
        prefs.edit().putString("models_$providerId", list.joinToString(",")).apply()
    }

    fun removeModel(providerId: String, model: String) {
        val trimmed = model.trim()
        val list = modelLists[providerId] ?: return
        synchronized(list) {
            list.remove(trimmed)
        }
        prefs.edit().putString("models_$providerId", list.joinToString(",")).apply()
    }

    // ── Dynamic Provider Cascade Priority ───────────────────────────────────

    fun getProviderCascadeOrder(): List<String> {
        return synchronized(cascadePriority) {
            cascadePriority.toList().ifEmpty { DEFAULT_CASCADE_ORDER }
        }
    }

    fun setProviderCascadeOrder(order: List<String>) {
        val cleaned = order.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.distinct()
        synchronized(cascadePriority) {
            cascadePriority.clear()
            cascadePriority.addAll(cleaned)
        }
        prefs.edit().putString("cascade_order", cleaned.joinToString(",")).apply()
    }

    fun getCircuitState(providerId: String): CircuitState = circuitStates.getOrDefault(providerId, CircuitState.CLOSED)
    fun getModels(providerId: String): List<String> = getModelList(providerId)
    fun reorderModels(providerId: String, models: List<String>) = setModelList(providerId, models)
    fun getCascadeOrder(): List<String> = getProviderCascadeOrder()
    fun setCascadeOrder(order: List<String>) = setProviderCascadeOrder(order)
}