package com.example.persona.features.chat_import.data.embedding

import android.content.Context
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import kotlin.math.sqrt

@OptIn(ExperimentalCoroutinesApi::class)
class EmbeddingEngineTest {

    private val modelManager = mockk<OnnxModelManager>(relaxed = true)
    private val tokenizer = mockk<HuggingFaceTokenizer>(relaxed = true)
    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var embeddingEngine: EmbeddingEngine

    @Before
    fun setUp() {
        embeddingEngine = EmbeddingEngine(modelManager, tokenizer, mockk(relaxed = true), testDispatcher)
    }

    @Test
    fun meanPool_calculatesCorrectAverageOfTokens() {
        val attentionMask = LongArray(5) { if (it < 3) 1L else 0L }
        val tokenEmbeddings = arrayOf(
            FloatArray(384) { 1.0f },
            FloatArray(384) { 2.0f },
            FloatArray(384) { 3.0f },
            FloatArray(384) { 10.0f }, // masked out
            FloatArray(384) { 20.0f }  // masked out
        )

        val result = embeddingEngine.meanPool(tokenEmbeddings, attentionMask)
        assertEquals(384, result.size)

        // First 3 tokens are active: (1.0 + 2.0 + 3.0) / 3 = 2.0f
        for (i in result.indices) {
            assertEquals(2.0f, result[i], 0.0001f)
        }
    }

    @Test
    fun l2Normalize_normalizesVectorCorrectly() {
        val vector = FloatArray(384) { 2.0f }
        val result = embeddingEngine.l2Normalize(vector)

        // expected norm = sqrt(384 * 2^2) = sqrt(1536) = 39.1918358
        // expected element value = 2.0 / 39.1918358 = 0.051031
        val expected = 2.0f / sqrt(384 * 4.0f)
        for (i in result.indices) {
            assertEquals(expected, result[i], 0.0001f)
        }
    }

    @Test
    fun embed_delegatesToModelManagerWithoutTimeout() = runTest {
        val expectedEmbedding = FloatArray(384) { 0.1f }

        coEvery { modelManager.withInferenceLock<FloatArray?>(0L, any()) } returns expectedEmbedding

        val result = embeddingEngine.embed("Hello")

        assertNotNull(result)
        assertArrayEquals(expectedEmbedding, result, 0.0001f)
        coVerify(exactly = 1) { modelManager.withInferenceLock<FloatArray?>(0L, any()) }
    }

    @Test
    fun embedForAutoReply_delegatesToModelManagerWithTimeout() = runTest {
        val expectedEmbedding = FloatArray(384) { 0.2f }

        coEvery { modelManager.withInferenceLock<FloatArray?>(5000L, any()) } returns expectedEmbedding

        val result = embeddingEngine.embedForAutoReply("Reply")

        assertNotNull(result)
        assertArrayEquals(expectedEmbedding, result, 0.0001f)
        coVerify(exactly = 1) { modelManager.withInferenceLock<FloatArray?>(5000L, any()) }
    }

    @Test
    fun embedBatch_processesSequentiallyOncePerItem() = runTest {
        val expectedEmbedding1 = FloatArray(384) { 0.3f }
        val expectedEmbedding2 = FloatArray(384) { 0.4f }

        coEvery { modelManager.withInferenceLock<FloatArray?>(0L, any()) } returnsMany listOf(expectedEmbedding1, expectedEmbedding2)

        val results = embeddingEngine.embedBatch(listOf("Msg1", "Msg2"))

        assertEquals(2, results.size)
        assertArrayEquals(expectedEmbedding1, results[0], 0.0001f)
        assertArrayEquals(expectedEmbedding2, results[1], 0.0001f)

        coVerify(exactly = 2) { modelManager.withInferenceLock<FloatArray?>(0L, any()) }
    }
}

