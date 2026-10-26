package com.example.persona.features.auto_reply.domain.model

import com.example.persona.features.chat_import.domain.model.ConversationContext

data class RetrievalCandidate(
    val messageText: String,
    val context: ConversationContext,
    val similarityScore: Float,
    val messageTimestamp: Long,
    val contactId: Long
)
