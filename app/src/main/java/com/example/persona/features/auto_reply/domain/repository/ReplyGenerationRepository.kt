package com.example.persona.features.auto_reply.domain.repository

import com.example.persona.features.auto_reply.domain.model.ProcessedNotification
import com.example.persona.features.auto_reply.domain.model.RetrievalCandidate

interface ReplyGenerationRepository {
    /**
     * Generates an auto-reply candidate response based on the incoming notification context
     * and a list of retrieved semantically similar historical message candidates.
     */
    suspend fun generateReply(
        notification: ProcessedNotification,
        candidates: List<RetrievalCandidate>
    ): String?
}
