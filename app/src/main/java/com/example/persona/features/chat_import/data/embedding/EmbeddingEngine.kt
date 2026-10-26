package com.example.persona.features.chat_import.data.embedding

import android.util.Log
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import com.example.persona.core.di.AiInferenceDispatcher
import com.example.persona.core.logging.PersonaLogger
import com.example.persona.core.logging.PersonaLogTags
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EmbeddingEngine @Inject constructor(
    private val modelManager: OnnxModelManager,
    private val tokenizer: HuggingFaceTokenizer,
    @ApplicationContext private val context: Context,
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
        PersonaLogger.d(PersonaLogTags.EMBEDDING, "Embedding request: batchSize=1")
        val startTime = System.currentTimeMillis()
        val result = modelManager.withInferenceLock { session ->
            runInference(session, text)
        }
        val duration = System.currentTimeMillis() - startTime
        val count = if (result != null) 1 else 0
        PersonaLogger.d(
            PersonaLogTags.EMBEDDING,
            "Embedding completed: batchSize=1, embeddingCount=$count, durationMs=$duration"
        )
        result
    }

    /**
     * Hidden #6: Priority-aware embedding for auto-reply path.
     * Uses a timeout to avoid being starved by batch import.
     * Returns null on timeout — orchestrator falls back to suggestion.
     */
    suspend fun embedForAutoReply(text: String): FloatArray? = withContext(aiDispatcher) {
        PersonaLogger.d(PersonaLogTags.EMBEDDING, "Embedding request: batchSize=1")
        val startTime = System.currentTimeMillis()

        // Force Application ClassLoader on the current thread to resolve JNI libs in Binder context
        val appClassLoader = context.applicationContext.classLoader
        Thread.currentThread().contextClassLoader = appClassLoader

        val result = modelManager.withInferenceLock(
            timeoutMs = OnnxModelManager.REALTIME_INFERENCE_TIMEOUT_MS
        ) { session ->
            runInference(session, text)
        }
        val duration = System.currentTimeMillis() - startTime
        val count = if (result != null) 1 else 0
        PersonaLogger.d(
            PersonaLogTags.EMBEDDING,
            "Embedding completed: batchSize=1, embeddingCount=$count, durationMs=$duration"
        )
        result
    }

    /**
     * Batch embedding for import pipeline. Processes in chunks of 32 to
     * control memory and allow concurrent auto-replies to yield.
     * Each embedding is ~1.5KB (384 × 4 bytes).
     * Acquires and releases the Mutex PER CHUNK.
     */
    suspend fun embedBatch(texts: List<String>): List<FloatArray?> = withContext(aiDispatcher) {
        PersonaLogger.d(PersonaLogTags.EMBEDDING, "Embedding request: batchSize=${texts.size}")
        val startTime = System.currentTimeMillis()
        
        val chunkSize = 32
        val chunks = texts.chunked(chunkSize)
        val results = mutableListOf<FloatArray?>()

        for (chunk in chunks) {
            val chunkResults = try {
                runInferenceBatch(chunk)
            } catch (e: Exception) {
                null
            }

            if (chunkResults != null) {
                results.addAll(chunkResults)
            } else {
                // FALLBACK: Chunk failed. Process sequentially, yielding lock PER ITEM
                Log.w(TAG, "Chunk failed, falling back to sequential inference...")
                for (text in chunk) {
                    val singleResult = try {
                        modelManager.withInferenceLock { session ->
                            runInference(session, text)
                        }
                    } catch (e: Exception) {
                        null
                    }
                    results.add(singleResult)
                }
            }
        }

        val duration = System.currentTimeMillis() - startTime
        val count = results.count { it != null }
        PersonaLogger.d(
            PersonaLogTags.EMBEDDING,
            "Embedding completed: batchSize=${texts.size}, embeddingCount=$count, durationMs=$duration"
        )
        results
    }

    /**
     * Core inference logic — MUST only be called while holding inferenceMutex.
     * Tokenizes, runs ONNX, mean-pools, and L2-normalizes.
     */
    @Suppress("UNCHECKED_CAST")
    private fun runInference(session: OrtSession, text: String): FloatArray? {
        val tokenized = tokenizer.tokenize(text)
        val env = OrtEnvironment.getEnvironment()

        var inputIdsTensor: OnnxTensor? = null
        var attentionMaskTensor: OnnxTensor? = null
        var tokenTypeIdsTensor: OnnxTensor? = null
        var output: OrtSession.Result? = null

        return try {
            inputIdsTensor = OnnxTensor.createTensor(env, arrayOf(tokenized.inputIds))
            attentionMaskTensor = OnnxTensor.createTensor(env, arrayOf(tokenized.attentionMask))
            tokenTypeIdsTensor = OnnxTensor.createTensor(env, arrayOf(tokenized.tokenTypeIds))

            val inputs = mapOf(
                "input_ids" to inputIdsTensor,
                "attention_mask" to attentionMaskTensor,
                "token_type_ids" to tokenTypeIdsTensor
            )

            output = session.run(inputs)
            val rawOutput = (output[0].value as Array<Array<FloatArray>>)[0]
            val pooled = meanPool(rawOutput, tokenized.attentionMask)
            l2Normalize(pooled)
        } catch (e: Throwable) {
            Log.e(TAG, "Inference failed -> Exception: ${e.javaClass.simpleName} | Message: ${e.message}")
            null
        } finally {
            inputIdsTensor?.close()
            attentionMaskTensor?.close()
            tokenTypeIdsTensor?.close()
            output?.close()
        }
    }

    /**
     * Runs true batch inference on a list of texts.
     * Input sequences are padded to exactly 128 tokens, and 2D arrays are shaped explicitly.
     */
    @Suppress("UNCHECKED_CAST")
    private suspend fun runInferenceBatch(texts: List<String>): List<FloatArray?> {
        if (texts.isEmpty()) return emptyList()

        val tokenizedBatch = tokenizer.batchTokenize(texts)
        val size = texts.size

        // Build flat arrays: shape [size, 128]
        val inputIds = LongArray(size * 128)
        val attentionMask = LongArray(size * 128)
        val tokenTypeIds = LongArray(size * 128)

        for (i in 0 until size) {
            val tokenized = tokenizedBatch[i]
            for (j in 0 until 128) {
                val idx = i * 128 + j
                inputIds[idx] = if (j < tokenized.inputIds.size) tokenized.inputIds[j] else 0L
                attentionMask[idx] = if (j < tokenized.attentionMask.size) tokenized.attentionMask[j] else 0L
                tokenTypeIds[idx] = if (j < tokenized.tokenTypeIds.size) tokenized.tokenTypeIds[j] else 0L
            }
        }

        return try {
            val rawOutputCopy = modelManager.withInferenceLock { session ->
                val env = OrtEnvironment.getEnvironment()
                var inputIdsTensor: OnnxTensor? = null
                var attentionMaskTensor: OnnxTensor? = null
                var tokenTypeIdsTensor: OnnxTensor? = null
                var output: OrtSession.Result? = null

                try {
                    inputIdsTensor = OnnxTensor.createTensor(env, java.nio.LongBuffer.wrap(inputIds), longArrayOf(size.toLong(), 128L))
                    attentionMaskTensor = OnnxTensor.createTensor(env, java.nio.LongBuffer.wrap(attentionMask), longArrayOf(size.toLong(), 128L))
                    tokenTypeIdsTensor = OnnxTensor.createTensor(env, java.nio.LongBuffer.wrap(tokenTypeIds), longArrayOf(size.toLong(), 128L))

                    val inputs = mapOf(
                        "input_ids" to inputIdsTensor,
                        "attention_mask" to attentionMaskTensor,
                        "token_type_ids" to tokenTypeIdsTensor
                    )

                    output = session.run(inputs)
                    val rawOutput = output[0].value as Array<Array<FloatArray>>
                    Array(size) { i ->
                        Array(rawOutput[i].size) { j ->
                            rawOutput[i][j].clone()
                        }
                    }
                } finally {
                    inputIdsTensor?.close()
                    attentionMaskTensor?.close()
                    tokenTypeIdsTensor?.close()
                    output?.close()
                }
            } ?: throw IllegalStateException("ONNX session is unavailable")

            (0 until size).map { i ->
                val pooled = meanPool(rawOutputCopy[i], tokenizedBatch[i].attentionMask)
                l2Normalize(pooled)
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Batch inference failed for chunk of size $size -> Exception: ${e.javaClass.simpleName} | Message: ${e.message}")
            throw e
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
