package com.example.persona.features.auto_reply.data.repository

import android.content.Context
import android.util.Log
import com.example.persona.BuildConfig
import com.example.persona.core.data.repository.UserPreferencesRepository
import com.example.persona.core.database.entity.ChatHistoryEntity
import com.example.persona.core.database.entity.OrderEntity
import com.example.persona.core.database.entity.OrderStatusCount
import com.example.persona.core.logging.PersonaLogger
import com.example.persona.core.logging.PersonaLogTags
import com.example.persona.core.network.dto.ChatMessage
import com.example.persona.core.network.dto.GroqRequest
import com.example.persona.core.network.dto.GroqResponse
import com.example.persona.core.network.dto.NvidiaRequest
import com.example.persona.core.network.dto.NvidiaResponse
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
        groqFallbackApiKey: String? = "",
        nvidiaApiKey: String? = ""
    ) : this(httpClient, promptContextBuilder, context, userPreferencesRepository) {
        this.primaryApiKeyOverride = primaryApiKey
        this.fallbackApiKeyOverride = fallbackApiKey
        this.groqPrimaryApiKeyOverride = groqPrimaryApiKey
        this.groqFallbackApiKeyOverride = groqFallbackApiKey
        this.nvidiaApiKeyOverride = nvidiaApiKey
    }

    private var primaryApiKeyOverride: String? = null
    private var fallbackApiKeyOverride: String? = null
    private var groqPrimaryApiKeyOverride: String? = null
    private var groqFallbackApiKeyOverride: String? = null
    private var nvidiaApiKeyOverride: String? = null

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

    companion object {
        private const val TAG = PersonaLogTags.LLM
        private val AUDIO_EXTENSIONS = setOf("opus", "ogg", "mp3", "m4a", "wav", "aac", "m4p", "amr")
        private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif")

        const val MODEL_TIMEOUT_MS = 10_000L
        const val VISION_MODEL_TIMEOUT_MS = 25_000L
        private const val ABSOLUTE_FAILOVER_CEILING = 8000

        private val jsonParser = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

        const val HITL_FALLBACK_JSON =
            """{"action":"HITL","refined_search_keyword":null,"order_action":null,"final_reply_text":"${PromptContextBuilder.CANONICAL_HITL_MESSAGE}","extracted_order":null}"""

        val CANONICAL_HITL_MESSAGE = PromptContextBuilder.CANONICAL_HITL_MESSAGE

        /** Vision Primary & Failover Tiers */
        val VISION_GROQ_MODELS = listOf(
            "qwen/qwen3.8-27b" // 1.2s Champion on apparel graphic OCR
        )
        val VISION_NVIDIA_MODELS = listOf(
            "meta/llama-3.2-11b-vision-instruct",
            "nvidia/nemotron-3.5-super-vl-120b-a12b" // max_tokens >= 1500
        )
        val VISION_OPENROUTER_MODELS = listOf(
            "nvidia/nemotron-3.5-super-vl:free"
        )

        /** Tier 1: NVIDIA NIM (Primary Fast Text - Top 4 Benchmarked) */
        val NVIDIA_TEXT_MODELS = listOf(
            "nvidia/nemotron-3-ultra-550b-a55b",     // Deep analytical reasoner
            "nvidia/nemotron-3.5-bolt-e4b",         // 304ms TTFT / 1.6s Champion
            "meta/llama-3.2-11b-vision-instruct",
            "nvidia/nemotron-3.5-lightning-30b-a3b", // 30B fast reasoning fallback
            "openai/gpt-oss-20b"                     // Reliable fallback
        )

        /** Tier 2: Groq Cloud (Fast Text Secondary) */
        val GROQ_MODELS = listOf(
            "openai/gpt-oss-120b",
            "openai/gpt-oss-20b"
        )

        /** Tier 3: OpenRouter Failover (Pure Text First, Multimodal Last) */
        val OPENROUTER_FAILOVER_MODELS = listOf(
            "nvidia/nemotron-3-ultra-550b-a55b:free",
            "liquid/lfm-2.5-2.6b:free",
            "cohere/north-mini-code:free",
            "openrouter/free"
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

        internal fun formatHistoricalAssistantTurn(rawText: String): String =
            PromptContextBuilder.formatHistoricalAssistantTurn(rawText)

        internal fun isMediaRefusalMessage(text: String): Boolean =
            PromptContextBuilder.isMediaRefusalMessage(text)

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

    internal fun compressAndScaleImage(
        filePath: String,
        maxDimension: Int = 1024,
        quality: Int = 80
    ): ByteArray? {
        val file = java.io.File(filePath)
        if (!file.exists() || file.length() == 0L) return null

        val ext = file.extension.lowercase()
        if (AUDIO_EXTENSIONS.contains(ext) || !IMAGE_EXTENSIONS.contains(ext)) {
            Log.d(TAG, "compressAndScaleImage: Skipping non-image file with extension .$ext ($filePath)")
            return null
        }

        return try {
            val boundsOptions = android.graphics.BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            android.graphics.BitmapFactory.decodeFile(file.absolutePath, boundsOptions)
            val origWidth = boundsOptions.outWidth
            val origHeight = boundsOptions.outHeight

            if (origWidth > 0 && origHeight > 0) {
                var sampleSize = 1
                val maxDim = maxOf(origWidth, origHeight)
                while ((maxDim / (sampleSize * 2)) >= maxDimension) {
                    sampleSize *= 2
                }
                val decodeOptions = android.graphics.BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                }
                val decoded = android.graphics.BitmapFactory.decodeFile(file.absolutePath, decodeOptions)
                if (decoded != null) {
                    val w = decoded.width
                    val h = decoded.height
                    val finalBitmap = if (w > maxDimension || h > maxDimension) {
                        val ratio = minOf(maxDimension.toFloat() / w, maxDimension.toFloat() / h)
                        val targetW = (w * ratio).toInt().coerceAtLeast(1)
                        val targetH = (h * ratio).toInt().coerceAtLeast(1)
                        val scaled = android.graphics.Bitmap.createScaledBitmap(decoded, targetW, targetH, true)
                        if (scaled != decoded) {
                            decoded.recycle()
                        }
                        scaled
                    } else {
                        decoded
                    }

                    try {
                        val stream = java.io.ByteArrayOutputStream()
                        finalBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, quality, stream)
                        val compressed = stream.toByteArray()
                        Log.d(TAG, "Image scaled and compressed to ${compressed.size} bytes (target quality=$quality)")
                        compressed
                    } finally {
                        finalBitmap.recycle()
                    }
                } else {
                    file.readBytes()
                }
            } else {
                file.readBytes()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Image decoding/scaling failed for $filePath, falling back to raw file: ${e.message}")
            try { file.readBytes() } catch (_: Exception) { null }
        }
    }

    internal fun resolveImageUri(filePathOrUri: String?): String? {
        if (filePathOrUri.isNullOrBlank()) return null
        if (filePathOrUri.startsWith("data:image/") || filePathOrUri.startsWith("http://") || filePathOrUri.startsWith("https://")) {
            return filePathOrUri
        }
        val file = java.io.File(filePathOrUri)
        val ext = file.extension.lowercase()
        if (AUDIO_EXTENSIONS.contains(ext) || !IMAGE_EXTENSIONS.contains(ext)) {
            Log.d(TAG, "resolveImageUri: Ignoring non-image media file path: $filePathOrUri")
            return null
        }
        return try {
            val bytes = compressAndScaleImage(filePathOrUri, maxDimension = 1024, quality = 80)
            if (bytes != null && bytes.isNotEmpty()) {
                val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                "data:image/jpeg;base64,$base64"
            } else null
        } catch (e: Exception) {
            Log.w(TAG, "Failed to encode media image to Base64: ${e.message}")
            null
        }
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
        orderStatusCounts: List<OrderStatusCount>
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
            val inboundText = if (notification.originalText.contains("Sent a photo", ignoreCase = true) || notification.originalText.trim() == "📷" || notification.originalText.isBlank()) {
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
        val baseSystemPrompt = PromptContextBuilder.buildFirstPassSystemPrompt(
            botName = userPreferencesRepository.getBotNameSync(),
            storeName = userPreferencesRepository.getStoreNameSync(),
            tone = tone,
            isMultimodal = false
        )
        val systemPromptWithContext = PromptContextBuilder.buildSystemPromptWithContext(
            staticSystemPrompt = baseSystemPrompt,
            candidates = candidates,
            activeOrders = activeOrders,
            storeTopics = storeTopics,
            storeWebsite = storeWebsite,
            lastOrder = lastOrder,
            statusCounts = orderStatusCounts
        )

        val messages = PromptContextBuilder.buildOpenRouterMessages(
            systemPrompt = systemPromptWithContext,
            chatHistory = chatHistory,
            currentInboundText = notification.originalText,
            imageUrl = null
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
        val messages = PromptContextBuilder.buildOpenRouterMessages(
            systemPrompt = systemPromptWithContext,
            chatHistory = chatHistory,
            currentInboundText = queryText,
            imageUrl = imageUri
        )
        val effectiveMaxTokens = if (isMultimodal) 1500 else maxTokens
        return executeFailoverChain(messages, effectiveMaxTokens)
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
        val hasImage = messages.any { !it.imageUrl.isNullOrBlank() }
        val targetMaxTokens = if (hasImage) 500 else minOf(maxTokens, ABSOLUTE_FAILOVER_CEILING)

        if (hasImage) {
            PersonaLogger.i(TAG, "📸 Multimodal Vision request detected. Dispatching to Vision cascade (Groq -> NVIDIA -> OpenRouter)...")
            Log.i(TAG, "📸 Multimodal Vision request detected. Dispatching to Vision cascade (Groq -> NVIDIA -> OpenRouter)...")

            // ── Vision Tier 1: Groq Cloud (Champion) ───────────────────
            val groqStartTime = System.currentTimeMillis()
            val hasGroqKeys = groqPrimaryApiKey.isNotBlank() || groqFallbackApiKey.isNotBlank()
            if (hasGroqKeys) {
                for (model in VISION_GROQ_MODELS) {
                    val result = callGroq(model, messages, targetMaxTokens)
                    if (result is CallResult.Success) {
                        val durationMs = System.currentTimeMillis() - groqStartTime
                        PersonaLogger.i(TAG, "Vision Tier 1 (Groq) succeeded with $model in ${durationMs}ms!")
                        Log.i(TAG, "Vision Tier 1 (Groq) succeeded with $model in ${durationMs}ms")
                        return result.content
                    }
                }
                PersonaLogger.w(TAG, "Vision Tier 1 (Groq) exhausted. Cascading to Vision Tier 2 (NVIDIA NIM)...")
                Log.w(TAG, "Vision Tier 1 (Groq) exhausted. Cascading to Vision Tier 2 (NVIDIA NIM)...")
            }

            // ── Vision Tier 2: NVIDIA NIM ──────────────────────────
            val nvidiaStartTime = System.currentTimeMillis()
            val nvidiaKey = nvidiaApiKey.ifBlank { userPreferencesRepository.getNvidiaApiKeySync() }.trim()
            if (nvidiaKey.isNotBlank()) {
                for (model in VISION_NVIDIA_MODELS) {
                    val result = callNvidiaNim(model, messages, targetMaxTokens)
                    if (result is CallResult.Success) {
                        val durationMs = System.currentTimeMillis() - nvidiaStartTime
                        PersonaLogger.i(TAG, "Vision Tier 2 (NVIDIA NIM) succeeded with $model in ${durationMs}ms!")
                        Log.i(TAG, "Vision Tier 2 (NVIDIA NIM) succeeded with $model in ${durationMs}ms")
                        return result.content
                    }
                }
                PersonaLogger.w(TAG, "Vision Tier 2 (NVIDIA NIM) exhausted. Cascading to Vision Tier 3 (OpenRouter)...")
                Log.w(TAG, "Vision Tier 2 (NVIDIA NIM) exhausted. Cascading to Vision Tier 3 (OpenRouter)...")
            }

            // ── Vision Tier 3: OpenRouter Fallback ──────────────────
            for (model in VISION_OPENROUTER_MODELS) {
                val result = callOpenRouter(model, messages, targetMaxTokens)
                if (result is CallResult.Success) {
                    PersonaLogger.i(TAG, "Vision Tier 3 (OpenRouter) succeeded with $model")
                    Log.i(TAG, "Vision Tier 3 (OpenRouter) succeeded with $model")
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

            Log.w(TAG, "All Vision models failed across Groq, NVIDIA, and OpenRouter tiers. Returning HITL fallback JSON.")
            PersonaLogger.d(TAG, "All Vision models failed — HITL fallback")
            return HITL_FALLBACK_JSON
        }

        // ── Pure Text / Transcribed Voice Request ─────────────────────

        // ── Tier 1: NVIDIA NIM (Primary Fast Text) ─────────────────────
        val nvidiaStartTime = System.currentTimeMillis()
        val nvidiaKey = nvidiaApiKey.ifBlank { userPreferencesRepository.getNvidiaApiKeySync() }.trim()
        if (nvidiaKey.isNotBlank()) {
            for (model in NVIDIA_TEXT_MODELS) {
                val result = callNvidiaNim(model, messages, targetMaxTokens)
                if (result is CallResult.Success) {
                    val durationMs = System.currentTimeMillis() - nvidiaStartTime
                    PersonaLogger.i(TAG, "NVIDIA Tier 1 succeeded with model $model in ${durationMs}ms!")
                    Log.i(TAG, "NVIDIA Tier 1 succeeded with model $model in ${durationMs}ms")
                    return result.content
                }
            }
            PersonaLogger.w(TAG, "NVIDIA Tier 1 exhausted. Cascading to Tier 2 (Groq)...")
            Log.w(TAG, "NVIDIA Tier 1 exhausted. Cascading to Tier 2 (Groq)...")
        }

        // ── Tier 2: Groq Cloud (Secondary Fast Text) ───────────────────
        val groqStartTime = System.currentTimeMillis()
        val hasGroqKeys = groqPrimaryApiKey.isNotBlank() || groqFallbackApiKey.isNotBlank()
        if (hasGroqKeys) {
            for (model in GROQ_MODELS) {
                val result = callGroq(model, messages, targetMaxTokens)
                if (result is CallResult.Success) {
                    val durationMs = System.currentTimeMillis() - groqStartTime
                    PersonaLogger.i(TAG, "Groq Tier 2 succeeded with model $model in ${durationMs}ms!")
                    Log.i(TAG, "Groq Tier 2 succeeded with model $model in ${durationMs}ms")
                    return result.content
                }
            }
            PersonaLogger.w(TAG, "Groq Tier 2 exhausted. Cascading to Tier 3 (OpenRouter)...")
            Log.w(TAG, "Groq Tier 2 exhausted. Cascading to Tier 3 (OpenRouter)...")
        }

        // ── Tier 3: OpenRouter Failover (Emergency Safety Net) ───────────
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

        Log.w(TAG, "All models failed across NVIDIA, Groq, and OpenRouter tiers. Returning HITL fallback JSON.")
        PersonaLogger.d(TAG, "All models failed — HITL fallback")
        return HITL_FALLBACK_JSON
    }

    internal suspend fun callNvidiaNim(
        model: String,
        messages: List<ChatMessage>,
        maxTokens: Int
    ): CallResult = withContext(Dispatchers.IO) {
        val apiKey = nvidiaApiKey.ifBlank { userPreferencesRepository.getNvidiaApiKeySync() }.trim()
        if (apiKey.isBlank()) {
            Log.w(TAG, "NVIDIA API key is blank; skipping request for model $model")
            return@withContext CallResult.Failed
        }

        val keyMasked = maskKey(apiKey)
        val hasImage = messages.any { !it.imageUrl.isNullOrBlank() }
        val isVision = model in VISION_NVIDIA_MODELS || hasImage
        val timeoutMs = if (isVision) VISION_MODEL_TIMEOUT_MS else MODEL_TIMEOUT_MS
        val effectiveMaxTokens = if (model.contains("super-vl")) maxOf(maxTokens, 1500) else maxTokens
        PersonaLogger.i(TAG, "Attempting [NVIDIA NIM] model $model with key $keyMasked (max_tokens=$effectiveMaxTokens, timeout=${timeoutMs}ms)")
        Log.i(TAG, "Attempting [NVIDIA NIM] model $model with key $keyMasked (max_tokens=$effectiveMaxTokens, timeout=${timeoutMs}ms)")

        try {
            withTimeout(timeoutMs) {
                val response = httpClient.post(BuildConfig.NVIDIA_BASE_URL) {
                    contentType(ContentType.Application.Json)
                    bearerAuth(apiKey)
                    setBody(
                        NvidiaRequest(
                            model = model,
                            messages = messages,
                            temperature = 0.5f,
                            maxTokens = effectiveMaxTokens
                        )
                    )
                }

                when {
                    response.status.value == 429 -> {
                        Log.w(TAG, "Rate limited (429) on [NVIDIA NIM] model $model (key $keyMasked)")
                        CallResult.RateLimited
                    }
                    response.status.value in listOf(401, 402, 403) -> {
                        Log.w(TAG, "Auth/quota error (${response.status.value}) on [NVIDIA NIM] model $model (key $keyMasked)")
                        CallResult.AuthOrQuotaError(response.status.value)
                    }
                    response.status.value in 500..599 -> {
                        Log.w(TAG, "Server error (${response.status.value}) on [NVIDIA NIM] model $model (key $keyMasked)")
                        CallResult.ServerError(response.status.value)
                    }
                    response.status.isSuccess() -> {
                        val responseBody = response.body<String>()
                        PersonaLogger.d(PersonaLogTags.LLM, "📥 Raw LLM Output from [NVIDIA NIM] $model: $responseBody")
                        val body = jsonParser.decodeFromString<NvidiaResponse>(responseBody)
                        val content = body.choices?.firstOrNull()?.message?.content
                        if (content.isNullOrBlank()) {
                            Log.w(TAG, "Empty content in successful response from [NVIDIA NIM] $model (key $keyMasked)")
                            CallResult.Failed
                        } else {
                            try {
                                ReasoningLeakSanitizer.decodeAgenticResponseSafely(content)
                                CallResult.Success(content)
                            } catch (e: Exception) {
                                Log.w(TAG, "Invalid JSON content in response from [NVIDIA NIM] $model (key $keyMasked): $content", e)
                                CallResult.Failed
                            }
                        }
                    }
                    else -> {
                        Log.w(TAG, "HTTP ${response.status.value} error from [NVIDIA NIM] $model (key $keyMasked)")
                        CallResult.Failed
                    }
                }
            }
        } catch (e: TimeoutCancellationException) {
            Log.w(TAG, "Timeout (${timeoutMs}ms) on [NVIDIA NIM] model $model (key $keyMasked)")
            CallResult.Timeout
        } catch (e: io.ktor.client.network.sockets.SocketTimeoutException) {
            Log.w(TAG, "Socket timeout on [NVIDIA NIM] model $model (key $keyMasked): ${e.message}")
            CallResult.Timeout
        } catch (e: Exception) {
            Log.e(TAG, "Network error on [NVIDIA NIM] model $model (key $keyMasked)", e)
            CallResult.Failed
        }
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
        val hasImage = messages.any { !it.imageUrl.isNullOrBlank() }
        val isVision = model in VISION_GROQ_MODELS || hasImage
        val timeoutMs = if (isVision) VISION_MODEL_TIMEOUT_MS else MODEL_TIMEOUT_MS
        val reasoningEffort = if (isVision) null else "low"

        return try {
            withTimeout(timeoutMs) {
                var response = httpClient.post(BuildConfig.GROQ_BASE_URL) {
                    contentType(ContentType.Application.Json)
                    bearerAuth(apiKey)
                    setBody(
                        GroqRequest(
                            model = model,
                            messages = messages,
                            maxCompletionTokens = maxTokens,
                            reasoningEffort = reasoningEffort
                        )
                    )
                }

                if (response.status.value == 400 && !isVision) {
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
            Log.w(TAG, "Timeout (${timeoutMs}ms) on [Groq] model $model (key $keyMasked)")
            CallResult.Timeout
        } catch (e: io.ktor.client.network.sockets.SocketTimeoutException) {
            Log.w(TAG, "Socket timeout on [Groq] model $model (key $keyMasked): ${e.message}")
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
        val hasImage = messages.any { !it.imageUrl.isNullOrBlank() }
        val isVision = model in VISION_OPENROUTER_MODELS || hasImage
        val timeoutMs = if (isVision) VISION_MODEL_TIMEOUT_MS else MODEL_TIMEOUT_MS
        val effectiveMaxTokens = if (model.contains("super-vl")) maxOf(maxTokens, 1500) else maxTokens

        return try {
            withTimeout(timeoutMs) {
                val reasoning = if (model in REASONING_MODELS && !hasImage) ReasoningConfig(effort = "low") else null
                var response = httpClient.post(BuildConfig.OPENROUTER_BASE_URL) {
                    contentType(ContentType.Application.Json)
                    bearerAuth(apiKey)
                    setBody(
                        OpenRouterRequest(
                            model = model,
                            messages = messages,
                            maxTokens = effectiveMaxTokens,
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
                                maxTokens = effectiveMaxTokens,
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
            Log.w(TAG, "Timeout (${timeoutMs}ms) on model $model (key $keyMasked)")
            CallResult.Timeout
        } catch (e: io.ktor.client.network.sockets.SocketTimeoutException) {
            Log.w(TAG, "Socket timeout on model $model (key $keyMasked): ${e.message}")
            CallResult.Timeout
        } catch (e: Exception) {
            Log.e(TAG, "Network error on model $model (key $keyMasked)", e)
            CallResult.Failed
        }
    }
}
