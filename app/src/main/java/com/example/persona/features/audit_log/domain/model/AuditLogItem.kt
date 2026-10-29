package com.example.persona.features.audit_log.domain.model

import kotlinx.datetime.Instant

/**
 * Lightweight read model combining a reply log row with its contact's
 * display name, used exclusively for UI rendering.
 */
data class AuditLogItem(
    val id: Long,
    val contactName: String,
    val incomingMessage: String,
    val generatedReply: String,
    val similarityScore: Float,
    val wasAutoSent: Boolean,
    val timestamp: Instant,
    val sourceClass: String = "BUSINESS"
)
