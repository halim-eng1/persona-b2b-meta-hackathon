package com.example.persona.core.database.dao

import kotlinx.datetime.Instant

/**
 * Room POJO that holds the result of the JOIN between [reply_log] and
 * [linked_contacts]. Column names must match the aliases in the @Query.
 */
data class AuditLogProjection(
    val id: Long,
    val contactName: String,
    val incomingMessage: String,
    val generatedReply: String,
    val similarityScore: Float,
    val wasAutoSent: Boolean,
    val timestamp: Instant
)
