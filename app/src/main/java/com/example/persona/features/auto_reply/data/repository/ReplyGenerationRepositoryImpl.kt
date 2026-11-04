package com.example.persona.features.auto_reply.data.repository

import android.content.Context
import android.util.Log
import com.example.persona.BuildConfig
import com.example.persona.core.data.repository.UserPreferencesRepository
import com.example.persona.core.database.entity.ChatHistoryEntity
import com.example.persona.core.database.entity.OrderEntity
import com.example.persona.core.database.entity.OrderStatusCount
import com.example.persona.core.logging.PersonaLogTags
import com.example.persona.core.logging.PersonaLogger
import com.example.persona.core.network.dto.ChatMessage
import com.example.persona.features.auto_reply.data.media.ImagePayloadPreprocessor
import com.example.persona.features.auto_reply.data.providers.MetaModelApiClient
import com.example.persona.features.auto_reply.data.providers.ProviderCircuitBreakerAndKeyManager
import com.example.persona.features.auto_reply.domain.gatekeeper.InboundIntent
import com.example.persona.features.auto_reply.domain.model.ProcessedNotification
import com.example.persona.features.auto_reply.domain.model.RetrievalCandidate
import com.example.persona.features.auto_reply.domain.orchestrator.SystemAlertNotificationHandler
import com.example.persona.features.auto_reply.domain.repository.ReplyGenerationRepository
import com.example.persona.features.settings.domain.model.AccountMode
import com.example.persona.features.settings.domain.repository.ModelCapability
import com.example.persona.features.settings.domain.repository.ModelProviders
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.client.HttpClient
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 100% Meta Model API runtime coordinator for conversational and multimodal response generation.
 * Exclusively dispatches to Meta's Muse Spark 1.3 / 1.2 models with transparent contributor failover.
 */
