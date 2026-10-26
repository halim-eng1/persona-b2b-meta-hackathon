package com.example.persona.features.chat_import.data.embedding

import android.content.Context
import android.util.Log
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.example.persona.core.di.AiInferenceDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton

class ModelLoadException(val filePath: String, message: String, cause: Throwable? = null) : Exception(message, cause)

@Singleton
class OnnxModelManager @Inject constructor(
    @ApplicationContext private val context: Context,
    @AiInferenceDispatcher private val aiDispatcher: CoroutineDispatcher
) {
    private var session: OrtSession? = null
    private var environment: OrtEnvironment? = null
    private val loadLock = Mutex()

    /**
     * Hidden #1: Inference-level Mutex.
     * OrtSession.run() is not thread-safe — concurrent calls from
     * import worker + NLS auto-reply will segfault the native ONNX runtime.
     * ALL session.run() calls MUST acquire this lock.
     */
    val inferenceMutex = Mutex()

    companion object {
        private const val MODEL_PATH = "models/minilm_l12_v2_int8.onnx"
        private const val TAG = "OnnxModelManager"
        
        /** Hidden #6: Max wait time for auto-reply before giving up */
        const val REALTIME_INFERENCE_TIMEOUT_MS = 5_000L
    }

    suspend fun loadModel(): Boolean = withContext(aiDispatcher) {
        loadLock.withLock {
            if (session != null) return@withContext true

            val modelFile = java.io.File(context.filesDir, MODEL_PATH)

            // Try to copy from assets to local filesDir if it does not exist
            if (!modelFile.exists()) {
                try {
                    modelFile.parentFile?.mkdirs()
                    context.assets.open(MODEL_PATH).use { input ->
                        modelFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    Log.i(TAG, "Successfully copied model from assets to ${modelFile.absolutePath}")
                } catch (e: Exception) {
                    Log.e("AiModel", "CRITICAL: Model file missing at expected path: ${modelFile.absolutePath}", e)
                }
            }

            // Explicit Existence Check: Before calling OrtSession.create, explicitly check file.exists()
            if (!modelFile.exists()) {
                val absolutePath = modelFile.absolutePath
                val errorMessage = "CRITICAL: Model file missing at expected path: $absolutePath"
                Log.e("AiModel", errorMessage)
                throw ModelLoadException(absolutePath, errorMessage)
            }

            try {
                environment = OrtEnvironment.getEnvironment()
                val sessionOptions = OrtSession.SessionOptions().apply {
                    setIntraOpNumThreads(2)
                    setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
                    addConfigEntry("session.intra_op.allow_spinning", "0")
                }

                session = environment!!.createSession(modelFile.absolutePath, sessionOptions)

                Log.i(TAG, "ONNX model loaded successfully from file. Input: ${session!!.inputNames}")
                true
            } catch (e: Exception) {
                Log.e("AiModel", "CRITICAL: Model file exists but failed to load. Details: ${e.message}", e)
                throw e
            }
        }
    }

    fun getSession(): OrtSession? = session

    fun isLoaded(): Boolean = session != null

    /**
     * Hidden #6: Priority-aware inference.
     * Auto-reply (real-time) calls this with a timeout to avoid
     * being starved by batch import holding the mutex.
     * Returns null on timeout — orchestrator falls back to suggestion notification.
     */
    suspend fun <T> withInferenceLock(
        timeoutMs: Long = 0L,
        block: suspend (OrtSession) -> T
    ): T? {
        val sess = session ?: return null
        return if (timeoutMs > 0) {
            try {
                withTimeout(timeoutMs) {
                    inferenceMutex.withLock { block(sess) }
                }
            } catch (e: TimeoutCancellationException) {
                Log.w(TAG, "Inference lock timeout after ${timeoutMs}ms — skipping")
                null
            }
        } else {
            inferenceMutex.withLock { block(sess) }
        }
    }

    suspend fun release() = withContext(aiDispatcher) {
        loadLock.withLock {
            session?.close()
            session = null
            environment?.close()
            environment = null
            Log.i(TAG, "ONNX model released")
        }
    }
}
