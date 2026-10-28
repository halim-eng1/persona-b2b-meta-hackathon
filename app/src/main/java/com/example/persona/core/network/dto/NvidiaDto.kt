package com.example.persona.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Request payload for NVIDIA NIM chat completions endpoint.
 * Follows standard OpenAI-compatible schema.
 */
@Serializable
data class NvidiaRequest(
    val model: String,
    val messages: List<OpenRouterChatMessage>,
    val temperature: Float = 0.45f,
    @SerialName("max_tokens")
    val maxTokens: Int = 1000
)

/**
 * Standard chat completion response from NVIDIA NIM.
 */
@Serializable
data class NvidiaResponse(
    val id: String? = null,
    val choices: List<NvidiaChoice>? = null,
    val error: NvidiaError? = null
)

@Serializable
data class NvidiaChoice(
    val index: Int? = null,
    val message: NvidiaMessageDto? = null
)

@Serializable
data class NvidiaMessageDto(
    val role: String? = null,
    val content: String? = null,
    @SerialName("reasoning_content")
    val reasoningContent: String? = null
)

@Serializable
data class NvidiaError(
    val message: String? = null,
    val type: String? = null,
    val code: String? = null
)
