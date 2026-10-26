package com.example.persona.features.auto_reply.data.repository

import com.example.persona.core.database.dao.EmbeddingDao
import com.example.persona.core.database.dao.LinkedContactDao
import com.example.persona.core.database.entity.EmbeddingWithMessage
import com.example.persona.core.database.entity.LinkedContactEntity
import com.example.persona.features.chat_import.domain.model.ConversationContext
import com.example.persona.features.chat_import.domain.model.IntentType
import com.example.persona.features.chat_import.domain.model.TopicType
import com.example.persona.features.chat_import.domain.model.SentimentType
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SemanticRetrievalRepositoryTest {

    private val embeddingDao = mockk<EmbeddingDao>()
    private val linkedContactDao = mockk<LinkedContactDao>()
    private val repository = SemanticRetrievalRepository(embeddingDao, linkedContactDao)

    @Test
    fun retrieveCandidates_contactNotLinked_returnsEmptyList(): Unit = runBlocking {
        coEvery { linkedContactDao.getByDisplayName("Alice") } returns null

        val result = repository.retrieveCandidates(
            contactName = "Alice",
            queryEmbedding = floatArrayOf(1.0f, 0.0f),
            queryContext = ConversationContext(IntentType.GREETING, TopicType.GENERAL, SentimentType.NEUTRAL, 0.8f)
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun retrieveCandidates_exactContextMatchesSortedCorrectly(): Unit = runBlocking {
        // Arrange
        val contact = LinkedContactEntity(
            id = 42L,
            whatsappDisplayName = "Alice",
            isActive = true,
            autoReplyEnabled = true,
            cooldownCount = 0,
            cooldownResetAt = null,
            linkedAt = Instant.fromEpochMilliseconds(0)
        )
        coEvery { linkedContactDao.getByDisplayName("Alice") } returns contact

        // Query: Intent = QUESTION, Topic = LOCATION, Sentiment = NEUTRAL
        val queryContext = ConversationContext(IntentType.QUESTION, TopicType.LOCATION, SentimentType.NEUTRAL, 0.9f)
        val queryEmbedding = floatArrayOf(1.0f, 0.0f, 0.0f) // 3D vectors for simplified testing

        // Create 105 candidates:
        // - 5 candidates with score = 4 (Intent=QUESTION, Topic=LOCATION, Sentiment=NEUTRAL) and vector [0.85f, 0.0f, 0.0f] (dot = 0.85)
        // - 95 candidates with score = 0 (Intent=GENERAL, Topic=WORK, Sentiment=NEGATIVE) and vector [0.95f, 0.0f, 0.0f] (dot = 0.95)
        // - 5 candidates with score = 0 (Intent=GENERAL, Topic=WORK, Sentiment=NEGATIVE) and vector [0.99f, 0.0f, 0.0f] (dot = 0.99), excluded from top 100 slice.
        
        val highContextEmbeddings = (1..5).map { i ->
            EmbeddingWithMessage(
                id = i.toLong(),
                messageId = i.toLong(),
                vector = floatArrayOf(0.85f, 0.0f, 0.0f),
                modelVersion = "test",
                createdAt = Instant.fromEpochMilliseconds(0),
                messageText = "High context message $i",
                intentType = IntentType.QUESTION.name,
                topicType = TopicType.LOCATION.name,
                sentimentType = SentimentType.NEUTRAL.name,
                contextConfidence = 0.9f,
                messageTimestamp = Instant.fromEpochMilliseconds(1000L * i)
            )
        }

        val lowContextEvaluated = (6..100).map { i ->
            EmbeddingWithMessage(
                id = i.toLong(),
                messageId = i.toLong(),
                vector = floatArrayOf(0.95f, 0.0f, 0.0f),
                modelVersion = "test",
                createdAt = Instant.fromEpochMilliseconds(0),
                messageText = "Low context evaluated $i",
                intentType = IntentType.GENERAL.name,
                topicType = TopicType.WORK.name,
                sentimentType = SentimentType.NEGATIVE.name,
                contextConfidence = 0.5f,
                messageTimestamp = Instant.fromEpochMilliseconds(1000L * i)
            )
        }

        val lowContextExcluded = (101..105).map { i ->
            EmbeddingWithMessage(
                id = i.toLong(),
                messageId = i.toLong(),
                vector = floatArrayOf(0.99f, 0.0f, 0.0f), // Extremely high dot product, but context score is 0 and excluded
                modelVersion = "test",
                createdAt = Instant.fromEpochMilliseconds(0),
                messageText = "Low context excluded $i",
                intentType = IntentType.GENERAL.name,
                topicType = TopicType.WORK.name,
                sentimentType = SentimentType.NEGATIVE.name,
                contextConfidence = 0.5f,
                messageTimestamp = Instant.fromEpochMilliseconds(1000L * i)
            )
        }

        coEvery { embeddingDao.getUserEmbeddingsForContact("Alice") } returns (highContextEmbeddings + lowContextEvaluated + lowContextExcluded)

        // Act
        val result = repository.retrieveCandidates(
            contactName = "Alice",
            queryEmbedding = queryEmbedding,
            queryContext = queryContext,
            limit = 5,
            minimumSimilarityThreshold = 0.7f
        )

        // Assert
        assertEquals(5, result.size)
        // Verify that no candidate in the results belongs to the excluded group
        result.forEach { candidate ->
            assertTrue(!candidate.messageText.contains("excluded"))
        }

        // Verify ordering: highest similarity score first
        assertEquals(0.95f, result[0].similarityScore, 0.001f)
        assertEquals(42L, result[0].contactId)
    }

    @Test
    fun retrieveCandidates_highestScoreBelowThreshold_returnsEmptyList(): Unit = runBlocking {
        // Arrange
        val contact = LinkedContactEntity(
            id = 42L,
            whatsappDisplayName = "Alice",
            isActive = true,
            autoReplyEnabled = true,
            cooldownCount = 0,
            cooldownResetAt = null,
            linkedAt = Instant.fromEpochMilliseconds(0)
        )
        coEvery { linkedContactDao.getByDisplayName("Alice") } returns contact

        val queryContext = ConversationContext(IntentType.QUESTION, TopicType.LOCATION, SentimentType.NEUTRAL, 0.9f)
        val queryEmbedding = floatArrayOf(1.0f, 0.0f)

        val embedding = EmbeddingWithMessage(
            id = 1L,
            messageId = 10L,
            vector = floatArrayOf(0.6f, 0.8f), // dot product = 0.6
            modelVersion = "test",
            createdAt = Instant.fromEpochMilliseconds(0),
            messageText = "Hello",
            intentType = IntentType.QUESTION.name,
            topicType = TopicType.LOCATION.name,
            sentimentType = SentimentType.NEUTRAL.name,
            contextConfidence = 0.9f,
            messageTimestamp = Instant.fromEpochMilliseconds(5000L)
        )
        coEvery { embeddingDao.getUserEmbeddingsForContact("Alice") } returns listOf(embedding)

        // Act
        val result = repository.retrieveCandidates(
            contactName = "Alice",
            queryEmbedding = queryEmbedding,
            queryContext = queryContext,
            limit = 5,
            minimumSimilarityThreshold = 0.7f
        )

        // Assert
        assertTrue(result.isEmpty())
    }
}
