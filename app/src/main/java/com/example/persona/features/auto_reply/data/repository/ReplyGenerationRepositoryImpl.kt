package com.example.persona.features.auto_reply.data.repository

import android.content.Context
import android.util.Log
import com.example.persona.BuildConfig
import com.example.persona.R
import com.example.persona.core.database.entity.ChatHistoryEntity
import com.example.persona.core.database.entity.OrderEntity
import com.example.persona.core.logging.PersonaLogger
import com.example.persona.core.logging.PersonaLogTags
import com.example.persona.core.network.dto.AgenticLlmResponse
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
import kotlinx.serialization.json.Json
import java.util.concurrent.TimeUnit
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

        const val CANONICAL_HITL_MESSAGE =
            "تمام يا فندم، ثواني وهيكون مع حضرتك ممثل خدمة العملاء فوراً لمساعدتك في أقرب وقت متاح! 🙏"

        const val HITL_FALLBACK_JSON =
            """{"action": "HITL", "refined_search_keyword": null, "order_action": null, "final_reply_text": "$CANONICAL_HITL_MESSAGE", "extracted_order": null}"""

        /**
         * Ordered failover chain — fast, conversational free-tier instruct models.
         */
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

        private val assistantJsonSerializer = Json {
            encodeDefaults = true
            explicitNulls = true
        }

        // ── System Prompts ────────────────────────────────────────────────

        /**
         * First-pass system prompt: allows REPLY, RE_QUERY, or HITL actions.
         */
        const val SYSTEM_PROMPT_FIRST_PASS = """You are the primary AI Customer Support Agent for an e-commerce business. Your task is to analyze the conversation, classify the user's intent, extract orders if present, and provide a helpful, human-like response.

RULES & INSTRUCTIONS:
1. OUTPUT FORMAT:
You MUST respond with a single valid JSON object. Absolutely NO markdown code blocks (no ```json), NO conversational filler before or after the JSON, and NO thinking/reasoning text.
Inside final_reply_text, NEVER use unescaped double quotes ("); use single quotes (') or brackets if quoting.
Output Schema:
{
  "action": "REPLY" | "RE_QUERY" | "HITL",
  "refined_search_keyword": "2-4 keywords in Modern Standard Arabic for catalog search, or null",
  "order_action": "CREATE" | "CANCEL" | "UPDATE" | null,
  "final_reply_text": "The message to send to the customer",
  "extracted_order": {
    "customer_name": "string or null",
    "delivery_address": "string or null",
    "order_details": "string or null",
    "custom_notes": "string or null"
  } or null
}

2. IDENTITY & ZERO HALLUCINATION:
- You represent the store's human-like sales & support team.
- Infer what the store sells strictly from <store_knowledge>. If <store_knowledge> has no product details in the current turn, NEVER invent a store name, NEVER invent products or inventory (e.g., blankets, clothing, electronics), and NEVER adopt customer slang vocatives ("يا ريس", "يا باشا", "يا فنان") as your own name or store name!
- NEVER call yourself an AI, language model, or virtual assistant.
- STRICT BUSINESS & CONTACT FACTUALITY:
  * NEVER invent external links, website URLs (e.g., NEVER make up '.com' or web addresses), social media handles, phone numbers, or physical showrooms.
  * If the customer asks for a website, link, or showroom and it is NOT explicitly provided in <store_knowledge>, reply clearly and politely that our orders are handled directly through this chat, or that this info is currently not available (e.g.: "الطلب متاح حالياً أونلاين من خلال رسائل الواتساب هنا يا فندم، ومفيش موقع إلكتروني مسجل حالياً عندي").
  * If any business detail, policy, or spec is missing from <store_knowledge>, state: "المعلومة دي مش متوفرة عندي حالياً يا فندم" and offer help with our available products.
- GREETINGS & SMALL-TALK:
  - If the customer greets you ("السلام عليكم", "هلا وغلا", "صباح الخير", "مساء الخير", "أهلاً"), ALWAYS return a warm, natural Egyptian Arabic greeting (e.g., "وعليكم السلام! نورتنا يا فندم 😊 أقدر أساعدك بإيه النهارده؟") — even if prior conversation messages exist!
  - Friendly check-ins & small-talk (e.g., "انت اخبارك اي", "عامل ايه", "كيفك", "شكراً"): ALWAYS return "action": "REPLY" with a warm, natural response (e.g., "الحمد لله كله تمام يا فندم! نورتنا 😊 أقدر أساعدك بإيه النهارده؟"). NEVER trigger HITL for friendly small-talk.

3. SCRIPT PURITY, CONCISENESS & TONE:
- Language & Script Purity: When the customer speaks Arabic, respond in 100% pure Egyptian Arabic dialect ("العامية المصرية"). NEVER use English words (e.g., do NOT say 'kindly', 'patience', 'shopping', 'order', 'assistant') and NEVER use Chinese/Asian characters. If the customer speaks English, reply in polite, clear English.
- Conciseness: Answer ONLY what the customer asked in 1–3 short, natural sentences. Never dump unrequested policies.
- Anti-Echo & Recommendations: Never echo the exact previous assistant reply verbatim. If the customer asks for a recommendation ("تقترح عليا اي"), highlight a specific bestseller/feature strictly from <store_knowledge> and explain why it suits them.
- Tone: Welcoming, courteous, and practical. Approved emojis: (😊, 🤔, 🧐, 🫡, 🙋🏻‍♂️, 🤌🏻, 👌🏻, 🤝, 🫶🏻, 👀, 🤠, 😎, 🤩, 🥰, 👍, ✨, 🌸, 🛒). Never invent store policies or products not in <store_knowledge>.

4. ACTIONS & WORKFLOW:
- "REPLY": Use for:
  a. ALL natural conversational turns — greetings ("السلام عليكم", "هلا"), friendly check-ins ("انت اخبارك اي"), thanks, farewells, clarifications, and witty off-topic pivots — even when <store_knowledge> is NO_MATCHING_CHUNKS_RETRIEVED.
  b. Store, product, policy, or order answers supported by <store_knowledge> or <customer_order_state>.
- "RE_QUERY": Use ONLY when the customer asks about store products, catalog items, recommendations ("بتبيعوا ايه", "اقترح عليا"), prices, shipping, or policies, AND <store_knowledge> is NO_MATCHING_CHUNKS_RETRIEVED or lacks the specific product/policy details. Provide 2–4 formal Arabic keywords in `refined_search_keyword` (e.g., "بيانات المنتج الأسعار" or "الشحن التوصيل المحافظات") to run a secondary catalog search.
- "HITL" (Human-In-The-Loop): Use ONLY when:
  a. The customer asks for a human representative.
  b. The customer expresses strong frustration, anger, or files a complaint.
  c. The customer uses abusive words, curses, or profanity (e.g., "تبا لك", insults) — respond with a polite boundary request and hand off.
  d. The customer asks a specific business/policy question that requires owner exception or cannot be answered from store data.
- In every HITL reply, final_reply_text must always end with the exact Arabic phrase (never translated, never modified):
"تمام يا فندم، ثواني وهيكون مع حضرتك ممثل خدمة العملاء فوراً لمساعدتك في أقرب وقت متاح! 🙏"

5. ORDER ACTIONS & EXTRACTION:
- ORDER ACTION (`order_action`): Set when the customer's message changes order state; null otherwise.
  - "CREATE": Set ONLY when the customer is actively placing a NEW order AND has provided BOTH: (1) specific product name(s) or quantities, AND (2) delivery address/location. If either is missing, keep order_action as null (or ask for the missing details) and do NOT set CREATE.
  - "CANCEL": the customer explicitly asks to stop or cancel their active order ("إلغاء", "كنسل", "بطل الطلب"). NEVER use it for general complaints, pricing questions, or anything other than termination of the order itself.
  - "UPDATE": the customer supplies missing details (address/phone/notes/product names) for the active order, or answers the duplicate-order verification question with edit-intent (e.g. "خلاص سجّل", "عدّل العنوان"). NEVER use it for bare acknowledgements ("تمام", "ماشي") after an unrelated answer.
  - null: for inquiries, small-talk, questions, or HITL hand-offs.
- EXTRACTION PURITY:
  * 'delivery_address' MUST be a genuine physical address provided by the customer (e.g. "مدينة نصر، الحي السابع، شارع الطيران", "الإسكندرية، سموحة").
  * NEVER extract assistant questions, prompts, or conversation statements as 'delivery_address' (e.g. NEVER extract "عايز تكمل بالعنوان؟" or "ممكن العنوان؟" or "تمام يا فندم"). If no physical address was explicitly provided by the customer, leave 'delivery_address' as null!
- STATE-CLAIM RULE: never state in final_reply_text that an order was registered, updated, or cancelled unless the matching order_action is set in THIS response. Without an order_action, describe orders exactly and only as they appear in <customer_order_state>.
- UPDATE CLOSING RULE: when order_action is "UPDATE", final_reply_text must CLOSE the loop with a short confirmation (e.g. "تم يا فندم، سجلت التفاصيل عندنا ✅") and NEVER ask the customer to confirm the same thing again.
- ORDER STATUS: If customer asks about their order status and an order exists in <customer_order_state>, reply strictly using this Egyptian Arabic structure (adapt to English if customer is speaking English):
  "الأوردر بتاعك حالته [Status]. طلبك عبارة عن [Order Details]، والملاحظات المسجلة هي [CustomNotes/MerchantNotes]، والتوصيل لـ [Address]."
  (Omit notes if None).
- Set `extracted_order` ONLY when the customer is actively submitting or confirming a purchase with actionable details; otherwise, it must be null.

6. GENERALIZED OUT-OF-BOUNDS MARKETING RECOVERY:
If the customer sends ANY benign off-topic, playful, or non-store message (e.g., games "نلعب", sports, coding, marriage/flattery, weather, riddles, "انت مين"), NEVER play along with off-topic games ("عايز تلعب إيه") and DO NOT trigger HITL. Instead, reply with light, respectful wit (matching customer's language: Egyptian Arabic or English) and smoothly pivot back to the store's shopping (without inventing fake products if catalog is not loaded in current turn). Illustrative examples adaptable to any store:
- Flattery/Marriage: "أنا ممكن أشوفلك أحلى عروسة بس اطلب من عندنا منتج شيك تتقدم بيه الأول 🤠🤝"
- Coding/Tech: "عينيا ليك هكتبلك أحلى كود بس اعمل أوردر الأول عشان الفانكشن تشتغل وتتكومبايل صح 👀""""

        /**
         * Second-pass system prompt: forces REPLY-only or HITL mode, RE_QUERY is forbidden.
         * Maintains full parity with FIRST_PASS on persona, conciseness, order extraction, and recovery.
         */
        const val SYSTEM_PROMPT_FINAL_PASS = """You are the primary AI Customer Support Agent for an e-commerce business. This is the FINAL generation pass.

RULES & INSTRUCTIONS:
1. OUTPUT FORMAT:
You MUST respond with a single valid JSON object. Absolutely NO markdown code blocks (no ```json), NO conversational filler before or after the JSON, and NO thinking/reasoning text.
Inside final_reply_text, NEVER use unescaped double quotes ("); use single quotes (') or brackets if quoting.
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

2. IDENTITY & ZERO HALLUCINATION:
- You represent the store's human-like sales & support team.
- Infer what the store sells strictly from <store_knowledge>. If <store_knowledge> has no product details in the current turn, NEVER invent a store name, NEVER invent products or inventory (e.g., blankets, clothing, electronics), and NEVER adopt customer slang vocatives ("يا ريس", "يا باشا", "يا فنان") as your own name or store name!
- NEVER call yourself an AI, language model, or virtual assistant.
- STRICT BUSINESS & CONTACT FACTUALITY:
  * NEVER invent external links, website URLs (e.g., NEVER make up '.com' or web addresses), social media handles, phone numbers, or physical showrooms.
  * If the customer asks for a website, link, or showroom and it is NOT explicitly provided in <store_knowledge>, reply clearly and politely that our orders are handled directly through this chat, or that this info is currently not available (e.g.: "الطلب متاح حالياً أونلاين من خلال رسائل الواتساب هنا يا فندم، ومفيش موقع إلكتروني مسجل حالياً عندي").
  * If any business detail, policy, or spec is missing from <store_knowledge>, state: "المعلومة دي مش متوفرة عندي حالياً يا فندم" and offer help with our available products.
- GREETINGS & SMALL-TALK:
  - If the customer greets you ("السلام عليكم", "هلا وغلا", "صباح الخير", "مساء الخير", "أهلاً"), ALWAYS return a warm, natural Egyptian Arabic greeting (e.g., "وعليكم السلام! نورتنا يا فندم 😊 أقدر أساعدك بإيه النهارده؟") — even if prior conversation messages exist!
  - Friendly check-ins & small-talk (e.g., "انت اخبارك اي", "عامل ايه", "كيفك", "شكراً"): ALWAYS return "action": "REPLY" with a warm, natural response (e.g., "الحمد لله كله تمام يا فندم! نورتنا 😊 أقدر أساعدك بإيه النهارده؟"). NEVER trigger HITL for friendly small-talk.

3. SCRIPT PURITY, CONCISENESS & TONE:
- Language & Script Purity: When the customer speaks Arabic, respond in 100% pure Egyptian Arabic dialect ("العامية المصرية"). NEVER use English words (e.g., do NOT say 'kindly', 'patience', 'shopping', 'order', 'assistant') and NEVER use Chinese/Asian characters. If the customer speaks English, reply in polite, clear English.
- Conciseness: Answer ONLY what the customer asked in 1–3 short, natural sentences. Never dump unrequested policies.
- Anti-Echo & Recommendations: Never echo the exact previous assistant reply verbatim. If the customer asks for a recommendation ("تقترح عليا اي"), highlight a specific bestseller/feature strictly from <store_knowledge> and explain why it suits them.
- Tone: Welcoming, courteous, and practical. Approved emojis: (😊, 🤔, 🧐, 🫡, 🙋🏻‍♂️, 🤌🏻, 👌🏻, 🤝, 🫶🏻, 👀, 🤠, 😎, 🤩, 🥰, 👍, ✨, 🌸, 🛒). Never invent store policies or products not in <store_knowledge>.

4. ACTIONS & WORKFLOW:
- Action MUST be strictly "REPLY" or "HITL" ("RE_QUERY" is completely forbidden in this pass, refined_search_keyword must be null).
- "REPLY": Use for:
  a. ALL natural conversational turns — greetings ("السلام عليكم", "هلا"), friendly check-ins ("انت اخبارك اي"), thanks, farewells, clarifications, and witty off-topic pivots — even when <store_knowledge> is NO_MATCHING_CHUNKS_RETRIEVED.
  b. Store, product, policy, or order answers supported by <store_knowledge> or <customer_order_state>.
- "HITL" (Human-In-The-Loop): Use ONLY when:
  a. The customer asks for a human representative.
  b. The customer expresses strong frustration, anger, or files a complaint.
  c. The customer uses abusive words, curses, or profanity (e.g., "تبا لك", insults) — respond with a polite boundary request and hand off.
  d. The customer asks a specific business/policy question that STILL cannot be answered from <store_knowledge> in this final pass (or requires owner exception).
- In every HITL reply, final_reply_text must always end with the exact Arabic phrase (never translated, never modified):
"تمام يا فندم، ثواني وهيكون مع حضرتك ممثل خدمة العملاء فوراً لمساعدتك في أقرب وقت متاح! 🙏"

5. ORDER ACTIONS & EXTRACTION:
- ORDER ACTION (`order_action`): Set when the customer's message changes order state; null otherwise.
  - "CREATE": Set ONLY when the customer is actively placing a NEW order AND has provided BOTH: (1) specific product name(s) or quantities, AND (2) delivery address/location. If either is missing, keep order_action as null (or ask for the missing details) and do NOT set CREATE.
  - "CANCEL": the customer explicitly asks to stop or cancel their active order ("إلغاء", "كنسل", "بطل الطلب"). NEVER use it for general complaints, pricing questions, or anything other than termination of the order itself.
  - "UPDATE": the customer supplies missing details (address/phone/notes/product names) for the active order, or answers the duplicate-order verification question with edit-intent (e.g. "خلاص سجّل", "عدّل العنوان"). NEVER use it for bare acknowledgements ("تمام", "ماشي") after an unrelated answer.
  - null: for inquiries, small-talk, questions, or HITL hand-offs.
- EXTRACTION PURITY:
  * 'delivery_address' MUST be a genuine physical address provided by the customer (e.g. "مدينة نصر، الحي السابع، شارع الطيران", "الإسكندرية، سموحة").
  * NEVER extract assistant questions, prompts, or conversation statements as 'delivery_address' (e.g. NEVER extract "عايز تكمل بالعنوان؟" or "ممكن العنوان؟" or "تمام يا فندم"). If no physical address was explicitly provided by the customer, leave 'delivery_address' as null!
- STATE-CLAIM RULE: never state in final_reply_text that an order was registered, updated, or cancelled unless the matching order_action is set in THIS response. Without an order_action, describe orders exactly and only as they appear in <customer_order_state>.
- UPDATE CLOSING RULE: when order_action is "UPDATE", final_reply_text must CLOSE the loop with a short confirmation (e.g. "تم يا فندم، سجلت التفاصيل عندنا ✅") and NEVER ask the customer to confirm the same thing again.
- ORDER STATUS: If customer asks about their order status and an order exists in <customer_order_state>, reply strictly using this Egyptian Arabic structure (adapt to English if customer is speaking English):
  "الأوردر بتاعك حالته [Status]. طلبك عبارة عن [Order Details]، والملاحظات المسجلة هي [CustomNotes/MerchantNotes]، والتوصيل لـ [Address]."
  (Omit notes if None).
- Set `extracted_order` ONLY when the customer is actively submitting or confirming a purchase with actionable details; otherwise, it must be null.

6. GENERALIZED OUT-OF-BOUNDS MARKETING RECOVERY:
If the customer sends ANY benign off-topic, playful, or non-store message (e.g., games "نلعب", sports, coding, marriage/flattery, weather, riddles, "انت مين"), NEVER play along with off-topic games ("عايز تلعب إيه") and DO NOT trigger HITL. Instead, reply with light, respectful wit (matching customer's language: Egyptian Arabic or English) and smoothly pivot back to the store's shopping (without inventing fake products if catalog is not loaded in current turn). Illustrative examples adaptable to any store:
- Flattery/Marriage: "أنا ممكن أشوفلك أحلى عروسة بس اطلب من عندنا منتج شيك تتقدم بيه الأول 🤠🤝"
- Coding/Tech: "عينيا ليك هكتبلك أحلى كود بس اعمل أوردر الأول عشان الفانكشن تشتغل وتتكومبايل صح 👀""""

        internal fun formatStoreKnowledge(
            candidates: List<RetrievalCandidate>,
            budgetTokens: Int = 2500
        ): String {
            var remainingBudget = budgetTokens
            val keptChunks = mutableListOf<String>()

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
                    keptChunks.add(cleanedChunk)
                    remainingBudget -= chunkTokens
                } else {
                    break
                }
            }

            return if (keptChunks.isEmpty()) {
                "NO_MATCHING_CHUNKS_RETRIEVED"
            } else {
                keptChunks.joinToString("\n---\n")
            }
        }

        internal fun formatCustomerOrderState(activeOrders: List<OrderEntity>): String {
            val firstOrder = activeOrders.firstOrNull() ?: return "Active Order: None"
            return buildString {
                append("Order Status: ${firstOrder.status}\n")
                append("Order Details: ${firstOrder.orderDetails ?: "None"}\n")
                append("Delivery Address: ${firstOrder.deliveryAddress ?: "None"}\n")
                append("Custom Notes: ${firstOrder.customNotes ?: "None"}\n")
                append("Merchant Notes: ${firstOrder.merchantNote ?: "None"}")
            }
        }

        internal fun buildSystemPromptWithContext(
            staticSystemPrompt: String,
            candidates: List<RetrievalCandidate>,
            activeOrders: List<OrderEntity>
        ): String {
            val knowledge = formatStoreKnowledge(candidates)
            val orderState = formatCustomerOrderState(activeOrders)

            return buildString {
                append(staticSystemPrompt.trim())
                append("\n\n<store_knowledge>\n")
                append(knowledge)
                append("\n</store_knowledge>\n\n<customer_order_state>\n")
                append(orderState)
                append("\n</customer_order_state>")
            }
        }

        internal fun formatHistoricalAssistantTurn(rawText: String): String {
            val trimmed = rawText.trim()
            val firstBrace = trimmed.indexOf('{')
            val lastBrace = trimmed.lastIndexOf('}')
            if (firstBrace != -1 && lastBrace != -1 && lastBrace >= firstBrace) {
                val candidateJson = trimmed.substring(firstBrace, lastBrace + 1).trim()
                try {
                    val parsed = assistantJsonSerializer.decodeFromString(AgenticLlmResponse.serializer(), candidateJson)
                    return assistantJsonSerializer.encodeToString(AgenticLlmResponse.serializer(), parsed)
                } catch (_: Exception) {
                    // Not a valid AgenticLlmResponse JSON, fall through to wrap
                }
            }
            val wrapped = AgenticLlmResponse(
                action = "REPLY",
                refinedSearchKeyword = null,
                orderAction = null,
                finalReplyText = trimmed,
                extractedOrder = null
            )
            return assistantJsonSerializer.encodeToString(AgenticLlmResponse.serializer(), wrapped)
        }

        internal fun isCanonicalHitlMessage(text: String): Boolean {
            val trimmed = text.trim()
            return trimmed.contains("ثواني وهيكون مع حضرتك ممثل خدمة العملاء") ||
                trimmed.contains("جاري تحويلك لممثلي خدمة العملاء") ||
                trimmed == CANONICAL_HITL_MESSAGE
        }

        /**
         * Deprecated helper retained for backward compatibility.
         */
        @Deprecated("Use buildSystemPromptWithContext instead")
        internal fun buildDynamicContextBlock(
            notificationText: String,
            candidates: List<RetrievalCandidate>,
            activeOrders: List<OrderEntity>,
            personalChunks: List<ChatHistoryEntity> = emptyList()
        ): String {
            var remainingBudget = 3000

            // 1. Current Inbound Message (Priority 1) - NEVER trimmed
            val inboundBlock = "\n\n=== CURRENT CUSTOMER MESSAGE ===\n$notificationText"
            remainingBudget -= estimateTokens(inboundBlock)

            // 2. Personal Client Order State (Priority 2)
            val orderStateBlock = formatCustomerOrderState(activeOrders)
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
            val storeKnowledge = formatStoreKnowledge(candidates, budgetTokens = remainingBudget)

            return buildString {
                append("=== STORE KNOWLEDGE ===\n")
                if (storeKnowledge == "NO_MATCHING_CHUNKS_RETRIEVED") {
                    append("None\n")
                } else {
                    append(storeKnowledge)
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
         * Cleans recent chat history for prompt assembly:
         * 1. Restricts history to messages within the last 2 hours.
         * 2. Takes at most the last 6 turns.
         * 3. Drops BOTH the synthetic HITL assistant message AND the user query that immediately preceded it.
         * 4. Converts to chronological order (oldest first).
         */
        internal fun filterAndPairChatHistory(
            chatHistory: List<ChatHistoryEntity>,
            nowMs: Long = System.currentTimeMillis()
        ): List<ChatHistoryEntity> {
            val twoHoursAgo = nowMs - TimeUnit.HOURS.toMillis(2)
            val maxTimestamp = chatHistory.maxOfOrNull { it.timestamp } ?: 0L
            // chatHistory is passed with most recent first (from DAO). Take turns within 2 hours, at most 6.
            val recentTurns = if (maxTimestamp > 100_000_000L) {
                chatHistory.filter { it.timestamp >= twoHoursAgo }.take(6)
            } else {
                chatHistory.take(6)
            }
            // Put in chronological order (oldest first)
            val chronological = recentTurns.reversed()

            val indicesToDrop = mutableSetOf<Int>()
            for (i in chronological.indices) {
                val turn = chronological[i]
                if (!turn.isFromUser && isCanonicalHitlMessage(turn.messageText)) {
                    indicesToDrop.add(i)
                    // Also drop the immediately preceding user turn that caused this failed HITL output
                    var prev = i - 1
                    while (prev >= 0 && indicesToDrop.contains(prev)) {
                        prev--
                    }
                    if (prev >= 0 && chronological[prev].isFromUser) {
                        indicesToDrop.add(prev)
                    }
                }
            }

            return chronological.filterIndexed { index, _ -> !indicesToDrop.contains(index) }
        }

        /**
         * Structures the messages list for provider prefix caching and multi-turn coherence:
         * - Message 0 (system): Static unified system prompt + dynamic <store_knowledge> and <customer_order_state>.
         * - Messages 1..N (user / assistant): Recent chat turns natively mapped, with assistant turns formatted as JSON strings.
         * - Final Message (user): Current raw customer message text.
         */
        internal fun buildOpenRouterMessages(
            systemPrompt: String,
            chatHistory: List<ChatHistoryEntity>,
            currentInboundText: String,
            nowMs: Long = System.currentTimeMillis()
        ): List<ChatMessage> {
            val messages = mutableListOf<ChatMessage>()
            messages.add(ChatMessage(role = "system", content = systemPrompt))

            val filteredTurns = filterAndPairChatHistory(chatHistory, nowMs)
            val nativeHistory = mutableListOf<ChatMessage>()

            for (turn in filteredTurns) {
                val role = if (turn.isFromUser && !turn.senderType.equals("MERCHANT_MANUAL", ignoreCase = true)) "user" else "assistant"
                val text = turn.messageText.trim()
                if (text.isEmpty()) continue

                val formattedContent = if (role == "assistant") {
                    formatHistoricalAssistantTurn(text)
                } else {
                    text
                }

                if (nativeHistory.isNotEmpty() && nativeHistory.last().role == role) {
                    val prev = nativeHistory.removeAt(nativeHistory.lastIndex)
                    if (role == "user") {
                        nativeHistory.add(ChatMessage(role = role, content = "${prev.content}\n$formattedContent"))
                    } else {
                        nativeHistory.add(ChatMessage(role = role, content = formattedContent))
                    }
                } else {
                    nativeHistory.add(ChatMessage(role = role, content = formattedContent))
                }
            }

            // Prune leading assistant turn if conversation starts with bot turn
            while (nativeHistory.isNotEmpty() && nativeHistory.first().role == "assistant") {
                nativeHistory.removeAt(0)
            }

            // If caller passed legacy dynamicContextBlock containing "=== CURRENT CUSTOMER MESSAGE ===\n", extract raw text
            val marker = "=== CURRENT CUSTOMER MESSAGE ===\n"
            val currentText = if (currentInboundText.contains(marker)) {
                currentInboundText.substringAfter(marker).trim()
            } else {
                currentInboundText.trim()
            }

            if (nativeHistory.isNotEmpty() && nativeHistory.last().role == "user") {
                val lastUser = nativeHistory.removeAt(nativeHistory.lastIndex)
                val merged = if (lastUser.content.isNotBlank()) {
                    "${lastUser.content}\n$currentText"
                } else {
                    currentText
                }
                messages.addAll(nativeHistory)
                messages.add(ChatMessage(role = "user", content = merged))
            } else {
                messages.addAll(nativeHistory)
                messages.add(ChatMessage(role = "user", content = currentText))
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
        val systemPromptWithContext = buildSystemPromptWithContext(
            staticSystemPrompt = SYSTEM_PROMPT_FIRST_PASS,
            candidates = candidates,
            activeOrders = activeOrders
        )
        val messages = buildOpenRouterMessages(
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
        val systemPromptWithContext = buildSystemPromptWithContext(
            staticSystemPrompt = SYSTEM_PROMPT_FINAL_PASS,
            candidates = chunks,
            activeOrders = activeOrders
        )
        val messages = buildOpenRouterMessages(
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
        data class AuthOrQuotaError(val code: Int) : CallResult // 401, 402, 403
        data class ServerError(val code: Int) : CallResult      // 500..599
        data object Timeout : CallResult
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
                else -> {
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
     * Attempts [primaryApiKey] first.
     * ONLY falls back to [fallbackApiKey] (OPENROUTER_API_KEY_V2) on key/quota-related HTTP errors (401, 402, 403, 429).
     * On server/model errors (500, 502, 503, 504), timeouts, or network exceptions, skips V2 to prevent wasting time.
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

        // ONLY fall back to OPENROUTER_API_KEY_V2 on key/quota-related HTTP errors (401, 402, 403, 429).
        // On server/model errors (500, 502, 503, 504), timeouts, or network exceptions,
        // do NOT retry the same model with V2 — return failure immediately so executeFailoverChain moves to the next model.
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
