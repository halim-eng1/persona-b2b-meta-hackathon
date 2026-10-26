package com.example.persona.core.database.dao

import androidx.room.ColumnInfo
import kotlinx.datetime.Instant

/**
 * Lightweight projection returned by [ReplyLogDao.observeLogsWithContact].
 *
 * B2B migration: the old JOIN on `linked_contacts` has been removed.
 * `contactName` is now read directly from `reply_log.contact_display_name`.
 */
data class AuditLogProjection(
    @ColumnInfo(name = "id") val id: Long,
    @ColumnInfo(name = "contact_display_name") val contactName: String,
    @ColumnInfo(name = "incoming_message") val incomingMessage: String,
    @ColumnInfo(name = "generated_reply") val generatedReply: String,
    @ColumnInfo(name = "similarity_score") val similarityScore: Float,
    @ColumnInfo(name = "was_auto_sent") val wasAutoSent: Boolean,
    @ColumnInfo(name = "timestamp") val timestamp: Instant
)
