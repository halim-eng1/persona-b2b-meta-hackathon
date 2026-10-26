package com.example.persona.features.auto_reply.data.repository

import com.example.persona.features.auto_reply.domain.model.ProcessedNotification
import com.example.persona.features.auto_reply.domain.model.RetrievalCandidate
import com.example.persona.features.auto_reply.domain.repository.ReplyGenerationRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReplyGenerationRepositoryImpl @Inject constructor() : ReplyGenerationRepository {
    
    override suspend fun generateReply(
        notification: ProcessedNotification,
        candidates: List<RetrievalCandidate>
    ): String? {
        // Strategy A: Return the best semantic match candidate's historical user reply text
        return candidates.firstOrNull()?.messageText
    }
}
