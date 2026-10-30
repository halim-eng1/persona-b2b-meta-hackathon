package com.example.persona.features.knowledge.data.repository

import com.example.persona.core.database.dao.BusinessKnowledgeDao
import com.example.persona.core.database.dao.EmbeddingIndexDao
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
    private val embeddingIndexDao = mockk<EmbeddingIndexDao>(relaxed = true)

    private val repository = KnowledgeRepository(
        businessKnowledgeDao = businessKnowledgeDao,
        vectorIndexer = vectorIndexer,
        embeddingIndexDao = embeddingIndexDao
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
    fun `updateKnowledge updates vector and persists updated entity`() = runTest {
        coEvery { vectorIndexer.indexVector("chunk_123", "Updated Price 300 EGP") } returns true

        val result = repository.updateKnowledge(
            chunkId = "chunk_123",
            newText = "Updated Price 300 EGP",
            headerContext = "المنتجات والأسعار"
        )

        assertTrue(result)
        coVerify(exactly = 1) { vectorIndexer.indexVector("chunk_123", "Updated Price 300 EGP") }
        coVerify(exactly = 1) {
            businessKnowledgeDao.insertChunk(match {
                it.chunkId == "chunk_123" &&
                    it.chunkText == "Updated Price 300 EGP" &&
                    it.headerContext == "المنتجات والأسعار"
            })
        }
    }

    @Test
    fun `deleteKnowledge deletes from businessKnowledgeDao vectorIndexer and embeddingIndexDao`() = runTest {
        repository.deleteKnowledge("target_chunk")
        coVerify(exactly = 1) { businessKnowledgeDao.deleteByChunkId("target_chunk") }
        coVerify(exactly = 1) { vectorIndexer.deleteVector("target_chunk") }
        coVerify(exactly = 1) { embeddingIndexDao.deleteByChunkId("target_chunk") }
    }

    @Test
    fun `clearAllKnowledge clears dao chunks and vector index`() = runTest {
        repository.clearAllKnowledge()
        coVerify(exactly = 1) { businessKnowledgeDao.deleteAll() }
        coVerify(exactly = 1) { vectorIndexer.clearIndex() }
    }

    @Test
    fun `ensureReady delegates to vectorIndexer`() = runTest {
        coEvery { vectorIndexer.ensureReady() } returns true
        val ready = repository.ensureReady()
        assertTrue(ready)
        coVerify(exactly = 1) { vectorIndexer.ensureReady() }
    }

    @Test
    fun `ingestMarkdownDocument returns failure when text is blank`() = runTest {
        val result = repository.ingestMarkdownDocument("   ")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun `ingestMarkdownDocument parses chunks indexes vectors and inserts chunks`() = runTest {
        val markdown = """
            # Store Info
            Our store operates from Cairo.

            # Shipping
            Shipping costs 50 EGP across all provinces.
        """.trimIndent()

        coEvery { vectorIndexer.ensureReady() } returns true
        coEvery { vectorIndexer.indexVector(any(), any()) } returns true

        val result = repository.ingestMarkdownDocument(markdown)
        assertTrue(result.isSuccess)
        val count = result.getOrNull() ?: 0
        assertTrue(count >= 2)
        coVerify(atLeast = 2) { businessKnowledgeDao.insertChunk(any()) }
    }

    @Test
    fun `ingestMarkdownDocument returns failure when vector indexer fails or throws`() = runTest {
        coEvery { vectorIndexer.ensureReady() } throws IllegalStateException("ONNX model corrupted")

        val result = repository.ingestMarkdownDocument("# Title\nSome content")
        assertTrue(result.isFailure)
        assertEquals("ONNX model corrupted", result.exceptionOrNull()?.message)
    }
}
