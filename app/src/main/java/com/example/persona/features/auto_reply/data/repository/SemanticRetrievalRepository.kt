package com.example.persona.features.auto_reply.data.repository

import android.util.Log
import com.example.persona.core.database.dao.BusinessKnowledgeDao
import com.example.persona.core.database.dao.EmbeddingIndexDao
import com.example.persona.core.logging.PersonaLogger
import com.example.persona.core.logging.PersonaLogTags
import com.example.persona.features.auto_reply.domain.model.RetrievalCandidate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Global knowledge base retrieval engine — the core of the B2B RAG pipeline.
 *
 * B2B migration changes:
 *  - Removed Layer 1 (SQL short-message mode) entirely.
 *  - Removed contact-scoped filtering (no [contactName] parameter).
 *  - Removed intent-mismatch penalty matrix (B2B chunks are answers, not typed messages).
 *  - Removed [linkedContactDao] and [chatMessageDao] dependencies.
 *  - Added [businessKnowledgeDao] and [embeddingIndexDao] for global vector search.
 *  - Retained: dynamic thresholding, repetition penalty, top-50 sweep.
 */
@Singleton
class SemanticRetrievalRepository @Inject constructor(
    private val embeddingIndexDao: EmbeddingIndexDao,
    private val businessKnowledgeDao: BusinessKnowledgeDao
) {

    /**
     * Performs a global cosine-similarity sweep over the entire [embeddings_index] table,
     * applies the repetition penalty, and returns the top [limit] chunks that exceed
     * the dynamic similarity threshold.
     *
     * @param queryEmbedding         384-dim embedding of the normalized incoming query.
     * @param limit                  Maximum number of candidates to return.
     * @param minimumSimilarityThreshold  Base threshold from user preferences.
     * @param queryText              Raw query text (used for dynamic thresholding by word count).
     * @param recentReplyHashes      Hash set of recently-sent chunk texts (repetition gate).
     */
    suspend fun retrieveCandidates(
        queryEmbedding: FloatArray,
        limit: Int = 5,
        minimumSimilarityThreshold: Float = 0.38f,
        queryText: String = "",
        recentReplyHashes: List<Int> = emptyList()
    ): Pair<List<RetrievalCandidate>, Float> {

        // ── Step 1: Load all vectors from the global index ─────────────────────
        val allVectorProjections = embeddingIndexDao.getAllVectors()

        if (allVectorProjections.isEmpty()) {
            PersonaLogger.d(PersonaLogTags.RETRIEVAL, "No embeddings in index — knowledge base is empty")
            return Pair(emptyList(), 0f)
        }

        // ── Step 2: Dynamic Thresholding by word count ─────────────────────────
        val baseThreshold = if (minimumSimilarityThreshold < 0f) 0.36f else minimumSimilarityThreshold
        val wordCount = queryText.split("\\s+".toRegex()).filter { it.isNotEmpty() }.size
        val adjustedThreshold = when {
            wordCount <= 2 -> (baseThreshold + 0.04f).coerceAtMost(0.98f)
            wordCount <= 4 -> (baseThreshold + 0.02f).coerceAtMost(0.95f)
            else           -> baseThreshold
        }.coerceAtLeast(if (minimumSimilarityThreshold < 0f) 0.36f else 0.0f)

        // ── Step 3: In-memory dot-product sweep (top 50) ───────────────────────
        val topProjections = allVectorProjections.asSequence()
            .map { it to dotProduct(queryEmbedding, it.vectorBlob) }
            .sortedByDescending { it.second }
            .take(50)
            .toList()

        if (topProjections.isEmpty()) {
            logEmpty(adjustedThreshold, wordCount)
            return Pair(emptyList(), 0f)
        }

        // ── Step 4: Resolve chunk text from business_knowledge table ───────────
        val chunkIds = topProjections.map { it.first.chunkId }
        val chunkDetails = businessKnowledgeDao.getChunkTextsByIds(chunkIds)
            .associateBy { it.chunkId }

        // ── Step 5: Apply repetition penalty & map to domain model ─────────────
        val candidates = topProjections.mapNotNull { (projection, similarity) ->
            val details = chunkDetails[projection.chunkId] ?: return@mapNotNull null

            var finalScore = similarity

            // Repetition Penalty: demote a chunk if its text was recently sent
            val textHash = details.chunkText.hashCode()
            if (recentReplyHashes.contains(textHash)) {
                finalScore = (finalScore - 0.20f).coerceAtLeast(0.0f)
                Log.d("SemanticRetrieval",
                    "Repetition penalty applied to chunk ${projection.chunkId}: " +
                        "${"%.4f".format(similarity)} → ${"%.4f".format(finalScore)}")
            }

            RetrievalCandidate(
                chunkId = projection.chunkId,
                headerContext = details.headerContext,
                chunkText = details.chunkText,
                similarityScore = finalScore,
                baseSimilarityScore = similarity
            )
        }

        if (candidates.isEmpty()) {
            logEmpty(adjustedThreshold, wordCount)
            return Pair(emptyList(), 0f)
        }

        // ── Step 6: Threshold filter, sort, take limit ─────────────────────────
        val maxScore = candidates.maxOf { it.similarityScore }

        // Log top-5 for diagnostics
        candidates.sortedByDescending { it.similarityScore }.take(5).forEachIndexed { i, c ->
            Log.d("SemanticRetrieval",
                "  #$i chunkId=${c.chunkId.take(8)} " +
                    "score=${"%.4f".format(c.similarityScore)} " +
                    "base=${"%.4f".format(c.baseSimilarityScore)} " +
                    "text='${c.chunkText.take(60)}...'")
        }

        val filtered = candidates
            .filter { it.similarityScore >= adjustedThreshold }
            .sortedByDescending { it.similarityScore }
            .take(limit)

        PersonaLogger.d(PersonaLogTags.RETRIEVAL,
            "Query wordCount=$wordCount threshold=$adjustedThreshold " +
                "totalVectors=${allVectorProjections.size} filtered=${filtered.size} maxScore=$maxScore")

        if (filtered.isEmpty()) {
            PersonaLogger.d(PersonaLogTags.RETRIEVAL, "No candidates above threshold — HITL trigger")
        } else {
            PersonaLogger.d(PersonaLogTags.RETRIEVAL,
                "Best candidate: score=${filtered.first().similarityScore} " +
                    "chunk=${filtered.first().chunkId.take(8)}")
        }

        val bestScore = candidates.maxOfOrNull { it.similarityScore } ?: 0f
        return Pair(filtered, bestScore)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ──────────────────────────────────────────────────────────────────────────

    private fun logEmpty(threshold: Float, wordCount: Int) {
        PersonaLogger.d(PersonaLogTags.RETRIEVAL,
            "Candidate count: 0 (threshold=$threshold, wordCount=$wordCount)")
    }

    /**
     * Dot-product of two equal-length float arrays.
     * MiniLM embeddings are L2-normalised at export time, so dot-product == cosine similarity.
     */
    private fun dotProduct(a: FloatArray, b: FloatArray): Float {
        require(a.size == b.size) { "Vector dimension mismatch: ${a.size} vs ${b.size}" }
        var sum = 0f
        for (i in a.indices) sum += a[i] * b[i]
        return sum
    }
}
