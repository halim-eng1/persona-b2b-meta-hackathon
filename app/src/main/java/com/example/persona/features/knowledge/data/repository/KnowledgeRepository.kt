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
    private val vectorIndexer: KnowledgeVectorIndexer,
    private val embeddingIndexDao: EmbeddingIndexDao? = null
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
        vectorIndexer = OnnxKnowledgeVectorIndexer(embeddingEngine, embeddingIndexDao),
        embeddingIndexDao = embeddingIndexDao
    )

    /**
     * Backward-compatible secondary constructor for testing with mock vectorIndexer.
     */
    constructor(
        businessKnowledgeDao: BusinessKnowledgeDao,
        vectorIndexer: KnowledgeVectorIndexer
    ) : this(
        businessKnowledgeDao = businessKnowledgeDao,
        vectorIndexer = vectorIndexer,
        embeddingIndexDao = null
    )

    suspend fun ensureReady(): Boolean = vectorIndexer.ensureReady()

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

    suspend fun updateKnowledge(
        chunkId: String,
        newText: String,
        sourceType: String = "MANUAL_ENTRY",
        sourceFileName: String? = null,
        headerContext: String? = null
    ): Boolean {
        if (newText.isBlank()) return false
        val indexed = vectorIndexer.indexVector(chunkId, newText)
        if (!indexed) return false

        val updatedEntity = BusinessKnowledgeEntity(
            chunkId = chunkId,
            headerContext = headerContext,
            chunkText = newText,
            sourceType = sourceType,
            sourceFileName = sourceFileName,
            timestamp = System.currentTimeMillis()
        )
        businessKnowledgeDao.insertChunk(updatedEntity)
        return true
    }

    /**
     * Deletes a knowledge chunk.
     * Deletes from business_knowledge (which cascades to embeddings_index when SQLite foreign keys are active)
     * and explicitly deletes from embeddings_index via vectorIndexer and DAO as defense-in-depth against zombie vectors.
     */
    suspend fun deleteKnowledge(chunkId: String) {
        businessKnowledgeDao.deleteByChunkId(chunkId)
        vectorIndexer.deleteVector(chunkId)
        embeddingIndexDao?.deleteByChunkId(chunkId)
    }

    suspend fun clearAllKnowledge() {
        businessKnowledgeDao.deleteAll()
        vectorIndexer.clearIndex()
    }
}
