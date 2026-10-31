package com.example.persona.features.auto_reply.domain.repository

import com.example.persona.core.database.entity.ChatHistoryEntity
import com.example.persona.core.database.entity.OrderEntity
import com.example.persona.core.database.entity.OrderStatusCount
import com.example.persona.features.auto_reply.domain.gatekeeper.InboundIntent
import com.example.persona.features.auto_reply.domain.model.ProcessedNotification
import com.example.persona.features.auto_reply.domain.model.RetrievalCandidate

interface ReplyGenerationRepository {
    /**
     * Generates an auto-reply candidate response based on the incoming notification context,
     * retrieved semantically similar historical message candidates, chronological chat history,
     * active orders, and personal vector chunks.
     *
     * Returns the raw LLM output string (structured JSON or plain text),
     * or null if all models in the failover chain failed.
     */
    suspend fun generateReply(
        notification: ProcessedNotification,
        candidates: List<RetrievalCandidate>,
        isOngoingConversation: Boolean = false,
        chatHistory: List<ChatHistoryEntity> = emptyList(),
        activeOrders: List<OrderEntity> = emptyList(),
        personalChunks: List<ChatHistoryEntity> = emptyList(),
        maxTokens: Int = 180,
        lastOrder: OrderEntity? = null,
        orderStatusCounts: List<OrderStatusCount> = emptyList(),
        inboundIntent: InboundIntent = InboundIntent.CATALOG_INQUIRY
    ): String?

    /**
     * Second-pass generation after a RE_QUERY loop.
     * Forces REPLY-only mode (no further RE_QUERY allowed) via a modified system prompt.
     *
     * @param queryText The customer's original query text.
     * @param chunks    Fresh Top-5 chunks from the refined vector search.
     * @param isOngoingConversation True if this is an ongoing session within the greeting suppression window.
     * @param chatHistory Chronological chat history turns.
     * @param activeOrders Active unfulfilled orders.
     * @param personalChunks Top personal chunks from vector sweep.
     * @param maxTokens The max tokens limit for the output response.
     * @param imageUrl Optional base64 or remote URL of an attached image for vision models.
     * @return Raw LLM output string, or null if all models failed.
     */
    suspend fun generateFinalReply(
        queryText: String,
        chunks: List<RetrievalCandidate>,
        isOngoingConversation: Boolean = false,
        chatHistory: List<ChatHistoryEntity> = emptyList(),
        activeOrders: List<OrderEntity> = emptyList(),
        personalChunks: List<ChatHistoryEntity> = emptyList(),
        maxTokens: Int = 350,
        imageUrl: String? = null,
        lastOrder: OrderEntity? = null,
        orderStatusCounts: List<OrderStatusCount> = emptyList()
    ): String?

    /**
     * Retrieves the execution telemetry (provider, model, duration, token usage)
     * of the most recent successful LLM call.
     */
    fun getLastExecutionTelemetry(): com.example.persona.features.auto_reply.domain.model.ExecutionTelemetry?
}
