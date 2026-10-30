package com.example.persona.features.chats.domain.usecase

import com.example.persona.core.database.dao.ConversationMetadataDao
import com.example.persona.features.auto_reply.domain.orchestrator.AutoReplyOrchestrator
import dagger.Lazy
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Clears the [needsHumanAttention] HITL escalation flag for a conversation
 * without requiring an active Android notification reply.
 * Also cancels any in-flight AI turn in [AutoReplyOrchestrator] to guard against
 * human-AI race condition collisions.
 */
@Singleton
class DismissHitlAttentionUseCase @Inject constructor(
    private val conversationMetadataDao: ConversationMetadataDao,
    private val autoReplyOrchestratorLazy: Lazy<AutoReplyOrchestrator>
) {
    // Secondary constructor for testing and backwards-compatibility
    constructor(
        conversationMetadataDao: ConversationMetadataDao,
        autoReplyOrchestrator: AutoReplyOrchestrator? = null
    ) : this(
        conversationMetadataDao = conversationMetadataDao,
        autoReplyOrchestratorLazy = Lazy { autoReplyOrchestrator ?: error("No orchestrator provided") }
    )

    suspend operator fun invoke(contactName: String, sourceClass: String) {
        try {
            autoReplyOrchestratorLazy.get()?.cancelInFlightTurn(contactName)
        } catch (_: Exception) {
            // Safe fallback when orchestrator is null or not provided in tests
        }
        conversationMetadataDao.setNeedsHumanAttention(contactName, sourceClass, false)
    }
}
