package com.example.persona.features.chat_import.data.embedding

import com.example.persona.core.di.AiInferenceDispatcher
import com.example.persona.core.logging.PersonaLogger
import com.example.persona.core.logging.PersonaLogTags
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

/**
 * On-device embedding engine backed exclusively by the deterministic
 * [FeatureHashingVectorizer].
 *
 * There is no model download, no native runtime and no cold start: the engine is
 * permanently ready the moment the process starts, which is what allows the RAG
 * pipeline to answer the first inbound message without a warm-up penalty.
 */
@Singleton
class EmbeddingEngine @Inject constructor(
    @AiInferenceDispatcher private val aiDispatcher: CoroutineDispatcher
) {
    companion object {
        const val EMBEDDING_DIM = FeatureHashingVectorizer.DIMENSION
        private const val TAG = "EmbeddingEngine"
    }

    /**
     * Deterministic feature hashing requires no model, so the engine is always ready.
     */
    fun isEngineReady(): Boolean = true

    /**
     * Always returns true immediately (zero cold start).
     */
    suspend fun ensureReady(): Boolean = true

    /**
     * Generates a 384-dimensional L2-normalized embedding for a single text using
     * deterministic multilingual feature hashing with query expansion & BM25 weighting.
     */
    suspend fun embed(text: String): FloatArray? = withContext(aiDispatcher) {
        if (text.isBlank()) return@withContext FloatArray(EMBEDDING_DIM)
        PersonaLogger.d(PersonaLogTags.EMBEDDING, "Embedding request: batchSize=1")
        val startTime = System.currentTimeMillis()
        val vector = FeatureHashingVectorizer.vectorize(text)
        val duration = System.currentTimeMillis() - startTime
        PersonaLogger.d(
            PersonaLogTags.EMBEDDING,
            "Embedding completed: batchSize=1, embeddingCount=1, durationMs=$duration"
        )
        vector
    }

    /**
     * Priority-aware embedding for auto-reply path.
     */
    suspend fun embedForAutoReply(text: String): FloatArray? = withContext(aiDispatcher) {
        if (text.isBlank()) return@withContext FloatArray(EMBEDDING_DIM)
        PersonaLogger.d(PersonaLogTags.EMBEDDING, "Embedding request (auto-reply): batchSize=1")
        val startTime = System.currentTimeMillis()
        val vector = FeatureHashingVectorizer.vectorize(text)
        val duration = System.currentTimeMillis() - startTime
        PersonaLogger.d(
            PersonaLogTags.EMBEDDING,
            "Embedding completed (auto-reply): durationMs=$duration"
        )
        vector
    }

    /**
     * Batch embedding for import pipeline.
     */
    suspend fun embedBatch(texts: List<String>): List<FloatArray?> = withContext(aiDispatcher) {
        PersonaLogger.d(PersonaLogTags.EMBEDDING, "Embedding batch request: batchSize=${texts.size}")
        val startTime = System.currentTimeMillis()
        val results = texts.map { text ->
            if (text.isBlank()) FloatArray(EMBEDDING_DIM)
            else FeatureHashingVectorizer.vectorize(text)
        }
        val duration = System.currentTimeMillis() - startTime
        PersonaLogger.d(
            PersonaLogTags.EMBEDDING,
            "Embedding batch completed: batchSize=${texts.size}, count=${results.size}, durationMs=$duration"
        )
        results
    }

    /**
     * Mean pooling: averages token embeddings weighted by attention mask.
     */
    internal fun meanPool(tokenEmbeddings: Array<FloatArray>, attentionMask: LongArray): FloatArray {
        val seqLen = attentionMask.size
        val result = FloatArray(EMBEDDING_DIM)
        var tokenCount = 0f

        for (i in 0 until seqLen) {
            if (attentionMask[i] == 1L) {
                val token = tokenEmbeddings[i]
                for (j in 0 until EMBEDDING_DIM) {
                    result[j] += token[j]
                }
                tokenCount++
            }
        }

        if (tokenCount > 0) {
            for (j in 0 until EMBEDDING_DIM) {
                result[j] /= tokenCount
            }
        }

        return result
    }

    /**
     * L2 normalization.
     */
    internal fun l2Normalize(vector: FloatArray): FloatArray {
        var norm = 0f
        for (v in vector) norm += v * v
        norm = sqrt(norm)
        if (norm > 0f) {
            for (i in vector.indices) vector[i] /= norm
        }
        return vector
    }
}
