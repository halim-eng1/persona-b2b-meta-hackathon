package com.example.persona.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ── Request DTOs ──────────────────────────────────────────────────────────────

@Serializable
data class OpenRouterRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Float = 0.3f,
    @SerialName("max_tokens")
    val maxTokens: Int = 512,
    /** Suppresses chain-of-thought / reasoning token leakage on supporting models. */
    @SerialName("include_reasoning")
    val includeReasoning: Boolean = false,
    /**
     * Disables extended thinking entirely (`effort: "none"`) and asks OpenRouter
     * to exclude reasoning tokens from the response (`exclude: true`).
     */
    val reasoning: ReasoningConfig? = ReasoningConfig(effort = "none", exclude = true)
)

/** OpenRouter-recommended reasoning suppression payload.
 *
 * - `effort: "none"` — OpenAI-style value that disables extended thinking entirely.
 * - `exclude: true` — all models support it; strips reasoning tokens from the
 *   response so chain-of-thought cannot bleed into `content`.
 *
 * `max_tokens: 0` inside `reasoning` was removed deliberately: it can trigger
 * HTTP 400 validation errors with certain providers.
 */
@Serializable
data class ReasoningConfig(
    val effort: String? = "none",
    val exclude: Boolean? = true
)

@Serializable
data class ChatMessage(
    val role: String,   // "system" | "user"
    val content: String
)

// ── Response DTOs ─────────────────────────────────────────────────────────────

@Serializable
data class OpenRouterResponse(
    val choices: List<Choice>? = null,
    val error: OpenRouterError? = null
)

@Serializable
data class Choice(
    val message: ChoiceMessage? = null
)

@Serializable
data class ChoiceMessage(
    val content: String? = null
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
 *
 * [extractedOrder] is populated when the LLM detects an active order being placed
 * in the conversation. It is null for all non-order interactions.
 * The [ignoreUnknownKeys = true] parser config in [AutoReplyOrchestrator] ensures this
 * field addition breaks zero legacy endpoint outputs that pre-date this schema.
 */
@Serializable
data class AgenticLlmResponse(
    val action: String,
    @SerialName("refined_search_keyword")
    val refinedSearchKeyword: String? = null,
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
