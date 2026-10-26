package com.example.persona.features.auto_reply.data.repository

import com.example.persona.core.database.dao.ReplyLogDao
import com.example.persona.core.database.dao.ReplyQueueDao
import com.example.persona.core.database.entity.ReplyLogEntity
import com.example.persona.core.database.entity.ReplyQueueEntity
import com.example.persona.features.auto_reply.domain.model.ProcessedNotification
import com.example.persona.features.auto_reply.domain.model.RetrievalCandidate
import com.example.persona.features.auto_reply.domain.repository.AuditLogRepository
import kotlinx.datetime.Clock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuditLogRepositoryImpl @Inject constructor(
    private val replyLogDao: ReplyLogDao,
    private val replyQueueDao: ReplyQueueDao
) : AuditLogRepository {

    override suspend fun logExecutedReply(
        notification: ProcessedNotification,
        candidate: RetrievalCandidate,
        replyText: String
    ) {
        val entity = ReplyLogEntity(
            contactId = candidate.contactId,
            incomingMessage = notification.originalText,
            generatedReply = replyText,
            confidenceScore = candidate.similarityScore,
            similarityScore = candidate.similarityScore,
            contextConfidence = notification.context.confidence,
            wasAutoSent = true,
            wasApproved = null,
            timestamp = Clock.System.now(),
            retrievedMessageIds = emptyList()
        )
        replyLogDao.insert(entity)
    }

    override suspend fun queuePendingReply(
        notification: ProcessedNotification,
        replyText: String
    ) {
        val entity = ReplyQueueEntity(
            contactDisplayName = notification.contactName,
            replyText = replyText,
            status = "PENDING",
            createdAt = Clock.System.now(),
            attemptCount = 0,
            lastError = null,
            notificationKey = notification.notificationKey
        )
        replyQueueDao.insert(entity)
    }
}
