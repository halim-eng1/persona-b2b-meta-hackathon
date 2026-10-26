package com.example.persona.features.auto_reply.data.repository

import com.example.persona.core.database.dao.EmbeddingDao
import com.example.persona.core.database.dao.LinkedContactDao
import com.example.persona.core.database.dao.ChatMessageDao
import com.example.persona.core.database.dao.ShortMessageReplyCandidate
import com.example.persona.core.logging.PersonaLogger
import com.example.persona.core.logging.PersonaLogTags
import com.example.persona.features.auto_reply.domain.model.RetrievalCandidate
import com.example.persona.features.auto_reply.domain.orchestrator.AutoReplyOrchestrator
import com.example.persona.features.chat_import.domain.model.ConversationContext
import com.example.persona.features.chat_import.domain.model.IntentType
import com.example.persona.features.chat_import.domain.model.TopicType
import com.example.persona.features.chat_import.domain.model.SentimentType
import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SemanticRetrievalRepository @Inject constructor(
    private val embeddingDao: EmbeddingDao,
    private val linkedContactDao: LinkedContactDao,
    private val chatMessageDao: ChatMessageDao
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
                contactId = contactEntity.id,
                triggerText = entity.triggerText ?: "",
                replyText = entity.messageText
            )
        }

        if (candidates.isEmpty()) {
            PersonaLogger.d(PersonaLogTags.RETRIEVAL, "Incoming context: $queryContext")
            PersonaLogger.d(PersonaLogTags.RETRIEVAL, "Candidate count: 0")
            PersonaLogger.d(PersonaLogTags.RETRIEVAL, "Best similarity score: 0.0")
            PersonaLogger.d(PersonaLogTags.RETRIEVAL, "Best candidate selected: none")
            return emptyList()
        }

        val nearestNeighbors = candidates.sortedByDescending { it.similarityScore }
        nearestNeighbors.take(3).forEachIndexed { index, candidate ->
            Log.d("PersonaRetrievalMatch", "Candidate #$index: HistoricTrigger='${candidate.triggerText}', HistoricReply='${candidate.replyText}', Score=${candidate.score}")
        }

        // Step 3: Thresholding & Ranking
        val maxScore = candidates.maxOf { it.similarityScore }
        PersonaLogger.d(PersonaLogTags.RETRIEVAL, "Incoming context: $queryContext")
        val filteredCandidates = candidates
            .filter { it.similarityScore >= minimumSimilarityThreshold }
            .sortedByDescending { it.similarityScore }
            .take(limit)
        PersonaLogger.d(PersonaLogTags.RETRIEVAL, "Candidate count: ${filteredCandidates.size}")
        PersonaLogger.d(PersonaLogTags.RETRIEVAL, "Best similarity score: $maxScore")

        val bestCandidate = filteredCandidates.firstOrNull()
        if (bestCandidate != null) {
            PersonaLogger.d(
                PersonaLogTags.RETRIEVAL,
                "Best candidate selected: score=${bestCandidate.similarityScore}, length=${bestCandidate.messageText.length}"
            )
        } else {
            PersonaLogger.d(PersonaLogTags.RETRIEVAL, "Best candidate selected: none")
        }

        if (maxScore < minimumSimilarityThreshold) {
            return emptyList()
        }

        return filteredCandidates
    }

    /**
     * Attempts a contact-scoped SQL-based lookup for short messages (<= 2 words).
     * Returns a RetrievalCandidate if successful, or null if aborted/insufficient matches.
     */
    suspend fun retrieveShortMessageReply(
        contactName: String,
        shortTrigger: String
    ): RetrievalCandidate? {
        val contactEntity = linkedContactDao.getByDisplayName(contactName) ?: return null

        // Step 1: Exact Match
        var rawCandidates = chatMessageDao.getExactShortMessageReplies(contactName, shortTrigger)
        
        // Sanitization Guard
        var sanitized = rawCandidates.filter { candidate ->
            candidate.messageText.length <= AutoReplyOrchestrator.SHORT_MSG_MAX_REPLY_LENGTH &&
            candidate.messageText.count { it == '\n' } <= AutoReplyOrchestrator.SHORT_MSG_MAX_NEWLINES
        }

        // Step 2: Fuzzy Match fallback if Step 1 yields < SHORT_MSG_MIN_MATCHES results
        if (sanitized.size < AutoReplyOrchestrator.SHORT_MSG_MIN_MATCHES) {
            rawCandidates = chatMessageDao.getFuzzyShortMessageReplies(contactName, shortTrigger)
            sanitized = rawCandidates.filter { candidate ->
                candidate.messageText.length <= AutoReplyOrchestrator.SHORT_MSG_MAX_REPLY_LENGTH &&
                candidate.messageText.count { it == '\n' } <= AutoReplyOrchestrator.SHORT_MSG_MAX_NEWLINES
            }
        }

        // Safety Guard: Abort if fewer than SHORT_MSG_MIN_MATCHES matches
        if (sanitized.size < AutoReplyOrchestrator.SHORT_MSG_MIN_MATCHES) {
            return null
        }

        // Scoring Logic:
        // Group the sanitized replies by text content.
        // Weight = (Count of occurrences * W1) + (Recency bonus of the latest occurrence * W2)
        // Recency bonus = 1.0f / (1.0f + ageInDays)
        data class ReplyGroup(
            val replyText: String,
            val occurrences: Int,
            val latestTimestamp: kotlinx.datetime.Instant
        )

        val groups = sanitized.groupBy { it.messageText }
            .map { (text, list) ->
                ReplyGroup(
                    replyText = text,
                    occurrences = list.size,
                    latestTimestamp = list.maxOf { it.timestamp }
                )
            }

        val scoredReplies = groups.map { group ->
            val ageMs = System.currentTimeMillis() - group.latestTimestamp.toEpochMilliseconds()
            val ageDays = (ageMs.toDouble() / (24.0 * 60.0 * 60.0 * 1000.0)).coerceAtLeast(0.0)
            val recencyBonus = 1.0f / (1.0f + ageDays.toFloat())
            val weight = (group.occurrences * AutoReplyOrchestrator.SHORT_MSG_FREQUENCY_WEIGHT) +
                    (recencyBonus * AutoReplyOrchestrator.SHORT_MSG_RECENCY_WEIGHT)
            group.replyText to weight
        }

        // Selection: Take Top 5 highest-weighted replies
        val top5Candidates = scoredReplies.sortedByDescending { it.second }.take(5)

        // Perform Weighted Random Selection
        val totalWeight = top5Candidates.sumOf { it.second.toDouble() }
        val finalReply = if (totalWeight <= 0.0) {
            top5Candidates.random().first
        } else {
            val randomValue = kotlin.random.Random.nextDouble() * totalWeight
            var accumulated = 0.0
            var selected = top5Candidates.last().first
            for (candidate in top5Candidates) {
                accumulated += candidate.second
                if (accumulated >= randomValue) {
                    selected = candidate.first
                    break
                }
            }
            selected
        }

        // Phase C: Required Logging
        val candidateLines = top5Candidates.joinToString("\n") { "    - ${it.first} (Weight: ${it.second})" }
        Log.d("ShortMessageMode", """
            ShortMessageMode Activated
            Trigger=${shortTrigger}
            Candidate Replies:
            $candidateLines
            Selected: ${finalReply}
        """.trimIndent())

        // Wrap selected reply in a synthetic RetrievalCandidate
        val matchedGroup = groups.first { it.replyText == finalReply }
        return RetrievalCandidate(
            messageText = finalReply,
            context = ConversationContext(
                intent = IntentType.GENERAL,
                topic = TopicType.GENERAL,
                sentiment = SentimentType.NEUTRAL,
                confidence = 1.0f
            ),
            similarityScore = 1.0f,
            messageTimestamp = matchedGroup.latestTimestamp.toEpochMilliseconds(),
            contactId = contactEntity.id,
            triggerText = shortTrigger,
            replyText = finalReply
        )
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