@Singleton
class ReplyGenerationRepositoryImpl @Inject constructor(
    private val httpClient: HttpClient,
    private val promptContextBuilder: PromptContextBuilder,
    @ApplicationContext private val context: Context,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val metaModelApiClient: MetaModelApiClient,
    private val imagePreprocessor: ImagePayloadPreprocessor = ImagePayloadPreprocessor(),
    circuitBreaker: ProviderCircuitBreakerAndKeyManager? = null
) : ReplyGenerationRepository {

    val circuitBreaker: ProviderCircuitBreakerAndKeyManager = circuitBreaker
        ?: ProviderCircuitBreakerAndKeyManager(context, SystemAlertNotificationHandler(context))

    @Volatile
    private var lastTelemetry: com.example.persona.features.auto_reply.domain.model.ExecutionTelemetry? = null

    override fun getLastExecutionTelemetry(): com.example.persona.features.auto_reply.domain.model.ExecutionTelemetry? = lastTelemetry

    /** Primary Meta key resolved from secure storage, falling back to BuildConfig. */
    private val metaApiKey: String
        get() = userPreferencesRepository.getApiKeys(ModelProviders.META).firstOrNull()?.trim()?.takeIf { it.isNotBlank() }
            ?: runCatching { BuildConfig.META_API_KEY }.getOrNull()?.trim() ?: ""

    companion object {
        private const val TAG = PersonaLogTags.LLM

        const val MODEL_TIMEOUT_MS = 10_000L
        const val VISION_MODEL_TIMEOUT_MS = 25_000L
        private const val ABSOLUTE_FAILOVER_CEILING = 8000

        const val HITL_FALLBACK_JSON =
            """{"action":"HITL","refined_search_keyword":null,"order_action":null,"final_reply_text":"${PromptContextBuilder.CANONICAL_HITL_MESSAGE}","extracted_order":null}"""

        val CANONICAL_HITL_MESSAGE = PromptContextBuilder.CANONICAL_HITL_MESSAGE

        val VISION_META_MODELS = MetaModelApiClient.META_VISION_MODELS
        val SYSTEM_PROMPT_FIRST_PASS = PromptContextBuilder.SYSTEM_PROMPT_FIRST_PASS
        val SYSTEM_PROMPT_FINAL_PASS = PromptContextBuilder.SYSTEM_PROMPT_FINAL_PASS

        internal fun formatStoreKnowledge(
            candidates: List<RetrievalCandidate>,
            budgetTokens: Int = 2500
        ): String = PromptContextBuilder.formatStoreKnowledge(candidates, budgetTokens)

        internal fun formatCustomerOrderState(
            activeOrders: List<OrderEntity>,
            lastOrder: OrderEntity? = null,
            statusCounts: List<OrderStatusCount> = emptyList()
        ): String =
            PromptContextBuilder.formatCustomerOrderState(activeOrders, lastOrder, statusCounts)

        internal fun buildSystemPromptWithContext(
            staticSystemPrompt: String,
            candidates: List<RetrievalCandidate>,
            activeOrders: List<OrderEntity>,
            storeTopics: List<String> = emptyList(),
            storeWebsite: String? = null,
            lastOrder: OrderEntity? = null,
            statusCounts: List<OrderStatusCount> = emptyList()
        ): String = PromptContextBuilder.buildSystemPromptWithContext(
            staticSystemPrompt = staticSystemPrompt,
            candidates = candidates,
            activeOrders = activeOrders,
            storeTopics = storeTopics,
            storeWebsite = storeWebsite,
            lastOrder = lastOrder,
            statusCounts = statusCounts
        )

        internal fun formatHistoricalAssistantTurn(
            rawText: String,
            customHoldingMessage: String? = null,
            customMediaFallbackMessage: String? = null
        ): String =
            PromptContextBuilder.formatHistoricalAssistantTurn(rawText, customHoldingMessage, customMediaFallbackMessage)

        internal fun isMediaRefusalMessage(text: String, customFallbackMessage: String? = null): Boolean =
            PromptContextBuilder.isMediaRefusalMessage(text, customFallbackMessage)

        fun buildVisionExtractionSystemPrompt(
            storeName: String = UserPreferencesRepository.DEFAULT_STORE_NAME,
            storeTopics: List<String> = emptyList()
        ): String = PromptContextBuilder.buildVisionExtractionSystemPrompt(storeName, storeTopics)

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
            nowMs: Long = System.currentTimeMillis(),
            customMediaFallbackMessage: String? = null
        ): List<ChatHistoryEntity> = PromptContextBuilder.filterAndPairChatHistory(chatHistory, nowMs, customMediaFallbackMessage)

        internal fun buildChatMessages(
            systemPrompt: String,
            chatHistory: List<ChatHistoryEntity>,
            currentInboundText: String,
            nowMs: Long = System.currentTimeMillis(),
            imageUrl: String? = null,
            customMediaFallbackMessage: String? = null
        ): List<ChatMessage> = PromptContextBuilder.buildChatMessages(systemPrompt, chatHistory, currentInboundText, nowMs, imageUrl, customMediaFallbackMessage)

        internal fun formatRelativeTime(
            timestampMs: Long,
            nowMs: Long = System.currentTimeMillis()
        ): String = PromptContextBuilder.formatRelativeTime(timestampMs, nowMs)

        internal fun estimateTokens(text: String): Int = PromptContextBuilder.estimateTokens(text)

        internal fun maskKey(key: String): String = ProviderCircuitBreakerAndKeyManager.maskKey(key)
    }

    sealed interface CallResult {
        data class Success(
            val content: String,
            val promptTokens: Int = 0,
            val completionTokens: Int = 0,
            val actualModel: String? = null
        ) : CallResult
        data object RateLimited : CallResult
        data class AuthOrQuotaError(val code: Int) : CallResult
        data class ServerError(val code: Int) : CallResult
        data object Timeout : CallResult
        data object Failed : CallResult
    }

    internal fun compressAndScaleImage(
        filePath: String,
        maxDimension: Int = 1024,
        quality: Int = 80
    ): ByteArray? = imagePreprocessor.compressAndScaleImage(filePath, maxDimension, quality)

    internal fun resolveImageUri(filePathOrUri: String?): String? =
        imagePreprocessor.resolveImageUri(filePathOrUri)

    private fun getEffectiveKeys(providerId: String, explicitOverrides: List<String>): List<String> {
        val activeFromManager = circuitBreaker.getActiveKeys(providerId)
        val combined = (explicitOverrides + activeFromManager).map { it.trim() }.filter { it.isNotBlank() }.distinct()
        return combined
    }

    override suspend fun generateReply(
        notification: ProcessedNotification,
        candidates: List<RetrievalCandidate>,
        isOngoingConversation: Boolean,
        chatHistory: List<ChatHistoryEntity>,
        activeOrders: List<OrderEntity>,
        personalChunks: List<ChatHistoryEntity>,
        maxTokens: Int,
        lastOrder: OrderEntity?,
        orderStatusCounts: List<OrderStatusCount>,
        inboundIntent: InboundIntent
    ): String? {
        val imageUri = resolveImageUri(notification.mediaFilePath)
        val isMultimodal = imageUri != null
        val storeTopics = userPreferencesRepository.storeKnowledgeTopicsFlow.first()

        if (isMultimodal) {
            val storeName = userPreferencesRepository.getStoreNameSync()
            val visionSystemPrompt = PromptContextBuilder.buildVisionExtractionSystemPrompt(
                storeName = storeName,
                storeTopics = storeTopics
            )
            val cleanText = notification.originalText.replace(Regex("[📷📸]"), "").trim().lowercase()
            val isBareImageNotification = cleanText.isEmpty() ||
                cleanText == "photo" ||
                cleanText == "صورة" ||
                cleanText.contains("sent a photo")
            val inboundText = if (isBareImageNotification) {
                "العميل أرسل هذه الصورة لمنتج. تفحص الصورة وتعرف على مواصفات ونوع المنتج المعروض فيها واستخرج وصف دقيق للبحث في الكتالوج."
            } else {
                notification.originalText
            }

            val messages = listOf(
                ChatMessage(role = "system", content = visionSystemPrompt),
                ChatMessage(role = "user", content = inboundText, imageUrl = imageUri)
            )
            return executeFailoverChain(messages, 500)
        }

        val storeWebsite = userPreferencesRepository.storeWebsiteFlow.first()
        val tone = userPreferencesRepository.stylePreferences.first().personaTone
        val accountMode = runCatching { userPreferencesRepository.getAccountModeSync() }.getOrDefault(AccountMode.BUSINESS)
        val botName = if (accountMode == AccountMode.PERSONAL) {
            userPreferencesRepository.getPersonalAssistantNameSync()
        } else {
            userPreferencesRepository.getBotNameSync()
        }
        val storeName = if (accountMode == AccountMode.PERSONAL) {
            userPreferencesRepository.getPersonalOwnerNameSync()
        } else {
            userPreferencesRepository.getStoreNameSync()
        }

        // Unified Single-Stage Reasoning: Directly provide retrieved knowledge chunks and context to Muse Spark 1.3
        val systemPromptWithContext = if (accountMode == AccountMode.PERSONAL) {
            val baseSystemPrompt = PromptContextBuilder.buildPersonalFirstPassSystemPrompt(
                assistantName = botName,
                ownerName = storeName,
                tone = tone,
                isMultimodal = false
            )
            PromptContextBuilder.buildPersonalSystemPromptWithContext(
                staticSystemPrompt = baseSystemPrompt,
                candidates = candidates
            )
        } else {
            val baseSystemPrompt = PromptContextBuilder.buildFirstPassSystemPrompt(
                botName = botName,
                storeName = storeName,
                tone = tone,
                isMultimodal = false
            )
            PromptContextBuilder.buildSystemPromptWithContext(
                staticSystemPrompt = baseSystemPrompt,
                candidates = candidates,
                activeOrders = activeOrders,
                storeTopics = storeTopics,
                storeWebsite = storeWebsite,
                lastOrder = lastOrder,
                statusCounts = orderStatusCounts
            )
        }

        val customMediaFallback = runCatching { userPreferencesRepository.getMediaFallbackMessageSync() }.getOrNull()
        val messages = PromptContextBuilder.buildChatMessages(
            systemPrompt = systemPromptWithContext,
            chatHistory = chatHistory,
            currentInboundText = notification.originalText,
            imageUrl = null,
            customMediaFallbackMessage = customMediaFallback
        )
        val effectiveMaxTokens = maxOf(maxTokens, 1500)
        return executeFailoverChain(messages, effectiveMaxTokens)
    }

    override suspend fun generateFinalReply(
        queryText: String,
        chunks: List<RetrievalCandidate>,
        isOngoingConversation: Boolean,
        chatHistory: List<ChatHistoryEntity>,
        activeOrders: List<OrderEntity>,
        personalChunks: List<ChatHistoryEntity>,
        maxTokens: Int,
        imageUrl: String?,
        lastOrder: OrderEntity?,
        orderStatusCounts: List<OrderStatusCount>
    ): String? {
        val imageUri = resolveImageUri(imageUrl)
        val isMultimodal = imageUri != null
        val storeTopics = userPreferencesRepository.storeKnowledgeTopicsFlow.first()
        val storeWebsite = userPreferencesRepository.storeWebsiteFlow.first()
        val tone = userPreferencesRepository.stylePreferences.first().personaTone
        val accountMode = runCatching { userPreferencesRepository.getAccountModeSync() }.getOrDefault(AccountMode.BUSINESS)
        val systemPromptWithContext = if (accountMode == AccountMode.PERSONAL) {
            val baseSystemPrompt = PromptContextBuilder.buildPersonalFirstPassSystemPrompt(
                assistantName = userPreferencesRepository.getPersonalAssistantNameSync(),
                ownerName = userPreferencesRepository.getPersonalOwnerNameSync(),
                tone = tone,
                isMultimodal = isMultimodal
            )
            PromptContextBuilder.buildPersonalSystemPromptWithContext(
                staticSystemPrompt = baseSystemPrompt,
                candidates = chunks
            )
        } else {
            val baseSystemPrompt = PromptContextBuilder.buildFinalPassSystemPrompt(
                botName = userPreferencesRepository.getBotNameSync(),
                storeName = userPreferencesRepository.getStoreNameSync(),
                tone = tone,
                isMultimodal = isMultimodal
            )
            PromptContextBuilder.buildSystemPromptWithContext(
                staticSystemPrompt = baseSystemPrompt,
                candidates = chunks,
                activeOrders = activeOrders,
                storeTopics = storeTopics,
                storeWebsite = storeWebsite,
                lastOrder = lastOrder,
                statusCounts = orderStatusCounts
            )
        }
        val customMediaFallback = runCatching { userPreferencesRepository.getMediaFallbackMessageSync() }.getOrNull()
        val messages = PromptContextBuilder.buildChatMessages(
            systemPrompt = systemPromptWithContext,
            chatHistory = chatHistory,
            currentInboundText = queryText,
            imageUrl = imageUri,
            customMediaFallbackMessage = customMediaFallback
        )
        val effectiveMaxTokens = if (isMultimodal) 1500 else maxOf(maxTokens, 1500)
        return executeFailoverChain(messages, effectiveMaxTokens)
    }

    internal suspend fun executeFailoverChain(messages: List<ChatMessage>, maxTokens: Int): String? {
        val hasImage = messages.any { !it.imageUrl.isNullOrBlank() }
        val effectiveTokens = if (hasImage) maxTokens else maxOf(maxTokens, 1500)
        val targetMaxTokens = minOf(effectiveTokens, ABSOLUTE_FAILOVER_CEILING)

        val metaKeys = getEffectiveKeys(
            ProviderCircuitBreakerAndKeyManager.PROVIDER_META,
            listOfNotNull(metaApiKey.takeIf { it.isNotBlank() })
        ).ifEmpty {
            val configKeys = userPreferencesRepository.getApiKeys(ModelProviders.META)
                .map { it.trim() }.filter { it.isNotBlank() }
            if (configKeys.isNotEmpty()) configKeys
            else listOfNotNull(runCatching { BuildConfig.META_API_KEY }.getOrNull()?.trim()?.takeIf { it.isNotBlank() })
        }

        if (metaKeys.isEmpty()) {
            PersonaLogger.w(TAG, "No Meta API key configured across secure storage and BuildConfig")
            return HITL_FALLBACK_JSON
        }

        val modelsToTry = listOf(
            MetaModelApiClient.MODEL_MUSE_SPARK_1_3_CONTRIBUTOR,
            MetaModelApiClient.MODEL_MUSE_SPARK_1_3,
            MetaModelApiClient.MODEL_MUSE_SPARK_1_2_CONTRIBUTOR,
            MetaModelApiClient.MODEL_MUSE_SPARK_1_2
        )

        val startTime = System.currentTimeMillis()
        for (key in metaKeys) {
            for (model in modelsToTry) {
                val modelMaxTokens = if (model.contains("muse-spark") || model.contains("reasoning")) maxOf(targetMaxTokens, 2000) else maxOf(targetMaxTokens, 1500)
                PersonaLogger.i(TAG, "Attempting Meta inference with model $model")
                when (val result = metaModelApiClient.executeSingleCall(model, messages, modelMaxTokens, key)) {
                    is CallResult.Success -> {
                        circuitBreaker.recordSuccess(ProviderCircuitBreakerAndKeyManager.PROVIDER_META)
                        val durationMs = System.currentTimeMillis() - startTime
                        val logMsg = "✨ Meta model $model succeeded in ${durationMs}ms (actual=${result.actualModel})"
                        PersonaLogger.i(TAG, logMsg)
                        Log.i(TAG, logMsg)
                        recordSuccessTelemetry("Meta", model, durationMs, result, messages)
                        return result.content
                    }
                    is CallResult.AuthOrQuotaError -> {
                        PersonaLogger.w(TAG, "Meta auth/quota error ${result.code} for key ${maskKey(key)}")
                        circuitBreaker.evictKey(ProviderCircuitBreakerAndKeyManager.PROVIDER_META, key, "Auth error: ${result.code}")
                        break
                    }
                    is CallResult.Timeout -> {
                        PersonaLogger.w(TAG, "Timeout on Meta model $model. Cascading...")
                        circuitBreaker.recordTimeout(ProviderCircuitBreakerAndKeyManager.PROVIDER_META)
                    }
                    is CallResult.ServerError -> {
                        PersonaLogger.w(TAG, "Server error (${result.code}) on Meta model $model. Cascading...")
                        circuitBreaker.recordFailure(ProviderCircuitBreakerAndKeyManager.PROVIDER_META, isServerError = true)
                    }
                    is CallResult.RateLimited -> {
                        PersonaLogger.w(TAG, "Rate limited (429) on Meta model $model. Cascading...")
                    }
                    else -> {
                        PersonaLogger.w(TAG, "Meta model $model call failed ($result). Cascading...")
                    }
                }
            }
        }

        PersonaLogger.d(TAG, "All Meta model cascades exhausted — degrading to HITL fallback")
        return HITL_FALLBACK_JSON
    }

    private fun recordSuccessTelemetry(
        providerLabel: String,
        fallbackModel: String,
        durationMs: Long,
        result: CallResult.Success,
        messages: List<ChatMessage>
    ) {
        val actualModel = result.actualModel?.takeIf { it.isNotBlank() } ?: fallbackModel
        val pTokens = if (result.promptTokens > 0) result.promptTokens else messages.sumOf { estimateTokens(it.content) }
        val cTokens = if (result.completionTokens > 0) result.completionTokens else estimateTokens(result.content)
        lastTelemetry = com.example.persona.features.auto_reply.domain.model.ExecutionTelemetry(
            providerModel = "$providerLabel / $actualModel",
            gatekeeperTier = "MODEL",
            latencyMs = durationMs,
            promptTokens = pTokens,
            completionTokens = cTokens
        )
    }
}
