package com.example.persona.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Request payload for QwenCloud / DashScope chat completions endpoint.
 * Follows OpenAI-compatible schema with optional streaming support.
 */
@Serializable
data class QwenChatRequest(
    val model: String,
    val messages: List<OpenRouterChatMessage>,
    val temperature: Float = 0.7f,
    @SerialName("max_tokens")
    val maxTokens: Int = 1000,
    val stream: Boolean = false
)

/**
 * Standard chat completion response from QwenCloud / DashScope.
 */
@Serializable
data class QwenChatResponse(
    val id: String? = null,
    val choices: List<QwenChoice>? = null,
    val error: QwenError? = null
)

@Serializable
data class QwenChoice(
    val index: Int? = null,
    val message: QwenMessageDto? = null,
    val delta: QwenMessageDto? = null
)

@Serializable
data class QwenMessageDto(
    val role: String? = null,
    val content: String? = null,
    @SerialName("reasoning_content")
    val reasoningContent: String? = null
)

@Serializable
data class QwenError(
    val message: String? = null,
    val type: String? = null,
    val code: String? = null
)
