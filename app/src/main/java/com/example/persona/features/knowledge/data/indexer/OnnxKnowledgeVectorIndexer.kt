package com.example.persona.features.knowledge.data.indexer

import com.example.persona.core.database.dao.EmbeddingIndexDao
import com.example.persona.core.database.entity.EmbeddingIndexEntity
import com.example.persona.features.chat_import.data.embedding.EmbeddingEngine
import com.example.persona.features.chat_import.data.embedding.OnnxModelManager
import com.example.persona.features.knowledge.domain.indexer.KnowledgeVectorIndexer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Concrete [KnowledgeVectorIndexer] implementation backed by on-device ONNX runtime.
 */
@Singleton
class OnnxKnowledgeVectorIndexer @Inject constructor(
    private val embeddingEngine: EmbeddingEngine,
    private val embeddingIndexDao: EmbeddingIndexDao,
    private val modelManager: OnnxModelManager? = null
) : KnowledgeVectorIndexer {

    constructor(
        embeddingEngine: EmbeddingEngine,
        embeddingIndexDao: EmbeddingIndexDao
    ) : this(embeddingEngine, embeddingIndexDao, null)

    override suspend fun ensureReady(): Boolean {
        return modelManager?.ensureModelLoaded() ?: embeddingEngine.ensureReady()
    }

    override suspend fun indexVector(chunkId: String, text: String): Boolean {
        ensureReady()
        val embedding = embeddingEngine.embed(text) ?: return false
        val embeddingEntity = EmbeddingIndexEntity(
            chunkId = chunkId,
            vectorBlob = embedding,
            modelVersion = DEFAULT_MODEL_VERSION
        )
        embeddingIndexDao.insertEmbedding(embeddingEntity)
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
