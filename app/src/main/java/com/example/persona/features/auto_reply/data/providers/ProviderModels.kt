package com.example.persona.features.auto_reply.data.providers

import com.example.persona.core.network.dto.ChatMessage

sealed interface StreamChunk {
    data class Text(val content: String) : StreamChunk
    data class Reasoning(val thought: String) : StreamChunk
    data class Error(val throwable: Throwable, val isTransient: Boolean = false) : StreamChunk
    data class Completed(val totalTokens: Int? = null) : StreamChunk
}

data class ProviderRequest(
    val model: String,
    val systemPrompt: String,
    val userPrompt: String,
    val chatHistory: List<Pair<String, String>> = emptyList(),
    val base64Image: String? = null,
    val temperature: Float = 0.7f,
    val maxTokens: Int = 1024
) {
    fun toChatMessages(): List<ChatMessage> {
        val messages = mutableListOf<ChatMessage>()
        if (systemPrompt.isNotBlank()) {
            messages.add(ChatMessage(role = "system", content = systemPrompt))
        }
        for ((role, content) in chatHistory) {
            messages.add(ChatMessage(role = role, content = content))
        }
        messages.add(
            ChatMessage(
                role = "user",
                content = userPrompt,
                imageUrl = base64Image
            )
        )
        return messages
    }
}
