package com.example.persona.features.auto_reply.domain.model

/**
 * A single knowledge chunk returned by the global vector sweep, scored for
 * relevance to the incoming customer query.
 *
 * B2B migration: replaced B2C contact-scoped fields (messageTimestamp, contactId,
 * triggerText, ConversationContext) with B2B knowledge fields (chunkId, headerContext).
 * Intent-mismatch penalty removed — all chunks are "answers", not typed messages.
 */
data class RetrievalCandidate(
    /** The UUID identifying this chunk in [business_knowledge]. */
    val chunkId: String,

    /**
     * Parent Markdown header(s) prepended to the chunk during import.
     * Null for manual entries or flat-text documents.
     */
    val headerContext: String?,

    /** The actual knowledge text that will be used as the reply. */
    val chunkText: String,

    /** Final similarity score after any post-processing penalties. */
    val similarityScore: Float,

    /**
     * Raw cosine similarity before penalties (e.g. repetition penalty).
     * Retained for audit logging and debugging.
     */
    val baseSimilarityScore: Float = similarityScore
) {
    val score: Float get() = similarityScore
}
