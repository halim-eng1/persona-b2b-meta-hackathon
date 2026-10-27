package com.example.persona.core.network.dto

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * Resilient serializer for [AgenticLlmResponse.refinedSearchKeyword].
 * Accepts either a flat JSON string, a JSON null, or a JSON array of strings
 * (joining array items into a single space-separated string).
 */
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
object LenientKeywordSerializer : KSerializer<String?> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("LenientKeyword", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: String?) {
        if (value == null) {
            encoder.encodeNull()
        } else {
            encoder.encodeString(value)
        }
    }

    override fun deserialize(decoder: Decoder): String? {
        val jsonDecoder = decoder as? JsonDecoder ?: return decoder.decodeString()
        return when (val element = jsonDecoder.decodeJsonElement()) {
            is JsonNull -> null
            is JsonPrimitive -> element.content
            is JsonArray -> {
                val joined = element.mapNotNull { item ->
                    (item as? JsonPrimitive)?.content?.trim()
                }.filter { it.isNotEmpty() }.joinToString(" ")
                joined.ifEmpty { null }
            }
            else -> element.toString()
        }
    }
}

// ── Request DTOs ──────────────────────────────────────────────────────────────

/**
 * Custom serializer for [OpenRouterChatMessage] that supports both standard
 * string content and OpenAI/Groq/OpenRouter multimodal array content (text + image_url).
 */
object OpenRouterChatMessageSerializer : KSerializer<OpenRouterChatMessage> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("OpenRouterChatMessage") {
            element<String>("role")
            element<JsonElement>("content")
        }

    override fun serialize(encoder: Encoder, value: OpenRouterChatMessage) {
        val jsonEncoder = encoder as? JsonEncoder
            ?: throw SerializationException("OpenRouterChatMessageSerializer only supports Json")

        val contentElement: JsonElement = if (value.imageUrl.isNullOrBlank()) {
            JsonPrimitive(value.content)
        } else {
            buildJsonArray {
                addJsonObject {
                    put("type", "text")
                    put("text", value.content)
                }
                addJsonObject {
                    put("type", "image_url")
                    putJsonObject("image_url") {
                        put("url", value.imageUrl)
                    }
                }
            }
        }

        val jsonObject = buildJsonObject {
            put("role", value.role)
            put("content", contentElement)
        }

        jsonEncoder.encodeJsonElement(jsonObject)
    }

    override fun deserialize(decoder: Decoder): OpenRouterChatMessage {
        val jsonDecoder = decoder as? JsonDecoder
            ?: return OpenRouterChatMessage(role = "user", content = decoder.decodeString())
        val jsonObject = jsonDecoder.decodeJsonElement().jsonObject

        val role = jsonObject["role"]?.jsonPrimitive?.content ?: "user"
        val contentElement = jsonObject["content"]

        var textContent = ""
        var extractedImageUrl: String? = null

        when (contentElement) {
            is JsonPrimitive -> {
                textContent = contentElement.content
            }
            is JsonArray -> {
                for (part in contentElement) {
                    if (part is JsonObject) {
                        val type = part["type"]?.jsonPrimitive?.content
                        if (type == "text") {
                            textContent = part["text"]?.jsonPrimitive?.content.orEmpty()
                        } else if (type == "image_url") {
                            val imgObj = part["image_url"]?.jsonObject
                            extractedImageUrl = imgObj?.get("url")?.jsonPrimitive?.content
                        }
                    }
                }
            }
            else -> {
                textContent = contentElement?.toString().orEmpty()
            }
        }

        return OpenRouterChatMessage(
            role = role,
            content = textContent,
            imageUrl = extractedImageUrl
        )
    }
}

/**
 * Dedicated request-only message schema containing strictly [role] and [content]
 * (and optional [imageUrl] for multimodal vision payloads).
 * Prevents any accidental serialization of `reasoning_content` on outgoing HTTP requests.
 */
@Serializable(with = OpenRouterChatMessageSerializer::class)
data class OpenRouterChatMessage(
    val role: String,   // "system" | "user" | "assistant"
    val content: String,
    val imageUrl: String? = null
)

