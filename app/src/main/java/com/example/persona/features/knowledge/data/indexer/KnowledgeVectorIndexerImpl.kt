package com.example.persona.features.knowledge.data.indexer

import com.example.persona.core.database.dao.EmbeddingIndexDao
import com.example.persona.core.database.entity.EmbeddingIndexEntity
import com.example.persona.features.chat_import.data.embedding.EmbeddingEngine
import com.example.persona.features.knowledge.domain.indexer.KnowledgeVectorIndexer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Concrete [KnowledgeVectorIndexer] backed by the deterministic on-device
 * [EmbeddingEngine] (multilingual feature hashing). No native runtime is involved.
 */
@Singleton
class KnowledgeVectorIndexerImpl @Inject constructor(
    private val embeddingEngine: EmbeddingEngine,
    private val embeddingIndexDao: EmbeddingIndexDao
) : KnowledgeVectorIndexer {

    override suspend fun ensureReady(): Boolean = embeddingEngine.ensureReady()

    override suspend fun indexVector(chunkId: String, text: String): Boolean {
        val embedding = embeddingEngine.embed(text) ?: return false
        embeddingIndexDao.insertEmbedding(
            EmbeddingIndexEntity(
                chunkId = chunkId,
                vectorBlob = embedding,
                modelVersion = DEFAULT_MODEL_VERSION
            )
        )
        return true
    }

    override suspend fun deleteVector(chunkId: String) {
        embeddingIndexDao.deleteByChunkId(chunkId)
    }

    override suspend fun clearIndex() {
        embeddingIndexDao.deleteAll()
    }

    companion object {
        const val DEFAULT_MODEL_VERSION = EmbeddingIndexEntity.CURRENT_MODEL_VERSION
    }
}
