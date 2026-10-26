package com.example.persona.features.auto_reply.data.repository

import android.content.Context
import android.util.Log
import com.example.persona.BuildConfig
import com.example.persona.R
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
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cloud LLM generation via OpenRouter with multi-model serial failover.
 *
 * Persists and updates context dynamically under a hard 3000-token budget ceiling,
 * applying priority-ranked trimming, and routes output max_tokens based on loop pass.
 */
@Singleton
class ReplyGenerationRepositoryImpl @Inject constructor(
    private val httpClient: HttpClient,
    @ApplicationContext private val context: Context
) : ReplyGenerationRepository {

    internal constructor(
        httpClient: HttpClient,
        context: Context,
        primaryApiKey: String,
        fallbackApiKey: String
    ) : this(httpClient, context) {
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

        /**
         * Elevated network timeout (25 seconds) — prevents timeouts on slower free models under heavy context load.
         */
        private const val MODEL_TIMEOUT_MS = 25000L

        /**
         * Absolute ceiling for max tokens across failover.
         */
        private const val ABSOLUTE_FAILOVER_CEILING = 8000

        private val HORIZONTAL_RULE_REGEX = Regex("^[-*_]{3,}$")

        const val HITL_FALLBACK_JSON =
            """{"action": "HITL", "refined_search_keyword": null, "order_action": null, "final_reply_text": "تمام يا فندم، ثواني وهيكون مع حضرتك ممثل خدمة العملاء فوراً لمساعدتك في أقرب وقت متاح! 🙏", "extracted_order": null}"""

        /**
         * Ordered failover chain — fast, conversational free-tier instruct models.
         */
        private val FAILOVER_MODELS = listOf(
            "nvidia/nemotron-3.5-super-vl:free",     // speed champion
            "nvidia/nemotron-3.5-lightning:free",
            "openrouter/free",
            "dots-studio/dots-3-note-preview:free",
            "google/gemma-4-31b-it:free",
            "nvidia/nemotron-3-ultra-550b-a55b:free"
        )

        // ── System Prompts ────────────────────────────────────────────────

        /**
         * First-pass system prompt: allows REPLY, RE_QUERY, or HITL actions.
         */
        const val SYSTEM_PROMPT_FIRST_PASS = """You are the primary AI Customer Support Agent for an e-commerce business. Your task is to analyze the conversation, classify the user's intent, extract orders if present, and provide a helpful, human-like response.

RULES & INSTRUCTIONS:
1. OUTPUT FORMAT:
You MUST respond with a single valid JSON object. Absolutely NO markdown code blocks (no ```json), NO conversational filler before or after the JSON, and NO thinking/reasoning text.
Output Schema:
{
  "action": "REPLY" | "RE_QUERY" | "HITL",
  "refined_search_keyword": "keywords in Modern Standard Arabic for catalog search, or null",
  "order_action": "CREATE" | "CANCEL" | "UPDATE" | null,
  "final_reply_text": "The message to send to the customer",
  "extracted_order": {
    "customer_name": "string or null",
    "delivery_address": "string or null",
    "order_details": "string or null",
    "custom_notes": "string or null"
  } or null
}

2. IDENTITY & CONTINUITY:
- You represent the store's human-like sales & support team (infer store products/domain strictly from STORE KNOWLEDGE).
- NEVER call yourself an AI, bot, or model name (e.g., Nemotron/Llama/Gemma).
- If prior conversation messages exist, NEVER repeat greetings (أهلاً بك, مرحباً, وعليكم السلام) or re-introduce yourself; answer directly.

3. CONCISENESS & TONE:
- Language Matching: Respond in the exact language used by the customer. If the user speaks Arabic, reply in natural, friendly Egyptian Arabic dialect ("العامية المصرية"). If the user speaks English, reply in polite and clear English.
- Conciseness: Answer ONLY what the customer asked in 1–3 short, natural sentences. Never dump unrequested policies.
- Anti-Echo & Recommendations: Never echo the exact previous assistant reply verbatim. If the customer asks for a recommendation ("تقترح عليا اي"), highlight a specific bestseller/feature from STORE KNOWLEDGE and explain why it suits them.
- Tone: Welcoming, courteous, and practical. Approved emojis: (😊, 🤔, 🧐, 🫡, 🙋🏻‍♂️, 🤌🏻, 👌🏻, 🤝, 🫶🏻, 👀, 🤠, 😎, 🤩, 🥰, 👍, ✨, 🌸, 🛒). Never invent store policies or products not in STORE KNOWLEDGE.

4. ACTIONS & WORKFLOW:
- "REPLY": Use when the answer is fully supported by STORE KNOWLEDGE or CUSTOMER ORDER STATE.
- "RE_QUERY": Use when the query is relevant to the store products/services but the current knowledge context lacks exact details. Provide 2-4 keywords in `refined_search_keyword` in formal Arabic to run a secondary catalog search.
- "HITL" (Human-In-The-Loop): Use when:
  a. The customer expresses strong frustration or complains about service.
  b. The customer asks for a human representative.
  c. The customer uses abusive words or profanity (respond with a polite boundary request and hand off).
  d. The inquiry requires owner intervention or information not available in the store data.
- In every HITL reply, final_reply_text must always end with the exact Arabic phrase (never translated, never modified):
"تمام يا فندم، ثواني وهيكون مع حضرتك ممثل خدمة العملاء فوراً لمساعدتك في أقرب وقت متاح! 🙏"

5. ORDER ACTIONS & EXTRACTION:
- ORDER ACTION (`order_action`): Set when the customer's message changes order state; null otherwise.
  - "CREATE": the customer is actively placing a NEW order with items/quantities.
  - "CANCEL": the customer explicitly asks to stop or cancel their active order ("إلغاء", "كنسل", "بطل الطلب"). NEVER use it for general complaints, pricing questions, or anything other than termination of the order itself.
  - "UPDATE": the customer supplies missing details (address/phone/notes/product names) for the active order, or answers the duplicate-order verification question with edit-intent (e.g. "خلاص سجّل", "عدّل العنوان"). NEVER use it for bare acknowledgements ("تمام", "ماشي") after an unrelated answer.
  - null: for inquiries, small-talk, questions, or HITL hand-offs.
- STATE-CLAIM RULE: never state in final_reply_text that an order was registered, updated, or cancelled unless the matching order_action is set in THIS response. Without an order_action, describe orders exactly and only as they appear in CUSTOMER ORDER STATE.
- UPDATE CLOSING RULE: when order_action is "UPDATE", final_reply_text must CLOSE the loop with a short confirmation (e.g. "تم يا فندم، سجلت التفاصيل عندنا ✅") and NEVER ask the customer to confirm the same thing again.
- ORDER STATUS: If customer asks about their order status and an order exists in CUSTOMER ORDER STATE, reply strictly using this Egyptian Arabic structure (adapt to English if customer is speaking English):
  "الأوردر بتاعك حالته [Status]. طلبك عبارة عن [Order Details]، والملاحظات المسجلة هي [CustomNotes/MerchantNotes]، والتوصيل لـ [Address]."
  (Omit notes if None).
- Set `extracted_order` ONLY when the customer is actively submitting or confirming a purchase with actionable details; otherwise, it must be null.

6. GENERALIZED OUT-OF-BOUNDS MARKETING RECOVERY:
If the customer sends ANY benign off-topic, playful, or non-store message (e.g., games "نلعب", sports, coding, marriage/flattery, weather, riddles, "انت مين"), NEVER play along with off-topic games ("عايز تلعب إيه") and DO NOT trigger HITL. Instead, reply with light, respectful wit (matching customer's language: Egyptian Arabic or English) and smoothly pivot back to the store's actual products from STORE KNOWLEDGE. Illustrative examples adaptable to any store catalog:
- Flattery/Marriage: "أنا ممكن أشوفلك أحلى عروسة بس اطلب من عندنا منتج شيك تتقدم بيه الأول 🤠🤝"
- Coding/Tech: "عينيا ليك هكتبلك أحلى كود بس اعمل أوردر الأول عشان الفانكشن تشتغل وتتكومبايل صح 👀""""

        /**
         * Second-pass system prompt: forces REPLY-only or HITL mode, RE_QUERY is forbidden.
         * Maintains full parity with FIRST_PASS on persona, conciseness, order extraction, and recovery.
         */
        const val SYSTEM_PROMPT_FINAL_PASS = """You are the primary AI Customer Support Agent for an e-commerce business. This is the FINAL generation pass.

RULES & INSTRUCTIONS:
1. OUTPUT FORMAT:
You MUST respond with a single valid JSON object. Absolutely NO markdown backticks (no ```json) and NO text outside the JSON.
Output Schema:
{
  "action": "REPLY" | "HITL",
  "refined_search_keyword": null,
  "order_action": "CREATE" | "CANCEL" | "UPDATE" | null,
  "final_reply_text": "The message to send to the customer",
  "extracted_order": {
    "customer_name": "string or null",
    "delivery_address": "string or null",
    "order_details": "string or null",
    "custom_notes": "string or null"
  } or null
}

2. IDENTITY & CONTINUITY:
- You represent the store's human-like sales & support team (infer store products/domain strictly from STORE KNOWLEDGE).
- NEVER call yourself an AI, bot, or model name (e.g., Nemotron/Llama/Gemma).
- If prior conversation messages exist, NEVER repeat greetings (أهلاً بك, مرحباً, وعليكم السلام) or re-introduce yourself; answer directly.

3. CONCISENESS & TONE:
- Language Matching: Match customer's language (natural Egyptian Arabic dialect "العامية المصرية" for Arabic inquiries, English for English).
- Conciseness: Answer ONLY what the customer asked in 1–3 short, natural sentences. Never dump unrequested policies.
- Anti-Echo & Recommendations: Never echo the exact previous assistant reply verbatim. If the customer asks for a recommendation ("تقترح عليا اي"), highlight a specific bestseller/feature from STORE KNOWLEDGE and explain why it suits them.
- Tone: Welcoming, courteous, and practical. Approved emojis: (😊, 🤔, 🧐, 🫡, 🙋🏻‍♂️, 🤌🏻, 👌🏻, 🤝, 🫶🏻, 👀, 🤠, 😎, 🤩, 🥰, 👍, ✨, 🌸, 🛒). Never invent store policies or products not in STORE KNOWLEDGE.

4. ACTIONS & WORKFLOW:
- Action MUST be strictly "REPLY" or "HITL" ("RE_QUERY" is completely forbidden in this pass, refined_search_keyword must be null).
- "REPLY": Use when the answer is fully supported by STORE KNOWLEDGE or CUSTOMER ORDER STATE.
- "HITL" (Human-In-The-Loop): Use when:
  a. The customer expresses strong frustration or complains about service.
  b. The customer asks for a human representative.
  c. The customer uses abusive words or profanity (respond with a polite boundary request and hand off).
  d. The knowledge context is still insufficient or requires owner intervention.
- In every HITL reply, final_reply_text must always end with the exact Arabic phrase (never translated, never modified):
"تمام يا فندم، ثواني وهيكون مع حضرتك ممثل خدمة العملاء فوراً لمساعدتك في أقرب وقت متاح! 🙏"

5. ORDER ACTIONS & EXTRACTION:
- ORDER ACTION (`order_action`): Set when the customer's message changes order state; null otherwise.
  - "CREATE": the customer is actively placing a NEW order with items/quantities.
  - "CANCEL": the customer explicitly asks to stop or cancel their active order ("إلغاء", "كنسل", "بطل الطلب"). NEVER use it for general complaints or pricing questions.
  - "UPDATE": the customer supplies missing details (address/phone/notes/product names) for the active order, or answers the duplicate-order verification question with edit-intent (e.g. "خلاص سجّل", "عدّل العنوان"). NEVER use it for bare acknowledgements ("تمام", "ماشي") after an unrelated answer.
  - null: for inquiries, small-talk, questions, or HITL hand-offs.
- STATE-CLAIM RULE: never state in final_reply_text that an order was registered, updated, or cancelled unless the matching order_action is set in THIS response. Without an order_action, describe orders exactly and only as they appear in CUSTOMER ORDER STATE.
- UPDATE CLOSING RULE: when order_action is "UPDATE", final_reply_text must CLOSE the loop with a short confirmation (e.g. "تم يا فندم، سجلت التفاصيل عندنا ✅") and NEVER ask the customer to confirm the same thing again.
- ORDER STATUS: If customer asks about order status and an order exists in CUSTOMER ORDER STATE, reply strictly using this Egyptian Arabic structure (adapt to English if customer is speaking English):
  "الأوردر بتاعك حالته [Status]. طلبك عبارة عن [Order Details]، والملاحظات المسجلة هي [CustomNotes/MerchantNotes]، والتوصيل لـ [Address]."
  (Omit notes if None).
- Set `extracted_order` ONLY when the customer is actively submitting or confirming a purchase with actionable details; otherwise, it must be null.

6. GENERALIZED OUT-OF-BOUNDS MARKETING RECOVERY:
If the customer sends ANY benign off-topic, playful, or non-store message (e.g., games "نلعب", sports, coding, marriage/flattery, weather, riddles, "انت مين"), NEVER play along with off-topic games ("عايز تلعب إيه") and DO NOT trigger HITL. Instead, reply with light, respectful wit (matching customer's language: Egyptian Arabic or English) and smoothly pivot back to the store's actual products from STORE KNOWLEDGE. Illustrative examples adaptable to any store catalog:
- Flattery/Marriage: "أنا ممكن أشوفلك أحلى عروسة بس اطلب من عندنا منتج شيك تتقدم بيه الأول 🤠🤝"
- Coding/Tech: "عينيا ليك هكتبلك أحلى كود بس اعمل أوردر الأول عشان الفانكشن تشتغل وتتكومبايل صح 👀""""

        /**
         * Builds the dynamic per-turn context block containing store knowledge,
         * customer order state, and current query under a 3000-token budget ceiling.
         */
        internal fun buildDynamicContextBlock(
            notificationText: String,
            candidates: List<RetrievalCandidate>,
            activeOrders: List<OrderEntity>,
            personalChunks: List<ChatHistoryEntity>
        ): String {
            var remainingBudget = 3000

            // 1. Current Inbound Message (Priority 1) - NEVER trimmed
            val inboundBlock = "\n\n=== CURRENT CUSTOMER MESSAGE ===\n$notificationText"
            remainingBudget -= estimateTokens(inboundBlock)

            // 2. Personal Client Order State (Priority 2)
            val firstOrder = activeOrders.firstOrNull()
            val orderStateBlock = if (firstOrder == null) {
                "Active Order: None"
            } else {
                buildString {
                    append("Order Status: ${firstOrder.status}\n")
                    append("Order Details: ${firstOrder.orderDetails ?: "None"}\n")
                    append("Delivery Address: ${firstOrder.deliveryAddress ?: "None"}\n")
                    append("Custom Notes: ${firstOrder.customNotes ?: "None"}\n")
                    append("Merchant Notes: ${firstOrder.merchantNote ?: "None"}")
                }
            }
            remainingBudget -= estimateTokens(orderStateBlock)

            // 3. Personal History Chunks (Priority 3) - Top 2 personal chunks, trimmed chunk-by-chunk
            val keptPChunks = mutableListOf<String>()
            val pChunks = personalChunks.take(2)
            for (chunk in pChunks) {
                val chunkText = "- ${chunk.messageText.trim()}"
                val chunkTokens = estimateTokens(chunkText) + 1
                if (remainingBudget >= chunkTokens) {
                    keptPChunks.add(chunkText)
                    remainingBudget -= chunkTokens
                } else {
                    break
                }
            }

            // 4. Global Business Knowledge Chunks (Priority 4) - trimmed dynamically
            val keptGlobalChunks = mutableListOf<String>()
            for (candidate in candidates) {
                val header = candidate.headerContext?.trim()
                val rawText = candidate.chunkText.trim()

                // DO NOT prepend headerContext a second time if chunkText already starts with headerContext
                val chunkText = if (!header.isNullOrBlank() && !rawText.startsWith(header)) {
                    "$header\n$rawText"
                } else {
                    rawText
                }

                // Clean stray horizontal rules and clean trailing dashes/newlines
                val cleanedChunk = chunkText.lines()
                    .filterNot { it.trim().matches(HORIZONTAL_RULE_REGEX) }
                    .joinToString("\n")
                    .trim()
                    .trimEnd('-', ' ', '\n', '\r')
                    .trim()

                if (cleanedChunk.isBlank()) continue

                val chunkTokens = estimateTokens(cleanedChunk) + 2
                if (remainingBudget >= chunkTokens) {
                    keptGlobalChunks.add(cleanedChunk)
                    remainingBudget -= chunkTokens
                } else {
                    break
                }
            }

            return buildString {
                append("=== STORE KNOWLEDGE ===\n")
                if (keptGlobalChunks.isEmpty()) {
                    append("None\n")
                } else {
                    append(keptGlobalChunks.joinToString("\n---\n"))
                    append("\n")
                }

                append("\n=== CUSTOMER ORDER STATE ===\n")
                append(orderStateBlock)
                if (keptPChunks.isNotEmpty()) {
                    append("\nRelevant Customer Records:\n")
                    append(keptPChunks.joinToString("\n"))
                }
                append("\n")

                append(inboundBlock.trimStart())
            }
        }

        /**
         * Structures the messages list for provider prefix caching and multi-turn coherence:
         * - Message 0 (system): Purely static unified system prompt.
         * - Messages 1..N (user / assistant): Recent chat turns natively mapped and coalesced.
         * - Final Message (user): Dynamic per-turn context block.
         */
        internal fun buildOpenRouterMessages(
            systemPrompt: String,
            chatHistory: List<ChatHistoryEntity>,
            dynamicContextBlock: String
        ): List<ChatMessage> {
            val messages = mutableListOf<ChatMessage>()
            messages.add(ChatMessage(role = "system", content = systemPrompt))

            // Recent 6 turns chronologically (oldest first)
            val turns = chatHistory.take(6).reversed()
            val nativeHistory = mutableListOf<ChatMessage>()

            for (turn in turns) {
                val role = if (turn.isFromUser) "user" else "assistant"
                val text = turn.messageText.trim()
                if (text.isEmpty()) continue

                if (nativeHistory.isNotEmpty() && nativeHistory.last().role == role) {
                    val prev = nativeHistory.removeAt(nativeHistory.lastIndex)
                    nativeHistory.add(ChatMessage(role = role, content = "${prev.content}\n$text"))
                } else {
                    nativeHistory.add(ChatMessage(role = role, content = text))
                }
            }

            // Prune leading assistant turn if conversation starts with bot turn
            while (nativeHistory.isNotEmpty() && nativeHistory.first().role == "assistant") {
                nativeHistory.removeAt(0)
            }

            // If the last history turn is a user message, coalesce it with the final user message to maintain strict role alternation
            if (nativeHistory.isNotEmpty() && nativeHistory.last().role == "user") {
                val lastUser = nativeHistory.removeAt(nativeHistory.lastIndex)
                val mergedContent = "${lastUser.content}\n\n$dynamicContextBlock"
                messages.addAll(nativeHistory)
                messages.add(ChatMessage(role = "user", content = mergedContent))
            } else {
                messages.addAll(nativeHistory)
                messages.add(ChatMessage(role = "user", content = dynamicContextBlock))
            }

            return messages
        }

        internal fun estimateTokens(text: String): Int {
            if (text.isEmpty()) return 0
            val words = text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.size
            return (words * 1.3).toInt()
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
        val dynamicBlock = buildDynamicContextBlock(
            notificationText = notification.originalText,
            candidates = candidates,
            activeOrders = activeOrders,
            personalChunks = personalChunks
        )
        val messages = buildOpenRouterMessages(
            systemPrompt = SYSTEM_PROMPT_FIRST_PASS,
            chatHistory = chatHistory,
            dynamicContextBlock = dynamicBlock
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
        val dynamicBlock = buildDynamicContextBlock(
            notificationText = queryText,
            candidates = chunks,
            activeOrders = activeOrders,
            personalChunks = personalChunks
        )
        val messages = buildOpenRouterMessages(
            systemPrompt = SYSTEM_PROMPT_FINAL_PASS,
            chatHistory = chatHistory,
            dynamicContextBlock = dynamicBlock
        )
        return executeFailoverChain(messages, maxTokens)
    }

    // ── Internal / Private Implementation ───────────────────────────────

    internal sealed interface CallResult {
        data class Success(val content: String) : CallResult
        data object RateLimited : CallResult
        data object Failed : CallResult
    }

    /**
     * Iterates through [FAILOVER_MODELS] sequentially. Each attempt is wrapped in
     * [withTimeout] at [MODEL_TIMEOUT_MS]. On 429 applies 1200ms backoff, and if 3 consecutive
     * models return 429 breaks early to HITL fallback.
     */
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

                    // Short backoff delay before advancing to next model
                    delay(1200L)
                }
                is CallResult.Failed -> {
                    consecutive429Count = 0
                }
            }
        }

        // All models exhausted
        Log.w(TAG, "All ${FAILOVER_MODELS.size} models failed. Returning HITL fallback JSON.")
        PersonaLogger.d(TAG, "All models failed — HITL fallback")
        return HITL_FALLBACK_JSON
    }

    /**
     * Executes an HTTP POST to the OpenRouter API for the given [model].
     * Attempts [primaryApiKey] first. If it encounters any error (5xx, 4xx like 429,
     * network error, or timeout), falls back to [fallbackApiKey] (OPENROUTER_API_KEY_V2)
     * before declaring failure on this model.
     */
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

        // Primary key failed (all errors: 4xx like 429, 5xx, timeout, or network exception)
        if (hasDistinctFallback) {
            val primarySuffix = if (effectivePrimary.length >= 6) effectivePrimary.takeLast(6) else "short"
            val fallbackSuffix = if (fallback.length >= 6) fallback.takeLast(6) else "short"
            Log.w(
                TAG,
                "Primary OpenRouter key (...$primarySuffix) failed on model $model with $primaryResult. " +
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

    /**
     * Executes a single HTTP POST attempt to OpenRouter using the designated [apiKey].
     * If HTTP 400 is returned, retries omitting the reasoning object.
     */
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

                // If HTTP 400 (e.g. openrouter/free, liquid/, recursive/ rejecting reasoning payload), retry without reasoning
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
                    response.status.value in 500..599 -> {
                        Log.w(TAG, "Server error (${response.status.value}) on model $model (key ...$keySuffix)")
                        CallResult.Failed
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
            CallResult.Failed
        } catch (e: Exception) {
            Log.e(TAG, "Network error on model $model (key ...$keySuffix)", e)
            CallResult.Failed
        }
    }
}
