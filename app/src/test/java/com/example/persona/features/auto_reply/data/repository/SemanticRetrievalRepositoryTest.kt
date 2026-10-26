package com.example.persona.features.auto_reply.data.repository

import com.example.persona.core.database.dao.BusinessKnowledgeDao
import com.example.persona.core.database.dao.EmbeddingIndexDao
import com.example.persona.core.database.dao.ChunkVectorProjection
import com.example.persona.core.database.dao.ChunkTextProjection
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SemanticRetrievalRepositoryTest {

    private val embeddingIndexDao = mockk<EmbeddingIndexDao>()
    private val businessKnowledgeDao = mockk<BusinessKnowledgeDao>()
    private lateinit var repository: SemanticRetrievalRepository

    @Before
    fun setUp() {
        repository = SemanticRetrievalRepository(embeddingIndexDao, businessKnowledgeDao)
    }

    @Test
    fun retrieveCandidates_whenIndexIsEmpty_returnsEmptyPair(): Unit = runBlocking {
        coEvery { embeddingIndexDao.getAllVectors() } returns emptyList()

        val (candidates, bestScore) = repository.retrieveCandidates(
            queryEmbedding = floatArrayOf(1.0f, 0.0f),
            minimumSimilarityThreshold = 0.75f
        )

        assertTrue(candidates.isEmpty())
        assertEquals(0f, bestScore)
    }

    @Test
    fun retrieveCandidates_selectsAndFiltersBySimilarity(): Unit = runBlocking {
        // Arrange: 3 vectors:
        // - A: [1.0f, 0.0f] (similarity with [1.0f, 0.0f] is 1.0f, above 0.75f)
        // - B: [0.8f, 0.0f] (similarity is 0.8f, above 0.75f)
        // - C: [0.5f, 0.0f] (similarity is 0.5f, below 0.75f)
        val projections = listOf(
            ChunkVectorProjection("chunk_A", floatArrayOf(1.0f, 0.0f)),
            ChunkVectorProjection("chunk_B", floatArrayOf(0.8f, 0.0f)),
            ChunkVectorProjection("chunk_C", floatArrayOf(0.5f, 0.0f))
        )
        coEvery { embeddingIndexDao.getAllVectors() } returns projections

        coEvery { businessKnowledgeDao.getChunkTextsByIds(any()) } returns listOf(
            ChunkTextProjection("chunk_A", "Knowledge A", "Header A"),
            ChunkTextProjection("chunk_B", "Knowledge B", "Header B"),
            ChunkTextProjection("chunk_C", "Knowledge C", "Header C")
        )

        // Act
        val (candidates, bestScore) = repository.retrieveCandidates(
            queryEmbedding = floatArrayOf(1.0f, 0.0f),
            minimumSimilarityThreshold = 0.75f,
            queryText = "this is a long query"
        )

        // Assert
        assertEquals(2, candidates.size)
        assertEquals("chunk_A", candidates[0].chunkId)
        assertEquals(1.0f, candidates[0].similarityScore, 0.001f)
        assertEquals("chunk_B", candidates[1].chunkId)
        assertEquals(0.8f, candidates[1].similarityScore, 0.001f)
        assertEquals(1.0f, bestScore, 0.001f)
    }

    @Test
    fun retrieveCandidates_appliesRepetitionPenalty(): Unit = runBlocking {
        // Arrange
        val projections = listOf(
            ChunkVectorProjection("chunk_A", floatArrayOf(0.9f, 0.0f))
        )
        coEvery { embeddingIndexDao.getAllVectors() } returns projections
        coEvery { businessKnowledgeDao.getChunkTextsByIds(any()) } returns listOf(
            ChunkTextProjection("chunk_A", "Knowledge A", "Header A")
        )

        // Text hash of "Knowledge A" is passed as recent
        val textHash = "Knowledge A".hashCode()

        // Act
        val (candidates, _) = repository.retrieveCandidates(
            queryEmbedding = floatArrayOf(1.0f, 0.0f),
            minimumSimilarityThreshold = 0.60f,
            queryText = "this is a long query",
            recentReplyHashes = listOf(textHash)
        )

        // Assert: 0.9f - 0.20f penalty = 0.70f
        assertEquals(1, candidates.size)
        assertEquals(0.70f, candidates[0].similarityScore, 0.001f)
        assertEquals(0.90f, candidates[0].baseSimilarityScore, 0.001f)
    }

    @Test
    fun retrieveCandidates_negativeThresholdNeverAllowsGarbageChunks(): Unit = runBlocking {
        // Arrange: Vector with 0.25 similarity (garbage/noise)
        val projections = listOf(
            ChunkVectorProjection("garbage_chunk", floatArrayOf(0.25f, 0.0f))
        )
        coEvery { embeddingIndexDao.getAllVectors() } returns projections
        coEvery { businessKnowledgeDao.getChunkTextsByIds(any()) } returns listOf(
            ChunkTextProjection("garbage_chunk", "Garbage text", "Header")
        )

        // Act: Pass negative threshold (-1.0f)
        val (candidates, _) = repository.retrieveCandidates(
            queryEmbedding = floatArrayOf(1.0f, 0.0f),
            minimumSimilarityThreshold = -1.0f,
            queryText = "short"
        )

        // Assert: Garbage chunk at 0.25f must NOT pass because floor is enforced (>= 0.36f + dynamic bump)
        assertTrue(candidates.isEmpty())
    }
}
