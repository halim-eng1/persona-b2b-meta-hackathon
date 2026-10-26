package com.example.persona.features.chat_import.data.embedding

import android.util.Log
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.example.persona.core.di.AiInferenceDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EmbeddingEngine @Inject constructor(
    private val modelManager: OnnxModelManager,
    private val tokenizer: HuggingFaceTokenizer,
    @AiInferenceDispatcher private val aiDispatcher: CoroutineDispatcher
) {
    companion object {
        private const val EMBEDDING_DIM = 384
        private const val TAG = "EmbeddingEngine"
    }

    /**
     * Generates a 384-dimensional L2-normalized embedding for a single text.
     * Used by the batch import pipeline (no timeout — waits as long as needed).
     * Returns null if model is not loaded or inference fails.
     */
    suspend fun embed(text: String): FloatArray? = withContext(aiDispatcher) {
        modelManager.withInferenceLock { session ->
            runInference(session, text)
        }
    }

    /**
     * Hidden #6: Priority-aware embedding for auto-reply path.
     * Uses a timeout to avoid being starved by batch import.
     * Returns null on timeout — orchestrator falls back to suggestion.
     */
    suspend fun embedForAutoReply(text: String): FloatArray? = withContext(aiDispatcher) {
        modelManager.withInferenceLock(
            timeoutMs = OnnxModelManager.REALTIME_INFERENCE_TIMEOUT_MS
        ) { session ->
            runInference(session, text)
        }
    }

    /**
     * Batch embedding for import pipeline. Processes sequentially to
     * control memory. Each embedding is ~1.5KB (384 × 4 bytes).
     * Acquires and releases the Mutex PER ITEM to yield to auto-reply.
     */
    suspend fun embedBatch(texts: List<String>): List<FloatArray?> = withContext(aiDispatcher) {
        texts.map { text ->
            // Hidden #6: Release mutex between items so auto-reply
            // can acquire it within the 5s timeout window
            modelManager.withInferenceLock { session ->
                runInference(session, text)
            }
        }
    }

    /**
     * Core inference logic — MUST only be called while holding inferenceMutex.
     * Tokenizes, runs ONNX, mean-pools, and L2-normalizes.
     */
    @Suppress("UNCHECKED_CAST")
    private fun runInference(session: OrtSession, text: String): FloatArray? {
        return try {
            val tokenized = tokenizer.tokenize(text)
            val env = OrtEnvironment.getEnvironment()

            val inputIdsTensor = OnnxTensor.createTensor(
                env, arrayOf(tokenized.inputIds)
            )
            val attentionMaskTensor = OnnxTensor.createTensor(
                env, arrayOf(tokenized.attentionMask)
            )
            val tokenTypeIdsTensor = OnnxTensor.createTensor(
                env, arrayOf(tokenized.tokenTypeIds)
            )

            val inputs = mapOf(
                "input_ids" to inputIdsTensor,
                "attention_mask" to attentionMaskTensor,
                "token_type_ids" to tokenTypeIdsTensor
            )

            val output = session.run(inputs)
            
            // Output shape of MiniLM last_hidden_state is [1, seqLen, 384]
            // Standard casting in ONNX Runtime Java API yields Array<Array<FloatArray>>
            val rawOutput = (output[0].value as Array<Array<FloatArray>>)[0]

            // Mean pooling with attention mask
            val pooled = meanPool(rawOutput, tokenized.attentionMask)

            // Refactor #8: L2-normalize at storage time
            l2Normalize(pooled)
        } catch (e: Exception) {
            Log.e(TAG, "Inference failed", e)
            null
        }
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
     * Refactor #8: L2 normalization.
     * Pre-normalizing all vectors means cosine_similarity(a, b) = dot(a, b),
     * avoiding the expensive sqrt/division at retrieval time.
     */
    internal fun l2Normalize(vector: FloatArray): FloatArray {
        var norm = 0f
        for (v in vector) norm += v * v
        norm = kotlin.math.sqrt(norm)
        if (norm > 0f) {
            for (i in vector.indices) vector[i] /= norm
        }
        return vector
    }
}
