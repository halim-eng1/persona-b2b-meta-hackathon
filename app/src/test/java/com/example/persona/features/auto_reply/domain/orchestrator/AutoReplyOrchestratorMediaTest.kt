package com.example.persona.features.auto_reply.domain.orchestrator

import android.content.Context
import com.example.persona.R
import com.example.persona.core.data.repository.UserPreferencesRepository
import com.example.persona.core.database.dao.BusinessKnowledgeDao
import com.example.persona.core.database.dao.ChatHistoryDao
import com.example.persona.core.database.dao.ConversationMetadataDao
import com.example.persona.core.database.dao.FailedQueryDao
import com.example.persona.core.database.dao.OrderDao
import com.example.persona.core.database.entity.ChatHistoryEntity
import com.example.persona.features.auto_reply.data.diagnostics.NotificationSourceClass
import com.example.persona.features.auto_reply.data.repository.NotificationProcessorImpl
import com.example.persona.features.auto_reply.data.repository.PromptContextBuilder
import com.example.persona.features.auto_reply.data.repository.SemanticRetrievalRepository
import com.example.persona.features.auto_reply.data.service.IncomingMessageDispatcher
import com.example.persona.features.auto_reply.data.service.WhatsAppMediaExtractor
import com.example.persona.features.auto_reply.domain.model.PersonaTone
import com.example.persona.features.auto_reply.domain.model.ProcessedNotification
import com.example.persona.features.auto_reply.domain.model.RetrievalCandidate
import com.example.persona.features.auto_reply.domain.model.StylePreferences
import com.example.persona.features.auto_reply.domain.model.StyleProfile
import com.example.persona.features.auto_reply.domain.model.WhatsAppNotification
import com.example.persona.features.auto_reply.domain.modifier.StylePostProcessor
import com.example.persona.features.auto_reply.domain.repository.AuditLogRepository
import com.example.persona.features.auto_reply.domain.repository.ReplyGenerationRepository
import com.example.persona.features.auto_reply.domain.usecase.DirectReplyExecutionUseCase
import com.example.persona.features.auto_reply.domain.usecase.ReplyExecutionResult
import com.example.persona.features.chat_import.data.embedding.EmbeddingEngine
import com.example.persona.features.chat_import.data.normalizer.ArabicTextNormalizer
import com.example.persona.features.chat_import.data.processor.ContextDetector
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class AutoReplyOrchestratorMediaTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val orderDao = mockk<OrderDao>(relaxed = true)
    private val chatHistoryDao = mockk<ChatHistoryDao>(relaxed = true)
    private val businessKnowledgeDao = mockk<BusinessKnowledgeDao>(relaxed = true)
    private val conversationMetadataDao = mockk<ConversationMetadataDao>(relaxed = true)
    private val semanticRetrievalRepository = mockk<SemanticRetrievalRepository>(relaxed = true)
    private val replyGenerationRepository = mockk<ReplyGenerationRepository>(relaxed = true)
    private val directReplyExecutionUseCase = mockk<DirectReplyExecutionUseCase>(relaxed = true)
    private val auditLogRepository = mockk<AuditLogRepository>(relaxed = true)
    private val prefsRepository = mockk<UserPreferencesRepository>(relaxed = true)
    private val normalizer = ArabicTextNormalizer()
    private val contextDetector = ContextDetector(normalizer)
    private val processor = NotificationProcessorImpl(normalizer, contextDetector)
    private val embeddingEngine = mockk<EmbeddingEngine>(relaxed = true)
    private val stylePostProcessor = StylePostProcessor()
    private val failedQueryDao = mockk<FailedQueryDao>(relaxed = true)
    private val mockContext = mockk<Context>(relaxed = true)
    private val mediaExtractor = mockk<WhatsAppMediaExtractor>(relaxed = true)

    private lateinit var orchestrator: AutoReplyOrchestrator

    @Before
    fun setup() {
        every { mockContext.getString(R.string.media_fallback_message) } returns
                "عفواً، لا يمكنني معالجة الوسائط. يرجى كتابة استفسارك في رسالة نصية."

        coEvery { prefsRepository.isAutoReplyEnabledSync() } returns true
        coEvery { prefsRepository.getMediaFallbackMessageSync() } returns
            "عفواً، لا يمكنني معالجة الوسائط. يرجى كتابة استفسارك في رسالة نصية."
        coEvery { prefsRepository.getHitlHoldingMessageSync() } returns
            PromptContextBuilder.CANONICAL_HITL_MESSAGE
        coEvery { prefsRepository.getStylePreferencesSync() } returns StylePreferences(
            styleProfile = StyleProfile.CASUAL,
            similarityThreshold = 0.75f,
            cooldownMins = 0,
            replyDelaySeconds = 0,
            personaTone = PersonaTone.WITTY_EGYPTIAN
        )
        coEvery { conversationMetadataDao.getConversation(any(), any()) } returns null
        coEvery { chatHistoryDao.insert(any()) } returns 101L
        coEvery { chatHistoryDao.claimForProcessing(any()) } returns 0

        coEvery { directReplyExecutionUseCase.execute(any(), any(), any(), any()) } returns
                ReplyExecutionResult.Success

        coEvery { embeddingEngine.embedForAutoReply(any()) } returns floatArrayOf(0.1f, 0.2f)
        coEvery { semanticRetrievalRepository.retrieveCandidates(any(), any(), any(), any()) } returns Pair(emptyList(), 0.0f)
        coEvery { chatHistoryDao.getRecentMessagesExcept(any(), any(), any()) } returns emptyList()
        coEvery { chatHistoryDao.getRecentMessagesExceptForSource(any(), any(), any(), any()) } returns emptyList()
        coEvery { orderDao.getActiveOrdersByContact(any()) } returns emptyList()
        coEvery { orderDao.getLatestOrderByContact(any()) } returns null
        coEvery { orderDao.getOrderStatusCounts(any()) } returns emptyList()

        orchestrator = AutoReplyOrchestrator(
            incomingMessageDispatcher = mockk(relaxed = true),
            processor = processor,
            embeddingEngine = embeddingEngine,
            semanticRetrievalRepository = semanticRetrievalRepository,
            replyGenerationRepository = replyGenerationRepository,
            directReplyExecutionUseCase = directReplyExecutionUseCase,
            auditLogRepository = auditLogRepository,
            prefsRepository = prefsRepository,
            stylePostProcessor = stylePostProcessor,
            normalizer = normalizer,
            failedQueryDao = failedQueryDao,
            chatHistoryDao = chatHistoryDao,
            orderDao = orderDao,
            businessKnowledgeDao = businessKnowledgeDao,
            conversationMetadataDao = conversationMetadataDao,
            context = mockContext,
            scope = CoroutineScope(Dispatchers.Unconfined),
            mediaExtractor = mediaExtractor
        )
    }

    @Test
    fun `containsMediaIndicators returns false for transcribed voice notes`() {
        assertFalse(orchestrator.containsMediaIndicators("[رسالة صوتية من العميل]: عايز أعرف الأسعار"))
        assertTrue(orchestrator.containsMediaIndicators("📷 Sent a photo"))
        assertTrue(orchestrator.containsMediaIndicators("🎤 Voice message (0:02)"))
    }

    @Test
    fun `when notification has extracted image, orchestrator calls LLM and does NOT send media fallback`() = runBlocking {
        val imageFile = tempFolder.newFile("sample_img.jpg").apply {
            writeBytes(byteArrayOf(1, 2, 3, 4))
        }

        val notification = WhatsAppNotification(
            title = "Halim•",
            text = "📷 بكام ده",
            postTime = 1000L,
            notificationKey = "key_img_123",
            sourceClass = NotificationSourceClass.BUSINESS,
            mediaFilePath = imageFile.absolutePath
        )

        coEvery {
            replyGenerationRepository.generateReply(
                notification = match { it.mediaFilePath == imageFile.absolutePath },
                candidates = any(),
                isOngoingConversation = any(),
                chatHistory = any(),
                activeOrders = any(),
                personalChunks = any(),
                maxTokens = any()
            )
        } returns """{"action":"REPLY","refined_search_keyword":null,"order_action":null,"final_reply_text":"ده التيشيرت القطن الجديد، سعره 350 جنيه ومتاح منه كل المقاسات 💙","extracted_order":null}"""

        orchestrator.processNotificationSequentially(notification)

        // Verify Pass 1 invoked with 500 maxTokens and diet context
        coVerify(atLeast = 1) {
            replyGenerationRepository.generateReply(
                notification = match { it.mediaFilePath == imageFile.absolutePath },
                candidates = eq(emptyList()),
                isOngoingConversation = any(),
                chatHistory = eq(emptyList()),
                activeOrders = eq(emptyList()),
                personalChunks = any(),
                maxTokens = 500
            )
        }

        // Verify that media fallback message was NOT sent
        coVerify(exactly = 0) {
            directReplyExecutionUseCase.execute(
                any(),
                match { it.contains("لا يمكنني معالجة الوسائط") },
                any(),
                any()
            )
        }

        // Verify that the LLM's final reply was dispatched
        coVerify(atLeast = 1) {
            directReplyExecutionUseCase.execute(
                eq("key_img_123"),
                match { it.contains("350 جنيه") },
                eq("Halim•"),
                any()
            )
        }
    }

    @Test
    fun `when notification has transcribed voice note, orchestrator routes as text turn and does NOT send fallback`() = runBlocking {
        val notification = WhatsAppNotification(
            title = "Halim•",
            text = "[رسالة صوتية من العميل]: عندكم ألوان تانية من السويت شيرت؟",
            postTime = 2000L,
            notificationKey = "key_voice_456",
            sourceClass = NotificationSourceClass.BUSINESS,
            mediaFilePath = null
        )

        coEvery {
            replyGenerationRepository.generateReply(
                notification = match { it.originalText.contains("عندكم ألوان تانية") },
                candidates = any(),
                isOngoingConversation = any(),
                chatHistory = any(),
                activeOrders = any(),
                personalChunks = any(),
                maxTokens = any()
            )
        } returns """{"action":"REPLY","refined_search_keyword":null,"order_action":null,"final_reply_text":"متاح منه أسود، رمادي، وكحلي يا فندم! 💙","extracted_order":null}"""

        orchestrator.processNotificationSequentially(notification)

        // Verify LLM was invoked
        coVerify(atLeast = 1) {
            replyGenerationRepository.generateReply(
                notification = match { it.originalText.contains("عندكم ألوان تانية") },
                candidates = any(),
                isOngoingConversation = any(),
                chatHistory = any(),
                activeOrders = any(),
                personalChunks = any(),
                maxTokens = any()
            )
        }

        // Verify media fallback was NOT sent
        coVerify(exactly = 0) {
            directReplyExecutionUseCase.execute(
                any(),
                match { it.contains("لا يمكنني معالجة الوسائط") },
                any(),
                any()
            )
        }

        // Verify direct reply was dispatched with the answer
        coVerify(atLeast = 1) {
            directReplyExecutionUseCase.execute(
                eq("key_voice_456"),
                match { it.contains("أسود، رمادي، وكحلي") },
                eq("Halim•"),
                any()
            )
        }
    }

    @Test
    fun `transcribed voice note with audio mediaFilePath forces mediaFilePath to null and executes as pure text turn`() = runBlocking {
        val audioFile = tempFolder.newFile("voice_note.opus").apply {
            writeBytes(byteArrayOf(1, 2, 3, 4, 5))
        }

        val notification = WhatsAppNotification(
            title = "Customer Voice",
            text = "[رسالة صوتية من العميل]: هو المفروض في حد موجود هنا دلوقتي ولا ايه",
            postTime = 3000L,
            notificationKey = "key_voice_audio_path",
            sourceClass = NotificationSourceClass.BUSINESS,
            mediaFilePath = audioFile.absolutePath
        )

        coEvery {
            replyGenerationRepository.generateReply(
                notification = match { it.mediaFilePath == null && it.originalText.contains("هو المفروض في حد موجود") },
                candidates = any(),
                isOngoingConversation = any(),
                chatHistory = any(),
                activeOrders = any(),
                personalChunks = any(),
                maxTokens = 600
            )
        } returns """{"action":"REPLY","refined_search_keyword":null,"order_action":null,"final_reply_text":"أهلاً بك! نعم أنا متواجد لمساعدتك، اتفضل تحت أمرك 💙","extracted_order":null}"""

        orchestrator.processNotificationSequentially(notification)

        // Verify LLM was invoked with mediaFilePath == null and maxTokens == 600 (not multimodal 500)
        coVerify(atLeast = 1) {
            replyGenerationRepository.generateReply(
                notification = match { it.mediaFilePath == null },
                candidates = any(),
                isOngoingConversation = any(),
                chatHistory = any(),
                activeOrders = any(),
                personalChunks = any(),
                maxTokens = 600
            )
        }

        // Verify reply dispatched
        coVerify(atLeast = 1) {
            directReplyExecutionUseCase.execute(
                eq("key_voice_audio_path"),
                match { it.contains("أهلاً بك! نعم أنا متواجد لمساعدتك") },
                eq("Customer Voice"),
                any()
            )
        }
    }

    @Test
    fun `when both bitmap extraction and disk fallback genuinely fail, sends media fallback message`() = runBlocking {
        every { mediaExtractor.isImageIndicator("📷 Sent a photo") } returns true
        every { mediaExtractor.findLatestImageOnDisk(any(), any()) } returns null
        every { mediaExtractor.isVoiceMessageIndicator(any()) } returns false

        val notification = WhatsAppNotification(
            title = "Halim•",
            text = "📷 Sent a photo",
            postTime = 3000L,
            notificationKey = "key_fail_789",
            sourceClass = NotificationSourceClass.BUSINESS,
            mediaFilePath = null
        )

        orchestrator.processNotificationSequentially(notification)

        // Verify that media fallback message WAS sent because extraction failed
        coVerify(atLeast = 1) {
            directReplyExecutionUseCase.execute(
                eq("key_fail_789"),
                match { it.contains("لا يمكنني معالجة الوسائط") },
                eq("Halim•"),
                any()
            )
        }
    }

    @Test
    fun `when voice note transcription failed at listener stage, orchestrator aborts execution and dispatches media fallback immediately without scanning disk`() = runBlocking {
        val notification = WhatsAppNotification(
            title = "Halim•",
            text = "🎤 Voice message (0:04)",
            postTime = 4000L,
            notificationKey = "key_voice_fail_123",
            sourceClass = NotificationSourceClass.BUSINESS,
            mediaFilePath = null,
            isVoiceTranscriptionFailed = true
        )

        orchestrator.processNotificationSequentially(notification)

        // Verify that mediaExtractor disk scanning and transcription were NEVER re-attempted
        coVerify(exactly = 0) {
            mediaExtractor.captureLatestVoiceNote(any(), any(), any())
        }
        coVerify(exactly = 0) {
            mediaExtractor.transcribeVoiceNote(any())
        }

        // Verify LLM reply was NEVER invoked
        coVerify(exactly = 0) {
            replyGenerationRepository.generateReply(
                any(), any(), any(), any(), any(), any(), any()
            )
        }

        // Verify that media fallback template was immediately dispatched
        coVerify(atLeast = 1) {
            directReplyExecutionUseCase.execute(
                eq("key_voice_fail_123"),
                match { it.contains("لا يمكنني معالجة الوسائط") },
                eq("Halim•"),
                any()
            )
        }
    }

    @Test
    fun `two-stage visual RE_QUERY combines visual description and search keyword, and omits heavy image in pass 2`() = runBlocking {
        val imageFile = tempFolder.newFile("hoodie_sample.jpg").apply {
            writeBytes(byteArrayOf(10, 20, 30))
        }

        val notification = WhatsAppNotification(
            title = "أحمد مهندس",
            text = "📷 بكام ده يا فندم؟",
            postTime = 5000L,
            notificationKey = "key_hoodie_999",
            sourceClass = NotificationSourceClass.BUSINESS,
            mediaFilePath = imageFile.absolutePath
        )

        // Pass 1: Vision model inspects image and returns RE_QUERY with visual_product_description
        coEvery {
            replyGenerationRepository.generateReply(
                notification = match { it.mediaFilePath == imageFile.absolutePath },
                candidates = any(),
                isOngoingConversation = any(),
                chatHistory = any(),
                activeOrders = any(),
                personalChunks = any(),
                maxTokens = any()
            )
        } returns """{
            "action": "RE_QUERY",
            "refined_search_keyword": "هودي أسود مطبوع",
            "visual_product_description": "هودي أسود أوفر سايز مطبوع عليه عبارة أنا مش بني آدم أنا مهندس",
            "order_action": null,
            "final_reply_text": null,
            "extracted_order": null
        }"""

        val mockChunk = RetrievalCandidate(
            chunkId = "chunk_hoodie_eng",
            headerContext = "كتالوج الهوديز",
            chunkText = "هودي أنا مهندس أوفر سايز أسود سعره 750 جنيه ميلتون قطن 100%",
            similarityScore = 0.94f,
            baseSimilarityScore = 0.94f
        )

        coEvery {
            semanticRetrievalRepository.retrieveCandidates(
                queryEmbedding = any(),
                minimumSimilarityThreshold = any(),
                queryText = match { it.contains("هودي أسود مطبوع") && it.contains("أنا مهندس") },
                recentReplyHashes = any()
            )
        } returns Pair(listOf(mockChunk), 0.94f)

        // Pass 2: Final reply generation
        coEvery {
            replyGenerationRepository.generateFinalReply(
                queryText = match {
                    it.contains("بكام ده يا فندم") &&
                    it.contains("[وصف الصورة المرفقة من العميل]: هودي أسود أوفر سايز مطبوع عليه عبارة أنا مش بني آدم أنا مهندس")
                },
                chunks = match { it.any { c -> c.chunkId == "chunk_hoodie_eng" } },
                isOngoingConversation = any(),
                chatHistory = any(),
                activeOrders = any(),
                personalChunks = any(),
                maxTokens = match { it <= 750 },
                imageUrl = null // MUST be null because visualProductDescription is non-blank!
            )
        } returns """{
            "action": "REPLY",
            "refined_search_keyword": null,
            "order_action": null,
            "final_reply_text": "ده هودي 'أنا مهندس' أوفر سايز أسود قطن ميلتون 100%، سعره 750 جنيه ومتاح بجميع المقاسات! تحب نجهزلك أوردر بيه؟ 💙",
            "extracted_order": null
        }"""

        orchestrator.processNotificationSequentially(notification)

        // Verify Pass 2 was called with augmented queryText, the retrieved chunks, and imageUrl == null
        coVerify(exactly = 1) {
            replyGenerationRepository.generateFinalReply(
                queryText = match {
                    it.contains("بكام ده يا فندم") &&
                    it.contains("[وصف الصورة المرفقة من العميل]: هودي أسود أوفر سايز مطبوع عليه عبارة أنا مش بني آدم أنا مهندس")
                },
                chunks = match { it.any { c -> c.chunkId == "chunk_hoodie_eng" } },
                isOngoingConversation = any(),
                chatHistory = any(),
                activeOrders = any(),
                personalChunks = any(),
                maxTokens = match { it <= 750 },
                imageUrl = null
            )
        }

        // Verify direct reply was dispatched
        coVerify(atLeast = 1) {
            directReplyExecutionUseCase.execute(
                eq("key_hoodie_999"),
                match { it.contains("750 جنيه") },
                eq("أحمد مهندس"),
                any()
            )
        }
    }

    @Test
    fun `Pass 1 multimodal vision request strictly isolates payload without chat history or candidates`() = runBlocking {
        val imageFile = tempFolder.newFile("sample_img_diet.jpg").apply {
            writeBytes(byteArrayOf(1, 2, 3))
        }

        val notification = WhatsAppNotification(
            title = "عميل الصورة",
            text = "📷 بكام ده",
            postTime = 1000L,
            notificationKey = "key_img_diet",
            sourceClass = NotificationSourceClass.BUSINESS,
            mediaFilePath = imageFile.absolutePath
        )

        val dummyHistory = listOf(
            ChatHistoryEntity(id = 1L, contactName = "عميل الصورة", messageText = "سلام عليكم", isFromUser = true, timestamp = 100L),
            ChatHistoryEntity(id = 2L, contactName = "عميل الصورة", messageText = "أهلاً بك", isFromUser = false, timestamp = 200L)
        )
        coEvery { chatHistoryDao.getRecentMessagesExceptForSource(any(), any(), any(), any()) } returns dummyHistory

        coEvery {
            replyGenerationRepository.generateReply(
                notification = any(),
                candidates = any(),
                isOngoingConversation = any(),
                chatHistory = any(),
                activeOrders = any(),
                personalChunks = any(),
                maxTokens = any()
            )
        } returns """{"action":"REPLY","final_reply_text":"تمام يا فندم"}"""

        orchestrator.processNotificationSequentially(notification)

        coVerify(exactly = 1) {
            replyGenerationRepository.generateReply(
                notification = match { it.mediaFilePath == imageFile.absolutePath },
                candidates = eq(emptyList()),
                isOngoingConversation = any(),
                chatHistory = eq(emptyList()),
                activeOrders = eq(emptyList()),
                personalChunks = any(),
                maxTokens = 500,
                lastOrder = isNull(),
                orderStatusCounts = eq(emptyList())
            )
        }
    }

    @Test
    fun `Pass 2 preserves transcribed voice note context during RE_QUERY`() = runBlocking {
        val notification = WhatsAppNotification(
            title = "سارة أحمد",
            text = "[رسالة صوتية من العميل]: عايزة أعرف إيه الألوان المتاحة للهودي الأوفر سايز",
            postTime = 6000L,
            notificationKey = "key_voice_777",
            sourceClass = NotificationSourceClass.BUSINESS,
            mediaFilePath = null
        )

        // Pass 1 returns RE_QUERY
        coEvery {
            replyGenerationRepository.generateReply(
                notification = match { it.originalText.contains("عايزة أعرف إيه الألوان") },
                candidates = any(),
                isOngoingConversation = any(),
                chatHistory = any(),
                activeOrders = any(),
                personalChunks = any(),
                maxTokens = any()
            )
        } returns """{
            "action": "RE_QUERY",
            "refined_search_keyword": "ألوان هودي أوفر سايز",
            "order_action": null,
            "final_reply_text": null,
            "extracted_order": null
        }"""

        // Pass 2 should receive effectiveProcessed.originalText containing the voice transcript!
        coEvery {
            replyGenerationRepository.generateFinalReply(
                queryText = match { it.contains("[رسالة صوتية من العميل]: عايزة أعرف إيه الألوان المتاحة للهودي الأوفر سايز") },
                chunks = any(),
                isOngoingConversation = any(),
                chatHistory = any(),
                activeOrders = any(),
                personalChunks = any(),
                maxTokens = any(),
                imageUrl = any()
            )
        } returns """{
            "action": "REPLY",
            "refined_search_keyword": null,
            "order_action": null,
            "final_reply_text": "متاح منه أسود، رمادي، زيتي داكن، وأبيض يا فندم! 💙",
            "extracted_order": null
        }"""

        orchestrator.processNotificationSequentially(notification)

        // Verify Pass 2 retained the full voice note transcript in queryText
        coVerify(exactly = 1) {
            replyGenerationRepository.generateFinalReply(
                queryText = match { it.contains("[رسالة صوتية من العميل]: عايزة أعرف إيه الألوان المتاحة للهودي الأوفر سايز") },
                chunks = any(),
                isOngoingConversation = any(),
                chatHistory = any(),
                activeOrders = any(),
                personalChunks = any(),
                maxTokens = any(),
                imageUrl = any()
            )
        }
    }
}
