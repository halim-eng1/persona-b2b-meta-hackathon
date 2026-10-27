package com.example.persona.features.knowledge.data.repository

import com.example.persona.core.database.dao.BusinessKnowledgeDao
import com.example.persona.core.database.dao.EmbeddingIndexDao
import com.example.persona.core.database.entity.BusinessKnowledgeEntity
import com.example.persona.core.database.entity.EmbeddingIndexEntity
import com.example.persona.features.chat_import.data.embedding.EmbeddingEngine
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KnowledgeRepository @Inject constructor(
    private val businessKnowledgeDao: BusinessKnowledgeDao,
    private val embeddingIndexDao: EmbeddingIndexDao,
    private val embeddingEngine: EmbeddingEngine
) {
    fun observeAllChunks(): Flow<List<BusinessKnowledgeEntity>> {
        return businessKnowledgeDao.observeAllChunks()
    }

    suspend fun addManualKnowledge(text: String, sourceType: String): Boolean {
        if (text.isBlank()) return false
        val chunkId = UUID.randomUUID().toString()
        val embedding = embeddingEngine.embed(text) ?: return false

        val knowledgeEntity = BusinessKnowledgeEntity(
            chunkId = chunkId,
            headerContext = null,
            chunkText = text,
            sourceType = sourceType,
            sourceFileName = null,
            timestamp = System.currentTimeMillis()
        )

        val embeddingEntity = EmbeddingIndexEntity(
            chunkId = chunkId,
            vectorBlob = embedding,
            modelVersion = "minilm-l12-v2-int8-1.0"
        )

        // Insert both in single transaction if possible (DAOs called sequentially here)
        businessKnowledgeDao.insertChunk(knowledgeEntity)
        embeddingIndexDao.insertEmbedding(embeddingEntity)
        return true
    }

    suspend fun deleteKnowledge(chunkId: String) {
        businessKnowledgeDao.deleteByChunkId(chunkId)
        // embeddingIndexDao.deleteByChunkId is handled by ON DELETE CASCADE in SQLite
    }

    suspend fun clearAllKnowledge() {
        businessKnowledgeDao.deleteAll()
        embeddingIndexDao.deleteAll()
    }
}
