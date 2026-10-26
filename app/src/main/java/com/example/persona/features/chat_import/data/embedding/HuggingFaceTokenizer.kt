package com.example.persona.features.chat_import.data.embedding

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HuggingFaceTokenizer @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var tokenizer: ai.djl.huggingface.tokenizers.HuggingFaceTokenizer? = null
    private var isInitialized = false

    companion object {
        private const val TOKENIZER_JSON_PATH = "models/tokenizer.json"
        private const val MAX_SEQ_LENGTH = 128
        private const val TAG = "HuggingFaceTokenizer"
    }

    /**
     * Loads the tokenizer.json from assets.
     * Must be called once before any tokenize() call.
     */
    fun initialize() {
        synchronized(this) {
            if (isInitialized) return
            try {
                val tempFile = java.io.File(context.cacheDir, "tokenizer.json")
                context.assets.open(TOKENIZER_JSON_PATH).use { input ->
                    java.io.FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                }

                // Force Application ClassLoader on the current thread to resolve JNI libs in Binder context
                val appClassLoader = context.applicationContext.classLoader
                Thread.currentThread().contextClassLoader = appClassLoader

                tokenizer = ai.djl.huggingface.tokenizers.HuggingFaceTokenizer.builder()
                    .optTokenizerPath(tempFile.toPath())
                    .optMaxLength(MAX_SEQ_LENGTH)
                    .optPadToMaxLength()
                    .optTruncation(true)
                    .build()
                isInitialized = true
                Log.i(TAG, "HuggingFace tokenizer loaded from tokenizer.json")
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to initialize HuggingFaceTokenizer -> Exception: ${e.javaClass.simpleName} | Message: ${e.message}")
            }

        }
    }

    /**
     * Tokenizes input text and returns input_ids, attention_mask, and token_type_ids
     * as LongArrays, padded/truncated to [MAX_SEQ_LENGTH].
     * DJL handles [CLS]/[SEP] insertion, padding, and truncation internally.
     */
    fun tokenize(text: String): TokenizedInput {
        if (!isInitialized) {
            initialize()
        }
        synchronized(this) {
            check(isInitialized) { "Tokenizer not initialized. Call initialize() first." }
            val encoding = tokenizer!!.encode(text)
            return TokenizedInput(
                inputIds = encoding.ids,
                attentionMask = encoding.attentionMask,
                tokenTypeIds = encoding.typeIds
            )
        }
    }

    fun release() {
        synchronized(this) {
            tokenizer?.close()
            tokenizer = null
            isInitialized = false
        }
    }
}

data class TokenizedInput(
    val inputIds: LongArray,
    val attentionMask: LongArray,
    val tokenTypeIds: LongArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TokenizedInput) return false
        return inputIds.contentEquals(other.inputIds) &&
                attentionMask.contentEquals(other.attentionMask) &&
                tokenTypeIds.contentEquals(other.tokenTypeIds)
    }

    override fun hashCode(): Int {
        var result = inputIds.contentHashCode()
        result = 31 * result + attentionMask.contentHashCode()
        result = 31 * result + tokenTypeIds.contentHashCode()
        return result
    }
}
