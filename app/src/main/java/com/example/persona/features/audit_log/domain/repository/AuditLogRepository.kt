package com.example.persona.features.audit_log.domain.repository

import com.example.persona.features.audit_log.domain.model.AuditLogItem
import com.example.persona.features.auto_reply.domain.model.ExecutionTelemetry
import com.example.persona.features.auto_reply.domain.model.ProcessedNotification
import com.example.persona.features.auto_reply.domain.model.RetrievalCandidate
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant

interface AuditLogRepository {

    suspend fun logExecutedReply(
        notification: ProcessedNotification,
        candidate: RetrievalCandidate,
        replyText: String,
        telemetry: ExecutionTelemetry = ExecutionTelemetry()
    )

    suspend fun queuePendingReply(
        notification: ProcessedNotification,
        replyText: String
    )

    /** Observe the most recent [limit] executed-reply log entries. */
    fun observeLogs(limit: Int = 100): Flow<List<AuditLogItem>>

    /** Observe the total number of auto-replies that have been executed. */
    fun observeReplyCount(): Flow<Int>

    /** Observe the count of auto-replies sent since a specific instant. */
    fun observeAutoSentCountSince(since: Instant): Flow<Int>

    suspend fun clearPendingRepliesForContact(contactName: String)
}
