package com.example.persona.features.auto_reply.domain.orchestrator

import android.content.Context
import com.example.persona.core.database.dao.BusinessKnowledgeDao
import com.example.persona.core.database.dao.ChatHistoryDao
import com.example.persona.core.database.dao.ConversationMetadataDao
import com.example.persona.core.database.dao.FailedQueryDao
import com.example.persona.core.database.dao.OrderDao
import com.example.persona.core.database.entity.ChatHistoryEntity
import com.example.persona.core.database.entity.OrderEntity
import com.example.persona.core.database.entity.OrderStatus
import com.example.persona.core.network.dto.AgenticLlmResponse
import com.example.persona.core.network.dto.ExtractedOrder
import com.example.persona.features.auto_reply.data.diagnostics.NotificationSourceClass
import com.example.persona.features.auto_reply.data.repository.NotificationProcessorImpl
import com.example.persona.features.auto_reply.data.repository.SemanticRetrievalRepository
import com.example.persona.core.data.repository.UserPreferencesRepository
import com.example.persona.features.auto_reply.data.service.IncomingMessageDispatcher
import com.example.persona.features.auto_reply.domain.model.ProcessedNotification
import com.example.persona.features.auto_reply.domain.model.RetrievalCandidate
import com.example.persona.features.auto_reply.domain.model.StylePreferences
import com.example.persona.features.auto_reply.domain.model.WhatsAppNotification
import com.example.persona.features.auto_reply.domain.modifier.StylePostProcessor
import com.example.persona.features.auto_reply.domain.repository.AuditLogRepository
import com.example.persona.features.auto_reply.domain.repository.ReplyGenerationRepository
import com.example.persona.features.auto_reply.domain.usecase.DirectReplyExecutionUseCase
import com.example.persona.features.auto_reply.data.gatekeeper.CloudflareClefClient
import com.example.persona.features.auto_reply.data.gatekeeper.LiquidDecisionClient
import com.example.persona.features.auto_reply.domain.gatekeeper.InboundIntent
import com.example.persona.features.chat_import.data.embedding.EmbeddingEngine
import com.example.persona.features.chat_import.data.normalizer.ArabicTextNormalizer
import com.example.persona.features.auto_reply.domain.orchestrator.OrderIngestionResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class AutoReplyOrchestratorRagTest {

    private fun createTestOrchestrator(
        orderDao: OrderDao = mockk(relaxed = true),
        chatHistoryDao: ChatHistoryDao = mockk(relaxed = true),
        businessKnowledgeDao: BusinessKnowledgeDao = mockk(relaxed = true),
        conversationMetadataDao: ConversationMetadataDao = mockk(relaxed = true),
        semanticRetrievalRepository: SemanticRetrievalRepository = mockk(relaxed = true),
        replyGenerationRepository: ReplyGenerationRepository = mockk(relaxed = true),
        directReplyExecutionUseCase: DirectReplyExecutionUseCase = mockk(relaxed = true),
        failedQueryDao: FailedQueryDao = mockk(relaxed = true),
        prefsRepository: UserPreferencesRepository = mockk(relaxed = true),
        processor: NotificationProcessorImpl = mockk(relaxed = true),
        embeddingEngine: EmbeddingEngine = mockk(relaxed = true),
        stylePostProcessor: StylePostProcessor = StylePostProcessor(),
        auditLogRepository: AuditLogRepository = mockk(relaxed = true),
        cloudflareClefClient: CloudflareClefClient = mockk(relaxed = true),
        liquidDecisionClient: LiquidDecisionClient = mockk(relaxed = true)
    ): AutoReplyOrchestrator = AutoReplyOrchestrator(
        incomingMessageDispatcher = mockk(relaxed = true),
        processor = processor,
        embeddingEngine = embeddingEngine,
        semanticRetrievalRepository = semanticRetrievalRepository,
        replyGenerationRepository = replyGenerationRepository,
        directReplyExecutionUseCase = directReplyExecutionUseCase,
        auditLogRepository = auditLogRepository,
        prefsRepository = prefsRepository,
        stylePostProcessor = stylePostProcessor,
        normalizer = ArabicTextNormalizer(),
        failedQueryDao = failedQueryDao,
        chatHistoryDao = chatHistoryDao,
        orderDao = orderDao,
        businessKnowledgeDao = businessKnowledgeDao,
        conversationMetadataDao = conversationMetadataDao,
        context = mockk(relaxed = true),
        scope = CoroutineScope(Dispatchers.Unconfined),
        cloudflareClefClient = cloudflareClefClient,
        liquidDecisionClient = liquidDecisionClient
    )

    @Test
    fun `calculateRagBaseThreshold maps preference to realistic MiniLM range with floor`() {
        // Aggressive (0.65f) -> 0.42f
        assertEquals(0.42f, AutoReplyOrchestrator.calculateRagBaseThreshold(0.65f), 0.001f)

        // Balanced (0.75f) -> 0.46f
        assertEquals(0.46f, AutoReplyOrchestrator.calculateRagBaseThreshold(0.75f), 0.001f)

        // Conservative (0.85f) -> 0.50f
        assertEquals(0.50f, AutoReplyOrchestrator.calculateRagBaseThreshold(0.85f), 0.001f)

        // Negative or zero threshold -> clamped to floor 0.42f
        assertEquals(0.42f, AutoReplyOrchestrator.calculateRagBaseThreshold(-1.0f), 0.001f)
        assertEquals(0.42f, AutoReplyOrchestrator.calculateRagBaseThreshold(0.0f), 0.001f)

        // Very high threshold -> clamped to 0.50f
        assertEquals(0.50f, AutoReplyOrchestrator.calculateRagBaseThreshold(1.0f), 0.001f)
    }

    @Test
    fun `mergeCandidates places Pass 2 chunks ahead of higher-scoring Pass 1 chunks to prevent catalog eviction`() {
        val pass1 = listOf(
            RetrievalCandidate(
                chunkId = "noise_pass1",
                headerContext = "أمثلة",
                chunkText = "أمثلة: طلب مساء الخميس",
                similarityScore = 0.95f,
                baseSimilarityScore = 0.95f
            ),
            RetrievalCandidate(
                chunkId = "c1",
                headerContext = "الشحن",
                chunkText = "الشحن للقاهرة 50 جنيه",
                similarityScore = 0.60f,
                baseSimilarityScore = 0.60f
            )
        )

        val pass2 = listOf(
            // Product chunk retrieved in Pass 2 RE_QUERY with score 0.55
            RetrievalCandidate(
                chunkId = "product_card",
                headerContext = "بيانات المنتج",
                chunkText = "بيانات المنتج: محفظة جيب كارد السعر 150 جنيه",
                similarityScore = 0.55f,
                baseSimilarityScore = 0.55f
            ),
            // Noise chunk below minScore (0.38f)
            RetrievalCandidate(
                chunkId = "noise_low",
                headerContext = "عام",
                chunkText = "نص عشوائي غير مفيد",
                similarityScore = 0.28f,
                baseSimilarityScore = 0.28f
            )
        )

        val merged = AutoReplyOrchestrator.mergeCandidates(pass1, pass2, limit = 8, minScore = 0.38f)

        // Noise_low is filtered (< 0.38). 3 chunks remain.
        assertEquals(3, merged.size)

        // Pass 2 chunks MUST come FIRST in rank order!
        assertEquals("product_card", merged[0].chunkId)
        assertEquals(0.55f, merged[0].similarityScore, 0.001f)

        // Non-duplicate Pass 1 chunks appended afterward
        assertEquals("noise_pass1", merged[1].chunkId)
        assertEquals("c1", merged[2].chunkId)
    }

    @Test
    fun `decodeAgenticResponseSafely repairs unescaped inner quotes in final reply text`() {
        val rawWithInnerQuotes = """
            {
              "action": "REPLY",
              "refined_search_keyword": null,
              "order_action": null,
              "final_reply_text": "أنا مش بس "أنا" يا فندم، أنا مساعدك هنا عشان أسهل عليك!",
              "extracted_order": null
            }
        """.trimIndent()

        val parsed = AutoReplyOrchestrator.decodeAgenticResponseSafely(rawWithInnerQuotes)

        assertEquals("REPLY", parsed.action)
        assertEquals("أنا مش بس \"أنا\" يا فندم، أنا مساعدك هنا عشان أسهل عليك!", parsed.finalReplyText)
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
        val orderDao = mockk<OrderDao>(relaxed = true)
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
        coEvery { chatHistoryDao.getRecentMessagesForSource("عميل تجريبي", any(), limit = 6) } returns emptyList()

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
    fun `Customer choosing NEW order following pivot inserts separate order and does not update existing`() = runBlocking {
        val orderDao = mockk<OrderDao>(relaxed = true)
        val chatHistoryDao = mockk<ChatHistoryDao>(relaxed = true)
        val existingOrder = OrderEntity(
            id = 2L,
            contactName = "محمد علي",
            orderDetails = "قميص أبيض",
            deliveryAddress = "المعادي",
            status = OrderStatus.PENDING.name
        )
        coEvery { orderDao.getActiveMatchingOrder("محمد علي") } returns existingOrder
        coEvery { orderDao.insert(any()) } returns 3L

        val pivotTimestamp = System.currentTimeMillis() - 10_000L
        val pivotMsg = ChatHistoryEntity(
            id = 10L,
            contactName = "محمد علي",
            messageText = "أنا شايف إنك لسه عامل أوردر مشابه من شوية، تحب نعدل على الأوردر القديم ونضيف عليه التفاصيل الجديدة دي، ولا ده أوردر جديد خالص يا فندم؟",
            isFromUser = false,
            timestamp = pivotTimestamp,
            sourceClass = "BUSINESS"
        )
        coEvery { chatHistoryDao.getRecentMessagesForSource("محمد علي", "BUSINESS", limit = 6) } returns listOf(pivotMsg)

        val orchestrator = createTestOrchestrator(orderDao = orderDao, chatHistoryDao = chatHistoryDao)

        val extracted = ExtractedOrder(
            customerName = "محمد علي",
            deliveryAddress = "التجمع الخامس",
            orderDetails = "بنطلون جينز",
            customNotes = "مقاس 34"
        )

        val result = orchestrator.handleOrderCreation(
            extractedOrder = extracted,
            contactName = "محمد علي",
            customerText = "لا اعملي اوردر جديد منفصل ب العنوان الجديد",
            orderAction = "CREATE",
            sourceClass = "BUSINESS"
        )

        assertTrue(result is OrderIngestionResult.Created)
        assertEquals(3L, (result as OrderIngestionResult.Created).orderId)

        // Verify orderDao.insert was called for new order with new address
        coVerify(exactly = 1) {
            orderDao.insert(match {
                it.contactName == "محمد علي" &&
                it.deliveryAddress == "التجمع الخامس" &&
                it.orderDetails == "بنطلون جينز" &&
                it.status == OrderStatus.PENDING.name
            })
        }

        // Verify existing order was NOT updated
        coVerify(exactly = 0) {
            orderDao.updateDeliveryAddress(eq(2L), any())
        }
    }

    @Test
    fun `Customer choosing UPDATE order following pivot merges into existing order without inserting new order`() = runBlocking {
        val orderDao = mockk<OrderDao>(relaxed = true)
        val chatHistoryDao = mockk<ChatHistoryDao>(relaxed = true)
        val existingOrder = OrderEntity(
            id = 2L,
            contactName = "محمد علي",
            orderDetails = "قميص أبيض",
            deliveryAddress = "المعادي",
            status = OrderStatus.PENDING.name
        )
        coEvery { orderDao.getActiveMatchingOrder("محمد علي") } returns existingOrder

        val pivotTimestamp = System.currentTimeMillis() - 10_000L
        val pivotMsg = ChatHistoryEntity(
            id = 10L,
            contactName = "محمد علي",
            messageText = "أنا شايف إنك لسه عامل أوردر مشابه من شوية، تحب نعدل على الأوردر القديم ونضيف عليه التفاصيل الجديدة دي، ولا ده أوردر جديد خالص يا فندم؟",
            isFromUser = false,
            timestamp = pivotTimestamp,
            sourceClass = "BUSINESS"
        )
        coEvery { chatHistoryDao.getRecentMessagesForSource("محمد علي", "BUSINESS", limit = 6) } returns listOf(pivotMsg)

        val orchestrator = createTestOrchestrator(orderDao = orderDao, chatHistoryDao = chatHistoryDao)

        val extracted = ExtractedOrder(
            customerName = "محمد علي",
            deliveryAddress = "المعادي، شارع النصر",
            orderDetails = "قميص أبيض مقاس L",
            customNotes = null
        )

        val result = orchestrator.handleOrderCreation(
            extractedOrder = extracted,
            contactName = "محمد علي",
            customerText = "عدل على القديم وخليه شارع النصر",
            orderAction = "UPDATE",
            sourceClass = "BUSINESS"
        )

        assertTrue(result is OrderIngestionResult.Merged)
        assertEquals(2L, (result as OrderIngestionResult.Merged).orderId)

        // Verify orderDao.insert was NOT called
        coVerify(exactly = 0) {
            orderDao.insert(any())
        }

        // Verify existing order was updated
        coVerify(exactly = 1) {
            orderDao.updateDeliveryAddress(2L, "المعادي، شارع النصر")
        }
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
            currentUserText = "فين عنوانكم؟",
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
            currentUserText = "فين عنوانكم؟",
            chatHistory = chatHistory,
            nowMs = now
        )

        // Must trigger because it is within 2 hours
        assertTrue(isEcho)
    }

    @Test
    fun `anti-echo guard does not trigger when user repeats the exact same query`() {
        val now = 10_000_000L
        val tenMinutesAgo = now - TimeUnit.MINUTES.toMillis(10)

        val chatHistory = listOf(
            ChatHistoryEntity(
                id = 2L,
                contactName = "عميل تجريبي",
                messageText = "الشحن 50 جنيه للقاهرة.",
                isFromUser = false,
                timestamp = tenMinutesAgo + 1000L
            ),
            ChatHistoryEntity(
                id = 1L,
                contactName = "عميل تجريبي",
                messageText = "بكام الشحن للقاهرة؟",
                isFromUser = true,
                timestamp = tenMinutesAgo
            )
        )

        val candidateReply = "الشحن 50 جنيه للقاهرة."

        val isEcho = AutoReplyOrchestrator.isEchoRepetition(
            candidateReply = candidateReply,
            currentUserText = "بكام الشحن للقاهرة؟",
            chatHistory = chatHistory,
            nowMs = now
        )

        // Must NOT trigger because user intentionally repeated their question
        assertFalse(isEcho)
    }

    @Test
    fun `filterDuplicateNoteLines removes existing lines and retains new ones`() {
        val existingNotes = "العنوان: التجمع الخامس\nالاسم: أحمد مصطفى"
        val newNotes = "العنوان: التجمع الخامس\nالتفاصيل: محفظة جيب كارد\nملاحظات: الاتصال مسبقاً"

        val filtered = AutoReplyOrchestrator.filterDuplicateNoteLines(
            existingNotes = existingNotes,
            newNotes = newNotes,
            existingDeliveryAddress = "التجمع الخامس",
            existingCustomerName = "أحمد مصطفى"
        )

        val expected = "التفاصيل: محفظة جيب كارد\nملاحظات: الاتصال مسبقاً"
        assertEquals(expected, filtered)
    }

    @Test
    fun `filterDuplicateNoteLines drops address already stored in discrete column`() {
        val existingNotes: String? = null
        val newNotes = "العنوان: مدينة نصر"

        val filtered = AutoReplyOrchestrator.filterDuplicateNoteLines(
            existingNotes = existingNotes,
            newNotes = newNotes,
            existingDeliveryAddress = "مدينة نصر"
        )

        assertEquals("", filtered)
    }

    @Test
    fun `empty RAG candidates proceed to LLM generation without Tier-B HITL trigger`() = runBlocking {
        val semanticRepo = mockk<SemanticRetrievalRepository>()
        val replyRepo = mockk<ReplyGenerationRepository>()
        val directReplyUseCase = mockk<DirectReplyExecutionUseCase>(relaxed = true)
        val chatHistoryDao = mockk<ChatHistoryDao>(relaxed = true)
        val orderDao = mockk<OrderDao>(relaxed = true)
        val failedQueryDao = mockk<FailedQueryDao>(relaxed = true)
        val prefsRepo = mockk<UserPreferencesRepository>()
        val processor = mockk<NotificationProcessorImpl>()

        coEvery { prefsRepo.isAutoReplyEnabledSync() } returns true
        coEvery { prefsRepo.getStylePreferencesSync() } returns StylePreferences(cooldownMins = 0)

        val processed = ProcessedNotification(
            contactName = "عميل تجريبي",
            originalText = "السلام عليكم",
            normalizedText = "السلام عليكم",
            notificationKey = "key_100",
            postTime = System.currentTimeMillis(),
            context = mockk(relaxed = true),
            sourceClass = NotificationSourceClass.BUSINESS
        )
        coEvery { processor.process(any()) } returns processed
        coEvery { chatHistoryDao.insert(any()) } returns 1L
        coEvery { chatHistoryDao.claimForProcessing(any()) } returns 0 // Skip async background embedding in test
        coEvery { chatHistoryDao.getRecentMessagesExcept(any(), any(), any()) } returns emptyList()
        coEvery { chatHistoryDao.getRecentMessagesExceptForSource(any(), any(), any(), any()) } returns emptyList()
        coEvery { orderDao.getActiveOrdersByContact(any()) } returns emptyList()
        coEvery { orderDao.getLatestOrderByContact(any()) } returns null
        coEvery { orderDao.getOrderStatusCounts(any()) } returns emptyList()
        coEvery { semanticRepo.retrieveCandidates(any(), any(), any(), any()) } returns Pair(emptyList(), 0.0f)

        val embeddingEngine = mockk<EmbeddingEngine>()
        coEvery { embeddingEngine.embedForAutoReply(any()) } returns floatArrayOf(0.1f, 0.2f)
        coEvery { semanticRepo.retrieveCandidates(any(), any(), any(), any()) } returns Pair(emptyList(), 0.0f)
        coEvery { directReplyUseCase.execute(any(), any(), any(), any()) } returns com.example.persona.features.auto_reply.domain.usecase.ReplyExecutionResult.Success

        // LLM handles greeting naturally
        coEvery {
            replyRepo.generateReply(
                notification = processed,
                candidates = emptyList(),
                isOngoingConversation = any(),
                chatHistory = any(),
                activeOrders = any(),
                personalChunks = any(),
                maxTokens = any(),
                inboundIntent = any()
            )
        } returns """{"action":"REPLY","refined_search_keyword":null,"order_action":null,"final_reply_text":"وعليكم السلام! نورتنا يا فندم 😊","extracted_order":null}"""

        val orchestrator = createTestOrchestrator(
            orderDao = orderDao,
            chatHistoryDao = chatHistoryDao,
            semanticRetrievalRepository = semanticRepo,
            replyGenerationRepository = replyRepo,
            directReplyExecutionUseCase = directReplyUseCase,
            failedQueryDao = failedQueryDao,
            prefsRepository = prefsRepo,
            processor = processor,
            embeddingEngine = embeddingEngine
        )

        val rawNotification = WhatsAppNotification(
            title = "عميل تجريبي",
            text = "السلام عليكم",
            postTime = System.currentTimeMillis(),
            notificationKey = "key_100"
        )

        orchestrator.processNotificationSequentially(rawNotification)

        // Verify replyRepo.generateReply was called with candidates = emptyList()
        coVerify(exactly = 1) {
            replyRepo.generateReply(
                notification = processed,
                candidates = emptyList(),
                isOngoingConversation = any(),
                chatHistory = any(),
                activeOrders = any(),
                personalChunks = any(),
                maxTokens = any(),
                inboundIntent = any()
            )
        }

        // Verify directReplyExecutionUseCase executed the greeting reply
        coVerify(exactly = 1) {
            directReplyUseCase.execute(
                processed.notificationKey,
                match { it.contains("وعليكم السلام") },
                processed.contactName,
                any()
            )
        }

        // Verify failedQueryDao.insert was NEVER called (no Tier-B empty-RAG HITL failure logged)
        coVerify(exactly = 0) {
            failedQueryDao.insert(any())
        }
    }

    @Test
    fun `sanitizeLlmJson removes markdown code blocks and extracts substring between curly braces`() {
        val rawWithFence = "```json\n{\"action\": \"REPLY\", \"final_reply_text\": \"تمام\"}\n```"
        assertEquals("{\"action\": \"REPLY\", \"final_reply_text\": \"تمام\"}", AutoReplyOrchestrator.sanitizeLlmJson(rawWithFence))

        val rawWithLeadingAndTrailingNoise = "Here is the response: {\"action\": \"REPLY\", \"final_reply_text\": \"تمام\"} hope this helps!"
        assertEquals("{\"action\": \"REPLY\", \"final_reply_text\": \"تمام\"}", AutoReplyOrchestrator.sanitizeLlmJson(rawWithLeadingAndTrailingNoise))

        val plainJson = "{\"action\": \"REPLY\", \"final_reply_text\": \"تمام\"}"
        assertEquals(plainJson, AutoReplyOrchestrator.sanitizeLlmJson(plainJson))
    }

    @Test
    fun `Pass 1 LLM with action UPDATE and valid reply text dispatches as normal reply without HITL fallback`() = runBlocking {
        val semanticRepo = mockk<SemanticRetrievalRepository>()
        val replyRepo = mockk<ReplyGenerationRepository>()
        val directReplyUseCase = mockk<DirectReplyExecutionUseCase>(relaxed = true)
        val chatHistoryDao = mockk<ChatHistoryDao>(relaxed = true)
        val orderDao = mockk<OrderDao>(relaxed = true)
        val failedQueryDao = mockk<FailedQueryDao>(relaxed = true)
        val prefsRepo = mockk<UserPreferencesRepository>()
        val processor = mockk<NotificationProcessorImpl>()

        coEvery { prefsRepo.isAutoReplyEnabledSync() } returns true
        coEvery { prefsRepo.getStylePreferencesSync() } returns StylePreferences(cooldownMins = 0)

        val processed = ProcessedNotification(
            contactName = "عميل تحديث",
            originalText = "العنوان مدينة نصر الحي السابع",
            normalizedText = "العنوان مدينة نصر الحي السابع",
            notificationKey = "key_update",
            postTime = System.currentTimeMillis(),
            context = mockk(relaxed = true),
            sourceClass = NotificationSourceClass.BUSINESS
        )
        coEvery { processor.process(any()) } returns processed
        coEvery { chatHistoryDao.insert(any()) } returns 1L
        coEvery { chatHistoryDao.claimForProcessing(any()) } returns 0
        coEvery { chatHistoryDao.getRecentMessagesExcept(any(), any(), any()) } returns emptyList()
        coEvery { chatHistoryDao.getRecentMessagesExceptForSource(any(), any(), any(), any()) } returns emptyList()

        val existingOrder = OrderEntity(
            id = 55L,
            contactName = "عميل تحديث",
            deliveryAddress = null,
            status = OrderStatus.PENDING.name
        )
        coEvery { orderDao.getActiveOrdersByContact("عميل تحديث") } returns listOf(existingOrder)
        coEvery { orderDao.getActiveMatchingOrder("عميل تحديث") } returns existingOrder
        coEvery { orderDao.getLatestOrderByContact(any()) } returns null
        coEvery { orderDao.getOrderStatusCounts(any()) } returns emptyList()

        val embeddingEngine = mockk<EmbeddingEngine>()
        coEvery { embeddingEngine.embedForAutoReply(any()) } returns floatArrayOf(0.1f, 0.2f)
        coEvery { semanticRepo.retrieveCandidates(any(), any(), any(), any()) } returns Pair(emptyList(), 0.0f)
        coEvery { directReplyUseCase.execute(any(), any(), any(), any()) } returns com.example.persona.features.auto_reply.domain.usecase.ReplyExecutionResult.Success

        coEvery {
            replyRepo.generateReply(
                notification = processed,
                candidates = emptyList(),
                isOngoingConversation = any(),
                chatHistory = any(),
                activeOrders = any(),
                personalChunks = any(),
                maxTokens = any(),
                inboundIntent = any()
            )
        } returns """{"action":"UPDATE","refined_search_keyword":null,"order_action":"UPDATE","final_reply_text":"تمام يا فندم، تم تسجيل العنوان الجديد ✅","extracted_order":{"customer_name":null,"delivery_address":"مدينة نصر الحي السابع","order_details":null,"custom_notes":null}}"""

        val orchestrator = createTestOrchestrator(
            orderDao = orderDao,
            chatHistoryDao = chatHistoryDao,
            semanticRetrievalRepository = semanticRepo,
            replyGenerationRepository = replyRepo,
            directReplyExecutionUseCase = directReplyUseCase,
            failedQueryDao = failedQueryDao,
            prefsRepository = prefsRepo,
            processor = processor,
            embeddingEngine = embeddingEngine
        )

        val rawNotification = WhatsAppNotification(
            title = "عميل تحديث",
            text = "العنوان مدينة نصر الحي السابع",
            postTime = System.currentTimeMillis(),
            notificationKey = "key_update"
        )

        orchestrator.processNotificationSequentially(rawNotification)

        // Verify discrete address column updated
        coVerify(exactly = 1) {
            orderDao.updateDeliveryAddress(55L, "مدينة نصر الحي السابع")
        }

        // Verify directReplyExecutionUseCase executed the greeting reply
        coVerify(exactly = 1) {
            directReplyUseCase.execute(
                processed.notificationKey,
                match { it.contains("تم تسجيل العنوان الجديد") },
                processed.contactName,
                any()
            )
        }
    }

    @Test
    fun `applyOrderUpdates updates discrete columns and avoids duplicating existing note lines`() = runBlocking {
        val orderDao = mockk<OrderDao>(relaxed = true)
        val orchestrator = createTestOrchestrator(orderDao = orderDao)

        val existingOrder = OrderEntity(
            id = 77L,
            contactName = "عميل تجريبي",
            customerNameFromLlm = "أحمد القديم",
            deliveryAddress = "المعادي",
            customNotes = "العنوان: المعادي\nملاحظات: قديمة",
            status = OrderStatus.PENDING.name
        )

        val extracted = ExtractedOrder(
            customerName = "أحمد الجديد",
            deliveryAddress = "الزمالك",
            orderDetails = "ساعة يد",
            customNotes = "ملاحظات: قديمة"
        )

        orchestrator.applyOrderUpdates(existingOrder, extracted)

        coVerify(exactly = 1) { orderDao.updateCustomerName(77L, "أحمد الجديد") }
        coVerify(exactly = 1) { orderDao.updateDeliveryAddress(77L, "الزمالك") }
        coVerify(exactly = 1) { orderDao.updateOrderDetails(77L, "ساعة يد") }
        coVerify(exactly = 1) {
            orderDao.appendCustomNotes(77L, match {
                it.contains("التفاصيل: ساعة يد") && !it.contains("ملاحظات: قديمة") && !it.contains("العنوان: الزمالك")
            })
        }
    }

    @Test
    fun `conversational message with مشكلة or ضروري does NOT trigger safety alert filter`() = runBlocking {
        val semanticRepo = mockk<SemanticRetrievalRepository>(relaxed = true)
        val replyRepo = mockk<ReplyGenerationRepository>(relaxed = true)
        val directReplyUseCase = mockk<DirectReplyExecutionUseCase>(relaxed = true)
        val chatHistoryDao = mockk<ChatHistoryDao>(relaxed = true)
        val orderDao = mockk<OrderDao>(relaxed = true)
        val auditLogRepo = mockk<AuditLogRepository>(relaxed = true)
        val prefsRepo = mockk<UserPreferencesRepository>(relaxed = true)
        val processor = mockk<NotificationProcessorImpl>()

        coEvery { prefsRepo.isAutoReplyEnabledSync() } returns true
        coEvery { prefsRepo.getStylePreferencesSync() } returns StylePreferences(cooldownMins = 0)
        coEvery { orderDao.getActiveOrdersByContact(any()) } returns emptyList()
        coEvery { orderDao.getLatestOrderByContact(any()) } returns null
        coEvery { orderDao.getOrderStatusCounts(any()) } returns emptyList()
        coEvery { semanticRepo.retrieveCandidates(any(), any(), any(), any()) } returns Pair(emptyList(), 0.0f)

        val textWithProblem = "محتاج نبقا اصحاب ؟ ولا ف مشكلة ؟"
        val processed = ProcessedNotification(
            contactName = "صديق",
            originalText = textWithProblem,
            normalizedText = ArabicTextNormalizer().normalize(textWithProblem) ?: textWithProblem,
            notificationKey = "key_problem",
            postTime = System.currentTimeMillis(),
            context = mockk(relaxed = true),
            sourceClass = NotificationSourceClass.BUSINESS
        )
        coEvery { processor.process(any()) } returns processed
        coEvery { chatHistoryDao.insert(any()) } returns 1L
        coEvery { chatHistoryDao.claimForProcessing(any()) } returns 0

        val embeddingEngine = mockk<EmbeddingEngine>()
        coEvery { embeddingEngine.embedForAutoReply(any()) } returns floatArrayOf(0.1f, 0.2f)

        coEvery {
            replyRepo.generateReply(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns """{"action":"REPLY","refined_search_keyword":null,"order_action":null,"final_reply_text":"أحلى صحاب يا غالي، ومفيش أي مشكلة خالص! 💙 قولّي محتاج إيه؟","extracted_order":null}"""

        val orchestrator = createTestOrchestrator(
            orderDao = orderDao,
            chatHistoryDao = chatHistoryDao,
            semanticRetrievalRepository = semanticRepo,
            replyGenerationRepository = replyRepo,
            directReplyExecutionUseCase = directReplyUseCase,
            auditLogRepository = auditLogRepo,
            prefsRepository = prefsRepo,
            processor = processor,
            embeddingEngine = embeddingEngine
        )

        orchestrator.processNotificationSequentially(
            WhatsAppNotification(
                title = "صديق",
                text = textWithProblem,
                postTime = System.currentTimeMillis(),
                notificationKey = "key_problem"
            )
        )

        // Safety alert MUST NOT be queued
        coVerify(exactly = 0) {
            auditLogRepo.queuePendingReply(any(), match { it.contains("[Safety Alert]") })
        }
        // Must proceed to LLM reply generation
        coVerify(exactly = 1) {
            replyRepo.generateReply(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `high-acuity crisis words like مستشفى or كارثة trigger emergency safety alarm and queue`() = runBlocking {
        val auditLogRepo = mockk<AuditLogRepository>(relaxed = true)
        val replyRepo = mockk<ReplyGenerationRepository>(relaxed = true)
        val prefsRepo = mockk<UserPreferencesRepository>(relaxed = true)
        val processor = mockk<NotificationProcessorImpl>()

        coEvery { prefsRepo.isAutoReplyEnabledSync() } returns true
        coEvery { prefsRepo.getStylePreferencesSync() } returns StylePreferences(cooldownMins = 0)

        val emergencyText = "الحقني في مصيبة حصلت"
        val processed = ProcessedNotification(
            contactName = "طوارئ",
            originalText = emergencyText,
            normalizedText = ArabicTextNormalizer().normalize(emergencyText) ?: emergencyText,
            notificationKey = "key_emergency",
            postTime = System.currentTimeMillis(),
            context = mockk(relaxed = true),
            sourceClass = NotificationSourceClass.BUSINESS
        )
        coEvery { processor.process(any()) } returns processed

        val orchestrator = createTestOrchestrator(
            replyGenerationRepository = replyRepo,
            auditLogRepository = auditLogRepo,
            prefsRepository = prefsRepo,
            processor = processor
        )

        orchestrator.processNotificationSequentially(
            WhatsAppNotification(
                title = "طوارئ",
                text = emergencyText,
                postTime = System.currentTimeMillis(),
                notificationKey = "key_emergency"
            )
        )

        // Safety alert MUST be queued
        coVerify(atLeast = 1) {
            auditLogRepo.queuePendingReply(any(), match { it.contains("[Safety Alert]") })
        }
        // LLM generation must NOT be called
        coVerify(exactly = 0) {
            replyRepo.generateReply(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `gatekeeper receives formatted context prefix when previous assistant turn exists in chat history`() = runBlocking {
        val chatHistoryDao = mockk<ChatHistoryDao>(relaxed = true)
        val cfClient = mockk<CloudflareClefClient>(relaxed = true)
        val processor = mockk<NotificationProcessorImpl>()
        val prefsRepo = mockk<UserPreferencesRepository>(relaxed = true)
        coEvery { prefsRepo.isAutoReplyEnabledSync() } returns true
        coEvery { prefsRepo.getStylePreferencesSync() } returns StylePreferences(cooldownMins = 0)

        val previousTurn = ChatHistoryEntity(
            id = 1L,
            contactName = "عميل سياقي",
            messageText = "تمام، تحب تشوف المقاسات ولا الألوان الأول؟",
            timestamp = System.currentTimeMillis() - 10_000L,
            isFromUser = false,
            sourceClass = NotificationSourceClass.BUSINESS.name
        )
        coEvery {
            chatHistoryDao.getRecentMessagesExceptForSource(
                contactName = "عميل سياقي",
                sourceClass = any(),
                excludeId = any(),
                limit = any()
            )
        } returns listOf(previousTurn)

        val inboundQuery = "وريني المقاسات المتاحة"
        val processed = ProcessedNotification(
            contactName = "عميل سياقي",
            originalText = inboundQuery,
            normalizedText = inboundQuery,
            notificationKey = "key_ctx_1",
            postTime = System.currentTimeMillis(),
            context = mockk(relaxed = true),
            sourceClass = NotificationSourceClass.BUSINESS
        )
        coEvery { processor.process(any()) } returns processed
        coEvery { cfClient.decide(any(), any()) } returns InboundIntent.CATALOG_INQUIRY

        val orchestrator = createTestOrchestrator(
            chatHistoryDao = chatHistoryDao,
            prefsRepository = prefsRepo,
            processor = processor,
            cloudflareClefClient = cfClient
        )

        orchestrator.processNotificationSequentially(
            WhatsAppNotification(
                title = "عميل سياقي",
                text = inboundQuery,
                postTime = System.currentTimeMillis(),
                notificationKey = "key_ctx_1"
            )
        )

        val expectedContextPrefix = "[الرد السابق]: تمام، تحب تشوف المقاسات ولا الألوان الأول؟\n[رسالة العميل]: $inboundQuery"
        coVerify(atLeast = 1) {
            cfClient.decide(expectedContextPrefix, timeoutMs = 1150L)
        }
    }

    @Test
    fun `gatekeeper receives raw query text without context prefix when no previous assistant turn exists`() = runBlocking {
        val chatHistoryDao = mockk<ChatHistoryDao>(relaxed = true)
        val cfClient = mockk<CloudflareClefClient>(relaxed = true)
        val processor = mockk<NotificationProcessorImpl>()
        val prefsRepo = mockk<UserPreferencesRepository>(relaxed = true)
        coEvery { prefsRepo.isAutoReplyEnabledSync() } returns true
        coEvery { prefsRepo.getStylePreferencesSync() } returns StylePreferences(cooldownMins = 0)

        coEvery {
            chatHistoryDao.getRecentMessagesExceptForSource(
                contactName = "عميل جديد",
                sourceClass = any(),
                excludeId = any(),
                limit = any()
            )
        } returns emptyList()

        val inboundQuery = "عندكم هوديز؟"
        val processed = ProcessedNotification(
            contactName = "عميل جديد",
            originalText = inboundQuery,
            normalizedText = inboundQuery,
            notificationKey = "key_new_1",
            postTime = System.currentTimeMillis(),
            context = mockk(relaxed = true),
            sourceClass = NotificationSourceClass.BUSINESS
        )
        coEvery { processor.process(any()) } returns processed
        coEvery { cfClient.decide(any(), any()) } returns InboundIntent.CATALOG_INQUIRY

        val orchestrator = createTestOrchestrator(
            chatHistoryDao = chatHistoryDao,
            prefsRepository = prefsRepo,
            processor = processor,
            cloudflareClefClient = cfClient
        )

        orchestrator.processNotificationSequentially(
            WhatsAppNotification(
                title = "عميل جديد",
                text = inboundQuery,
                postTime = System.currentTimeMillis(),
                notificationKey = "key_new_1"
            )
        )

        coVerify(atLeast = 1) {
            cfClient.decide(inboundQuery, timeoutMs = 1150L)
        }
        coVerify(exactly = 0) {
            cfClient.decide(match { it.contains("[الرد السابق]:") }, any())
        }
    }

    @Test
    fun `Pass 1 response with action REPLY and non-blank refinedSearchKeyword is auto-promoted to RE_QUERY and triggers Pass 2`() = runBlocking {
        val replyRepo = mockk<ReplyGenerationRepository>(relaxed = true)
        val semanticRepo = mockk<SemanticRetrievalRepository>(relaxed = true)
        val directReplyUseCase = mockk<DirectReplyExecutionUseCase>(relaxed = true)
        val embeddingEngine = mockk<EmbeddingEngine>(relaxed = true)
        val prefsRepo = mockk<UserPreferencesRepository>(relaxed = true)
        val processor = mockk<NotificationProcessorImpl>()

        coEvery { prefsRepo.isAutoReplyEnabledSync() } returns true
        coEvery { prefsRepo.getStylePreferencesSync() } returns StylePreferences(cooldownMins = 0)
        coEvery { directReplyUseCase.execute(any(), any(), any(), any()) } returns com.example.persona.features.auto_reply.domain.usecase.ReplyExecutionResult.Success

        val inboundQuery = "فين صور الهوديز؟"
        val processed = ProcessedNotification(
            contactName = "عميل الهودي",
            originalText = inboundQuery,
            normalizedText = inboundQuery,
            notificationKey = "key_hoodie_1",
            postTime = System.currentTimeMillis(),
            context = mockk(relaxed = true),
            sourceClass = NotificationSourceClass.BUSINESS
        )
        coEvery { processor.process(any()) } returns processed
        coEvery { embeddingEngine.embedForAutoReply(any()) } returns floatArrayOf(0.1f, 0.2f)
        coEvery { semanticRepo.retrieveCandidates(any(), any(), any(), any()) } returns Pair(emptyList(), 0.0f)

        // Pass 1 outputs action: "REPLY" with a valid search keyword (stalling pattern)
        coEvery {
            replyRepo.generateReply(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns """{"action":"REPLY","refined_search_keyword":"هوديز قطن شتوي","order_action":null,"final_reply_text":"ثواني أشوفلك المتاح","extracted_order":null}"""

        // Pass 2 final reply
        val expectedFinalReply = """{"action":"REPLY","refined_search_keyword":null,"order_action":null,"final_reply_text":"متوفر عندنا هوديز قطن ثقيل 850 جرام ألوان أسود وكحلي!","extracted_order":null}"""
        coEvery {
            replyRepo.generateFinalReply(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns expectedFinalReply

        val orchestrator = createTestOrchestrator(
            replyGenerationRepository = replyRepo,
            semanticRetrievalRepository = semanticRepo,
            directReplyExecutionUseCase = directReplyUseCase,
            embeddingEngine = embeddingEngine,
            prefsRepository = prefsRepo,
            processor = processor
        )

        orchestrator.processNotificationSequentially(
            WhatsAppNotification(
                title = "عميل الهودي",
                text = inboundQuery,
                postTime = System.currentTimeMillis(),
                notificationKey = "key_hoodie_1"
            )
        )

        // Must auto-promote and invoke generateFinalReply for Pass 2 retrieval
        coVerify(exactly = 1) {
            replyRepo.generateFinalReply(
                queryText = inboundQuery,
                chunks = any(),
                isOngoingConversation = any(),
                chatHistory = any(),
                activeOrders = any(),
                personalChunks = any(),
                maxTokens = any(),
                imageUrl = null,
                lastOrder = any(),
                orderStatusCounts = any()
            )
        }

        // Direct execution must dispatch the final reply from Pass 2
        coVerify(exactly = 1) {
            directReplyUseCase.execute(
                processed.notificationKey,
                match { it.contains("هوديز قطن ثقيل 850 جرام") },
                processed.contactName,
                any()
            )
        }
    }

    @Test
    fun `coalesceNotifications combines multiple rapid notifications preserving image path and chronological order`() {
        val orchestrator = createTestOrchestrator()
        val now = System.currentTimeMillis()

        val n1 = WhatsAppNotification(
            title = "Halim",
            text = "التيشيرت مقطوع من الكتف",
            postTime = now - 2000L,
            notificationKey = "key_1",
            sourceClass = NotificationSourceClass.BUSINESS,
            mediaFilePath = "/storage/emulated/0/WhatsApp/Media/torn_shirt.jpg"
        )
        val n2 = WhatsAppNotification(
            title = "Halim",
            text = "[رسالة صوتية من العميل]: أنا ما ليش فيه أنا عايز فلوسه",
            postTime = now - 1000L,
            notificationKey = "key_2",
            sourceClass = NotificationSourceClass.BUSINESS,
            mediaFilePath = null
        )
        val n3 = WhatsAppNotification(
            title = "Halim",
            text = "اتصرفوا بسرعة",
            postTime = now,
            notificationKey = "key_3",
            sourceClass = NotificationSourceClass.BUSINESS,
            mediaFilePath = null
        )

        val batch = listOf(n1, n2, n3)
        val insertedIds = listOf(101L, 102L, 103L)

        val coalesced = orchestrator.coalesceNotifications(batch, insertedIds)

        assertEquals("key_3", coalesced.notificationKey)
        assertEquals("/storage/emulated/0/WhatsApp/Media/torn_shirt.jpg", coalesced.mediaFilePath)
        assertTrue(coalesced.isPrePersisted)
        assertEquals(insertedIds, coalesced.burstMessageIds)
        assertTrue(coalesced.text.contains("التيشيرت مقطوع من الكتف"))
        assertTrue(coalesced.text.contains("[رسالة صوتية من العميل]: أنا ما ليش فيه أنا عايز فلوسه"))
        assertTrue(coalesced.text.contains("اتصرفوا بسرعة"))
        assertFalse(coalesced.isVoiceTranscriptionFailed)
    }

    @Test
    fun `enqueueInboundNotification immediately records message to chatHistory and metadata for zero data loss`() = runBlocking {
        val chatHistoryDao = mockk<ChatHistoryDao>(relaxed = true)
        val convMetadataDao = mockk<ConversationMetadataDao>(relaxed = true)
        val prefsRepo = mockk<UserPreferencesRepository>(relaxed = true)
        val processor = mockk<NotificationProcessorImpl>()
        coEvery { prefsRepo.isAutoReplyEnabledSync() } returns true
        coEvery { prefsRepo.getStylePreferencesSync() } returns StylePreferences(replyDelaySeconds = 0)
        coEvery { chatHistoryDao.insert(any()) } returns 555L

        val processed = ProcessedNotification(
            contactName = "طارق",
            originalText = "مساء الخير، الأوردر وصل؟",
            normalizedText = "مساء الخير، الأوردر وصل؟",
            notificationKey = "key_immed_1",
            postTime = 12345678L,
            context = mockk(relaxed = true),
            sourceClass = NotificationSourceClass.BUSINESS
        )
        coEvery { processor.process(any()) } returns processed

        val orchestrator = createTestOrchestrator(
            processor = processor,
            chatHistoryDao = chatHistoryDao,
            conversationMetadataDao = convMetadataDao,
            prefsRepository = prefsRepo
        )

        val notification = WhatsAppNotification(
            title = "طارق",
            text = "مساء الخير، الأوردر وصل؟",
            postTime = 12345678L,
            notificationKey = "key_immed_1",
            sourceClass = NotificationSourceClass.BUSINESS
        )

        orchestrator.enqueueInboundNotification(notification)

        coVerify(exactly = 1) {
            chatHistoryDao.insert(match {
                it.contactName == "طارق" &&
                it.messageText == "مساء الخير، الأوردر وصل؟" &&
                it.isFromUser &&
                it.timestamp == 12345678L
            })
        }

        coVerify(exactly = 1) {
            convMetadataDao.recordMessageTurn(
                contactName = "طارق",
                sourceClass = "BUSINESS",
                messageText = "مساء الخير، الأوردر وصل؟",
                senderType = "CUSTOMER",
                timestamp = 12345678L,
                notificationKey = "key_immed_1",
                incrementUnread = true
            )
        }
    }

    @Test
    fun `processNotificationSequentially filters out burstMessageIds from assembled chatHistory to prevent duplicate turns`() = runBlocking {
        val chatHistoryDao = mockk<ChatHistoryDao>(relaxed = true)
        val replyRepo = mockk<ReplyGenerationRepository>(relaxed = true)
        val prefsRepo = mockk<UserPreferencesRepository>(relaxed = true)
        val processor = mockk<NotificationProcessorImpl>()
        coEvery { prefsRepo.isAutoReplyEnabledSync() } returns true
        coEvery { prefsRepo.getStylePreferencesSync() } returns StylePreferences(cooldownMins = 0, replyDelaySeconds = 0)

        val processed = ProcessedNotification(
            contactName = "عميل الباقة",
            originalText = "رسالة أولى في الباقة\nرسالة ثانية في الباقة",
            normalizedText = "رسالة أولى في الباقة\nرسالة ثانية في الباقة",
            notificationKey = "key_burst_final",
            postTime = 2500L,
            context = mockk(relaxed = true),
            sourceClass = NotificationSourceClass.BUSINESS
        )
        coEvery { processor.process(any()) } returns processed

        // Previous history in DB includes burst messages (IDs 201, 202) and an older message (ID 200)
        val olderMessage = ChatHistoryEntity(
            id = 200L,
            contactName = "عميل الباقة",
            messageText = "سؤال قديم من أمس",
            isFromUser = true,
            timestamp = 1000L
        )
        val burstMessage1 = ChatHistoryEntity(
            id = 201L,
            contactName = "عميل الباقة",
            messageText = "رسالة أولى في الباقة",
            isFromUser = true,
            timestamp = 2000L
        )
        val burstMessage2 = ChatHistoryEntity(
            id = 202L,
            contactName = "عميل الباقة",
            messageText = "رسالة ثانية في الباقة",
            isFromUser = true,
            timestamp = 2500L
        )

        coEvery {
            chatHistoryDao.getRecentMessagesExceptForSource(
                contactName = "عميل الباقة",
                sourceClass = any(),
                excludeId = any(),
                limit = any()
            )
        } returns listOf(burstMessage2, burstMessage1, olderMessage)

        coEvery { replyRepo.generateReply(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns
            """{"action":"REPLY","final_reply_text":"تم استلام جميع رسائلكم وسنقوم بالرد فورا"}"""

        val orchestrator = createTestOrchestrator(
            processor = processor,
            chatHistoryDao = chatHistoryDao,
            replyGenerationRepository = replyRepo,
            prefsRepository = prefsRepo
        )

        val coalescedNotification = WhatsAppNotification(
            title = "عميل الباقة",
            text = "رسالة أولى في الباقة\nرسالة ثانية في الباقة",
            postTime = 2500L,
            notificationKey = "key_burst_final",
            sourceClass = NotificationSourceClass.BUSINESS,
            isPrePersisted = true,
            burstMessageIds = listOf(201L, 202L)
        )

        orchestrator.processNotificationSequentially(coalescedNotification)

        // Verify that replyRepo.generateReply received chatHistory containing only olderMessage (ID 200),
        // and excluded burstMessage1 (201) and burstMessage2 (202)
        coVerify(exactly = 1) {
            replyRepo.generateReply(
                notification = any(),
                candidates = any(),
                isOngoingConversation = any(),
                chatHistory = match { history ->
                    history.any { it.id == 200L } && history.none { it.id == 201L || it.id == 202L }
                },
                activeOrders = any(),
                personalChunks = any(),
                maxTokens = any(),
                lastOrder = any(),
                orderStatusCounts = any(),
                inboundIntent = any()
            )
        }
    }

    @Test
    fun `isAudioFilePath correctly identifies audio and non-audio extensions`() {
        assertTrue(AutoReplyOrchestrator.isAudioFilePath("/storage/voice.ogg"))
        assertTrue(AutoReplyOrchestrator.isAudioFilePath("/storage/voice.opus"))
        assertTrue(AutoReplyOrchestrator.isAudioFilePath("/storage/voice.mp3"))
        assertTrue(AutoReplyOrchestrator.isAudioFilePath("/storage/voice.m4a"))
        assertTrue(AutoReplyOrchestrator.isAudioFilePath("/storage/voice.wav"))
        assertTrue(AutoReplyOrchestrator.isAudioFilePath("/storage/voice.aac"))
        assertTrue(AutoReplyOrchestrator.isAudioFilePath("/storage/voice.amr"))
        assertTrue(AutoReplyOrchestrator.isAudioFilePath("/storage/VOICE.OGG"))

        assertFalse(AutoReplyOrchestrator.isAudioFilePath("/storage/photo.jpg"))
        assertFalse(AutoReplyOrchestrator.isAudioFilePath("/storage/photo.jpeg"))
        assertFalse(AutoReplyOrchestrator.isAudioFilePath("/storage/photo.png"))
        assertFalse(AutoReplyOrchestrator.isAudioFilePath("/storage/photo.webp"))
        assertFalse(AutoReplyOrchestrator.isAudioFilePath(null))
        assertFalse(AutoReplyOrchestrator.isAudioFilePath(""))
        assertFalse(AutoReplyOrchestrator.isAudioFilePath("   "))
    }

    @Test
    fun `processNotificationSequentially preserves image file path and triggers image fast-path when burst contains transcribed voice and image`() = runBlocking {
        val chatHistoryDao = mockk<ChatHistoryDao>(relaxed = true)
        val replyRepo = mockk<ReplyGenerationRepository>(relaxed = true)
        val prefsRepo = mockk<UserPreferencesRepository>(relaxed = true)
        val cloudflareClient = mockk<CloudflareClefClient>(relaxed = true)
        val liquidClient = mockk<LiquidDecisionClient>(relaxed = true)
        val processor = mockk<NotificationProcessorImpl>()

        coEvery { prefsRepo.isAutoReplyEnabledSync() } returns true
        coEvery { prefsRepo.getStylePreferencesSync() } returns StylePreferences(cooldownMins = 0, replyDelaySeconds = 0)

        val coalescedText = "شوف التيشيرت دا\n[رسالة صوتية من العميل]: عايز اعرف السعر"
        val imagePath = "/storage/emulated/0/WhatsApp/Media/image123.jpg"

        val processed = ProcessedNotification(
            contactName = "عميل المتعدد",
            originalText = coalescedText,
            normalizedText = coalescedText,
            mediaFilePath = imagePath,
            notificationKey = "key_multimodal_burst",
            postTime = 3000L,
            context = mockk(relaxed = true),
            sourceClass = NotificationSourceClass.BUSINESS
        )
        coEvery { processor.process(any()) } returns processed
        coEvery { replyRepo.generateReply(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns
            """{"action":"REPLY","final_reply_text":"التيشيرت دا متوفر بسعر ٤٥٠ جنيه"}"""

        val orchestrator = createTestOrchestrator(
            processor = processor,
            chatHistoryDao = chatHistoryDao,
            replyGenerationRepository = replyRepo,
            prefsRepository = prefsRepo,
            cloudflareClefClient = cloudflareClient,
            liquidDecisionClient = liquidClient
        )

        val coalescedNotification = WhatsAppNotification(
            title = "عميل المتعدد",
            text = coalescedText,
            mediaFilePath = imagePath,
            postTime = 3000L,
            notificationKey = "key_multimodal_burst",
            sourceClass = NotificationSourceClass.BUSINESS,
            isPrePersisted = true,
            burstMessageIds = listOf(301L, 302L)
        )

        orchestrator.processNotificationSequentially(coalescedNotification)

        // Verify gatekeepers were NOT called because isImagePresent triggered fast-path
        coVerify(exactly = 0) { cloudflareClient.decide(any(), any()) }
        coVerify(exactly = 0) { liquidClient.decide(any(), any()) }

        // Verify reply generation received the image media path and CATALOG_INQUIRY intent
        coVerify(exactly = 1) {
            replyRepo.generateReply(
                notification = match { it.mediaFilePath == imagePath },
                candidates = any(),
                isOngoingConversation = any(),
                chatHistory = any(),
                activeOrders = any(),
                personalChunks = any(),
                maxTokens = any(),
                lastOrder = any(),
                orderStatusCounts = any(),
                inboundIntent = InboundIntent.CATALOG_INQUIRY
            )
        }
    }

    @Test
    fun `processNotificationSequentially marks non-terminal burst message IDs as SKIPPED in embedding state`() = runBlocking {
        val chatHistoryDao = mockk<ChatHistoryDao>(relaxed = true)
        val replyRepo = mockk<ReplyGenerationRepository>(relaxed = true)
        val prefsRepo = mockk<UserPreferencesRepository>(relaxed = true)
        val embeddingEngine = mockk<EmbeddingEngine>(relaxed = true)
        val processor = mockk<NotificationProcessorImpl>()

        coEvery { prefsRepo.isAutoReplyEnabledSync() } returns true
        coEvery { prefsRepo.getStylePreferencesSync() } returns StylePreferences(cooldownMins = 0, replyDelaySeconds = 0)
        coEvery { embeddingEngine.embedForAutoReply(any()) } returns FloatArray(384) { 0.1f }

        val processed = ProcessedNotification(
            contactName = "عميل الدفعة",
            originalText = "رسالة 1\nرسالة 2\nرسالة 3",
            normalizedText = "رسالة 1 رسالة 2 رسالة 3",
            notificationKey = "key_burst_skip",
            postTime = 5000L,
            context = mockk(relaxed = true),
            sourceClass = NotificationSourceClass.BUSINESS
        )
        coEvery { processor.process(any()) } returns processed
        coEvery { replyRepo.generateReply(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns
            """{"action":"REPLY","final_reply_text":"أهلاً بك"}"""

        val orchestrator = createTestOrchestrator(
            processor = processor,
            chatHistoryDao = chatHistoryDao,
            replyGenerationRepository = replyRepo,
            prefsRepository = prefsRepo,
            embeddingEngine = embeddingEngine
        )

        val coalescedNotification = WhatsAppNotification(
            title = "عميل الدفعة",
            text = "رسالة 1\nرسالة 2\nرسالة 3",
            postTime = 5000L,
            notificationKey = "key_burst_skip",
            sourceClass = NotificationSourceClass.BUSINESS,
            isPrePersisted = true,
            burstMessageIds = listOf(401L, 402L, 403L) // 403L is terminal (insertedId)
        )

        orchestrator.processNotificationSequentially(coalescedNotification)

        // Verify terminal message receives embedding update
        coVerify(exactly = 1) { chatHistoryDao.updateEmbeddingRaw(403L, any()) }

        // Verify non-terminal messages are marked as SKIPPED
        coVerify(exactly = 1) { chatHistoryDao.updateEmbeddingState(401L, "SKIPPED") }
        coVerify(exactly = 1) { chatHistoryDao.updateEmbeddingState(402L, "SKIPPED") }
        coVerify(exactly = 0) { chatHistoryDao.updateEmbeddingState(403L, "SKIPPED") }
    }

    @Test
    fun `when global auto-reply is disabled and override is OFF, reply is suppressed`() = runBlocking {
        val prefsRepo: UserPreferencesRepository = mockk(relaxed = true) {
            coEvery { isAutoReplyEnabledSync() } returns false
        }
        val convDao: ConversationMetadataDao = mockk(relaxed = true) {
            coEvery { getConversation(any(), any()) } returns com.example.persona.core.database.entity.ConversationMetadataEntity(
                contactName = "عميل بدون استثناء",
                sourceClass = "BUSINESS",
                isAiPaused = false,
                isAiOverrideEnabled = false
            )
        }
        val replyGen: ReplyGenerationRepository = mockk(relaxed = true)
        val orchestrator = createTestOrchestrator(
            prefsRepository = prefsRepo,
            conversationMetadataDao = convDao,
            replyGenerationRepository = replyGen
        )

        val notification = WhatsAppNotification(
            title = "عميل بدون استثناء",
            text = "السلام عليكم",
            postTime = 1000L,
            notificationKey = "key_no_override",
            sourceClass = NotificationSourceClass.BUSINESS
        )

        orchestrator.processNotificationSequentially(notification)

        // Reply generation should NOT be invoked
        coVerify(exactly = 0) { replyGen.generateReply(any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `when global auto-reply is disabled and override is ON, reply is allowed and processed`() = runBlocking {
        val prefsRepo: UserPreferencesRepository = mockk(relaxed = true) {
            coEvery { isAutoReplyEnabledSync() } returns false
            coEvery { getStylePreferencesSync() } returns StylePreferences(cooldownMins = 0, replyDelaySeconds = 0)
        }
        val convDao: ConversationMetadataDao = mockk(relaxed = true) {
            coEvery { getConversation(any(), any()) } returns com.example.persona.core.database.entity.ConversationMetadataEntity(
                contactName = "عميل الاستثناء الفردي",
                sourceClass = "BUSINESS",
                isAiPaused = false,
                isAiOverrideEnabled = true
            )
        }
        val replyGen: ReplyGenerationRepository = mockk(relaxed = true) {
            coEvery { generateReply(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns """{"action":"REPLY","refined_search_keyword":null,"order_action":null,"final_reply_text":"أهلاً بك يا فندم في متجرنا!","extracted_order":null}"""
        }
        val executionUseCase: DirectReplyExecutionUseCase = mockk(relaxed = true) {
            coEvery { execute(any(), any(), any(), any()) } returns com.example.persona.features.auto_reply.domain.usecase.ReplyExecutionResult.Success
        }

        val orchestrator = createTestOrchestrator(
            prefsRepository = prefsRepo,
            conversationMetadataDao = convDao,
            replyGenerationRepository = replyGen,
            directReplyExecutionUseCase = executionUseCase
        )

        val notification = WhatsAppNotification(
            title = "عميل الاستثناء الفردي",
            text = "هل التيشيرت متاح؟",
            postTime = 1000L,
            notificationKey = "key_override_allowed",
            sourceClass = NotificationSourceClass.BUSINESS
        )

        orchestrator.processNotificationSequentially(notification)

        // Reply generation MUST be invoked because isAiOverrideEnabled = true
        coVerify(atLeast = 1) { replyGen.generateReply(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `when global auto-reply is enabled and contact is paused even if override is true, reply is suppressed`() = runBlocking {
        val prefsRepo: UserPreferencesRepository = mockk(relaxed = true) {
            coEvery { isAutoReplyEnabledSync() } returns true
        }
        val convDao: ConversationMetadataDao = mockk(relaxed = true) {
            coEvery { getConversation(any(), any()) } returns com.example.persona.core.database.entity.ConversationMetadataEntity(
                contactName = "عميل موقف فردياً",
                sourceClass = "BUSINESS",
                isAiPaused = true,
                isAiOverrideEnabled = true
            )
        }
        val replyGen: ReplyGenerationRepository = mockk(relaxed = true)
        val orchestrator = createTestOrchestrator(
            prefsRepository = prefsRepo,
            conversationMetadataDao = convDao,
            replyGenerationRepository = replyGen
        )

        val notification = WhatsAppNotification(
            title = "عميل موقف فردياً",
            text = "السلام عليكم",
            postTime = 1000L,
            notificationKey = "key_paused_with_override",
            sourceClass = NotificationSourceClass.BUSINESS
        )

        orchestrator.processNotificationSequentially(notification)

        // Reply generation should NOT be invoked because contact is paused
        coVerify(exactly = 0) { replyGen.generateReply(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `order_action normalization maps variations to canonical actions`() {
        assertEquals("CANCEL", OrderIngestionManager.normalizeOrderAction("CANCEL_ORDER"))
        assertEquals("CANCEL", OrderIngestionManager.normalizeOrderAction("CANCEL"))
        assertEquals("CANCEL", OrderIngestionManager.normalizeOrderAction("CANCELLED"))
        assertEquals("CANCEL", OrderIngestionManager.normalizeOrderAction(" cancel_order "))
        assertEquals("CREATE", OrderIngestionManager.normalizeOrderAction("CREATE_ORDER"))
        assertEquals("CREATE", OrderIngestionManager.normalizeOrderAction("CREATE"))
        assertEquals("CREATE", OrderIngestionManager.normalizeOrderAction(" create "))
        assertEquals("UPDATE", OrderIngestionManager.normalizeOrderAction("UPDATE_ORDER"))
        assertEquals("UPDATE", OrderIngestionManager.normalizeOrderAction("UPDATE"))
        assertEquals("UPDATE", OrderIngestionManager.normalizeOrderAction(" update "))
        assertNull(OrderIngestionManager.normalizeOrderAction(null))
        assertNull(OrderIngestionManager.normalizeOrderAction(""))
        assertNull(OrderIngestionManager.normalizeOrderAction("   "))
        assertNull(OrderIngestionManager.normalizeOrderAction("INVALID_ACTION"))
    }
}
