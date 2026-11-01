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
    private val cache = ThreadSafeLruCache<String, TokenizedInput>(500)

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
     * Checks the LRU cache first to prevent JNI boundary crossing.
     * This method is completely lock-free for the JNI call.
     */
    fun tokenize(text: String): TokenizedInput {
        val cached = cache.get(text)
        if (cached != null) return cached

        if (!isInitialized) {
            initialize()
        }
        check(isInitialized) { "Tokenizer not initialized. Call initialize() first." }
        
        val encoding = tokenizer!!.encode(text)
        val typeIds = encoding.typeIds ?: LongArray(encoding.ids.size) { 0L }
        val tokenized = TokenizedInput(
            inputIds = encoding.ids,
            attentionMask = encoding.attentionMask,
            tokenTypeIds = typeIds
        )
        cache.put(text, tokenized)
        return tokenized
    }

    /**
     * Batch tokenizes a list of strings.
     * Checks the LRU cache first to prevent JNI boundary crossing.
     * Cache misses are batched and encoded natively in a single lock-free JNI call.
     * The original order of the input texts is strictly preserved.
     */
    fun batchTokenize(texts: List<String>): List<TokenizedInput> {
        if (texts.isEmpty()) return emptyList()
        if (!isInitialized) {
            initialize()
        }

        val results = arrayOfNulls<TokenizedInput>(texts.size)
        val missIndices = ArrayList<Int>()
        val missTexts = ArrayList<String>()

        for (i in texts.indices) {
            val text = texts[i]
            val cached = cache.get(text)
            if (cached != null) {
                results[i] = cached
            } else {
                missIndices.add(i)
                missTexts.add(text)
            }
        }

        if (missTexts.isNotEmpty()) {
            check(isInitialized) { "Tokenizer not initialized. Call initialize() first." }
            val encodings = tokenizer!!.batchEncode(missTexts.toTypedArray())
            
            for (k in missTexts.indices) {
                val encoding = encodings[k]
                val typeIds = encoding.typeIds ?: LongArray(encoding.ids.size) { 0L }
                val tokenized = TokenizedInput(
                    inputIds = encoding.ids,
                    attentionMask = encoding.attentionMask,
                    tokenTypeIds = typeIds
                )
                val originalIndex = missIndices[k]
                results[originalIndex] = tokenized
                cache.put(missTexts[k], tokenized)
            }
        }

        return results.map { it!! }
    }

    fun release() {
        synchronized(this) {
            tokenizer?.close()
            tokenizer = null
            isInitialized = false
        }
    }
}

private class ThreadSafeLruCache<K, V>(private val maxEntries: Int) {
    private val map = object : java.util.LinkedHashMap<K, V>(maxEntries, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean {
            return size > maxEntries
        }
    }

    fun get(key: K): V? = synchronized(map) {
        map[key]
    }

    fun put(key: K, value: V) {
        synchronized(map) {
            map[key] = value
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
