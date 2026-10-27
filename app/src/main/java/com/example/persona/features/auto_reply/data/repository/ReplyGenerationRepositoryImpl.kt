package com.example.persona.features.auto_reply.data.repository

import android.content.Context
import android.util.Log
import com.example.persona.BuildConfig
import com.example.persona.core.data.repository.UserPreferencesRepository
import com.example.persona.core.database.entity.ChatHistoryEntity
import com.example.persona.core.database.entity.OrderEntity
import com.example.persona.core.logging.PersonaLogger
import com.example.persona.core.logging.PersonaLogTags
import com.example.persona.core.network.dto.ChatMessage
import com.example.persona.core.network.dto.GroqRequest
import com.example.persona.core.network.dto.GroqResponse
import com.example.persona.core.network.dto.OpenRouterRequest
import com.example.persona.core.network.dto.OpenRouterResponse
import com.example.persona.core.network.dto.ReasoningConfig
import com.example.persona.features.auto_reply.domain.model.ProcessedNotification
import com.example.persona.features.auto_reply.domain.model.RetrievalCandidate
import com.example.persona.features.auto_reply.domain.orchestrator.ReasoningLeakSanitizer
import com.example.persona.features.auto_reply.domain.repository.ReplyGenerationRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cloud LLM generation via Groq Cloud (Primary Tier 1) with OpenRouter Failover (Tier 2).
 * Delegates prompt assembly and token budgeting to [PromptContextBuilder].
 */
