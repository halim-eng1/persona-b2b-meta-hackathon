package com.example.persona.features.auto_reply.domain.repository

import com.example.persona.features.auto_reply.domain.model.ProcessedNotification
import com.example.persona.features.auto_reply.domain.model.RetrievalCandidate

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
}
