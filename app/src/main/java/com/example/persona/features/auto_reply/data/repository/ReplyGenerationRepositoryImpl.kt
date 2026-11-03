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
import com.example.persona.features.auto_reply.data.providers.GeminiClient
import com.example.persona.features.auto_reply.data.providers.GroqClient
import com.example.persona.features.auto_reply.data.providers.MetaModelApiClient
import com.example.persona.features.auto_reply.data.providers.NaraRouterClient
import com.example.persona.features.auto_reply.data.providers.NvidiaNimClient
import com.example.persona.features.auto_reply.data.providers.OpenRouterClient
import com.example.persona.features.auto_reply.data.providers.ProviderCircuitBreakerAndKeyManager
import com.example.persona.features.auto_reply.data.providers.QwenCloudClient
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
 * Lean coordinator for cloud LLM response generation with active circuit breaker,
 * unlimited keys pool, dynamic model management, and dynamic provider priority reordering.
 */
@Singleton
class ReplyGenerationRepositoryImpl @Inject constructor(
    private val httpClient: HttpClient,
    private val promptContextBuilder: PromptContextBuilder,
    @ApplicationContext private val context: Context,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val qwenClient: QwenCloudClient,
    private val nvidiaClient: NvidiaNimClient,
    private val groqClient: GroqClient,
    private val geminiClient: GeminiClient,
    private val openRouterClient: OpenRouterClient,
    private val naraRouterClient: NaraRouterClient,
    private val metaModelApiClient: MetaModelApiClient? = null,
    private val imagePreprocessor: ImagePayloadPreprocessor,
    circuitBreaker: ProviderCircuitBreakerAndKeyManager? = null
) : ReplyGenerationRepository {

    val circuitBreaker: ProviderCircuitBreakerAndKeyManager = circuitBreaker
        ?: ProviderCircuitBreakerAndKeyManager(context, SystemAlertNotificationHandler(context))

    @Volatile
    private var lastTelemetry: com.example.persona.features.auto_reply.domain.model.ExecutionTelemetry? = null

    override fun getLastExecutionTelemetry(): com.example.persona.features.auto_reply.domain.model.ExecutionTelemetry? = lastTelemetry

    /** Test seam: overrides API keys and supplies dependencies explicitly. */
    internal constructor(
        httpClient: HttpClient,
        context: Context,
        primaryApiKey: String,
        fallbackApiKey: String,
        promptContextBuilder: PromptContextBuilder = PromptContextBuilder(),
        userPreferencesRepository: UserPreferencesRepository,
        groqPrimaryApiKey: String? = "",
        groqFallbackApiKey: String? = "",
        nvidiaApiKey: String? = "",
        qwenApiKey: String? = "",
        geminiApiKey: String? = "",
        geminiApiKey2: String? = "",
        naraApiKey: String? = "",
        metaApiKey: String? = "",
        metaModelApiClient: MetaModelApiClient? = null
    ) : this(
        httpClient = httpClient,
        promptContextBuilder = promptContextBuilder,
        context = context,
        userPreferencesRepository = userPreferencesRepository,
        qwenClient = QwenCloudClient(httpClient),
        nvidiaClient = NvidiaNimClient(httpClient),
        groqClient = GroqClient(httpClient),
        geminiClient = GeminiClient(httpClient),
        openRouterClient = OpenRouterClient(httpClient),
        naraRouterClient = NaraRouterClient(httpClient),
        metaModelApiClient = metaModelApiClient,
        imagePreprocessor = ImagePayloadPreprocessor(),
        circuitBreaker = ProviderCircuitBreakerAndKeyManager(context, SystemAlertNotificationHandler(context))
    ) {
        this.primaryApiKeyOverride = primaryApiKey
        this.fallbackApiKeyOverride = fallbackApiKey
        this.groqPrimaryApiKeyOverride = groqPrimaryApiKey
        this.groqFallbackApiKeyOverride = groqFallbackApiKey
        this.nvidiaApiKeyOverride = nvidiaApiKey
        this.qwenApiKeyOverride = qwenApiKey
        this.geminiApiKeyOverride = geminiApiKey
        this.geminiApiKey2Override = geminiApiKey2
        this.naraApiKeyOverride = naraApiKey
        this.metaApiKeyOverride = metaApiKey
    }

    /** Secondary convenience constructor preserving standard 4-arg instantiations. */
    constructor(
        httpClient: HttpClient,
        promptContextBuilder: PromptContextBuilder,
        context: Context,
        userPreferencesRepository: UserPreferencesRepository
    ) : this(
        httpClient = httpClient,
        promptContextBuilder = promptContextBuilder,
        context = context,
        userPreferencesRepository = userPreferencesRepository,
        qwenClient = QwenCloudClient(httpClient),
        nvidiaClient = NvidiaNimClient(httpClient),
        groqClient = GroqClient(httpClient),
        geminiClient = GeminiClient(httpClient),
        openRouterClient = OpenRouterClient(httpClient),
        naraRouterClient = NaraRouterClient(httpClient),
        metaModelApiClient = null,
        imagePreprocessor = ImagePayloadPreprocessor(),
        circuitBreaker = ProviderCircuitBreakerAndKeyManager(context, SystemAlertNotificationHandler(context))
    )

    private var primaryApiKeyOverride: String? = null
    private var fallbackApiKeyOverride: String? = null
    private var groqPrimaryApiKeyOverride: String? = null
    private var groqFallbackApiKeyOverride: String? = null
    private var nvidiaApiKeyOverride: String? = null
    private var qwenApiKeyOverride: String? = null
    private var geminiApiKeyOverride: String? = null
    private var geminiApiKey2Override: String? = null
    private var naraApiKeyOverride: String? = null
    private var metaApiKeyOverride: String? = null

    internal val metaApiKey: String
        get() = metaApiKeyOverride?.trim()
            ?: userPreferencesRepository.getApiKeys(ModelProviders.META).firstOrNull()?.trim()?.takeIf { it.isNotBlank() }
            ?: runCatching { BuildConfig.META_API_KEY }.getOrNull()?.trim() ?: ""

    internal val primaryApiKey: String
        get() = primaryApiKeyOverride?.trim()
            ?: userPreferencesRepository.getApiKeys(ModelProviders.OPENROUTER).firstOrNull()?.trim()?.takeIf { it.isNotBlank() }
            ?: BuildConfig.OPENROUTER_API_KEY.trim()

    internal val fallbackApiKey: String
        get() = fallbackApiKeyOverride?.trim()
            ?: userPreferencesRepository.getApiKeys(ModelProviders.OPENROUTER).drop(1).firstOrNull()?.trim()?.takeIf { it.isNotBlank() }
            ?: BuildConfig.OPENROUTER_API_KEY_V2.trim()

    internal val groqPrimaryApiKey: String
        get() = groqPrimaryApiKeyOverride?.trim()
            ?: userPreferencesRepository.getApiKeys(ModelProviders.GROQ).firstOrNull()?.trim()?.takeIf { it.isNotBlank() }
            ?: BuildConfig.GROQ_API_KEY.trim()

    internal val groqFallbackApiKey: String
        get() = groqFallbackApiKeyOverride?.trim()
            ?: userPreferencesRepository.getApiKeys(ModelProviders.GROQ).drop(1).firstOrNull()?.trim()?.takeIf { it.isNotBlank() }
            ?: BuildConfig.GROQ_API_KEY_V2.trim()

    internal val nvidiaApiKey: String
        get() = nvidiaApiKeyOverride?.trim()
            ?: userPreferencesRepository.getApiKeys(ModelProviders.NVIDIA).firstOrNull()?.trim()?.takeIf { it.isNotBlank() }
            ?: BuildConfig.NVIDIA_API_KEY.trim()

    internal val qwenApiKey: String
        get() = qwenApiKeyOverride?.trim()
            ?: userPreferencesRepository.getApiKeys(ModelProviders.QWEN).firstOrNull()?.trim()?.takeIf { it.isNotBlank() }
            ?: BuildConfig.QWEN_API_KEY.trim()

    internal val geminiApiKey: String
        get() = geminiApiKeyOverride?.trim()
            ?: userPreferencesRepository.getApiKeys(ModelProviders.GEMINI).firstOrNull()?.trim()?.takeIf { it.isNotBlank() }
            ?: BuildConfig.GEMINI_API_KEY.trim()

    internal val geminiApiKey2: String
        get() = geminiApiKey2Override?.trim()
            ?: userPreferencesRepository.getApiKeys(ModelProviders.GEMINI).drop(1).firstOrNull()?.trim()?.takeIf { it.isNotBlank() }
            ?: BuildConfig.GEMINI_API_KEY_2.trim()

    internal val naraApiKey: String
        get() = naraApiKeyOverride?.trim()
            ?: userPreferencesRepository.getApiKeys(ModelProviders.NARA).firstOrNull()?.trim()?.takeIf { it.isNotBlank() }
            ?: (runCatching {
                BuildConfig::class.java.getField("NARA_API_KEY").get(null) as? String
            }.getOrNull()?.trim() ?: "")

    override fun hasConfiguredKeys(provider: String): Boolean {
        val norm = ModelProviders.normalize(provider)
        if (userPreferencesRepository.hasConfiguredKeys(norm)) return true
        return when (norm) {
            ModelProviders.QWEN -> qwenApiKey.isNotBlank()
            ModelProviders.NVIDIA -> nvidiaApiKey.isNotBlank()
            ModelProviders.GROQ -> groqPrimaryApiKey.isNotBlank() || groqFallbackApiKey.isNotBlank()
            ModelProviders.GEMINI -> geminiApiKey.isNotBlank() || geminiApiKey2.isNotBlank()
            ModelProviders.OPENROUTER -> primaryApiKey.isNotBlank() || fallbackApiKey.isNotBlank()
            ModelProviders.NARA -> naraApiKey.isNotBlank()
            else -> false
        }
    }

    companion object {
        private const val TAG = PersonaLogTags.LLM

        const val MODEL_TIMEOUT_MS = 10_000L
        const val VISION_MODEL_TIMEOUT_MS = 25_000L
        const val QWEN_TTFT_TIMEOUT_MS = 5_000L
        const val QWEN_STREAM_TIMEOUT_MS = 25_000L
        private const val ABSOLUTE_FAILOVER_CEILING = 8000

        const val HITL_FALLBACK_JSON =
            """{"action":"HITL","refined_search_keyword":null,"order_action":null,"final_reply_text":"${PromptContextBuilder.CANONICAL_HITL_MESSAGE}","extracted_order":null}"""

        val CANONICAL_HITL_MESSAGE = PromptContextBuilder.CANONICAL_HITL_MESSAGE

        val VISION_META_MODELS = MetaModelApiClient.META_VISION_MODELS
        val VISION_QWEN_MODELS = QwenCloudClient.VISION_QWEN_MODELS
        val VISION_GROQ_MODELS = GroqClient.VISION_GROQ_MODELS
        val VISION_GEMINI_MODELS = GeminiClient.VISION_GEMINI_MODELS
        val VISION_NVIDIA_MODELS = NvidiaNimClient.VISION_NVIDIA_MODELS
        val VISION_OPENROUTER_MODELS = OpenRouterClient.VISION_OPENROUTER_MODELS

        /**
         * MANDATORY ARCHITECTURAL OVERRIDE:
         * Strictly FORBIDDEN to reduce experimental Qwen models!
         * All 10 default Qwen models MUST remain.
         */
        val QWEN_TEXT_MODELS = QwenCloudClient.QWEN_TEXT_MODELS
        val GEMINI_TEXT_MODELS = GeminiClient.GEMINI_TEXT_MODELS
        val NVIDIA_TEXT_MODELS = NvidiaNimClient.NVIDIA_TEXT_MODELS
        val GROQ_MODELS = GroqClient.GROQ_MODELS
        val NARA_TEXT_MODELS = NaraRouterClient.NARA_TEXT_MODELS
        val OPENROUTER_FAILOVER_MODELS = OpenRouterClient.OPENROUTER_FAILOVER_MODELS
        val FAILOVER_MODELS = OPENROUTER_FAILOVER_MODELS
        val REASONING_MODELS = OpenRouterClient.REASONING_MODELS

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

        internal fun buildOpenRouterMessages(
            systemPrompt: String,
            chatHistory: List<ChatHistoryEntity>,
            currentInboundText: String,
            nowMs: Long = System.currentTimeMillis(),
            imageUrl: String? = null,
            customMediaFallbackMessage: String? = null
        ): List<ChatMessage> = PromptContextBuilder.buildOpenRouterMessages(systemPrompt, chatHistory, currentInboundText, nowMs, imageUrl, customMediaFallbackMessage)

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

    /**
     * Returns the user-configured models for [provider] that have [capability] set.
     * Falls back to the hardcoded constant list if the user hasn't stored any configs yet,
     * or if no user-configured models carry the requested capability.
     */
    private suspend fun getDynamicModels(
        provider: String,
        capability: ModelCapability,
        hardcodedFallback: List<String>
    ): List<String> {
        val norm = ModelProviders.normalize(provider)
        val configs = runCatching {
            userPreferencesRepository.getProviderModelConfigsSync(norm)
        }.getOrElse { emptyList() }
        val userDefined = configs.filter { capability in it.capabilities }.map { it.modelId }
        return userDefined.ifEmpty { hardcodedFallback }
    }

    internal suspend fun callQwenCloud(
        model: String,
        messages: List<ChatMessage>,
        maxTokens: Int,
        apiKey: String? = null
    ): CallResult {
        val key = apiKey?.trim()?.takeIf { it.isNotBlank() }
            ?: qwenApiKey.ifBlank { userPreferencesRepository.getQwenApiKeySync() }.trim()
        if (key.isBlank()) {
            PersonaLogger.w(TAG, "callQwenCloud aborted: No API key configured")
            return CallResult.AuthOrQuotaError(401)
        }
        return qwenClient.call(model, messages, maxTokens, key)
    }

    internal suspend fun callNvidiaNim(
        model: String,
        messages: List<ChatMessage>,
        maxTokens: Int,
        apiKey: String? = null
    ): CallResult {
        val key = apiKey?.trim()?.takeIf { it.isNotBlank() }
            ?: nvidiaApiKey.ifBlank { userPreferencesRepository.getNvidiaApiKeySync() }.trim()
        if (key.isBlank()) {
            PersonaLogger.w(TAG, "callNvidiaNim aborted: No API key configured")
            return CallResult.AuthOrQuotaError(401)
        }
        return nvidiaClient.call(model, messages, maxTokens, key)
    }

    internal suspend fun callGroq(
        model: String,
        messages: List<ChatMessage>,
        maxTokens: Int,
        apiKey: String? = null
    ): CallResult {
        if (apiKey != null && apiKey.isNotBlank()) {
            return groqClient.executeSingleCall(model, messages, maxTokens, apiKey.trim())
        }
        val primary = groqPrimaryApiKey.trim()
        val fallback = groqFallbackApiKey.trim()
        if (primary.isBlank() && fallback.isBlank()) {
            PersonaLogger.w(TAG, "callGroq aborted: No API key configured")
            return CallResult.AuthOrQuotaError(401)
        }
        return groqClient.call(model, messages, maxTokens, primary, fallback)
    }

    internal suspend fun callGemini(
        model: String,
        messages: List<ChatMessage>,
        maxTokens: Int,
        apiKey: String? = null
    ): CallResult {
        if (apiKey != null && apiKey.isNotBlank()) {
            return geminiClient.executeSingleCall(model, messages, maxTokens, apiKey.trim())
        }
        val primary = geminiApiKey.trim()
        val secondary = geminiApiKey2.trim()
        if (primary.isBlank() && secondary.isBlank()) {
            PersonaLogger.w(TAG, "callGemini aborted: No API key configured")
            return CallResult.AuthOrQuotaError(401)
        }
        return geminiClient.call(model, messages, maxTokens, primary, secondary)
    }

    internal suspend fun callOpenRouter(
        model: String,
        messages: List<ChatMessage>,
        maxTokens: Int,
        apiKey: String? = null
    ): CallResult {
        if (apiKey != null && apiKey.isNotBlank()) {
            return openRouterClient.executeSingleCall(model, messages, maxTokens, apiKey.trim())
        }
        val primary = primaryApiKey.trim()
        val fallback = fallbackApiKey.trim()
        if (primary.isBlank() && fallback.isBlank()) {
            PersonaLogger.w(TAG, "callOpenRouter aborted: No API key configured")
            return CallResult.AuthOrQuotaError(401)
        }
        return openRouterClient.call(model, messages, maxTokens, primary, fallback)
    }

    internal suspend fun callNaraRouter(
        model: String,
        messages: List<ChatMessage>,
        maxTokens: Int,
        apiKey: String? = null
    ): CallResult {
        val key = apiKey?.trim()?.takeIf { it.isNotBlank() } ?: naraApiKey.trim()
        if (key.isBlank()) {
            PersonaLogger.w(TAG, "callNaraRouter aborted: No API key configured")
            return CallResult.AuthOrQuotaError(401)
        }
        return naraRouterClient.call(model, messages, maxTokens, key)
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
        val botName = userPreferencesRepository.getBotNameSync()
        val storeName = userPreferencesRepository.getStoreNameSync()
        val accountMode = runCatching { userPreferencesRepository.getAccountModeSync() }.getOrDefault(AccountMode.BUSINESS)

        val systemPromptWithContext = if (accountMode == AccountMode.PERSONAL) {
            val baseSystemPrompt = PromptContextBuilder.buildPersonalFirstPassSystemPrompt(
                assistantName = botName,
                ownerName = storeName,
                tone = tone
            )
            PromptContextBuilder.buildPersonalSystemPromptWithContext(
                staticSystemPrompt = baseSystemPrompt,
                candidates = candidates
            )
        } else {
            when (inboundIntent) {
                InboundIntent.GREETING, InboundIntent.BANTER -> {
                    Log.d(TAG, "⚡ Routing to buildMicroBanterPrompt (intent=$inboundIntent, bypassing RAG)")
                    PromptContextBuilder.buildMicroBanterPrompt(
                        botName = botName,
                        storeName = storeName,
                        tone = tone,
                        storeTopics = storeTopics
                    )
                }
                InboundIntent.ORDER_STATUS -> {
                    Log.d(TAG, "⚡ Routing to buildMicroOrderStatusPrompt (intent=$inboundIntent)")
                    val orderContext = PromptContextBuilder.formatCustomerOrderState(
                        activeOrders = activeOrders,
                        lastOrder = lastOrder,
                        statusCounts = orderStatusCounts
                    )
                    PromptContextBuilder.buildMicroOrderStatusPrompt(
                        botName = botName,
                        storeName = storeName,
                        tone = tone,
                        orderStateContext = orderContext
                    )
                }
                InboundIntent.CATALOG_INQUIRY -> {
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
            }
        }

        val customMediaFallback = runCatching { userPreferencesRepository.getMediaFallbackMessageSync() }.getOrNull()
        val messages = PromptContextBuilder.buildOpenRouterMessages(
            systemPrompt = systemPromptWithContext,
            chatHistory = chatHistory,
            currentInboundText = notification.originalText,
            imageUrl = null,
            customMediaFallbackMessage = customMediaFallback
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
                assistantName = userPreferencesRepository.getBotNameSync(),
                ownerName = userPreferencesRepository.getStoreNameSync(),
                tone = tone
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
        val messages = PromptContextBuilder.buildOpenRouterMessages(
            systemPrompt = systemPromptWithContext,
            chatHistory = chatHistory,
            currentInboundText = queryText,
            imageUrl = imageUri,
            customMediaFallbackMessage = customMediaFallback
        )
        val effectiveMaxTokens = if (isMultimodal) 1500 else maxTokens
        return executeFailoverChain(messages, effectiveMaxTokens)
    }

    internal suspend fun executeFailoverChain(messages: List<ChatMessage>, maxTokens: Int): String? {
        val hasImage = messages.any { !it.imageUrl.isNullOrBlank() }
        val targetMaxTokens = minOf(maxTokens, ABSOLUTE_FAILOVER_CEILING)

        if (hasImage) {
            PersonaLogger.i(TAG, "📸 Multimodal Vision request detected. Dispatching to Vision cascade...")
            Log.i(TAG, "📸 Multimodal Vision request detected. Dispatching to Vision cascade...")

            // ── Vision Tier 0: Meta Model API (muse-spark-1.3-contributor Primary) ───
            if (circuitBreaker.isProviderHealthy(ProviderCircuitBreakerAndKeyManager.PROVIDER_META)) {
                val effectiveVisionMeta = getDynamicModels(
                    ModelProviders.META, ModelCapability.VISION, VISION_META_MODELS
                )
                val metaKeys = getEffectiveKeys(
                    ProviderCircuitBreakerAndKeyManager.PROVIDER_META,
                    listOfNotNull(metaApiKey.takeIf { it.isNotBlank() })
                )
                if (metaKeys.isNotEmpty() && metaModelApiClient != null) {
                    val metaStartTime = System.currentTimeMillis()
                    for (key in metaKeys) {
                        for (model in effectiveVisionMeta) {
                            when (val result = metaModelApiClient.executeSingleCall(model, messages, targetMaxTokens, key)) {
                                is CallResult.Success -> {
                                    circuitBreaker.recordSuccess(ProviderCircuitBreakerAndKeyManager.PROVIDER_META)
                                    val durationMs = System.currentTimeMillis() - metaStartTime
                                    PersonaLogger.i(TAG, "Vision Tier 0 (Meta) succeeded with $model in ${durationMs}ms!")
                                    recordSuccessTelemetry("Meta", model, durationMs, result, messages)
                                    return result.content
                                }
                                is CallResult.AuthOrQuotaError -> {
                                    circuitBreaker.evictKey(ProviderCircuitBreakerAndKeyManager.PROVIDER_META, key, "Auth/quota error: ${result.code}")
                                    break
                                }
                                is CallResult.Timeout -> {
                                    circuitBreaker.recordTimeout(ProviderCircuitBreakerAndKeyManager.PROVIDER_META)
                                    if (!circuitBreaker.isProviderHealthy(ProviderCircuitBreakerAndKeyManager.PROVIDER_META)) break
                                }
                                is CallResult.ServerError -> {
                                    circuitBreaker.recordFailure(ProviderCircuitBreakerAndKeyManager.PROVIDER_META, isServerError = true)
                                    if (!circuitBreaker.isProviderHealthy(ProviderCircuitBreakerAndKeyManager.PROVIDER_META)) break
                                }
                                else -> {}
                            }
                        }
                        if (!circuitBreaker.isProviderHealthy(ProviderCircuitBreakerAndKeyManager.PROVIDER_META)) break
                    }
                }
            }

            // ── Vision Tier 1: QwenCloud Multimodal Vision (Secondary) ───
            if (circuitBreaker.isProviderHealthy(ProviderCircuitBreakerAndKeyManager.PROVIDER_QWEN)) {
                val effectiveVisionQwen = getDynamicModels(
                    ModelProviders.QWEN, ModelCapability.VISION, VISION_QWEN_MODELS
                )
                val qwenKeys = getEffectiveKeys(
                    ProviderCircuitBreakerAndKeyManager.PROVIDER_QWEN,
                    listOfNotNull(qwenApiKey.ifBlank { userPreferencesRepository.getQwenApiKeySync() }.takeIf { it.isNotBlank() })
                )
                if (qwenKeys.isNotEmpty()) {
                    val qwenStartTime = System.currentTimeMillis()
                    for (key in qwenKeys) {
                        for (model in effectiveVisionQwen) {
                            when (val result = qwenClient.executeSingleCall(model, messages, targetMaxTokens, key)) {
                                is CallResult.Success -> {
                                    circuitBreaker.recordSuccess(ProviderCircuitBreakerAndKeyManager.PROVIDER_QWEN)
                                    val durationMs = System.currentTimeMillis() - qwenStartTime
                                    PersonaLogger.i(TAG, "Vision Tier 1 (Qwen) succeeded with $model in ${durationMs}ms!")
                                    recordSuccessTelemetry("Qwen", model, durationMs, result, messages)
                                    return result.content
                                }
                                is CallResult.AuthOrQuotaError -> {
                                    circuitBreaker.evictKey(ProviderCircuitBreakerAndKeyManager.PROVIDER_QWEN, key, "Auth/quota error: ${result.code}")
                                    break
                                }
                                is CallResult.Timeout -> {
                                    circuitBreaker.recordTimeout(ProviderCircuitBreakerAndKeyManager.PROVIDER_QWEN)
                                    if (!circuitBreaker.isProviderHealthy(ProviderCircuitBreakerAndKeyManager.PROVIDER_QWEN)) break
                                }
                                is CallResult.ServerError -> {
                                    circuitBreaker.recordFailure(ProviderCircuitBreakerAndKeyManager.PROVIDER_QWEN, isServerError = true)
                                    if (!circuitBreaker.isProviderHealthy(ProviderCircuitBreakerAndKeyManager.PROVIDER_QWEN)) break
                                }
                                else -> {}
                            }
                        }
                        if (!circuitBreaker.isProviderHealthy(ProviderCircuitBreakerAndKeyManager.PROVIDER_QWEN)) break
                    }
                }
            }

            // ── Vision Tier 2: Groq Cloud (Secondary) ───────────────────
            if (circuitBreaker.isProviderHealthy(ProviderCircuitBreakerAndKeyManager.PROVIDER_GROQ)) {
                val effectiveVisionGroq = getDynamicModels(
                    ModelProviders.GROQ, ModelCapability.VISION, VISION_GROQ_MODELS
                )
                val groqKeys = getEffectiveKeys(
                    ProviderCircuitBreakerAndKeyManager.PROVIDER_GROQ,
                    listOfNotNull(groqPrimaryApiKey.takeIf { it.isNotBlank() }, groqFallbackApiKey.takeIf { it.isNotBlank() })
                )
                if (groqKeys.isNotEmpty()) {
                    val groqStartTime = System.currentTimeMillis()
                    for (key in groqKeys) {
                        for (model in effectiveVisionGroq) {
                            when (val result = groqClient.executeSingleCall(model, messages, targetMaxTokens, key)) {
                                is CallResult.Success -> {
                                    circuitBreaker.recordSuccess(ProviderCircuitBreakerAndKeyManager.PROVIDER_GROQ)
                                    val durationMs = System.currentTimeMillis() - groqStartTime
                                    PersonaLogger.i(TAG, "Vision Tier 2 (Groq) succeeded with $model in ${durationMs}ms!")
                                    recordSuccessTelemetry("Groq", model, durationMs, result, messages)
                                    return result.content
                                }
                                is CallResult.AuthOrQuotaError -> {
                                    circuitBreaker.evictKey(ProviderCircuitBreakerAndKeyManager.PROVIDER_GROQ, key, "Auth/quota error: ${result.code}")
                                    break
                                }
                                is CallResult.Timeout -> {
                                    circuitBreaker.recordTimeout(ProviderCircuitBreakerAndKeyManager.PROVIDER_GROQ)
                                    if (!circuitBreaker.isProviderHealthy(ProviderCircuitBreakerAndKeyManager.PROVIDER_GROQ)) break
                                }
                                is CallResult.ServerError -> {
                                    circuitBreaker.recordFailure(ProviderCircuitBreakerAndKeyManager.PROVIDER_GROQ, isServerError = true)
                                    if (!circuitBreaker.isProviderHealthy(ProviderCircuitBreakerAndKeyManager.PROVIDER_GROQ)) break
                                }
                                else -> {}
                            }
                        }
                        if (!circuitBreaker.isProviderHealthy(ProviderCircuitBreakerAndKeyManager.PROVIDER_GROQ)) break
                    }
                }
            }

            // ── Vision Tier 2.5: Gemini ──────────────────────────────────
            if (circuitBreaker.isProviderHealthy(ProviderCircuitBreakerAndKeyManager.PROVIDER_GEMINI)) {
                val effectiveVisionGemini = getDynamicModels(
                    ModelProviders.GEMINI, ModelCapability.VISION, VISION_GEMINI_MODELS
                )
                val geminiKeys = getEffectiveKeys(
                    ProviderCircuitBreakerAndKeyManager.PROVIDER_GEMINI,
                    listOfNotNull(geminiApiKey.takeIf { it.isNotBlank() }, geminiApiKey2.takeIf { it.isNotBlank() && it != "gemini_placeholder_key_2" })
                )
                if (geminiKeys.isNotEmpty()) {
                    val geminiStartTime = System.currentTimeMillis()
                    for (key in geminiKeys) {
                        for (model in effectiveVisionGemini) {
                            when (val result = geminiClient.executeSingleCall(model, messages, targetMaxTokens, key)) {
                                is CallResult.Success -> {
                                    circuitBreaker.recordSuccess(ProviderCircuitBreakerAndKeyManager.PROVIDER_GEMINI)
                                    val durationMs = System.currentTimeMillis() - geminiStartTime
                                    PersonaLogger.i(TAG, "Vision Tier 2.5 (Gemini) succeeded with $model in ${durationMs}ms!")
                                    recordSuccessTelemetry("Gemini", model, durationMs, result, messages)
                                    return result.content
                                }
                                is CallResult.AuthOrQuotaError -> {
                                    circuitBreaker.evictKey(ProviderCircuitBreakerAndKeyManager.PROVIDER_GEMINI, key, "Auth/quota error: ${result.code}")
                                    break
                                }
                                is CallResult.Timeout -> {
                                    circuitBreaker.recordTimeout(ProviderCircuitBreakerAndKeyManager.PROVIDER_GEMINI)
                                    if (!circuitBreaker.isProviderHealthy(ProviderCircuitBreakerAndKeyManager.PROVIDER_GEMINI)) break
                                }
                                is CallResult.ServerError -> {
                                    circuitBreaker.recordFailure(ProviderCircuitBreakerAndKeyManager.PROVIDER_GEMINI, isServerError = true)
                                    if (!circuitBreaker.isProviderHealthy(ProviderCircuitBreakerAndKeyManager.PROVIDER_GEMINI)) break
                                }
                                else -> {}
                            }
                        }
                        if (!circuitBreaker.isProviderHealthy(ProviderCircuitBreakerAndKeyManager.PROVIDER_GEMINI)) break
                    }
                }
            }

            // ── Vision Tier 3: NVIDIA NIM ──────────────────────────
            if (circuitBreaker.isProviderHealthy(ProviderCircuitBreakerAndKeyManager.PROVIDER_NVIDIA)) {
                val effectiveVisionNvidia = getDynamicModels(
                    ModelProviders.NVIDIA, ModelCapability.VISION, VISION_NVIDIA_MODELS
                )
                val nvidiaKeys = getEffectiveKeys(
                    ProviderCircuitBreakerAndKeyManager.PROVIDER_NVIDIA,
                    listOfNotNull(nvidiaApiKey.ifBlank { userPreferencesRepository.getNvidiaApiKeySync() }.takeIf { it.isNotBlank() })
                )
                if (nvidiaKeys.isNotEmpty()) {
                    val nvidiaStartTime = System.currentTimeMillis()
                    for (key in nvidiaKeys) {
                        for (model in effectiveVisionNvidia) {
                            when (val result = nvidiaClient.executeSingleCall(model, messages, targetMaxTokens, key)) {
                                is CallResult.Success -> {
                                    circuitBreaker.recordSuccess(ProviderCircuitBreakerAndKeyManager.PROVIDER_NVIDIA)
                                    val durationMs = System.currentTimeMillis() - nvidiaStartTime
                                    PersonaLogger.i(TAG, "Vision Tier 3 (NVIDIA NIM) succeeded with $model in ${durationMs}ms!")
                                    recordSuccessTelemetry("NVIDIA", model, durationMs, result, messages)
                                    return result.content
                                }
                                is CallResult.AuthOrQuotaError -> {
                                    circuitBreaker.evictKey(ProviderCircuitBreakerAndKeyManager.PROVIDER_NVIDIA, key, "Auth/quota error: ${result.code}")
                                    break
                                }
                                is CallResult.Timeout -> {
                                    circuitBreaker.recordTimeout(ProviderCircuitBreakerAndKeyManager.PROVIDER_NVIDIA)
                                    if (!circuitBreaker.isProviderHealthy(ProviderCircuitBreakerAndKeyManager.PROVIDER_NVIDIA)) break
                                }
                                is CallResult.ServerError -> {
                                    circuitBreaker.recordFailure(ProviderCircuitBreakerAndKeyManager.PROVIDER_NVIDIA, isServerError = true)
                                    if (!circuitBreaker.isProviderHealthy(ProviderCircuitBreakerAndKeyManager.PROVIDER_NVIDIA)) break
                                }
                                else -> {}
                            }
                        }
                        if (!circuitBreaker.isProviderHealthy(ProviderCircuitBreakerAndKeyManager.PROVIDER_NVIDIA)) break
                    }
                }
            }

            // ── Vision Tier 4: OpenRouter Fallback ──────────────────
            if (circuitBreaker.isProviderHealthy(ProviderCircuitBreakerAndKeyManager.PROVIDER_OPENROUTER)) {
                val effectiveVisionOpenRouter = getDynamicModels(
                    ModelProviders.OPENROUTER, ModelCapability.VISION, VISION_OPENROUTER_MODELS
                )
                val openRouterKeys = getEffectiveKeys(
                    ProviderCircuitBreakerAndKeyManager.PROVIDER_OPENROUTER,
                    listOfNotNull(primaryApiKey.takeIf { it.isNotBlank() }, fallbackApiKey.takeIf { it.isNotBlank() })
                )
                if (openRouterKeys.isNotEmpty()) {
                    for (key in openRouterKeys) {
                        for (model in effectiveVisionOpenRouter) {
                            when (val result = openRouterClient.executeSingleCall(model, messages, targetMaxTokens, key)) {
                                is CallResult.Success -> {
                                    circuitBreaker.recordSuccess(ProviderCircuitBreakerAndKeyManager.PROVIDER_OPENROUTER)
                                    PersonaLogger.i(TAG, "Vision Tier 4 (OpenRouter) succeeded with $model")
                                    recordSuccessTelemetry("OpenRouter", model, 0L, result, messages)
                                    return result.content
                                }
                                is CallResult.AuthOrQuotaError -> {
                                    circuitBreaker.evictKey(ProviderCircuitBreakerAndKeyManager.PROVIDER_OPENROUTER, key, "Auth/quota error: ${result.code}")
                                    break
                                }
                                is CallResult.Timeout -> {
                                    circuitBreaker.recordTimeout(ProviderCircuitBreakerAndKeyManager.PROVIDER_OPENROUTER)
                                    if (!circuitBreaker.isProviderHealthy(ProviderCircuitBreakerAndKeyManager.PROVIDER_OPENROUTER)) break
                                }
                                is CallResult.ServerError -> {
                                    circuitBreaker.recordFailure(ProviderCircuitBreakerAndKeyManager.PROVIDER_OPENROUTER, isServerError = true)
                                    if (!circuitBreaker.isProviderHealthy(ProviderCircuitBreakerAndKeyManager.PROVIDER_OPENROUTER)) break
                                }
                                else -> {}
                            }
                        }
                        if (!circuitBreaker.isProviderHealthy(ProviderCircuitBreakerAndKeyManager.PROVIDER_OPENROUTER)) break
                    }
                }
            }

            // Fallback to text chain if visual description is available
            val hasVisualDescription = messages.any { it.content.contains("[وصف الصورة المرفقة من العميل]:") }
            if (hasVisualDescription) {
                PersonaLogger.w(TAG, "Dedicated Vision models exhausted. Falling back to text model chain using visual_product_description...")
                Log.w(TAG, "Dedicated Vision models exhausted. Falling back to text model chain using visual_product_description...")
                val strippedMessages = messages.map { it.copy(imageUrl = null) }
                return executeFailoverChain(strippedMessages, maxTokens)
            }

            Log.w(TAG, "All Vision models failed across tiers. Returning HITL fallback JSON.")
            PersonaLogger.d(TAG, "All Vision models failed — HITL fallback")
            return HITL_FALLBACK_JSON
        }

        // ── Pure Text / Transcribed Voice Request (Dynamic Cascade with Circuit Breaker) ──
        val cascadeOrder = circuitBreaker.getProviderCascadeOrder()

                for (providerId in cascadeOrder) {
                        if (!circuitBreaker.isProviderHealthy(providerId)) {
                PersonaLogger.i(TAG, "⚡ [$providerId] circuit breaker is OPEN (cooldown 5m). Bypassing in 0ms.")
                Log.i(TAG, "⚡ [$providerId] circuit breaker is OPEN (cooldown 5m). Bypassing in 0ms.")
                continue
            }

            val keys = when (providerId) {
                ProviderCircuitBreakerAndKeyManager.PROVIDER_META -> getEffectiveKeys(
                    providerId,
                    metaApiKeyOverride?.takeIf { it.isNotBlank() }?.let { listOf(it) }
                        ?: userPreferencesRepository.getApiKeys(ModelProviders.META)
                            .map { it.trim() }
                            .filter { it.isNotBlank() }
                            .ifEmpty {
                                listOfNotNull(runCatching { BuildConfig.META_API_KEY }.getOrNull()?.trim()?.takeIf { it.isNotBlank() })
                            }
                )
                ProviderCircuitBreakerAndKeyManager.PROVIDER_QWEN -> getEffectiveKeys(
                    providerId,
                    listOfNotNull(qwenApiKey.ifBlank { userPreferencesRepository.getQwenApiKeySync() }.takeIf { it.isNotBlank() })
                )
                ProviderCircuitBreakerAndKeyManager.PROVIDER_GEMINI -> getEffectiveKeys(
                    providerId,
                    listOfNotNull(geminiApiKey.takeIf { it.isNotBlank() }, geminiApiKey2.takeIf { it.isNotBlank() && it != "gemini_placeholder_key_2" })
                )
                ProviderCircuitBreakerAndKeyManager.PROVIDER_NVIDIA -> getEffectiveKeys(
                    providerId,
                    listOfNotNull(nvidiaApiKey.ifBlank { userPreferencesRepository.getNvidiaApiKeySync() }.takeIf { it.isNotBlank() })
                )
                ProviderCircuitBreakerAndKeyManager.PROVIDER_GROQ -> getEffectiveKeys(
                    providerId,
                    listOfNotNull(groqPrimaryApiKey.takeIf { it.isNotBlank() }, groqFallbackApiKey.takeIf { it.isNotBlank() })
                )
                ProviderCircuitBreakerAndKeyManager.PROVIDER_OPENROUTER -> getEffectiveKeys(
                    providerId,
                    listOfNotNull(primaryApiKey.takeIf { it.isNotBlank() }, fallbackApiKey.takeIf { it.isNotBlank() })
                )
                ProviderCircuitBreakerAndKeyManager.PROVIDER_NARA -> getEffectiveKeys(
                    providerId,
                    listOfNotNull(naraApiKey.takeIf { it.isNotBlank() })
                )
                else -> circuitBreaker.getActiveKeys(providerId)
            }

            if (keys.isEmpty()) {
                continue
            }

            val models = circuitBreaker.getModelList(providerId)
            val startTime = System.currentTimeMillis()

            for (key in keys) {
                for (model in models) {
                    val result = when (providerId) {
                        ProviderCircuitBreakerAndKeyManager.PROVIDER_META -> metaModelApiClient?.executeSingleCall(model, messages, targetMaxTokens, key) ?: CallResult.Failed
                        ProviderCircuitBreakerAndKeyManager.PROVIDER_QWEN -> qwenClient.executeSingleCall(model, messages, targetMaxTokens, key)
                        ProviderCircuitBreakerAndKeyManager.PROVIDER_GEMINI -> geminiClient.executeSingleCall(model, messages, targetMaxTokens, key)
                        ProviderCircuitBreakerAndKeyManager.PROVIDER_NVIDIA -> nvidiaClient.executeSingleCall(model, messages, targetMaxTokens, key)
                        ProviderCircuitBreakerAndKeyManager.PROVIDER_GROQ -> groqClient.executeSingleCall(model, messages, targetMaxTokens, key)
                        ProviderCircuitBreakerAndKeyManager.PROVIDER_OPENROUTER -> openRouterClient.executeSingleCall(model, messages, targetMaxTokens, key)
                        ProviderCircuitBreakerAndKeyManager.PROVIDER_NARA -> naraRouterClient.call(model, messages, targetMaxTokens, key)
                        else -> CallResult.Failed
                    }
                    

                    when (result) {
                        is CallResult.Success -> {
                            circuitBreaker.recordSuccess(providerId)
                            val durationMs = System.currentTimeMillis() - startTime
                            PersonaLogger.i(TAG, "Tier [$providerId] succeeded with model $model in ${durationMs}ms!")
                            Log.i(TAG, "Tier [$providerId] succeeded with model $model in ${durationMs}ms")
                            val providerLabel = when (providerId) {
                                ProviderCircuitBreakerAndKeyManager.PROVIDER_META -> "Meta"
                                ProviderCircuitBreakerAndKeyManager.PROVIDER_QWEN -> "Qwen"
                                ProviderCircuitBreakerAndKeyManager.PROVIDER_GEMINI -> "Gemini"
                                ProviderCircuitBreakerAndKeyManager.PROVIDER_NVIDIA -> "NVIDIA"
                                ProviderCircuitBreakerAndKeyManager.PROVIDER_GROQ -> "Groq"
                                ProviderCircuitBreakerAndKeyManager.PROVIDER_OPENROUTER -> "OpenRouter"
                                ProviderCircuitBreakerAndKeyManager.PROVIDER_NARA -> "Nara"
                                else -> providerId.replaceFirstChar { it.uppercase() }
                            }
                            recordSuccessTelemetry(providerLabel, model, durationMs, result, messages)
                            return result.content
                        }
                        is CallResult.AuthOrQuotaError -> {
                            if (result.code == 401 || result.code == 402) {
                                PersonaLogger.w(TAG, "Key auth error (${result.code}) on [$providerId]. Evicting key.")
                                circuitBreaker.evictKey(providerId, key, "Auth error: ${result.code}")
                                break // rotate to next key
                            } else {
                                PersonaLogger.w(TAG, "Model $model quota error (${result.code}) on [$providerId]. Cascading to next model.")
                            }
                        }
                        is CallResult.Timeout -> {
                            PersonaLogger.w(TAG, "Timeout on [$providerId] model $model.")
                            circuitBreaker.recordTimeout(providerId)
                            if (!circuitBreaker.isProviderHealthy(providerId)) {
                                break // 2 timeouts tripped breaker -> 0ms cascade to next provider
                            }
                        }
                        is CallResult.ServerError -> {
                            PersonaLogger.w(TAG, "Server error (${result.code}) on [$providerId] model $model.")
                            circuitBreaker.recordFailure(providerId, isServerError = true)
                            if (!circuitBreaker.isProviderHealthy(providerId)) {
                                break
                            }
                        }
                        is CallResult.RateLimited -> {
                            PersonaLogger.w(TAG, "Rate limited (429) on [$providerId] model $model. Trying next model.")
                        }
                        else -> {
                            Log.w(TAG, "Model $model call failed ($result) on [$providerId].")
                        }
                    }
                }
                if (!circuitBreaker.isProviderHealthy(providerId)) {
                    break // failover immediately to next provider
                }
            }
        }

        Log.w(TAG, "All models failed across configured cascade tiers. Returning HITL fallback JSON.")
        PersonaLogger.d(TAG, "All models failed — HITL fallback")
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
