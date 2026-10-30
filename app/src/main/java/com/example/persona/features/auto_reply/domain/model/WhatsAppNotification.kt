package com.example.persona.features.auto_reply.domain.model

import com.example.persona.features.auto_reply.data.diagnostics.NotificationSourceClass

data class ExtraMessage(
    val text: String,
    val timestamp: Long,
    val isUser: Boolean
)

/**
 * Domain model for an incoming WhatsApp notification.
 *
 * [sourceClass] is populated by [WhatsAppNotificationListener] using the
 * [NotificationSourceDiagnostics.classify] logic, so the orchestrator and
 * downstream components can make routing decisions without re-inspecting the SBN.
 *
 * [mediaFilePath] contains the absolute path to an extracted image or captured media file on disk,
 * or null if no media was extracted.
 */
data class WhatsAppNotification(
    val title: String,
    val text: String,
    val postTime: Long,
    val notificationKey: String,
    val extraMessages: List<ExtraMessage> = emptyList(),
    val sourceClass: NotificationSourceClass = NotificationSourceClass.UNKNOWN,
    val mediaFilePath: String? = null,
    val isVoiceTranscriptionFailed: Boolean = false,
    val isPrePersisted: Boolean = false,
    val burstMessageIds: List<Long> = emptyList()
)
