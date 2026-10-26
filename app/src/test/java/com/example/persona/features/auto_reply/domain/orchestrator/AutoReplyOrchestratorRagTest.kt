package com.example.persona.features.auto_reply.domain.orchestrator

import android.content.Context
import com.example.persona.core.database.dao.ChatHistoryDao
import com.example.persona.core.database.dao.FailedQueryDao
import com.example.persona.core.database.dao.OrderDao
import com.example.persona.core.database.entity.ChatHistoryEntity
import com.example.persona.core.database.entity.OrderEntity
import com.example.persona.core.database.entity.OrderStatus
import com.example.persona.core.network.dto.AgenticLlmResponse
import com.example.persona.core.network.dto.ExtractedOrder
import com.example.persona.features.auto_reply.data.repository.NotificationProcessorImpl
import com.example.persona.features.auto_reply.data.repository.SemanticRetrievalRepository
import com.example.persona.core.data.repository.UserPreferencesRepository
import com.example.persona.features.auto_reply.data.service.IncomingMessageDispatcher
import com.example.persona.features.auto_reply.domain.model.ProcessedNotification
import com.example.persona.features.auto_reply.domain.model.RetrievalCandidate
import com.example.persona.features.auto_reply.domain.modifier.StylePostProcessor
import com.example.persona.features.auto_reply.domain.repository.AuditLogRepository
import com.example.persona.features.auto_reply.domain.repository.ReplyGenerationRepository
import com.example.persona.features.auto_reply.domain.usecase.DirectReplyExecutionUseCase
import com.example.persona.features.chat_import.data.embedding.EmbeddingEngine
import com.example.persona.features.chat_import.data.normalizer.ArabicTextNormalizer
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class AutoReplyOrchestratorRagTest {

    private fun createTestOrchestrator(
        orderDao: OrderDao = mockk(relaxed = true),
        chatHistoryDao: ChatHistoryDao = mockk(relaxed = true)
    ): AutoReplyOrchestrator = AutoReplyOrchestrator(
        incomingMessageDispatcher = mockk(relaxed = true),
        processor = mockk(relaxed = true),
        embeddingEngine = mockk(relaxed = true),
        semanticRetrievalRepository = mockk(relaxed = true),
        replyGenerationRepository = mockk(relaxed = true),
        directReplyExecutionUseCase = mockk(relaxed = true),
        auditLogRepository = mockk(relaxed = true),
        prefsRepository = mockk(relaxed = true),
        stylePostProcessor = mockk(relaxed = true),
        normalizer = mockk(relaxed = true),
        failedQueryDao = mockk(relaxed = true),
        chatHistoryDao = chatHistoryDao,
        orderDao = orderDao,
        context = mockk(relaxed = true),
        scope = CoroutineScope(Dispatchers.Unconfined)
    )

    @Test
    fun `calculateRagBaseThreshold maps preference to realistic MiniLM range with floor`() {
        // Balanced (0.75f) -> 0.375f
        assertEquals(0.375f, AutoReplyOrchestrator.calculateRagBaseThreshold(0.75f), 0.001f)

        // Conservative (0.85f) -> 0.425f
        assertEquals(0.425f, AutoReplyOrchestrator.calculateRagBaseThreshold(0.85f), 0.001f)

        // Aggressive (0.65f) -> clamped to floor 0.36f (since 0.65 * 0.5 = 0.325 < 0.36)
        assertEquals(0.36f, AutoReplyOrchestrator.calculateRagBaseThreshold(0.65f), 0.001f)

        // Negative or zero threshold -> clamped to floor 0.36f
        assertEquals(0.36f, AutoReplyOrchestrator.calculateRagBaseThreshold(-1.0f), 0.001f)
        assertEquals(0.36f, AutoReplyOrchestrator.calculateRagBaseThreshold(0.0f), 0.001f)

        // Very high threshold -> clamped to 0.45f
        assertEquals(0.45f, AutoReplyOrchestrator.calculateRagBaseThreshold(1.0f), 0.001f)
    }

    @Test
    fun `mergeCandidates merges pass 1 and pass 2 deduplicating by chunkId and keeping highest score`() {
        val pass1 = listOf(
            RetrievalCandidate(
                chunkId = "c1",
                headerContext = "الشحن",
                chunkText = "الشحن للقاهرة 50 جنيه",
                similarityScore = 0.60f,
                baseSimilarityScore = 0.60f
            ),
            RetrievalCandidate(
                chunkId = "c2",
                headerContext = "الاسترجاع",
                chunkText = "فترة الاسترجاع 14 يوم",
                similarityScore = 0.42f,
                baseSimilarityScore = 0.42f
            )
        )

        val pass2 = listOf(
            // Overlapping chunk with c2, but with higher score found during refined RE_QUERY
            RetrievalCandidate(
                chunkId = "c2",
                headerContext = "الاسترجاع",
                chunkText = "فترة الاسترجاع 14 يوم",
                similarityScore = 0.72f,
                baseSimilarityScore = 0.72f
            ),
            // New chunk found in Pass 2
            RetrievalCandidate(
                chunkId = "c3",
                headerContext = "طرق الدفع",
                chunkText = "الدفع عند الاستلام متاح",
                similarityScore = 0.55f,
                baseSimilarityScore = 0.55f
            ),
            // Noise chunk below minScore (0.36f)
            RetrievalCandidate(
                chunkId = "noise",
                headerContext = "عام",
                chunkText = "نص عشوائي غير مفيد",
                similarityScore = 0.28f,
                baseSimilarityScore = 0.28f
            )
        )

        val merged = AutoReplyOrchestrator.mergeCandidates(pass1, pass2, limit = 5, minScore = 0.36f)

        // c1 (0.60), c2 (0.72 - highest), c3 (0.55). Noise is filtered out.
        assertEquals(3, merged.size)

        // Highest score first: c2 (0.72)
        assertEquals("c2", merged[0].chunkId)
        assertEquals(0.72f, merged[0].similarityScore, 0.001f)

        // Second: c1 (0.60)
        assertEquals("c1", merged[1].chunkId)
        assertEquals(0.60f, merged[1].similarityScore, 0.001f)

        // Third: c3 (0.55)
        assertEquals("c3", merged[2].chunkId)
        assertEquals(0.55f, merged[2].similarityScore, 0.001f)

        // Verify Pass 1 chunks were NOT wiped out
        assertTrue(merged.any { it.chunkId == "c1" })
    }

    @Test
    fun `mergeCandidates respects limit parameter`() {
        val pass1 = (1..5).map { i ->
            RetrievalCandidate(
                chunkId = "p1_$i",
                headerContext = "Header",
                chunkText = "Chunk text $i",
                similarityScore = 0.40f + (i * 0.05f),
                baseSimilarityScore = 0.40f + (i * 0.05f)
            )
        }
        val pass2 = (6..10).map { i ->
            RetrievalCandidate(
                chunkId = "p2_$i",
                headerContext = "Header",
                chunkText = "Chunk text $i",
                similarityScore = 0.40f + (i * 0.05f),
                baseSimilarityScore = 0.40f + (i * 0.05f)
            )
        }

        val merged = AutoReplyOrchestrator.mergeCandidates(pass1, pass2, limit = 3, minScore = 0.36f)

        assertEquals(3, merged.size)
        assertEquals("p2_10", merged[0].chunkId)
        assertEquals("p2_9", merged[1].chunkId)
        assertEquals("p2_8", merged[2].chunkId)
    }

    @Test
    fun `Pass 2 finalParsed with orderAction CREATE and valid extractedOrder successfully invokes orderDao insert`() = runBlocking {
        val orderDao = mockk<OrderDao>()
        val chatHistoryDao = mockk<ChatHistoryDao>()
        coEvery { orderDao.getActiveMatchingOrder(any()) } returns null
        coEvery { orderDao.insert(any()) } returns 101L

        val orchestrator = createTestOrchestrator(orderDao = orderDao, chatHistoryDao = chatHistoryDao)

        val finalParsed = AgenticLlmResponse(
            action = "REPLY",
            orderAction = "CREATE",
            finalReplyText = "تم تسجيل طلبك يا فندم ✅",
            extractedOrder = ExtractedOrder(
                customerName = "أحمد محمد",
                deliveryAddress = "المعادي، القاهرة",
                orderDetails = "حذاء جلدي مقاس 43",
                customNotes = "التسليم بعد الخامسة مساء"
            )
        )

        val processed = ProcessedNotification(
            contactName = "عميل تجريبي",
            originalText = "عايز حذاء مقاس 43 المعادي",
            normalizedText = "عايز حذاء مقاس 43 المعادي",
            notificationKey = "key_123",
            postTime = System.currentTimeMillis(),
            context = mockk(relaxed = true)
        )

        val override = orchestrator.handleFinalPassOrderActions(finalParsed, processed)

        // No override was required since it was cleanly ingested
        assertEquals(null, override)

        // Verify orderDao.insert was invoked with expected entity details
        coVerify(exactly = 1) {
            orderDao.insert(match {
                it.contactName == "عميل تجريبي" &&
                it.customerNameFromLlm == "أحمد محمد" &&
                it.deliveryAddress == "المعادي، القاهرة" &&
                it.orderDetails == "حذاء جلدي مقاس 43" &&
                it.status == OrderStatus.PENDING.name
            })
        }
    }

    @Test
    fun `Pass 2 finalParsed with existing order triggers duplicate verification pivot`() = runBlocking {
        val orderDao = mockk<OrderDao>()
        val chatHistoryDao = mockk<ChatHistoryDao>()
        val existingOrder = OrderEntity(
            id = 55L,
            contactName = "عميل تجريبي",
            orderDetails = "حذاء",
            status = OrderStatus.PENDING.name
        )
        coEvery { orderDao.getActiveMatchingOrder("عميل تجريبي") } returns existingOrder
        coEvery { chatHistoryDao.getRecentMessages("عميل تجريبي", limit = 6) } returns emptyList()

        val orchestrator = createTestOrchestrator(orderDao = orderDao, chatHistoryDao = chatHistoryDao)

        val finalParsed = AgenticLlmResponse(
            action = "REPLY",
            orderAction = "CREATE",
            finalReplyText = "تمام سجلت",
            extractedOrder = ExtractedOrder(
                customerName = "أحمد",
                orderDetails = "حذاء أسود"
            )
        )

        val processed = ProcessedNotification(
            contactName = "عميل تجريبي",
            originalText = "أوردر جديد",
            normalizedText = "اوردر جديد",
            notificationKey = "key_1",
            postTime = System.currentTimeMillis(),
            context = mockk(relaxed = true)
        )

        val override = orchestrator.handleFinalPassOrderActions(finalParsed, processed)

        assertTrue(override?.contains("أنا شايف إنك لسه عامل أوردر مشابه") == true)
        coVerify(exactly = 0) { orderDao.insert(any()) }
    }

    @Test
    fun `anti-echo guard does NOT trigger for identical queries sent more than 2 hours apart`() {
        val now = 10_000_000L
        val threeHoursAgo = now - TimeUnit.HOURS.toMillis(3)

        val chatHistory = listOf(
            ChatHistoryEntity(
                id = 1L,
                contactName = "عميل تجريبي",
                messageText = "الشحن 50 جنيه للقاهرة.",
                isFromUser = false,
                timestamp = threeHoursAgo
            )
        )

        val candidateReply = "الشحن 50 جنيه للقاهرة."

        val isEcho = AutoReplyOrchestrator.isEchoRepetition(
            candidateReply = candidateReply,
            chatHistory = chatHistory,
            nowMs = now
        )

        // Must NOT trigger because it is outside the 2-hour session window
        assertFalse(isEcho)
    }

    @Test
    fun `anti-echo guard triggers for identical reply within the 2-hour session window`() {
        val now = 10_000_000L
        val thirtyMinutesAgo = now - TimeUnit.MINUTES.toMillis(30)

        val chatHistory = listOf(
            ChatHistoryEntity(
                id = 1L,
                contactName = "عميل تجريبي",
                messageText = "الشحن 50 جنيه للقاهرة.",
                isFromUser = false,
                timestamp = thirtyMinutesAgo
            )
        )

        val candidateReply = "الشحن 50 جنيه للقاهرة."

        val isEcho = AutoReplyOrchestrator.isEchoRepetition(
            candidateReply = candidateReply,
            chatHistory = chatHistory,
            nowMs = now
        )

        // Must trigger because it is within 2 hours
        assertTrue(isEcho)
    }
}