@Singleton
class ReplyGenerationRepositoryImpl @Inject constructor(
    private val httpClient: HttpClient,
    private val promptContextBuilder: PromptContextBuilder,
    @ApplicationContext private val context: Context,
    private val userPreferencesRepository: UserPreferencesRepository
) : ReplyGenerationRepository {

    /** Test seam: overrides API keys and supplies dependencies explicitly. */
    internal constructor(
        httpClient: HttpClient,
        context: Context,
        primaryApiKey: String,
        fallbackApiKey: String,
        promptContextBuilder: PromptContextBuilder = PromptContextBuilder(),
        userPreferencesRepository: UserPreferencesRepository,
        groqPrimaryApiKey: String? = "",
        groqFallbackApiKey: String? = ""
    ) : this(httpClient, promptContextBuilder, context, userPreferencesRepository) {
        this.primaryApiKeyOverride = primaryApiKey
        this.fallbackApiKeyOverride = fallbackApiKey
        this.groqPrimaryApiKeyOverride = groqPrimaryApiKey
        this.groqFallbackApiKeyOverride = groqFallbackApiKey
    }

    private var primaryApiKeyOverride: String? = null
    private var fallbackApiKeyOverride: String? = null
    private var groqPrimaryApiKeyOverride: String? = null
    private var groqFallbackApiKeyOverride: String? = null

    internal val primaryApiKey: String
        get() = primaryApiKeyOverride ?: BuildConfig.OPENROUTER_API_KEY

    internal val fallbackApiKey: String
        get() = fallbackApiKeyOverride ?: BuildConfig.OPENROUTER_API_KEY_V2

    internal val groqPrimaryApiKey: String
        get() = groqPrimaryApiKeyOverride ?: BuildConfig.GROQ_API_KEY

    internal val groqFallbackApiKey: String
        get() = groqFallbackApiKeyOverride ?: BuildConfig.GROQ_API_KEY_V2

    companion object {
        private const val TAG = PersonaLogTags.LLM

        private const val MODEL_TIMEOUT_MS = 25000L
        private const val ABSOLUTE_FAILOVER_CEILING = 8000

        private val jsonParser = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

        const val HITL_FALLBACK_JSON =
            """{"action":"HITL","refined_search_keyword":null,"order_action":null,"final_reply_text":"${PromptContextBuilder.CANONICAL_HITL_MESSAGE}","extracted_order":null}"""

        val CANONICAL_HITL_MESSAGE = PromptContextBuilder.CANONICAL_HITL_MESSAGE

        /** Tier 1: Groq Cloud models */
        val GROQ_MODELS = listOf(
            "openai/gpt-oss-120b", // Primary Champion: high intelligence, witty Egyptian persona
            "openai/gpt-oss-20b"    // Lightning Secondary: sub-second fallback
        )

        /** Tier 2: OpenRouter Failover chain */
        val OPENROUTER_FAILOVER_MODELS = listOf(
            "nvidia/nemotron-3-ultra-550b-a55b:free",
            "liquid/lfm-2.5-2.6b:free",
            "cohere/north-mini-code:free",
            "openrouter/free"
        )

        /** Multimodal Vision models for Groq */
        val VISION_GROQ_MODELS = listOf(
            "llama-3.2-11b-vision-preview"
        )

        /** Multimodal Vision models for OpenRouter */
        val VISION_OPENROUTER_MODELS = listOf(
            "nvidia/nemotron-3.5-super-vl:free",
            "meta-llama/llama-3.2-11b-vision-instruct:free"
        )

        val FAILOVER_MODELS = OPENROUTER_FAILOVER_MODELS

        internal val REASONING_MODELS = setOf(
            "nvidia/nemotron-3-ultra-550b-a55b:free",
            "liquid/lfm-2.5-2.6b:free",
            "cohere/north-mini-code:free"
        )

        // Derived from the single-source builders; not `const` because the rule text is
        // now assembled by Kotlin functions rather than compile-time literals.
        val SYSTEM_PROMPT_FIRST_PASS = PromptContextBuilder.SYSTEM_PROMPT_FIRST_PASS
        val SYSTEM_PROMPT_FINAL_PASS = PromptContextBuilder.SYSTEM_PROMPT_FINAL_PASS

        // Backward compatibility bridges for existing static callers & tests
        internal fun formatStoreKnowledge(
            candidates: List<RetrievalCandidate>,
            budgetTokens: Int = 2500
        ): String = PromptContextBuilder.formatStoreKnowledge(candidates, budgetTokens)

        internal fun formatCustomerOrderState(activeOrders: List<OrderEntity>): String =
            PromptContextBuilder.formatCustomerOrderState(activeOrders)

        internal fun buildSystemPromptWithContext(
            staticSystemPrompt: String,
            candidates: List<RetrievalCandidate>,
            activeOrders: List<OrderEntity>,
            storeTopics: List<String> = emptyList()
        ): String = PromptContextBuilder.buildSystemPromptWithContext(staticSystemPrompt, candidates, activeOrders, storeTopics)

        internal fun formatHistoricalAssistantTurn(rawText: String): String =
            PromptContextBuilder.formatHistoricalAssistantTurn(rawText)

        internal fun isCanonicalHitlMessage(text: String): Boolean =
            PromptContextBuilder.isCanonicalHitlMessage(text)

        @Deprecated("Use buildSystemPromptWithContext instead")
        internal fun buildDynamicContextBlock(
            notificationText: String,
            candidates: List<RetrievalCandidate>,
            activeOrders: List<OrderEntity>,
            personalChunks: List<ChatHistoryEntity> = emptyList()
        ): String = PromptContextBuilder.buildDynamicContextBlock(notificationText, candidates, activeOrders, personalChunks)

        internal fun filterAndPairChatHistory(
            chatHistory: List<ChatHistoryEntity>,
            nowMs: Long = System.currentTimeMillis()
        ): List<ChatHistoryEntity> = PromptContextBuilder.filterAndPairChatHistory(chatHistory, nowMs)

        internal fun buildOpenRouterMessages(
            systemPrompt: String,
            chatHistory: List<ChatHistoryEntity>,
            currentInboundText: String,
            nowMs: Long = System.currentTimeMillis(),
            imageUrl: String? = null
        ): List<ChatMessage> = PromptContextBuilder.buildOpenRouterMessages(systemPrompt, chatHistory, currentInboundText, nowMs, imageUrl)

        internal fun formatRelativeTime(
            timestampMs: Long,
            nowMs: Long = System.currentTimeMillis()
        ): String = PromptContextBuilder.formatRelativeTime(timestampMs, nowMs)

        internal fun estimateTokens(text: String): Int = PromptContextBuilder.estimateTokens(text)

        internal fun maskKey(key: String): String {
            return if (key.length >= 10) {
                "${key.take(6)}...${key.takeLast(4)}"
            } else if (key.isNotEmpty()) {
                "...${key.takeLast(minOf(4, key.length))}"
            } else {
                "[BLANK]"
            }
        }
    }

    // ── Public API ─────────────────────────────────────────────────────────

    override suspend fun generateReply(
        notification: ProcessedNotification,
        candidates: List<RetrievalCandidate>,
        isOngoingConversation: Boolean,
        chatHistory: List<ChatHistoryEntity>,
        activeOrders: List<OrderEntity>,
        personalChunks: List<ChatHistoryEntity>,
        maxTokens: Int
    ): String? {
        val storeTopics = userPreferencesRepository.storeKnowledgeTopicsFlow.first()
        val tone = userPreferencesRepository.stylePreferences.first().personaTone
        val baseSystemPrompt = PromptContextBuilder.buildFirstPassSystemPrompt(
            botName = userPreferencesRepository.getBotNameSync(),
            storeName = userPreferencesRepository.getStoreNameSync(),
            tone = tone
        )
        val systemPromptWithContext = PromptContextBuilder.buildSystemPromptWithContext(
            staticSystemPrompt = baseSystemPrompt,
            candidates = candidates,
            activeOrders = activeOrders,
            storeTopics = storeTopics
        )

        val imageUri = notification.mediaFilePath?.let { path ->
            try {
                val file = java.io.File(path)
                if (file.exists() && file.length() > 0) {
                    val bytes = file.readBytes()
                    val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                    "data:image/jpeg;base64,$base64"
                } else null
            } catch (e: Exception) {
                Log.w(TAG, "Failed to encode media image to Base64: ${e.message}")
                null
            }
        }

        val inboundText = if (imageUri != null && (notification.originalText.contains("Sent a photo", ignoreCase = true) || notification.originalText.trim() == "📷")) {
            "العميل أرسل هذه الصورة لمنتج. تفحص الصورة وتعرف على مواصفات ونوع المنتج المعروض فيها، وقدم له تفاصيل المنتج وسعره المتاح من واقع بيانات المتجر واعرض عليه إتمام الطلب."
        } else {
            notification.originalText
        }

        val messages = PromptContextBuilder.buildOpenRouterMessages(
            systemPrompt = systemPromptWithContext,
            chatHistory = chatHistory,
            currentInboundText = inboundText,
            imageUrl = imageUri
        )
        return executeFailoverChain(messages, maxTokens)
    }

    override suspend fun generateFinalReply(
        queryText: String,
        chunks: List<RetrievalCandidate>,
        isOngoingConversation: Boolean,
        chatHistory: List<ChatHistoryEntity>,
        activeOrders: List<OrderEntity>,
        personalChunks: List<ChatHistoryEntity>,
        maxTokens: Int,
        imageUrl: String?
    ): String? {
        val storeTopics = userPreferencesRepository.storeKnowledgeTopicsFlow.first()
        val tone = userPreferencesRepository.stylePreferences.first().personaTone
        val baseSystemPrompt = PromptContextBuilder.buildFinalPassSystemPrompt(
            botName = userPreferencesRepository.getBotNameSync(),
            storeName = userPreferencesRepository.getStoreNameSync(),
            tone = tone
        )
        val systemPromptWithContext = PromptContextBuilder.buildSystemPromptWithContext(
            staticSystemPrompt = baseSystemPrompt,
            candidates = chunks,
            activeOrders = activeOrders,
            storeTopics = storeTopics
        )
        val messages = PromptContextBuilder.buildOpenRouterMessages(
            systemPrompt = systemPromptWithContext,
            chatHistory = chatHistory,
            currentInboundText = queryText,
            imageUrl = imageUrl
        )
        return executeFailoverChain(messages, maxTokens)
    }

    // ── Internal / Private Implementation ───────────────────────────────

    internal sealed interface CallResult {
        data class Success(val content: String) : CallResult
        data object RateLimited : CallResult
        data class AuthOrQuotaError(val code: Int) : CallResult
        data class ServerError(val code: Int) : CallResult
        data object Timeout : CallResult
        data object Failed : CallResult
    }

    internal suspend fun executeFailoverChain(messages: List<ChatMessage>, maxTokens: Int): String? {
        val targetMaxTokens = minOf(maxTokens, ABSOLUTE_FAILOVER_CEILING)
        val hasImage = messages.any { !it.imageUrl.isNullOrBlank() }

        val groqModelsToTry = if (hasImage) VISION_GROQ_MODELS else GROQ_MODELS
        val openRouterModelsToTry = if (hasImage) VISION_OPENROUTER_MODELS else OPENROUTER_FAILOVER_MODELS

        if (hasImage) {
            PersonaLogger.i(TAG, "📸 Multimodal Vision request detected. Dispatching to Vision-capable models...")
            Log.i(TAG, "📸 Multimodal Vision request detected. Dispatching to Vision-capable models...")
        }

        // ── Tier 1: Groq Cloud (Primary Engine) ──────────────────────────
        val groqStartTime = System.currentTimeMillis()
        val hasGroqKeys = groqPrimaryApiKey.isNotBlank() || groqFallbackApiKey.isNotBlank()

        if (hasGroqKeys) {
            for (model in groqModelsToTry) {
                val result = callGroq(model, messages, targetMaxTokens)
                if (result is CallResult.Success) {
                    val durationMs = System.currentTimeMillis() - groqStartTime
                    PersonaLogger.i(TAG, "Groq Tier succeeded in ${durationMs}ms!")
                    Log.i(TAG, "Groq Tier succeeded with model $model in ${durationMs}ms")
                    return result.content
                }
            }
            PersonaLogger.w(TAG, "Groq Tier exhausted. Cascading to Tier 2 (OpenRouter)...")
            Log.w(TAG, "Groq Tier exhausted. Cascading to Tier 2 (OpenRouter)...")
        }

        // ── Tier 2: OpenRouter Failover (Emergency Safety Net) ───────────
        var consecutive429Count = 0

        for ((index, model) in openRouterModelsToTry.withIndex()) {
            Log.d(TAG, "Attempting [OpenRouter] model ${index + 1}/${openRouterModelsToTry.size}: $model with max_tokens=$targetMaxTokens")
            PersonaLogger.d(TAG, "Trying OpenRouter model: $model")

            when (val result = callOpenRouter(model, messages, targetMaxTokens)) {
                is CallResult.Success -> {
                    Log.i(TAG, "Success on OpenRouter model $model")
                    PersonaLogger.d(TAG, "LLM response received from OpenRouter model: $model")
                    return result.content
                }
                is CallResult.RateLimited -> {
                    consecutive429Count++

                    if (consecutive429Count >= 3) {
                        Log.w(TAG, "3 consecutive OpenRouter models returned 429 (account quota / RPM limit). Breaking early to HITL fallback.")
                        PersonaLogger.d(TAG, "OpenRouter account quota exhausted (consecutive 429s) — early HITL fallback")
                        return HITL_FALLBACK_JSON
                    }

                    delay(1200L)
                }
                else -> {
                    consecutive429Count = 0
                }
            }
        }

        Log.w(TAG, "All models failed across Groq and OpenRouter tiers. Returning HITL fallback JSON.")
        PersonaLogger.d(TAG, "All models failed — HITL fallback")
        return HITL_FALLBACK_JSON
    }

    internal suspend fun callGroq(
        model: String,
        messages: List<ChatMessage>,
        maxTokens: Int
    ): CallResult = withContext(Dispatchers.IO) {
        val primary = groqPrimaryApiKey.trim()
        val fallback = groqFallbackApiKey.trim()

        val effectivePrimary = primary.ifBlank { fallback }
        val hasDistinctFallback = fallback.isNotBlank() && fallback != effectivePrimary

        if (effectivePrimary.isBlank()) {
            Log.w(TAG, "No Groq API keys configured; skipping Groq model $model")
            return@withContext CallResult.Failed
        }

        val primaryMasked = maskKey(effectivePrimary)
        PersonaLogger.i(TAG, "Attempting [Groq] model $model with key $primaryMasked")
        Log.i(TAG, "Attempting [Groq] model $model with key $primaryMasked")

        val primaryResult = executeSingleGroqCall(
            model = model,
            messages = messages,
            maxTokens = maxTokens,
            apiKey = effectivePrimary
        )

        if (primaryResult is CallResult.Success) {
            return@withContext primaryResult
        }

        val isKeyOrServerError = primaryResult is CallResult.RateLimited ||
            primaryResult is CallResult.AuthOrQuotaError ||
            primaryResult is CallResult.ServerError

        if (hasDistinctFallback && isKeyOrServerError) {
            val fallbackMasked = maskKey(fallback)
            Log.w(
                TAG,
                "Primary Groq key ($primaryMasked) failed on model $model with $primaryResult. " +
                    "Falling back to GROQ_API_KEY_V2 ($fallbackMasked)."
            )
            PersonaLogger.w(
                TAG,
                "Primary Groq key ($primaryMasked) failed on model $model with $primaryResult -> fallback to GROQ_API_KEY_V2 ($fallbackMasked)"
            )
            PersonaLogger.i(TAG, "Attempting [Groq] model $model with key $fallbackMasked")

            val fallbackResult = executeSingleGroqCall(
                model = model,
                messages = messages,
                maxTokens = maxTokens,
                apiKey = fallback
            )

            if (fallbackResult is CallResult.Success) {
                Log.i(TAG, "Fallback GROQ_API_KEY_V2 succeeded on model $model")
                PersonaLogger.i(TAG, "Fallback GROQ_API_KEY_V2 succeeded on model $model")
                return@withContext fallbackResult
            }

            Log.w(
                TAG,
                "Fallback GROQ_API_KEY_V2 also failed on model $model with $fallbackResult"
            )
            return@withContext fallbackResult
        }

        primaryResult
    }

    internal suspend fun executeSingleGroqCall(
        model: String,
        messages: List<ChatMessage>,
        maxTokens: Int,
        apiKey: String
    ): CallResult {
        if (apiKey.isBlank()) {
            Log.w(TAG, "Groq API key is blank; skipping request for model $model")
            return CallResult.Failed
        }

        val keyMasked = maskKey(apiKey)

        return try {
            withTimeout(MODEL_TIMEOUT_MS) {
                var response = httpClient.post(BuildConfig.GROQ_BASE_URL) {
                    contentType(ContentType.Application.Json)
                    bearerAuth(apiKey)
                    setBody(
                        GroqRequest(
                            model = model,
                            messages = messages,
                            maxCompletionTokens = maxTokens,
                            reasoningEffort = "low"
                        )
                    )
                }

                if (response.status.value == 400) {
                    Log.w(TAG, "HTTP 400 on [Groq] $model with reasoning_effort. Retrying without reasoning payload.")
                    response = httpClient.post(BuildConfig.GROQ_BASE_URL) {
                        contentType(ContentType.Application.Json)
                        bearerAuth(apiKey)
                        setBody(
                            GroqRequest(
                                model = model,
                                messages = messages,
                                maxCompletionTokens = maxTokens,
                                reasoningEffort = null
                            )
                        )
                    }
                }

                when {
                    response.status.value == 429 -> {
                        Log.w(TAG, "Rate limited (429) on [Groq] model $model (key $keyMasked)")
                        CallResult.RateLimited
                    }
                    response.status.value in listOf(401, 402, 403) -> {
                        Log.w(TAG, "Auth/quota error (${response.status.value}) on [Groq] model $model (key $keyMasked)")
                        CallResult.AuthOrQuotaError(response.status.value)
                    }
                    response.status.value in 500..599 -> {
                        Log.w(TAG, "Server error (${response.status.value}) on [Groq] model $model (key $keyMasked)")
                        CallResult.ServerError(response.status.value)
                    }
                    response.status.isSuccess() -> {
                        val responseBody = response.body<String>()
                        PersonaLogger.d(PersonaLogTags.LLM, "📥 Raw LLM Output from [Groq] $model: $responseBody")
                        val body = jsonParser.decodeFromString<GroqResponse>(responseBody)
                        val content = body.choices?.firstOrNull()?.message?.content
                        if (content.isNullOrBlank()) {
                            Log.w(TAG, "Empty content in successful response from [Groq] $model (key $keyMasked)")
                            CallResult.Failed
                        } else {
                            try {
                                ReasoningLeakSanitizer.decodeAgenticResponseSafely(content)
                                CallResult.Success(content)
                            } catch (e: Exception) {
                                Log.w(TAG, "Invalid JSON content in response from [Groq] $model (key $keyMasked): $content", e)
                                CallResult.Failed
                            }
                        }
                    }
                    else -> {
                        Log.w(TAG, "HTTP ${response.status.value} error from [Groq] $model (key $keyMasked)")
                        CallResult.Failed
                    }
                }
            }
        } catch (e: TimeoutCancellationException) {
            Log.w(TAG, "Timeout (${MODEL_TIMEOUT_MS}ms) on [Groq] model $model (key $keyMasked)")
            CallResult.Timeout
        } catch (e: Exception) {
            Log.e(TAG, "Network error on [Groq] model $model (key $keyMasked)", e)
            CallResult.Failed
        }
    }

    internal suspend fun callOpenRouter(
        model: String,
        messages: List<ChatMessage>,
        maxTokens: Int
    ): CallResult = withContext(Dispatchers.IO) {
        val primary = primaryApiKey.trim()
        val fallback = fallbackApiKey.trim()

        val effectivePrimary = primary.ifBlank { fallback }
        val hasDistinctFallback = fallback.isNotBlank() && fallback != effectivePrimary

        if (effectivePrimary.isBlank()) {
            Log.w(TAG, "No OpenRouter API keys configured; skipping model $model")
            return@withContext CallResult.Failed
        }

        val primaryMasked = maskKey(effectivePrimary)
        PersonaLogger.i(TAG, "Attempting [OpenRouter] model $model with key $primaryMasked")

        val primaryResult = executeSingleOpenRouterCall(
            model = model,
            messages = messages,
            maxTokens = maxTokens,
            apiKey = effectivePrimary
        )

        if (primaryResult is CallResult.Success) {
            return@withContext primaryResult
        }

        val isKeyOrQuotaError = primaryResult is CallResult.RateLimited || primaryResult is CallResult.AuthOrQuotaError

        if (hasDistinctFallback && isKeyOrQuotaError) {
            val fallbackMasked = maskKey(fallback)
            Log.w(
                TAG,
                "Primary OpenRouter key ($primaryMasked) failed on model $model with key/quota error $primaryResult. " +
                    "Falling back to OPENROUTER_API_KEY_V2 ($fallbackMasked)."
            )
            PersonaLogger.w(
                TAG,
                "Primary OpenRouter key ($primaryMasked) failed on $model ($primaryResult) -> fallback to OPENROUTER_API_KEY_V2 ($fallbackMasked)"
            )
            PersonaLogger.i(TAG, "Attempting [OpenRouter] model $model with key $fallbackMasked")

            val fallbackResult = executeSingleOpenRouterCall(
                model = model,
                messages = messages,
                maxTokens = maxTokens,
                apiKey = fallback
            )

            if (fallbackResult is CallResult.Success) {
                Log.i(TAG, "Fallback OPENROUTER_API_KEY_V2 succeeded on model $model")
                PersonaLogger.d(TAG, "Fallback OPENROUTER_API_KEY_V2 succeeded on model: $model")
                return@withContext fallbackResult
            }

            Log.w(
                TAG,
                "Fallback OPENROUTER_API_KEY_V2 also failed on model $model with $fallbackResult"
            )
            return@withContext fallbackResult
        }

        primaryResult
    }

    internal suspend fun executeSingleOpenRouterCall(
        model: String,
        messages: List<ChatMessage>,
        maxTokens: Int,
        apiKey: String
    ): CallResult {
        if (apiKey.isBlank()) {
            Log.w(TAG, "API key is blank; skipping request for model $model")
            return CallResult.Failed
        }

        val keyMasked = maskKey(apiKey)

        return try {
            withTimeout(MODEL_TIMEOUT_MS) {
                val reasoning = if (model in REASONING_MODELS) ReasoningConfig(effort = "low") else null
                var response = httpClient.post(BuildConfig.OPENROUTER_BASE_URL) {
                    contentType(ContentType.Application.Json)
                    bearerAuth(apiKey)
                    setBody(
                        OpenRouterRequest(
                            model = model,
                            messages = messages,
                            maxTokens = maxTokens,
                            reasoning = reasoning
                        )
                    )
                }

                if (response.status.value == 400 && reasoning != null) {
                    Log.w(TAG, "HTTP 400 on $model with reasoning object. Retrying without reasoning payload.")
                    response = httpClient.post(BuildConfig.OPENROUTER_BASE_URL) {
                        contentType(ContentType.Application.Json)
                        bearerAuth(apiKey)
                        setBody(
                            OpenRouterRequest(
                                model = model,
                                messages = messages,
                                maxTokens = maxTokens,
                                reasoning = null
                            )
                        )
                    }
                }

                when {
                    response.status.value == 429 -> {
                        Log.w(TAG, "Rate limited (429) on model $model (key $keyMasked)")
                        CallResult.RateLimited
                    }
                    response.status.value in listOf(401, 402, 403) -> {
                        Log.w(TAG, "Auth/quota error (${response.status.value}) on model $model (key $keyMasked)")
                        CallResult.AuthOrQuotaError(response.status.value)
                    }
                    response.status.value in 500..599 -> {
                        Log.w(TAG, "Server error (${response.status.value}) on model $model (key $keyMasked)")
                        CallResult.ServerError(response.status.value)
                    }
                    response.status.isSuccess() -> {
                        val responseBody = response.body<String>()
                        PersonaLogger.d(PersonaLogTags.LLM, "📥 Raw LLM Output from $model: $responseBody")
                        val body = jsonParser.decodeFromString<OpenRouterResponse>(responseBody)
                        val content = body.choices?.firstOrNull()?.message?.content
                        if (content.isNullOrBlank()) {
                            Log.w(TAG, "Empty content in successful response from $model (key $keyMasked)")
                            CallResult.Failed
                        } else {
                            CallResult.Success(content)
                        }
                    }
                    else -> {
                        Log.w(TAG, "HTTP ${response.status.value} error from $model (key $keyMasked)")
                        CallResult.Failed
                    }
                }
            }
        } catch (e: TimeoutCancellationException) {
            Log.w(TAG, "Timeout (${MODEL_TIMEOUT_MS}ms) on model $model (key $keyMasked)")
            CallResult.Timeout
        } catch (e: Exception) {
            Log.e(TAG, "Network error on model $model (key $keyMasked)", e)
            CallResult.Failed
        }
    }
}
