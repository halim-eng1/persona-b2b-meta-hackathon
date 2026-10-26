package com.example.persona.features.chat_import.domain.model

import kotlinx.datetime.Instant

data class ParsedMessage(
    val senderName: String,
    val messageText: String,
    val timestamp: Instant,
    val isUserMessage: Boolean,
    val context: ConversationContext,
    val sourceFormat: ChatFormat,
    val normalizedText: String?,  // null if not Arabic
    val isGrouped: Boolean = false
)
