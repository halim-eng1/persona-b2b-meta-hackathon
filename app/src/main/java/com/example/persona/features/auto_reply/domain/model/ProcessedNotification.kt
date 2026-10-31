package com.example.persona.features.auto_reply.domain.model

import com.example.persona.features.auto_reply.data.diagnostics.NotificationSourceClass
import com.example.persona.features.chat_import.domain.model.ConversationContext

data class ProcessedNotification(
    val notificationKey: String,
    val contactName: String,
    val originalText: String,
    val normalizedText: String,
    val context: ConversationContext,
    val postTime: Long,
    val sourceClass: NotificationSourceClass = NotificationSourceClass.UNKNOWN,
    val mediaFilePath: String? = null,
    val isVoiceTranscriptionFailed: Boolean = false,
    val resolvedPhoneNumber: String? = null
)
