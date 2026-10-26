package com.example.persona.features.auto_reply.domain.model

data class WhatsAppNotification(
    val title: String,
    val text: String,
    val postTime: Long,
    val notificationKey: String
)
