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
import com.example.persona.features.auto_reply.data.providers.NaraRouterClient
import com.example.persona.features.auto_reply.data.providers.NvidiaNimClient
import com.example.persona.features.auto_reply.data.providers.OpenRouterClient
import com.example.persona.features.auto_reply.data.providers.QwenCloudClient
import com.example.persona.features.auto_reply.domain.gatekeeper.InboundIntent
import com.example.persona.features.auto_reply.domain.model.ProcessedNotification
import com.example.persona.features.auto_reply.domain.model.RetrievalCandidate
import com.example.persona.features.auto_reply.domain.repository.ReplyGenerationRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.client.HttpClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lean coordinator for cloud LLM response generation.
 * Orchestrates multi-tier failover chains across Qwen, Gemini, NVIDIA, Groq, and OpenRouter,
 * delegating network transports and image processing to dedicated single-responsibility components.
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
    private val imagePreprocessor: ImagePayloadPreprocessor
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
        groqFallbackApiKey: String? = "",
        nvidiaApiKey: String? = "",
        qwenApiKey: String? = "",
        geminiApiKey: String? = "",
        geminiApiKey2: String? = ""
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
        imagePreprocessor = ImagePayloadPreprocessor()
    ) {
        this.primaryApiKeyOverride = primaryApiKey
        this.fallbackApiKeyOverride = fallbackApiKey
        this.groqPrimaryApiKeyOverride = groqPrimaryApiKey
        this.groqFallbackApiKeyOverride = groqFallbackApiKey
        this.nvidiaApiKeyOverride = nvidiaApiKey
        this.qwenApiKeyOverride = qwenApiKey
        this.geminiApiKeyOverride = geminiApiKey
        this.geminiApiKey2Override = geminiApiKey2
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
        imagePreprocessor = ImagePayloadPreprocessor()
    )

    private var primaryApiKeyOverride: String? = null
    private var fallbackApiKeyOverride: String? = null
    private var groqPrimaryApiKeyOverride: String? = null
    private var groqFallbackApiKeyOverride: String? = null
    private var nvidiaApiKeyOverride: String? = null
    private var qwenApiKeyOverride: String? = null
    private var geminiApiKeyOverride: String? = null
    private var geminiApiKey2Override: String? = null

    internal val primaryApiKey: String
        get() = primaryApiKeyOverride ?: BuildConfig.OPENROUTER_API_KEY

    internal val fallbackApiKey: String
        get() = fallbackApiKeyOverride ?: BuildConfig.OPENROUTER_API_KEY_V2

    internal val groqPrimaryApiKey: String
        get() = groqPrimaryApiKeyOverride ?: BuildConfig.GROQ_API_KEY

    internal val groqFallbackApiKey: String
        get() = groqFallbackApiKeyOverride ?: BuildConfig.GROQ_API_KEY_V2

    internal val nvidiaApiKey: String
        get() = nvidiaApiKeyOverride ?: BuildConfig.NVIDIA_API_KEY

    internal val qwenApiKey: String
        get() = qwenApiKeyOverride ?: BuildConfig.QWEN_API_KEY

    internal val geminiApiKey: String
        get() = geminiApiKeyOverride ?: BuildConfig.GEMINI_API_KEY

    internal val geminiApiKey2: String
        get() = geminiApiKey2Override ?: BuildConfig.GEMINI_API_KEY_2

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

        val VISION_QWEN_MODELS = QwenCloudClient.VISION_QWEN_MODELS
        val VISION_GROQ_MODELS = GroqClient.VISION_GROQ_MODELS
        val VISION_GEMINI_MODELS = GeminiClient.VISION_GEMINI_MODELS
        val VISION_NVIDIA_MODELS = NvidiaNimClient.VISION_NVIDIA_MODELS
        val VISION_OPENROUTER_MODELS = OpenRouterClient.VISION_OPENROUTER_MODELS

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

    sealed interface CallResult {
        data class Success(val content: String) : CallResult
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

    internal suspend fun callQwenCloud(
        model: String,
        messages: List<ChatMessage>,
        maxTokens: Int
    ): CallResult {
        val apiKey = qwenApiKey.ifBlank { userPreferencesRepository.getQwenApiKeySync() }.trim()
        return qwenClient.call(model, messages, maxTokens, apiKey)
    }

    internal suspend fun callNvidiaNim(
        model: String,
        messages: List<ChatMessage>,
        maxTokens: Int
    ): CallResult {
        val apiKey = nvidiaApiKey.ifBlank { userPreferencesRepository.getNvidiaApiKeySync() }.trim()
        return nvidiaClient.call(model, messages, maxTokens, apiKey)
    }

    internal suspend fun callGroq(
        model: String,
        messages: List<ChatMessage>,
        maxTokens: Int
    ): CallResult = groqClient.call(model, messages, maxTokens, groqPrimaryApiKey, groqFallbackApiKey)

    internal suspend fun callGemini(
        model: String,
        messages: List<ChatMessage>,
        maxTokens: Int
    ): CallResult = geminiClient.call(model, messages, maxTokens, geminiApiKey, geminiApiKey2)

    internal suspend fun callOpenRouter(
        model: String,
        messages: List<ChatMessage>,
        maxTokens: Int
    ): CallResult = openRouterClient.call(model, messages, maxTokens, primaryApiKey, fallbackApiKey)

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

        val systemPromptWithContext = when (inboundIntent) {
            InboundIntent.GREETING, InboundIntent.BANTER -> {
                Log.d(TAG, "⚡ Routing to buildMicroBanterPrompt (intent=$inboundIntent, bypassing RAG)")
                PromptContextBuilder.buildMicroBanterPrompt(
                    botName = botName,
                    storeName = storeName,
                    tone = tone
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
        val baseSystemPrompt = PromptContextBuilder.buildFinalPassSystemPrompt(
            botName = userPreferencesRepository.getBotNameSync(),
            storeName = userPreferencesRepository.getStoreNameSync(),
            tone = tone,
            isMultimodal = isMultimodal
        )
        val systemPromptWithContext = PromptContextBuilder.buildSystemPromptWithContext(
            staticSystemPrompt = baseSystemPrompt,
            candidates = chunks,
            activeOrders = activeOrders,
            storeTopics = storeTopics,
            storeWebsite = storeWebsite,
            lastOrder = lastOrder,
            statusCounts = orderStatusCounts
        )
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
            PersonaLogger.i(TAG, "📸 Multimodal Vision request detected. Dispatching to Vision cascade (Qwen -> Groq -> Gemini -> NVIDIA -> OpenRouter)...")
            Log.i(TAG, "📸 Multimodal Vision request detected. Dispatching to Vision cascade (Qwen -> Groq -> Gemini -> NVIDIA -> OpenRouter)...")

            // ── Vision Tier 1: QwenCloud Multimodal Vision (Primary) ───
            val qwenStartTime = System.currentTimeMillis()
            val qwenKey = qwenApiKey.ifBlank { userPreferencesRepository.getQwenApiKeySync() }.trim()
            if (qwenKey.isNotBlank()) {
                for (model in VISION_QWEN_MODELS) {
                    val result = callQwenCloud(model, messages, targetMaxTokens)
                    if (result is CallResult.Success) {
                        val durationMs = System.currentTimeMillis() - qwenStartTime
                        PersonaLogger.i(TAG, "Vision Tier 1 (Qwen) succeeded with $model in ${durationMs}ms!")
                        Log.i(TAG, "Vision Tier 1 (Qwen) succeeded with $model in ${durationMs}ms")
                        return result.content
                    }
                }
                PersonaLogger.w(TAG, "Vision Tier 1 (Qwen) exhausted. Cascading to Vision Tier 2 (Groq)...")
                Log.w(TAG, "Vision Tier 1 (Qwen) exhausted. Cascading to Vision Tier 2 (Groq)...")
            }

            // ── Vision Tier 2: Groq Cloud (Secondary) ───────────────────
            val groqStartTime = System.currentTimeMillis()
            val hasGroqKeys = groqPrimaryApiKey.isNotBlank() || groqFallbackApiKey.isNotBlank()
            if (hasGroqKeys) {
                for (model in VISION_GROQ_MODELS) {
                    val result = callGroq(model, messages, targetMaxTokens)
                    if (result is CallResult.Success) {
                        val durationMs = System.currentTimeMillis() - groqStartTime
                        PersonaLogger.i(TAG, "Vision Tier 2 (Groq) succeeded with $model in ${durationMs}ms!")
                        Log.i(TAG, "Vision Tier 2 (Groq) succeeded with $model in ${durationMs}ms")
                        return result.content
                    }
                }
                PersonaLogger.w(TAG, "Vision Tier 2 (Groq) exhausted. Cascading to Vision Tier 2.5 (Gemini)...")
                Log.w(TAG, "Vision Tier 2 (Groq) exhausted. Cascading to Vision Tier 2.5 (Gemini)...")
            }

            // ── Vision Tier 2.5: Gemini ──────────────────────────────────
            val geminiStartTime = System.currentTimeMillis()
            val hasGeminiKeys = geminiApiKey.isNotBlank() || (geminiApiKey2.isNotBlank() && geminiApiKey2 != "gemini_placeholder_key_2")
            if (hasGeminiKeys) {
                for (model in VISION_GEMINI_MODELS) {
                    val result = callGemini(model, messages, targetMaxTokens)
                    if (result is CallResult.Success) {
                        val durationMs = System.currentTimeMillis() - geminiStartTime
                        PersonaLogger.i(TAG, "Vision Tier 2.5 (Gemini) succeeded with $model in ${durationMs}ms!")
                        Log.i(TAG, "Vision Tier 2.5 (Gemini) succeeded with $model in ${durationMs}ms")
                        return result.content
                    }
                }
                PersonaLogger.w(TAG, "Vision Tier 2.5 (Gemini) exhausted. Cascading to Vision Tier 3 (NVIDIA NIM)...")
                Log.w(TAG, "Vision Tier 2.5 (Gemini) exhausted. Cascading to Vision Tier 3 (NVIDIA NIM)...")
            }

            // ── Vision Tier 3: NVIDIA NIM ──────────────────────────
            val nvidiaStartTime = System.currentTimeMillis()
            val nvidiaKey = nvidiaApiKey.ifBlank { userPreferencesRepository.getNvidiaApiKeySync() }.trim()
            if (nvidiaKey.isNotBlank()) {
                for (model in VISION_NVIDIA_MODELS) {
                    val result = callNvidiaNim(model, messages, targetMaxTokens)
                    if (result is CallResult.Success) {
                        val durationMs = System.currentTimeMillis() - nvidiaStartTime
                        PersonaLogger.i(TAG, "Vision Tier 3 (NVIDIA NIM) succeeded with $model in ${durationMs}ms!")
                        Log.i(TAG, "Vision Tier 3 (NVIDIA NIM) succeeded with $model in ${durationMs}ms")
                        return result.content
                    }
                }
                PersonaLogger.w(TAG, "Vision Tier 3 (NVIDIA NIM) exhausted. Cascading to Vision Tier 4 (OpenRouter)...")
                Log.w(TAG, "Vision Tier 3 (NVIDIA NIM) exhausted. Cascading to Vision Tier 4 (OpenRouter)...")
            }

            // ── Vision Tier 4: OpenRouter Fallback ──────────────────
            for (model in VISION_OPENROUTER_MODELS) {
                val result = callOpenRouter(model, messages, targetMaxTokens)
                if (result is CallResult.Success) {
                    PersonaLogger.i(TAG, "Vision Tier 4 (OpenRouter) succeeded with $model")
                    Log.i(TAG, "Vision Tier 4 (OpenRouter) succeeded with $model")
                    return result.content
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

            Log.w(TAG, "All Vision models failed across Qwen, Groq, Gemini, NVIDIA, and OpenRouter tiers. Returning HITL fallback JSON.")
            PersonaLogger.d(TAG, "All Vision models failed — HITL fallback")
            return HITL_FALLBACK_JSON
        }

        // ── Pure Text / Transcribed Voice Request ─────────────────────

        // ── Tier 1: QwenCloud Active Quotas (Primary Text & VIP Reasoning) ──
        val qwenStartTime = System.currentTimeMillis()
        val qwenKey = qwenApiKey.ifBlank { userPreferencesRepository.getQwenApiKeySync() }.trim()
        if (qwenKey.isNotBlank()) {
            for (model in QWEN_TEXT_MODELS) {
                when (val result = callQwenCloud(model, messages, targetMaxTokens)) {
                    is CallResult.Success -> {
                        val durationMs = System.currentTimeMillis() - qwenStartTime
                        PersonaLogger.i(TAG, "Qwen Tier 1 succeeded with model $model in ${durationMs}ms!")
                        Log.i(TAG, "Qwen Tier 1 succeeded with model $model in ${durationMs}ms")
                        return result.content
                    }
                    is CallResult.AuthOrQuotaError -> {
                        PersonaLogger.w(TAG, "Qwen model $model quota/auth error (${result.code}). Cascading immediately to next model.")
                        Log.w(TAG, "Qwen model $model quota/auth error (${result.code}). Cascading immediately to next model.")
                    }
                    is CallResult.RateLimited -> {
                        PersonaLogger.w(TAG, "Qwen model $model rate limited (429). Cascading immediately to next model.")
                        Log.w(TAG, "Qwen model $model rate limited (429). Cascading immediately to next model.")
                    }
                    else -> {
                        Log.w(TAG, "Qwen model $model call failed ($result). Cascading to next model.")
                    }
                }
            }
            PersonaLogger.w(TAG, "Qwen Tier 1 exhausted. Cascading to Tier 1.5 (Gemini)...")
            Log.w(TAG, "Qwen Tier 1 exhausted. Cascading to Tier 1.5 (Gemini)...")
        }

        // ── Tier 1.5: Gemini ──────────────────────────────────────────
        val geminiStartTime = System.currentTimeMillis()
        val hasGeminiKeys = geminiApiKey.isNotBlank() || (geminiApiKey2.isNotBlank() && geminiApiKey2 != "gemini_placeholder_key_2")
        if (hasGeminiKeys) {
            for (model in GEMINI_TEXT_MODELS) {
                when (val result = callGemini(model, messages, targetMaxTokens)) {
                    is CallResult.Success -> {
                        val durationMs = System.currentTimeMillis() - geminiStartTime
                        PersonaLogger.i(TAG, "Gemini Tier 1.5 succeeded with model $model in ${durationMs}ms!")
                        Log.i(TAG, "Gemini Tier 1.5 succeeded with model $model in ${durationMs}ms")
                        return result.content
                    }
                    is CallResult.AuthOrQuotaError -> {
                        PersonaLogger.w(TAG, "Gemini model $model quota/auth error (${result.code}). Cascading immediately to next model.")
                        Log.w(TAG, "Gemini model $model quota/auth error (${result.code}). Cascading immediately to next model.")
                    }
                    is CallResult.RateLimited -> {
                        PersonaLogger.w(TAG, "Gemini model $model rate limited (429). Cascading immediately to next model.")
                        Log.w(TAG, "Gemini model $model rate limited (429). Cascading immediately to next model.")
                    }
                    else -> {
                        Log.w(TAG, "Gemini model $model call failed ($result). Cascading to next model.")
                    }
                }
            }
            PersonaLogger.w(TAG, "Gemini Tier 1.5 exhausted. Cascading to Tier 2 (NVIDIA NIM)...")
            Log.w(TAG, "Gemini Tier 1.5 exhausted. Cascading to Tier 2 (NVIDIA NIM)...")
        }

        // ── Tier 2: NVIDIA NIM (Primary Fast Text) ─────────────────────
        val nvidiaStartTime = System.currentTimeMillis()
        val nvidiaKey = nvidiaApiKey.ifBlank { userPreferencesRepository.getNvidiaApiKeySync() }.trim()
        if (nvidiaKey.isNotBlank()) {
            for (model in NVIDIA_TEXT_MODELS) {
                val result = callNvidiaNim(model, messages, targetMaxTokens)
                if (result is CallResult.Success) {
                    val durationMs = System.currentTimeMillis() - nvidiaStartTime
                    PersonaLogger.i(TAG, "NVIDIA Tier 2 succeeded with model $model in ${durationMs}ms!")
                    Log.i(TAG, "NVIDIA Tier 2 succeeded with model $model in ${durationMs}ms")
                    return result.content
                }
            }
            PersonaLogger.w(TAG, "NVIDIA Tier 2 exhausted. Cascading to Tier 3 (Groq)...")
            Log.w(TAG, "NVIDIA Tier 2 exhausted. Cascading to Tier 3 (Groq)...")
        }

        // ── Tier 3: Groq Cloud (Secondary Fast Text) ───────────────────
        val groqStartTime = System.currentTimeMillis()
        val hasGroqKeys = groqPrimaryApiKey.isNotBlank() || groqFallbackApiKey.isNotBlank()
        if (hasGroqKeys) {
            for (model in GROQ_MODELS) {
                val result = callGroq(model, messages, targetMaxTokens)
                if (result is CallResult.Success) {
                    val durationMs = System.currentTimeMillis() - groqStartTime
                    PersonaLogger.i(TAG, "Groq Tier 3 succeeded with model $model in ${durationMs}ms!")
                    Log.i(TAG, "Groq Tier 3 succeeded with model $model in ${durationMs}ms")
                    return result.content
                }
            }
            PersonaLogger.w(TAG, "Groq Tier 3 exhausted. Cascading to Tier 4 (OpenRouter)...")
            Log.w(TAG, "Groq Tier 3 exhausted. Cascading to Tier 4 (OpenRouter)...")
        }

        // ── Tier 4: OpenRouter Failover (Emergency Safety Net) ───────────
        var consecutive429Count = 0
        for ((index, model) in OPENROUTER_FAILOVER_MODELS.withIndex()) {
            Log.d(TAG, "Attempting [OpenRouter] model ${index + 1}/${OPENROUTER_FAILOVER_MODELS.size}: $model with max_tokens=$targetMaxTokens")
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

        Log.w(TAG, "All models failed across Qwen, Gemini, NVIDIA, Groq, and OpenRouter tiers. Returning HITL fallback JSON.")
        PersonaLogger.d(TAG, "All models failed — HITL fallback")
        return HITL_FALLBACK_JSON
    }
}
