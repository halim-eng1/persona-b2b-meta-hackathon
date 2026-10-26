package com.example.persona.features.auto_reply.domain.model

import com.example.persona.features.chat_import.domain.model.ConversationContext

data class ProcessedNotification(
    val notificationKey: String,
    val contactName: String,
    val originalText: String,
    val normalizedText: String,
    val context: ConversationContext,
    val postTime: Long
)
