package com.example.persona.features.audit_log.data.repository

import com.example.persona.core.database.dao.AuditLogProjection
import com.example.persona.core.database.dao.ReplyLogDao
import com.example.persona.core.database.dao.ReplyQueueDao
import com.example.persona.features.auto_reply.domain.model.ProcessedNotification
import com.example.persona.features.auto_reply.domain.model.RetrievalCandidate
import com.example.persona.features.chat_import.domain.model.ConversationContext
import com.example.persona.features.chat_import.domain.model.IntentType
import com.example.persona.features.chat_import.domain.model.SentimentType
import com.example.persona.features.chat_import.domain.model.TopicType
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AuditLogRepositoryImplTest {

    private val replyLogDao = mockk<ReplyLogDao>(relaxed = true)
    private val replyQueueDao = mockk<ReplyQueueDao>(relaxed = true)
    private val repository = AuditLogRepositoryImpl(replyLogDao, replyQueueDao)

    @Test
    fun `logExecutedReply inserts ReplyLogEntity into dao`() = runTest {
        val notification = ProcessedNotification(
            notificationKey = "key_1",
            contactName = "سارة",
            originalText = "أريد فستان مقاس L",
            normalizedText = "أريد فستان مقاس L",
            postTime = 123456789L,
            context = ConversationContext(
                intent = IntentType.REQUEST,
                topic = TopicType.GENERAL,
                sentiment = SentimentType.POSITIVE,
                confidence = 0.9f
            )
        )
        val candidate = RetrievalCandidate(
            chunkId = "chunk_123",
            headerContext = "فستان",
            similarityScore = 0.88f,
            chunkText = "فستان سواريه متوفر"
        )

        repository.logExecutedReply(notification, candidate, "متوفر مقاس L")

        coVerify(exactly = 1) {
            replyLogDao.insert(match { entity ->
                entity.contactDisplayName == "سارة" &&
                entity.incomingMessage == "أريد فستان مقاس L" &&
                entity.generatedReply == "متوفر مقاس L" &&
                entity.confidenceScore == 0.88f &&
                entity.wasAutoSent
            })
        }
    }

    @Test
    fun `queuePendingReply inserts ReplyQueueEntity into dao`() = runTest {
        val notification = ProcessedNotification(
            notificationKey = "key_2",
            contactName = "محمد",
            originalText = "طلب مستعجل",
            normalizedText = "طلب مستعجل",
            postTime = 123456789L,
            context = ConversationContext(
                intent = IntentType.REQUEST,
                topic = TopicType.GENERAL,
                sentiment = SentimentType.NEUTRAL,
                confidence = 0.7f
            )
        )

        repository.queuePendingReply(notification, "جاري المتابعة")

        coVerify(exactly = 1) {
            replyQueueDao.insert(match { entity ->
                entity.contactDisplayName == "محمد" &&
                entity.replyText == "جاري المتابعة" &&
                entity.status == "PENDING"
            })
        }
    }

    @Test
    fun `observeLogs transforms projections to AuditLogItems with telemetry`() = runTest {
        val projection = AuditLogProjection(
            id = 42L,
            contactName = "علي",
            incomingMessage = "أين العنوان؟",
            generatedReply = "شارع التحرير الدقي",
            similarityScore = 0.92f,
            wasAutoSent = true,
            timestamp = Clock.System.now(),
            gatekeeperTier = "Tier 1: Muse Spark 1.3",
            providerModel = "Meta / muse-spark-1.3",
            latencyMs = 1120L,
            promptTokens = 905,
            completionTokens = 71,
            decisionReason = "تم الرد تلقائياً بناءً على نية (BANTER)"
        )
        every { replyLogDao.observeLogsWithContact(100) } returns flowOf(listOf(projection))

        val result = repository.observeLogs(100).first()
        assertEquals(1, result.size)
        assertEquals(42L, result[0].id)
        assertEquals("علي", result[0].contactName)
        assertEquals("شارع التحرير الدقي", result[0].generatedReply)
        assertEquals("Tier 1: Muse Spark 1.3", result[0].gatekeeperTier)
        assertEquals("SUCCESS", result[0].status)
        assertEquals("Meta / muse-spark-1.3", result[0].providerModel)
        assertEquals(1120L, result[0].latencyMs)
        assertEquals(905, result[0].promptTokens)
        assertEquals(71, result[0].completionTokens)
        assertEquals("تم الرد تلقائياً بناءً على نية (BANTER)", result[0].decisionReason)
    }

    @Test
    fun `clearPendingRepliesForContact delegates to dao`() = runTest {
        repository.clearPendingRepliesForContact("علي")
        coVerify(exactly = 1) { replyQueueDao.deletePendingRepliesForContact("علي") }
    }
}
