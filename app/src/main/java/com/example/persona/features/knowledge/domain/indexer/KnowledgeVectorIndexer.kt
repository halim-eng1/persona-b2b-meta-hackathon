package com.example.persona.features.knowledge.domain.indexer

/**
 * Domain boundary contract for generating and persisting vector embeddings.
 * Decouples the knowledge repository from specific inference engines (ONNX, cloud LLM, etc.).
 */
interface KnowledgeVectorIndexer {
    /**
     * Ensures embedding engine / model is loaded and ready for inference.
     */
    suspend fun ensureReady(): Boolean = true

    /**
     * Computes vector embedding for [text] and indexes it under [chunkId].
     * Returns true if vector generation and indexing succeeded.
     */
    suspend fun indexVector(chunkId: String, text: String): Boolean

    /**
     * Deletes vector indexed under [chunkId].
     */
    suspend fun deleteVector(chunkId: String) {}

    /**
     * Clears all vector indices.
     */
    suspend fun clearIndex()
}