typealias ChatMessage = OpenRouterChatMessage

@Serializable
data class OpenRouterRequest(
    val model: String,
    val messages: List<OpenRouterChatMessage>,
    val temperature: Float = 0.45f,
    @SerialName("max_tokens")
    val maxTokens: Int = 512,
    /** Suppresses chain-of-thought / reasoning token leakage on supporting models. */
    @SerialName("include_reasoning")
    val includeReasoning: Boolean = false,
    /**
     * Optional reasoning configuration. Only attached for benchmark-proven models.
     */
    val reasoning: ReasoningConfig? = null
)

/**
 * OpenRouter reasoning configuration for selective reasoning support.
 */
@Serializable
data class ReasoningConfig(
    val effort: String = "low"
)

// ── Response DTOs ─────────────────────────────────────────────────────────────

@Serializable
data class OpenRouterMessageDto(
    val role: String? = null,
    val content: String? = null,
    @SerialName("reasoning_content")
    val reasoningContent: String? = null
)

typealias ChoiceMessage = OpenRouterMessageDto

@Serializable
data class OpenRouterResponse(
    val choices: List<Choice>? = null,
    val error: OpenRouterError? = null
)

@Serializable
data class Choice(
    val message: OpenRouterMessageDto? = null
)

@Serializable
data class OpenRouterError(
    val message: String? = null,
    val code: Int? = null
)

// ── Agentic Response Schema ──────────────────────────────────────────────────

/**
 * Structured JSON output that the LLM is instructed to produce.
 *
 * - `action = "REPLY"` → [finalReplyText] contains the customer-facing response.
 * - `action = "RE_QUERY"` → [refinedSearchKeyword] contains optimized Arabic
 *   search terms for a second vector sweep.
 * - `action = "HITL"` → Handover to human representative (agentic loop halts).
 * - `orderAction` is the optional state-transition tool call (CREATE/CANCEL/UPDATE)
 *   executed by the orchestrator against the orders table.
 *
 * [extractedOrder] is populated when the LLM detects an active order being placed
 * in the conversation. It is null for all non-order interactions.
 * The [ignoreUnknownKeys = true] parser config in [AutoReplyOrchestrator] ensures this
 * field addition breaks zero legacy endpoint outputs that pre-date this schema.
 */
@Serializable
data class AgenticLlmResponse(
    val action: String,
    @Serializable(with = LenientKeywordSerializer::class)
    @SerialName("refined_search_keyword")
    val refinedSearchKeyword: String? = null,
    /**
     * Explicit agentic state-transition instruction emitted by the LLM:
     * "CREATE" (new order), "CANCEL" (stop the active order), "UPDATE"
     * (merge details into / confirm the active pending order), or null for
     * non-mutating turns (inquiries, small-talk, questions, HITL).
     *
     * Executed as a deterministic DB write in [com.example.persona.features.auto_reply.domain.orchestrator.AutoReplyOrchestrator]
     * so the bot's spoken claims (e.g. "تم الإلغاء") always match the persisted
     * state instead of relying on LLM prose.
     */
    @SerialName("order_action")
    val orderAction: String? = null,
    @SerialName("final_reply_text")
    val finalReplyText: String? = null,
    @SerialName("extracted_order")
    val extractedOrder: ExtractedOrder? = null
)

/**
 * Structured order payload extracted by the LLM when it detects a customer
 * actively placing an order in the conversation.
 *
 * All fields are nullable — the LLM may capture partial order details across
 * multiple conversation turns. Unstructured parameters that don't fit the
 * standard fields are dumped into [customNotes].
 *
 * This payload is mapped directly to [OrderEntity] fields during order ingestion
 * inside [AutoReplyOrchestrator] after the deduplication verification check.
 */
@Serializable
data class ExtractedOrder(
    @SerialName("customer_name")
    val customerName: String? = null,
    @SerialName("delivery_address")
    val deliveryAddress: String? = null,
    @SerialName("order_details")
    val orderDetails: String? = null,
    @SerialName("custom_notes")
    val customNotes: String? = null
)
