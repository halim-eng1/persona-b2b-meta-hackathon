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
import com.example.persona.features.auto_reply.data.repository.SemanticRetrievalRepository
import com.example.persona.features.auto_reply.data.service.IncomingMessageDispatcher
import com.example.persona.features.auto_reply.data.service.WhatsAppMediaExtractor
import com.example.persona.features.auto_reply.domain.model.PersonaTone
import com.example.persona.features.auto_reply.domain.model.ProcessedNotification
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
        coEvery { businessKnowledgeDao.getProductCatalogChunks(any()) } returns emptyList()

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

        // Verify that LLM was invoked with the notification containing mediaFilePath, 7000 token budget, and catalog candidates
        coVerify(atLeast = 1) {
            replyGenerationRepository.generateReply(
                notification = match { it.mediaFilePath == imageFile.absolutePath },
                candidates = match { it.isNotEmpty() },
                isOngoingConversation = any(),
                chatHistory = any(),
                activeOrders = any(),
                personalChunks = any(),
                maxTokens = 7000
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
}
