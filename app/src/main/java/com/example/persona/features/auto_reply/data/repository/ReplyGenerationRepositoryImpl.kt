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
import com.example.persona.core.network.dto.OpenRouterRequest
import com.example.persona.core.network.dto.OpenRouterResponse
import com.example.persona.core.network.dto.ReasoningConfig
import com.example.persona.features.auto_reply.domain.model.ProcessedNotification
import com.example.persona.features.auto_reply.domain.model.RetrievalCandidate
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
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cloud LLM generation via OpenRouter with multi-model serial failover.
 * Delegates prompt assembly and token budgeting to [PromptContextBuilder].
 */
@Singleton
class ReplyGenerationRepositoryImpl @Inject constructor(
    private val httpClient: HttpClient,
    private val promptContextBuilder: PromptContextBuilder,
    @ApplicationContext private val context: Context,
    private val userPreferencesRepository: UserPreferencesRepository
) : ReplyGenerationRepository {

    /** Test seam: overrides the OpenRouter auth keys and supplies dependencies explicitly. */
    internal constructor(
        httpClient: HttpClient,
        context: Context,
        primaryApiKey: String,
        fallbackApiKey: String,
        promptContextBuilder: PromptContextBuilder = PromptContextBuilder(),
        userPreferencesRepository: UserPreferencesRepository
    ) : this(httpClient, promptContextBuilder, context, userPreferencesRepository) {
        this.primaryApiKeyOverride = primaryApiKey
        this.fallbackApiKeyOverride = fallbackApiKey
    }

    private var primaryApiKeyOverride: String? = null
    private var fallbackApiKeyOverride: String? = null

    internal val primaryApiKey: String
        get() = primaryApiKeyOverride ?: BuildConfig.OPENROUTER_API_KEY

    internal val fallbackApiKey: String
        get() = fallbackApiKeyOverride ?: BuildConfig.OPENROUTER_API_KEY_V2

    companion object {
        private const val TAG = PersonaLogTags.LLM

        private const val MODEL_TIMEOUT_MS = 25000L
        private const val ABSOLUTE_FAILOVER_CEILING = 8000

        const val HITL_FALLBACK_JSON =
            """{"action":"HITL","refined_search_keyword":null,"order_action":null,"final_reply_text":"${PromptContextBuilder.CANONICAL_HITL_MESSAGE}","extracted_order":null}"""

        val CANONICAL_HITL_MESSAGE = PromptContextBuilder.CANONICAL_HITL_MESSAGE

        private val FAILOVER_MODELS = listOf(
            "nvidia/nemotron-3.5-super-vl:free",
            "google/gemma-4-31b-it:free",
            "cohere/north-mini-code:free",
            "liquid/lfm-2.5-2.6b:free",
            "openrouter/free",
            "dots-studio/dots-3-note-preview:free",
            "nvidia/nemotron-3-ultra-550b-a55b:free",
            "nvidia/nemotron-3.5-lightning:free"
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
            nowMs: Long = System.currentTimeMillis()
        ): List<ChatMessage> = PromptContextBuilder.buildOpenRouterMessages(systemPrompt, chatHistory, currentInboundText, nowMs)

        internal fun estimateTokens(text: String): Int = PromptContextBuilder.estimateTokens(text)
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
        val baseSystemPrompt = PromptContextBuilder.buildFirstPassSystemPrompt(
            botName = userPreferencesRepository.getBotNameSync(),
            storeName = userPreferencesRepository.getStoreNameSync()
        )
        val systemPromptWithContext = PromptContextBuilder.buildSystemPromptWithContext(
            staticSystemPrompt = baseSystemPrompt,
            candidates = candidates,
            activeOrders = activeOrders,
            storeTopics = storeTopics
        )
        val messages = PromptContextBuilder.buildOpenRouterMessages(
            systemPrompt = systemPromptWithContext,
            chatHistory = chatHistory,
            currentInboundText = notification.originalText
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
        maxTokens: Int
    ): String? {
        val storeTopics = userPreferencesRepository.storeKnowledgeTopicsFlow.first()
        val baseSystemPrompt = PromptContextBuilder.buildFinalPassSystemPrompt(
            botName = userPreferencesRepository.getBotNameSync(),
            storeName = userPreferencesRepository.getStoreNameSync()
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
            currentInboundText = queryText
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
        var consecutive429Count = 0

        for ((index, model) in FAILOVER_MODELS.withIndex()) {
            Log.d(TAG, "Attempting model ${index + 1}/${FAILOVER_MODELS.size}: $model with max_tokens=$targetMaxTokens")
            PersonaLogger.d(TAG, "Trying model: $model")

            when (val result = callOpenRouter(model, messages, targetMaxTokens)) {
                is CallResult.Success -> {
                    Log.i(TAG, "Success on model $model")
                    PersonaLogger.d(TAG, "LLM response received from $model")
                    return result.content
                }
                is CallResult.RateLimited -> {
                    consecutive429Count++

                    if (consecutive429Count >= 3) {
                        Log.w(TAG, "3 consecutive models returned 429 (account quota / RPM limit). Breaking early to HITL fallback.")
                        PersonaLogger.d(TAG, "Account quota exhausted (consecutive 429s) — early HITL fallback")
                        return HITL_FALLBACK_JSON
                    }

                    delay(1200L)
                }
                else -> {
                    consecutive429Count = 0
                }
            }
        }

        Log.w(TAG, "All ${FAILOVER_MODELS.size} models failed. Returning HITL fallback JSON.")
        PersonaLogger.d(TAG, "All models failed — HITL fallback")
        return HITL_FALLBACK_JSON
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
            val primarySuffix = if (effectivePrimary.length >= 6) effectivePrimary.takeLast(6) else "short"
            val fallbackSuffix = if (fallback.length >= 6) fallback.takeLast(6) else "short"
            Log.w(
                TAG,
                "Primary OpenRouter key (...$primarySuffix) failed on model $model with key/quota error $primaryResult. " +
                    "Falling back to OPENROUTER_API_KEY_V2 (...$fallbackSuffix)."
            )
            PersonaLogger.d(
                TAG,
                "Primary key failed on $model ($primaryResult) -> fallback to OPENROUTER_API_KEY_V2"
            )

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

        val keySuffix = if (apiKey.length >= 6) apiKey.takeLast(6) else "short"

        return try {
            withTimeout(MODEL_TIMEOUT_MS) {
                var response = httpClient.post(BuildConfig.OPENROUTER_BASE_URL) {
                    contentType(ContentType.Application.Json)
                    bearerAuth(apiKey)
                    setBody(
                        OpenRouterRequest(
                            model = model,
                            messages = messages,
                            maxTokens = maxTokens,
                            reasoning = ReasoningConfig(effort = "none", exclude = true)
                        )
                    )
                }

                if (response.status.value == 400) {
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
                        Log.w(TAG, "Rate limited (429) on model $model (key ...$keySuffix)")
                        CallResult.RateLimited
                    }
                    response.status.value in listOf(401, 402, 403) -> {
                        Log.w(TAG, "Auth/quota error (${response.status.value}) on model $model (key ...$keySuffix)")
                        CallResult.AuthOrQuotaError(response.status.value)
                    }
                    response.status.value in 500..599 -> {
                        Log.w(TAG, "Server error (${response.status.value}) on model $model (key ...$keySuffix)")
                        CallResult.ServerError(response.status.value)
                    }
                    response.status.isSuccess() -> {
                        val body = response.body<OpenRouterResponse>()
                        val content = body.choices?.firstOrNull()?.message?.content
                        if (content.isNullOrBlank()) {
                            Log.w(TAG, "Empty content in successful response from $model (key ...$keySuffix)")
                            CallResult.Failed
                        } else {
                            CallResult.Success(content)
                        }
                    }
                    else -> {
                        Log.w(TAG, "HTTP ${response.status.value} error from $model (key ...$keySuffix)")
                        CallResult.Failed
                    }
                }
            }
        } catch (e: TimeoutCancellationException) {
            Log.w(TAG, "Timeout (${MODEL_TIMEOUT_MS}ms) on model $model (key ...$keySuffix)")
            CallResult.Timeout
        } catch (e: Exception) {
            Log.e(TAG, "Network error on model $model (key ...$keySuffix)", e)
            CallResult.Failed
        }
    }
}
