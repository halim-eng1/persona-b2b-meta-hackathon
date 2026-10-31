package com.example.persona.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Request payload for Groq Cloud chat completions endpoint.
 *
 * Groq uses root-level reasoning parameters (e.g. `"reasoning_effort": "low"`)
 * rather than OpenRouter's nested `reasoning` object.
 */
@Serializable
data class GroqRequest(
    val model: String,
    val messages: List<OpenRouterChatMessage>,
    val temperature: Float = 0.45f,
    @SerialName("max_completion_tokens")
    val maxCompletionTokens: Int = 1000,
    @SerialName("reasoning_effort")
    val reasoningEffort: String? = "low",
    val stream: Boolean = false
)

/**
 * Standard chat completion response from Groq Cloud.
 */
@Serializable
data class GroqResponse(
    val id: String? = null,
    val model: String? = null,
    val choices: List<GroqChoice>? = null,
    val usage: CommonUsageDto? = null,
    val error: GroqError? = null
)

@Serializable
data class GroqChoice(
    val index: Int? = null,
    val message: GroqMessageDto? = null,
    val delta: GroqMessageDto? = null
)

@Serializable
data class GroqMessageDto(
    val role: String? = null,
    val content: String? = null,
    val reasoning: String? = null
)

@Serializable
data class GroqError(
    val message: String? = null,
    val type: String? = null,
    val code: String? = null
)
