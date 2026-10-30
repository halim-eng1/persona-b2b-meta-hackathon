package com.example.persona.features.auto_reply.domain.orchestrator

import android.content.Context
import com.example.persona.core.data.repository.UserPreferencesRepository
import com.example.persona.core.database.dao.BusinessKnowledgeDao
import com.example.persona.core.database.dao.ChatHistoryDao
import com.example.persona.core.database.dao.ConversationMetadataDao
import com.example.persona.core.database.dao.FailedQueryDao
import com.example.persona.core.database.dao.OrderDao
import com.example.persona.core.database.entity.ChatHistoryEntity
import com.example.persona.features.auto_reply.data.diagnostics.NotificationSourceClass
import com.example.persona.features.auto_reply.data.gatekeeper.CloudflareClefClient
import com.example.persona.features.auto_reply.data.gatekeeper.LiquidDecisionClient
import com.example.persona.features.auto_reply.data.repository.NotificationProcessorImpl
import com.example.persona.features.auto_reply.data.repository.PersistentDebounceBuffer
import com.example.persona.features.auto_reply.data.repository.SemanticRetrievalRepository
import com.example.persona.features.auto_reply.data.service.IncomingMessageDispatcher
import com.example.persona.features.auto_reply.domain.gatekeeper.InboundIntent
import com.example.persona.features.auto_reply.domain.gatekeeper.LocalDecisionClassifier
import com.example.persona.features.auto_reply.domain.model.ProcessedNotification
import com.example.persona.features.auto_reply.domain.model.StylePreferences
import com.example.persona.features.auto_reply.domain.model.WhatsAppNotification
import com.example.persona.features.auto_reply.domain.modifier.StylePostProcessor
import com.example.persona.features.auto_reply.domain.repository.AuditLogRepository
import com.example.persona.features.auto_reply.domain.repository.ReplyGenerationRepository
import com.example.persona.features.auto_reply.domain.usecase.DirectReplyExecutionUseCase
import com.example.persona.features.auto_reply.domain.usecase.ReplyExecutionResult
import com.example.persona.features.chat_import.data.embedding.EmbeddingEngine
import com.example.persona.features.chat_import.data.normalizer.ArabicTextNormalizer
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class AutoReplyOrchestratorConcurrencyTest {

    private lateinit var tempDir: File
    private lateinit var context: Context
    private lateinit var chatHistoryDao: ChatHistoryDao
    private lateinit var prefsRepo: UserPreferencesRepository
    private lateinit var replyGenerationRepository: ReplyGenerationRepository
    private lateinit var directReplyExecutionUseCase: DirectReplyExecutionUseCase
    private lateinit var cfClient: CloudflareClefClient
    private lateinit var liquidClient: LiquidDecisionClient
    private lateinit var localClassifier: LocalDecisionClassifier
    private lateinit var embeddingEngine: EmbeddingEngine
    private lateinit var processor: NotificationProcessorImpl
    private lateinit var orchestrator: AutoReplyOrchestrator

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("orchestrator_concurrency").toFile()
        context = mockk(relaxed = true)
        every { context.filesDir } returns tempDir

        chatHistoryDao = mockk(relaxed = true)
        prefsRepo = mockk(relaxed = true)
        replyGenerationRepository = mockk(relaxed = true)
        directReplyExecutionUseCase = mockk(relaxed = true)
        cfClient = mockk(relaxed = true)
        liquidClient = mockk(relaxed = true)
        localClassifier = LocalDecisionClassifier()
        embeddingEngine = mockk(relaxed = true)
        processor = mockk(relaxed = true)

        coEvery { prefsRepo.isAutoReplyEnabledSync() } returns true
        coEvery { prefsRepo.getStylePreferencesSync() } returns StylePreferences(cooldownMins = 0, replyDelaySeconds = 0)
        coEvery { directReplyExecutionUseCase.execute(any(), any(), any(), any()) } returns ReplyExecutionResult.Success
    }

    private fun buildOrchestrator(scope: CoroutineScope): AutoReplyOrchestrator {
        return AutoReplyOrchestrator(
            incomingMessageDispatcher = mockk(relaxed = true),
            processor = processor,
            embeddingEngine = embeddingEngine,
            semanticRetrievalRepository = mockk(relaxed = true),
            replyGenerationRepository = replyGenerationRepository,
            directReplyExecutionUseCase = directReplyExecutionUseCase,
            auditLogRepository = mockk(relaxed = true),
            prefsRepository = prefsRepo,
            stylePostProcessor = StylePostProcessor(),
            normalizer = ArabicTextNormalizer(),
            failedQueryDao = mockk(relaxed = true),
            chatHistoryDao = chatHistoryDao,
            orderDao = mockk(relaxed = true),
            businessKnowledgeDao = mockk(relaxed = true),
            conversationMetadataDao = mockk(relaxed = true),
            context = context,
            scope = scope,
            cloudflareClefClient = cfClient,
            liquidDecisionClient = liquidClient,
            localDecisionClassifier = localClassifier,
            persistentDebounceBuffer = PersistentDebounceBuffer(context)
        )
    }

    @Test
    fun `gatekeeper resolves via Tier 1 Cloudflare Clef when available`() = runBlocking {
        orchestrator = buildOrchestrator(this)

        val greetingText = "صباح الخير"
        val processed = ProcessedNotification(
            contactName = "GreetingUser",
            originalText = greetingText,
            normalizedText = greetingText,
            notificationKey = "key_greeting_1",
            postTime = System.currentTimeMillis(),
            context = mockk(relaxed = true),
            sourceClass = NotificationSourceClass.BUSINESS
        )
        coEvery { processor.process(any()) } returns processed
        coEvery { cfClient.decide(any(), any()) } returns InboundIntent.GREETING
        coEvery {
            replyGenerationRepository.generateReply(
                notification = any(),
                candidates = any(),
                isOngoingConversation = any(),
                chatHistory = any(),
                activeOrders = any(),
                personalChunks = any(),
                maxTokens = any(),
                lastOrder = any(),
                orderStatusCounts = any(),
                inboundIntent = InboundIntent.GREETING
            )
        } returns """{"action":"REPLY","final_reply_text":"صباح النور يا فندم! أقدر أساعدك إزاي؟"}"""

        orchestrator.processNotificationSequentially(
            WhatsAppNotification(
                title = "GreetingUser",
                text = greetingText,
                postTime = System.currentTimeMillis(),
                notificationKey = "key_greeting_1",
                sourceClass = NotificationSourceClass.BUSINESS
            )
        )

        // Verifies Tier 1 Cloudflare Clef was called as primary
        coVerify(exactly = 1) { cfClient.decide(any(), any()) }
        coVerify(exactly = 0) { liquidClient.decide(any(), any()) }

        // And direct reply executed
        coVerify(atLeast = 1) {
            directReplyExecutionUseCase.execute(
                "key_greeting_1",
                match { it.contains("صباح النور") },
                "GreetingUser",
                "BUSINESS"
            )
        }
    }

    @Test
    fun `gatekeeper falls back to Tier 3 LocalDecisionClassifier when Cloudflare and Liquid fail`() = runBlocking {
        orchestrator = buildOrchestrator(this)

        val greetingText = "صباح الخير"
        val processed = ProcessedNotification(
            contactName = "FallbackUser",
            originalText = greetingText,
            normalizedText = greetingText,
            notificationKey = "key_fallback_1",
            postTime = System.currentTimeMillis(),
            context = mockk(relaxed = true),
            sourceClass = NotificationSourceClass.BUSINESS
        )
        coEvery { processor.process(any()) } returns processed
        coEvery { cfClient.decide(any(), any()) } throws RuntimeException("CF down")
        coEvery { liquidClient.decide(any(), any()) } throws RuntimeException("Liquid down")
        coEvery {
            replyGenerationRepository.generateReply(
                notification = any(),
                candidates = any(),
                isOngoingConversation = any(),
                chatHistory = any(),
                activeOrders = any(),
                personalChunks = any(),
                maxTokens = any(),
                lastOrder = any(),
                orderStatusCounts = any(),
                inboundIntent = any()
            )
        } returns """{"action":"REPLY","final_reply_text":"أهلاً بك، تفضل كيف أساعدك؟"}"""

        orchestrator.processNotificationSequentially(
            WhatsAppNotification(
                title = "FallbackUser",
                text = greetingText,
                postTime = System.currentTimeMillis(),
                notificationKey = "key_fallback_1",
                sourceClass = NotificationSourceClass.BUSINESS
            )
        )

        // Verifies both cloud gatekeepers were attempted before fallback
        coVerify(exactly = 1) { cfClient.decide(any(), any()) }
        coVerify(exactly = 1) { liquidClient.decide(any(), any()) }

        coVerify(atLeast = 1) {
            directReplyExecutionUseCase.execute(
                "key_fallback_1",
                any(),
                "FallbackUser",
                "BUSINESS"
            )
        }
    }

    @Test
    fun `cancelInFlightProcessing cancels active processing for contact`() = runBlocking {
        orchestrator = buildOrchestrator(this)

        val contactKey = "BUSINESS_busy_customer"
        val notification = WhatsAppNotification(
            title = "busy_customer",
            text = "ألو",
            postTime = System.currentTimeMillis(),
            notificationKey = "key_busy_1",
            sourceClass = NotificationSourceClass.BUSINESS
        )

        coEvery { processor.process(any()) } coAnswers {
            delay(1000L) // Simulate work
            mockk(relaxed = true)
        }

        orchestrator.dispatchContactProcessing(notification)
        // Cancel in-flight processing
        orchestrator.cancelInFlightProcessing(contactKey)

        // Job was cancelled
        assertFalse(AutoReplyOrchestrator.isContactProcessing("busy_customer"))
    }
}
