package com.example.persona.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CommonUsageDto(
    @SerialName("prompt_tokens") val promptTokens: Int? = null,
    @SerialName("completion_tokens") val completionTokens: Int? = null,
    @SerialName("total_tokens") val totalTokens: Int? = null,
    @SerialName("promptTokenCount") val promptTokenCount: Int? = null,
    @SerialName("candidatesTokenCount") val candidatesTokenCount: Int? = null,
    @SerialName("totalTokenCount") val totalTokenCount: Int? = null
) {
    val effectivePromptTokens: Int
        get() = promptTokens ?: promptTokenCount ?: 0

    val effectiveCompletionTokens: Int
        get() = completionTokens ?: candidatesTokenCount ?: 0

    val effectiveTotalTokens: Int
        get() = totalTokens ?: totalTokenCount ?: (effectivePromptTokens + effectiveCompletionTokens)
}

