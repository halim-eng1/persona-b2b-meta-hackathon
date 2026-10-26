package com.example.persona.features.auto_reply.domain.model

sealed class AutoReplyDecision {
    data class ReplyExecuted(
        val replyText: String,
        val candidate: RetrievalCandidate
    ) : AutoReplyDecision()

    data class QueuedForApproval(
        val replyText: String,
        val candidate: RetrievalCandidate
    ) : AutoReplyDecision()

    data object Ignored : AutoReplyDecision()
}
