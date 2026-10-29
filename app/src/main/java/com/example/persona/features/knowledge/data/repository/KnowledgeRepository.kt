package com.example.persona.features.knowledge.data.repository

import com.example.persona.core.database.dao.BusinessKnowledgeDao
import com.example.persona.core.database.dao.EmbeddingIndexDao
import com.example.persona.core.database.entity.BusinessKnowledgeEntity
import com.example.persona.features.chat_import.data.embedding.EmbeddingEngine
import com.example.persona.features.knowledge.data.indexer.OnnxKnowledgeVectorIndexer
import com.example.persona.features.knowledge.domain.indexer.KnowledgeVectorIndexer
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KnowledgeRepository @Inject constructor(
    private val businessKnowledgeDao: BusinessKnowledgeDao,
    private val vectorIndexer: KnowledgeVectorIndexer
) {

    /**
     * Backward-compatible secondary constructor for testing or manual instantiation
     * with raw embedding engine and DAO.
     */
    constructor(
        businessKnowledgeDao: BusinessKnowledgeDao,
        embeddingIndexDao: EmbeddingIndexDao,
        embeddingEngine: EmbeddingEngine
    ) : this(
        businessKnowledgeDao = businessKnowledgeDao,
        vectorIndexer = OnnxKnowledgeVectorIndexer(embeddingEngine, embeddingIndexDao)
    )

    fun observeAllChunks(): Flow<List<BusinessKnowledgeEntity>> {
        return businessKnowledgeDao.observeAllChunks()
    }

    suspend fun addManualKnowledge(text: String, sourceType: String): Boolean {
        if (text.isBlank()) return false
        val chunkId = UUID.randomUUID().toString()

        val indexed = vectorIndexer.indexVector(chunkId, text)
        if (!indexed) return false

        val knowledgeEntity = BusinessKnowledgeEntity(
            chunkId = chunkId,
            headerContext = null,
            chunkText = text,
            sourceType = sourceType,
            sourceFileName = null,
            timestamp = System.currentTimeMillis()
        )

        businessKnowledgeDao.insertChunk(knowledgeEntity)
        return true
    }

    suspend fun deleteKnowledge(chunkId: String) {
        businessKnowledgeDao.deleteByChunkId(chunkId)
    }

    suspend fun clearAllKnowledge() {
        businessKnowledgeDao.deleteAll()
        vectorIndexer.clearIndex()
    }
}
