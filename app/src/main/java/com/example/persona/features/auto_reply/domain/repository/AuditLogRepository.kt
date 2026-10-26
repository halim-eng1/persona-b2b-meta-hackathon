package com.example.persona.features.auto_reply.domain.repository

import com.example.persona.features.auto_reply.domain.model.AuditLogItem
import com.example.persona.features.auto_reply.domain.model.ProcessedNotification
import com.example.persona.features.auto_reply.domain.model.RetrievalCandidate
import kotlinx.coroutines.flow.Flow

interface AuditLogRepository {

    suspend fun logExecutedReply(
        notification: ProcessedNotification,
        candidate: RetrievalCandidate,
        replyText: String
    )

    suspend fun queuePendingReply(
        notification: ProcessedNotification,
        replyText: String
    )

    /** Observe the most recent [limit] executed-reply log entries. */
    fun observeLogs(limit: Int = 100): Flow<List<AuditLogItem>>

    /** Observe the total number of auto-replies that have been executed. */
    fun observeReplyCount(): Flow<Int>
}
