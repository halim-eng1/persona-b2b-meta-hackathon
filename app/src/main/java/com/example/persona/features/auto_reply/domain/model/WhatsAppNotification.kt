package com.example.persona.features.auto_reply.domain.model

data class ExtraMessage(
    val text: String,
    val timestamp: Long,
    val isUser: Boolean
)

data class WhatsAppNotification(
    val title: String,
    val text: String,
    val postTime: Long,
    val notificationKey: String,
    val extraMessages: List<ExtraMessage> = emptyList()
)
