package com.example.persona.features.chat_import.domain.model

/**
 * Normalized representation of a chat message parsed from an exported transcript (WhatsApp txt, iOS, Android).
 */
data class ParsedChatMessage(
    val timestampMs: Long,
    val senderName: String,
    val messageText: String,
    val isSystemMessage: Boolean = false,
    val hasMediaAttachment: Boolean = false,
    val rawTimestamp: String? = null
)
