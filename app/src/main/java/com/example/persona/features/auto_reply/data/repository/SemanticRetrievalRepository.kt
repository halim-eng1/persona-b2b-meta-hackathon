package com.example.persona.features.auto_reply.data.repository

import com.example.persona.core.database.dao.EmbeddingDao
import com.example.persona.core.database.dao.LinkedContactDao
import com.example.persona.features.auto_reply.domain.model.RetrievalCandidate
import com.example.persona.features.chat_import.domain.model.ConversationContext
import com.example.persona.features.chat_import.domain.model.IntentType
import com.example.persona.features.chat_import.domain.model.TopicType
import com.example.persona.features.chat_import.domain.model.SentimentType
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SemanticRetrievalRepository @Inject constructor(
    private val embeddingDao: EmbeddingDao,
    private val linkedContactDao: LinkedContactDao
) {
    /**
     * Retrieves the top N similar messages for a given contact using weighted pre-filtering,
     * dot product calculations, and a similarity threshold.
     */
    suspend fun retrieveCandidates(
        contactName: String,
        queryEmbedding: FloatArray,
        queryContext: ConversationContext,
        limit: Int = 5,
        minimumSimilarityThreshold: Float = 0.7f
    ): List<RetrievalCandidate> {
        // Step 0: Find the linked contact to get its ID
        val contactEntity = linkedContactDao.getByDisplayName(contactName) ?: return emptyList()

        // Step 1: Query the Room Database for the linked contact
        val allUserEmbeddings = embeddingDao.getUserEmbeddingsForContact(contactName)

        // Filter candidates FIRST using the ConversationContext (Weighted Scoring)
        // Exact Intent match: +2 points
        // Exact Topic match: +1 point
        // Exact Sentiment match: +1 point
        val scoredEmbeddings = allUserEmbeddings.map { entity ->
            var contextScore = 0
            if (entity.intentType == queryContext.intent.name) contextScore += 2
            if (entity.topicType == queryContext.topic.name) contextScore += 1
            if (entity.sentimentType == queryContext.sentiment.name) contextScore += 1
            entity to contextScore
        }.sortedByDescending { it.second }
            .take(100)
            .map { it.first }

        // Step 2: Vector Math (Dot Product since vectors are L2-normalized)
        val candidates = scoredEmbeddings.map { entity ->
            val similarity = dotProduct(queryEmbedding, entity.vector)
            val candidateContext = ConversationContext(
                intent = try { IntentType.valueOf(entity.intentType) } catch (e: Exception) { IntentType.GENERAL },
                topic = try { TopicType.valueOf(entity.topicType) } catch (e: Exception) { TopicType.GENERAL },
                sentiment = try { SentimentType.valueOf(entity.sentimentType) } catch (e: Exception) { SentimentType.NEUTRAL },
                confidence = entity.contextConfidence
            )
            RetrievalCandidate(
                messageText = entity.messageText,
                context = candidateContext,
                similarityScore = similarity,
                messageTimestamp = entity.messageTimestamp.toEpochMilliseconds(),
                contactId = contactEntity.id
            )
        }

        if (candidates.isEmpty()) return emptyList()

        // Step 3: Thresholding & Ranking
        val maxScore = candidates.maxOf { it.similarityScore }
        if (maxScore < minimumSimilarityThreshold) {
            return emptyList()
        }

        return candidates
            .filter { it.similarityScore >= minimumSimilarityThreshold }
            .sortedByDescending { it.similarityScore }
            .take(limit)
    }

    private fun dotProduct(a: FloatArray, b: FloatArray): Float {
        require(a.size == b.size) { "Vector dimensions must match: ${a.size} vs ${b.size}" }
        var sum = 0f
        for (i in a.indices) {
            sum += a[i] * b[i]
        }
        return sum
    }
}
