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

    companion object {
        private const val TAG = PersonaLogTags.LLM

        /**
         * Elevated network timeout (25 seconds) — prevents timeouts on slower free models under heavy context load.
         */
        private const val MODEL_TIMEOUT_MS = 25000L

        /**
         * Absolute ceiling for max tokens across failover.
         *
         * NOTE: this is a clamp, not the request value. The orchestrator passes
         * 600 (first pass) / 750 (final pass) per call, both of which are below
         * this ceiling — the constant only becomes relevant if a caller ever
         * asks for more than 8000.
         */
        private const val ABSOLUTE_FAILOVER_CEILING = 8000

        /**
         * Ordered failover chain — six fast, conversational free-tier instruct
         * models with a benchmark-proven record (see `benchmark_results.json`).
         *
         * Worst-case bound: 6 entries × 25s per-model timeout = ~150s (~2.5 min)
         * per pass, at most two passes per customer message (first + final).
         *
         * Excluded, and why (all confirmed by the benchmark):
         *  - cohere/north-mini-code:free — coding model, kept out of the chain
         *    because it may drift off the conversational JSON contract.
         *  - nvidia/nemotron-3-nano-omni-30b-a3b-reasoning:free — 22s latency and
         *    a factually-wrong reply in the benchmark.
         *  - liquid/lfm-2.5-2.6b:free & recursive/flint-v1.0:free — the request
         *    payload (`reasoning: {effort:"none"}`) yields HTTP 400 from both;
         *    the benchmark only succeeded with a param-omitted retry we don't
         *    implement. flint additionally emits no content below max_tokens
         *    ~2500 (calls send 600/750).
         *  - xingchen-agi/xing4.0-29b-a4b:free & google/gemma-4-26b-a4b-it:free —
         *    persistent upstream 429 (rate-limited) across spaced attempts.
         *  - nvidia/nemotron-3-super-120b-a12b:free — provider 404, deployment
         *    broken; never answers.
         *  - thinkingmachines/inkling:free — 403: only available on agentic
         *    harnesses, not usable via chat completions.
         */
        private val FAILOVER_MODELS = listOf(
            "nvidia/nemotron-3.5-super-vl:free",     //  1.35s | JSON OK | speed champion
            "nvidia/nemotron-3.5-lightning:free",    //  1.16s | JSON OK
            "openrouter/free",                       //  2.97s | JSON OK
            "dots-studio/dots-3-note-preview:free",  //  3.30s | JSON OK | valid until Dec 31, 2026
            "google/gemma-4-31b-it:free",            //  3.93s | JSON OK
            "nvidia/nemotron-3-ultra-550b-a55b:free" //  5.32s | JSON OK
        )

        // ── System Prompts ────────────────────────────────────────────────

        /**
         * First-pass system prompt: allows REPLY, RE_QUERY, or HITL actions.
         *
         * Written in English so instruction-following/free-tier models reliably
         * hold the strict JSON contract and suppress reasoning-token leakage.
         * Language Matching rule forces dynamic customer-language matching
         * (Egyptian Arabic dialect for Arabic input, English for English input).
         * All Arabic examples/templates (order status template, humor pivots,
         * HITL fallback strings) are intentionally kept in their original Arabic.
         */
        private const val SYSTEM_PROMPT_FIRST_PASS = """You are the primary AI Customer Support Agent for an e-commerce business. Your task is to analyze the conversation, classify the user's intent, extract orders if present, and provide a helpful, human-like response.

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

2. LANGUAGE & TONE:
- Language Matching: Respond in the exact language used by the customer. If the user speaks Arabic, reply in natural, friendly Egyptian Arabic dialect ("العامية المصرية"). If the user speaks English, reply in polite and clear English.
- Tone: Welcoming, courteous, and practical. Use only approved emojis: (😊, 🤔, 🧐, 🫡, 🙋🏻‍♂️, 🤌🏻, 👌🏻, 🤝, 🫶🏻, 👀, 🤠, 😎, 🤩, 🥰, 👍, ✨, 🌸, 🛒). Never invent store policies or products not in GLOBAL KNOWLEDGE.

3. ACTIONS & WORKFLOW:
- "REPLY": Use when the answer is fully supported by GLOBAL KNOWLEDGE or PERSONAL USER STATE.
- "RE_QUERY": Use when the query is relevant to the store products/services but the current knowledge context lacks exact details. Provide 2-4 keywords in `refined_search_keyword` in formal Arabic to run a secondary catalog search.
- "HITL" (Human-In-The-Loop): Use when:
  a. The customer expresses strong frustration or complains about service.
  b. The customer asks for a human representative.
  c. The customer uses abusive words or profanity (respond with a polite boundary request and hand off).
  d. The inquiry requires owner intervention or information not available in the store data.
- In every HITL reply, final_reply_text must always end with the exact Arabic phrase (never translated, never modified):
"تمام يا فندم، ثواني وهيكون مع حضرتك ممثل خدمة العملاء فوراً لمساعدتك في أقرب وقت متاح! 🙏"

- ORDER ACTION (`order_action`): Set when the customer's message changes order state; null otherwise.
  - "CREATE": the customer is actively placing a NEW order with items/quantities.
  - "CANCEL": the customer explicitly asks to stop or cancel their active order ("إلغاء", "كنسل", "بطل الطلب"). NEVER use it for general complaints, pricing questions, or anything other than termination of the order itself.
  - "UPDATE": the customer supplies missing details (address/phone/notes/product names) for, or confirms, the already-active order — e.g. answering the duplicate-order verification question, or short confirmations like "خلاص", "تمام", "ماشي".
  - null: for inquiries, small-talk, questions, or HITL hand-offs.
- STATE-CLAIM RULE: never state in final_reply_text that an order was registered, updated, or cancelled unless the matching order_action is set in THIS response. Without an order_action, describe orders exactly and only as they appear in PERSONAL USER STATE.
- UPDATE CLOSING RULE: when order_action is "UPDATE", final_reply_text must CLOSE the loop with a short confirmation (e.g. "تم يا فندم، سجلت التفاصيل عندنا ✅") and NEVER ask the customer to confirm the same thing again.

4. ORDER STATUS & EXTRACTION:
- If customer asks about their order status and an order exists in PERSONAL USER STATE, reply strictly using this Egyptian Arabic structure (adapt to English if customer is speaking English):
  "الأوردر بتاعك حالته [Status]. طلبك عبارة عن [Order Details]، والملاحظات المسجلة هي [CustomNotes/MerchantNotes]، والتوصيل لـ [Address]."
  (Omit notes if None).
- Set `extracted_order` ONLY when the customer is actively submitting or confirming a purchase with actionable details; otherwise, it must be null.

5. OUT-OF-BOUNDS MARKETING RECOVERY (HUMOR):
If the customer asks for coding/programming, flattery, or marriage, DO NOT trigger HITL. Reply with Egyptian humor and pivot playfully back to making a purchase:
- Flattery/Marriage example: "أنا ممكن أشوفلك أحلى عروسة بس اطلب من عندنا منتج شيك تتقدم بيه الأول 🤠🤝"
- Coding/Tech example: "عينيا ليك هكتبلك أحلى كود بس اعمل أوردر الأول عشان الفانكشن تشتغل وتتكومبايل صح 👀\""""

        /**
         * Second-pass system prompt: forces REPLY-only or HITL mode, RE_QUERY is forbidden.
         * This structurally caps the agentic loop at max 2 LLM calls.
         *
         * Written in English for reliable JSON-contract adherence on free-tier models.
         * Language Matching rule forces Egyptian Arabic for Arabic inquiries, English
         * for English ones. All Arabic HITL fallback strings remain untranslated.
         */
        private const val SYSTEM_PROMPT_FINAL_PASS = """You are the primary AI Customer Support Agent for an e-commerce business. This is the FINAL generation pass.

RULES & INSTRUCTIONS:
1. OUTPUT FORMAT:
You MUST respond with a single valid JSON object. Absolutely NO markdown backticks (no ```json) and NO text outside the JSON.
Output Schema:
{
  "action": "REPLY" | "HITL",
  "refined_search_keyword": null,
  "order_action": "CREATE" | "CANCEL" | "UPDATE" | null,
  "final_reply_text": "The message to send to the customer",
  "extracted_order": null
}

2. LANGUAGE & BEHAVIOR:
- Match customer's language (Egyptian Arabic for Arabic inquiries, English for English).
- Action MUST be strictly "REPLY" or "HITL" ("RE_QUERY" is completely forbidden in this pass).
- ORDER STATE (`order_action`): "CANCEL" when the customer explicitly asks to stop/cancel their active order; "UPDATE" when they complete or confirm the active order's details; null otherwise.
- Never state in final_reply_text that an order was registered, updated, or cancelled unless a matching order_action is set this turn — report the real PERSONAL USER STATE status instead.
- When order_action is "UPDATE", close the loop with a short confirmation and never ask the customer to confirm again.
- If the knowledge context is still insufficient, set action to "HITL" with an apologetic hand-off message in `final_reply_text`.
- In every HITL reply, final_reply_text must always end with the exact Arabic phrase (never translated, never modified):
"تمام يا فندم، ثواني وهيكون مع حضرتك ممثل خدمة العملاء فوراً لمساعدتك في أقرب وقت متاح! 🙏\""""
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
        val baseUserMessage = buildContextualUserMessage(
            notificationText = notification.originalText,
            candidates = candidates,
            chatHistory = chatHistory,
            activeOrders = activeOrders,
            personalChunks = personalChunks
        )
        val userMessage = if (isOngoingConversation) {
            baseUserMessage + "\n\n[System Rule: This is an ongoing conversation context window. Do NOT include any greetings or welcoming words like 'أهلاً بك', 'مرحباً', or 'منورنا'. Provide the answer directly and conversationally.]"
        } else {
            baseUserMessage
        }
        val messages = listOf(
            ChatMessage(role = "system", content = SYSTEM_PROMPT_FIRST_PASS),
            ChatMessage(role = "user", content = userMessage)
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
        val baseUserMessage = buildContextualUserMessage(
            notificationText = queryText,
            candidates = chunks,
            chatHistory = chatHistory,
            activeOrders = activeOrders,
            personalChunks = personalChunks
        )
        val userMessage = if (isOngoingConversation) {
            baseUserMessage + "\n\n[System Rule: This is an ongoing conversation context window. Do NOT include any greetings or welcoming words like 'أهلاً بك', 'مرحباً', or 'منورنا'. Provide the answer directly and conversationally.]"
        } else {
            baseUserMessage
        }
        val messages = listOf(
            ChatMessage(role = "system", content = SYSTEM_PROMPT_FINAL_PASS),
            ChatMessage(role = "user", content = userMessage)
        )
        return executeFailoverChain(messages, maxTokens)
    }

    // ── Private Implementation ────────────────────────────────────────────

    /**
     * Estimates tokens using standard word count * 1.3 heuristic.
     */
    private fun estimateTokens(text: String): Int {
        if (text.isEmpty()) return 0
        val words = text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.size
        return (words * 1.3).toInt()
    }

    /**
     * Builds the priority-ranked multi-source context block within a 3000 token limit.
     */
    private fun buildContextualUserMessage(
        notificationText: String,
        candidates: List<RetrievalCandidate>,
        chatHistory: List<ChatHistoryEntity>,
        activeOrders: List<OrderEntity>,
        personalChunks: List<ChatHistoryEntity>
    ): String {
        var remainingBudget = 3000

        // 1. Current Inbound Message (Priority 1) - NEVER trimmed
        val inboundBlock = "\n\nاستفسار العميل الحالي:\n$notificationText"
        remainingBudget -= estimateTokens(inboundBlock)

        // 2. Personal Client Order State (Priority 2)
        val firstOrder = activeOrders.firstOrNull()
        val orderStatusStr = firstOrder?.status ?: "No Active Order"
        val orderDetailsStr = firstOrder?.orderDetails ?: "None"
        val deliveryAddressStr = firstOrder?.deliveryAddress ?: "None"
        val customNotesStr = firstOrder?.customNotes ?: "None"
        val merchantNoteStr = firstOrder?.merchantNote ?: "None"

        val orderStateHeader = buildString {
            append("Order Status: $orderStatusStr\n")
            append("Order Details: $orderDetailsStr\n")
            append("Delivery Address: $deliveryAddressStr\n")
            append("Custom Notes: $customNotesStr\n")
            append("Merchant Notes: $merchantNoteStr")
        }
        remainingBudget -= estimateTokens(orderStateHeader)

        // 3. Chronological Chat History (Priority 3) - Up to last 6 turns, trimmed from oldest
        val turns = chatHistory.take(6).reversed().map { turn ->
            val sender = if (turn.isFromUser) "العميل" else "الرد التلقائي"
            "[$sender]: ${turn.messageText}"
        }
        val keptTurns = mutableListOf<String>()
        for (i in turns.indices.reversed()) {
            val turnText = turns[i]
            val turnTokens = estimateTokens(turnText) + 1
            if (remainingBudget >= turnTokens) {
                keptTurns.add(0, turnText)
                remainingBudget -= turnTokens
            } else {
                break
            }
        }

        // 4. Personal History Chunks (Priority 4) - Top 2 personal chunks, trimmed chunk-by-chunk
        val keptPChunks = mutableListOf<String>()
        val pChunks = personalChunks.take(2)
        for (chunk in pChunks) {
            val chunkText = "- ${chunk.messageText}"
            val chunkTokens = estimateTokens(chunkText) + 1
            if (remainingBudget >= chunkTokens) {
                keptPChunks.add(chunkText)
                remainingBudget -= chunkTokens
            } else {
                break
            }
        }

        // 5. Global Business Knowledge Chunks (Priority 5) - trimmed dynamically
        val keptGlobalChunks = mutableListOf<String>()
        for (candidate in candidates) {
            val header = candidate.headerContext
            val chunkText = if (!header.isNullOrBlank()) "$header\n${candidate.chunkText}" else candidate.chunkText
            val chunkTokens = estimateTokens(chunkText) + 2
            if (remainingBudget >= chunkTokens) {
                keptGlobalChunks.add(chunkText)
                remainingBudget -= chunkTokens
            } else {
                break
            }
        }

        return buildString {
            append("=== GLOBAL KNOWLEDGE ===\n")
            if (keptGlobalChunks.isEmpty()) {
                append("None\n")
            } else {
                append(keptGlobalChunks.joinToString("\n---\n"))
                append("\n")
            }

            append("\n=== CHAT HISTORY ===\n")
            if (keptTurns.isEmpty()) {
                append("None\n")
            } else {
                append(keptTurns.joinToString("\n"))
                append("\n")
            }

            append("\n=== PERSONAL USER STATE ===\n")
            append("Order Status: $orderStatusStr\n")
            append("Order Details: $orderDetailsStr\n")
            append("Delivery Address: $deliveryAddressStr\n")
            append("Custom Notes: $customNotesStr\n")
            append("Merchant Notes: $merchantNoteStr\n")
            append("Relevant Customer Records:\n")
            if (keptPChunks.isEmpty()) {
                append("None")
            } else {
                append(keptPChunks.joinToString("\n"))
            }
            append("\n\n")

            append("استفسار العميل الحالي:\n")
            append(notificationText)
        }
    }

    /**
     * Iterates through [FAILOVER_MODELS] sequentially. Each attempt is wrapped in
     * [withTimeout] at [MODEL_TIMEOUT_MS]. On timeout, 429, or 5xx, logs warning
     * and proceeds to the next model. Returns fallback JSON only if all models exhausted.
     */
    private suspend fun executeFailoverChain(messages: List<ChatMessage>, maxTokens: Int): String? {
        val targetMaxTokens = minOf(maxTokens, ABSOLUTE_FAILOVER_CEILING)

        for ((index, model) in FAILOVER_MODELS.withIndex()) {
            Log.d(TAG, "Attempting model ${index + 1}/${FAILOVER_MODELS.size}: $model with max_tokens=$targetMaxTokens")
            PersonaLogger.d(TAG, "Trying model: $model")

            val result = callOpenRouter(model, messages, targetMaxTokens)
            if (result != null) {
                Log.i(TAG, "Success on model $model")
                PersonaLogger.d(TAG, "LLM response received from $model")
                return result
            }
            // result == null means this model failed — loop continues to next
        }

        // All models exhausted
        Log.w(TAG, "All ${FAILOVER_MODELS.size} models failed. Returning HITL fallback JSON.")
        PersonaLogger.d(TAG, "All models failed — HITL fallback")
        return """{"action": "HITL", "refined_search_keyword": null, "final_reply_text": "تمام يا فندم، ثواني وهيكون مع حضرتك ممثل خدمة العملاء فوراً لمساعدتك في أقرب وقت متاح! 🙏"}"""
    }

    /**
     * Executes a single HTTP POST to the OpenRouter API for the given [model].
     *
     * @return The LLM-generated content string, or null on any failure
     *         (timeout, rate limit, server error, network error).
     */
    private suspend fun callOpenRouter(
        model: String,
        messages: List<ChatMessage>,
        maxTokens: Int
    ): String? = withContext(Dispatchers.IO) {
        try {
            withTimeout(MODEL_TIMEOUT_MS) {
                val response = httpClient.post(BuildConfig.OPENROUTER_BASE_URL) {
                    contentType(ContentType.Application.Json)
                    bearerAuth(BuildConfig.OPENROUTER_API_KEY)
                    setBody(
                        OpenRouterRequest(
                            model = model,
                            messages = messages,
                            maxTokens = maxTokens
                        )
                    )
                }

                when {
                    response.status.value == 429 -> {
                        Log.w(TAG, "Rate limited (429) on model $model")
                        null
                    }
                    response.status.value in 500..599 -> {
                        Log.w(TAG, "Server error (${response.status.value}) on model $model")
                        null
                    }
                    response.status.isSuccess() -> {
                        val body = response.body<OpenRouterResponse>()
                        val content = body.choices?.firstOrNull()?.message?.content
                        if (content.isNullOrBlank()) {
                            Log.w(TAG, "Empty content in successful response from $model")
                            null
                        } else {
                            content
                        }
                    }
                    else -> {
                        Log.w(TAG, "Unexpected HTTP ${response.status.value} from $model")
                        null
                    }
                }
            }
        } catch (e: TimeoutCancellationException) {
            Log.w(TAG, "Timeout (${MODEL_TIMEOUT_MS}ms) on model $model")
            null
        } catch (e: Exception) {
            Log.e(TAG, "Network error on model $model", e)
            null
        }
    }
}
