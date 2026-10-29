package com.example.persona.features.knowledge.data.repository

import com.example.persona.core.database.dao.BusinessKnowledgeDao
import com.example.persona.core.database.entity.BusinessKnowledgeEntity
import com.example.persona.features.knowledge.domain.indexer.KnowledgeVectorIndexer
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KnowledgeRepositoryTest {

    private val businessKnowledgeDao = mockk<BusinessKnowledgeDao>(relaxed = true)
    private val vectorIndexer = mockk<KnowledgeVectorIndexer>(relaxed = true)

    private val repository = KnowledgeRepository(
        businessKnowledgeDao = businessKnowledgeDao,
        vectorIndexer = vectorIndexer
    )

    @Test
    fun `observeAllChunks delegates directly to dao`() = runTest {
        val chunks = listOf(
            BusinessKnowledgeEntity(
                chunkId = "chunk1",
                chunkText = "Policy 1",
                sourceType = "MANUAL_ENTRY",
                timestamp = 1000L
            )
        )
        every { businessKnowledgeDao.observeAllChunks() } returns flowOf(chunks)

        repository.observeAllChunks().collect { result ->
            assertEquals(1, result.size)
            assertEquals("chunk1", result[0].chunkId)
        }
    }

    @Test
    fun `addManualKnowledge returns false when text is blank`() = runTest {
        val result = repository.addManualKnowledge("   ", "MANUAL_ENTRY")
        assertFalse(result)
        coVerify(exactly = 0) { vectorIndexer.indexVector(any(), any()) }
        coVerify(exactly = 0) { businessKnowledgeDao.insertChunk(any()) }
    }

    @Test
    fun `addManualKnowledge returns false when vector indexing fails`() = runTest {
        coEvery { vectorIndexer.indexVector(any(), "Valid text") } returns false

        val result = repository.addManualKnowledge("Valid text", "MANUAL_ENTRY")
        assertFalse(result)
        coVerify(exactly = 0) { businessKnowledgeDao.insertChunk(any()) }
    }

    @Test
    fun `addManualKnowledge indexes vector and inserts chunk on success`() = runTest {
        coEvery { vectorIndexer.indexVector(any(), "Valid text") } returns true

        val result = repository.addManualKnowledge("Valid text", "MANUAL_ENTRY")
        assertTrue(result)
        coVerify(exactly = 1) { vectorIndexer.indexVector(any(), "Valid text") }
        coVerify(exactly = 1) {
            businessKnowledgeDao.insertChunk(match {
                it.chunkText == "Valid text" && it.sourceType == "MANUAL_ENTRY"
            })
        }
    }

    @Test
    fun `deleteKnowledge delegates to dao`() = runTest {
        repository.deleteKnowledge("target_chunk")
        coVerify(exactly = 1) { businessKnowledgeDao.deleteByChunkId("target_chunk") }
    }

    @Test
    fun `clearAllKnowledge clears dao chunks and vector index`() = runTest {
        repository.clearAllKnowledge()
        coVerify(exactly = 1) { businessKnowledgeDao.deleteAll() }
        coVerify(exactly = 1) { vectorIndexer.clearIndex() }
    }
}
