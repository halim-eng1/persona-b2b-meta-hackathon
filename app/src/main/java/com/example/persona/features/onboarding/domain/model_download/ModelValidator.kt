package com.example.persona.features.onboarding.domain.model_download

import android.content.Context
import android.util.Log
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.example.persona.core.data.repository.UserPreferencesRepository
import com.example.persona.core.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

sealed interface ValidationResult {
    data object Success : ValidationResult
    data class Failed(val reason: String, val cause: Throwable? = null) : ValidationResult
}

@Singleton
class ModelValidator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userPreferencesRepository: UserPreferencesRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    companion object {
        private const val TAG = "ModelValidator"
        const val MODEL_FILE_NAME = "model_qint8_arm64.onnx"
        const val TOKENIZER_FILE_NAME = "tokenizer.json"
        private const val MAX_SEQ_LENGTH = 128
    }

    /**
     * Executes headless validation (tokenizer tokenization + ONNX 1-token inference)
     * using deterministic [use] blocks to ensure native C++ allocations are closed.
     */
    suspend fun validate(modelsDir: File): ValidationResult = withContext(ioDispatcher) {
        val modelFile = File(modelsDir, MODEL_FILE_NAME)
        val tokenizerFile = File(modelsDir, TOKENIZER_FILE_NAME)

        if (!modelFile.exists() || modelFile.length() == 0L) {
            val msg = "Model file does not exist or is empty: ${modelFile.absolutePath}"
            Log.e(TAG, msg)
            return@withContext ValidationResult.Failed(msg)
        }

        if (!tokenizerFile.exists() || tokenizerFile.length() == 0L) {
            val msg = "Tokenizer file does not exist or is empty: ${tokenizerFile.absolutePath}"
            Log.e(TAG, msg)
            return@withContext ValidationResult.Failed(msg)
        }

        try {
            // 1. Force Application ClassLoader on current thread for JNI resolution
            val appClassLoader = context.applicationContext.classLoader
            Thread.currentThread().contextClassLoader = appClassLoader

            // 2. Validate DJL Tokenizer
            val tokenizer = ai.djl.huggingface.tokenizers.HuggingFaceTokenizer.builder()
                .optTokenizerPath(tokenizerFile.toPath())
                .optMaxLength(MAX_SEQ_LENGTH)
                .optPadToMaxLength()
                .optTruncation(true)
                .build()

            val testText = "مرحبا بك في متجرنا"
            val encoding = tokenizer.encode(testText)
            if (encoding.ids.isEmpty()) {
                throw IllegalStateException("Tokenizer produced empty token IDs for '$testText'")
            }
            Log.i(TAG, "Dry-run Tokenizer test passed. Token count: ${encoding.ids.size}")

            // 3. Validate ONNX Runtime Inference
            val env = OrtEnvironment.getEnvironment()
            val sessionOptions = OrtSession.SessionOptions().apply {
                setIntraOpNumThreads(2)
                setOptimizationLevel(OrtSession.SessionOptions.OptLevel.BASIC_OPT)
            }

            env.createSession(modelFile.absolutePath, sessionOptions).use { session ->
                val inputNames = session.inputNames
                val inputIdsArray = Array(1) { encoding.ids }
                val attentionMaskArray = Array(1) { encoding.attentionMask }
                val tokenTypeIdsArray = Array(1) { encoding.typeIds ?: LongArray(encoding.ids.size) { 0L } }

                val inputIdsTensor = OnnxTensor.createTensor(env, inputIdsArray)
                val attentionMaskTensor = OnnxTensor.createTensor(env, attentionMaskArray)
                val tokenTypeIdsTensor = OnnxTensor.createTensor(env, tokenTypeIdsArray)

                try {
                    val inputs = mutableMapOf<String, OnnxTensor>()
                    if (inputNames.contains("input_ids")) inputs["input_ids"] = inputIdsTensor
                    if (inputNames.contains("attention_mask")) inputs["attention_mask"] = attentionMaskTensor
                    if (inputNames.contains("token_type_ids")) inputs["token_type_ids"] = tokenTypeIdsTensor

                    session.run(inputs).use { results ->
                        if (results.size() == 0) {
                            throw IllegalStateException("Dry-run ONNX inference returned 0 outputs")
                        }
                        Log.i(TAG, "Dry-run ONNX inference passed. Output tensor count: ${results.size()}")
                    }
                } finally {
                    inputIdsTensor.close()
                    attentionMaskTensor.close()
                    tokenTypeIdsTensor.close()
                }
            }

            // Mark model prepared in persistent DataStore
            userPreferencesRepository.setModelPrepared(true)
            Log.i(TAG, "Model validation successfully completed and persistence flag saved.")
            ValidationResult.Success
        } catch (t: Throwable) {
            Log.e(TAG, "Dry-run model validation failed: ${t.message}", t)
            // Purge corrupted files on validation failure
            try {
                if (modelFile.exists()) modelFile.delete()
                if (tokenizerFile.exists()) tokenizerFile.delete()
            } catch (cleanupEx: Exception) {
                Log.w(TAG, "Failed to purge corrupted model files after validation failure", cleanupEx)
            }
            ValidationResult.Failed(t.message ?: "Unknown model validation failure", t)
        }
    }
}

