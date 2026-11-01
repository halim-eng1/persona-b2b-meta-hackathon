package com.example.persona.features.auto_reply.domain.orchestrator

import android.content.Context
import com.example.persona.core.data.repository.UserPreferencesRepository
import com.example.persona.core.database.dao.BusinessKnowledgeDao
import com.example.persona.core.database.dao.ChatHistoryDao
import com.example.persona.core.database.dao.FailedQueryDao
import com.example.persona.core.database.dao.OrderDao
import com.example.persona.core.database.entity.ChatHistoryEntity
import com.example.persona.core.network.dto.ChatMessage
import com.example.persona.core.network.dto.Choice
import com.example.persona.core.network.dto.OpenRouterMessageDto
import com.example.persona.core.network.dto.OpenRouterResponse
import com.example.persona.features.auto_reply.data.gatekeeper.CloudflareClefClient
import com.example.persona.features.auto_reply.data.gatekeeper.LiquidDecisionClient
import com.example.persona.features.auto_reply.data.providers.OpenRouterClient
import com.example.persona.features.auto_reply.data.repository.NotificationProcessorImpl
import com.example.persona.features.auto_reply.data.repository.ReplyGenerationRepositoryImpl
import com.example.persona.features.auto_reply.data.repository.SemanticRetrievalRepository
import com.example.persona.features.auto_reply.domain.gatekeeper.InboundIntent
import com.example.persona.features.auto_reply.domain.gatekeeper.LocalDecisionClassifier
import com.example.persona.features.auto_reply.domain.modifier.StylePostProcessor
import com.example.persona.features.auto_reply.domain.repository.AuditLogRepository
import com.example.persona.features.auto_reply.domain.repository.ReplyGenerationRepository
import com.example.persona.features.auto_reply.domain.usecase.DirectReplyExecutionUseCase
import com.example.persona.features.chat_import.data.embedding.EmbeddingEngine
import com.example.persona.features.chat_import.data.normalizer.ArabicTextNormalizer
import com.example.persona.features.settings.domain.repository.ModelCapability
import com.example.persona.features.settings.domain.repository.ModelConfig
import com.example.persona.features.settings.domain.repository.ModelProviders
import com.example.persona.features.settings.domain.repository.SecureCredentialsRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class AutoReplyOrchestratorOpenRouterTest {

    private val context: Context = mockk(relaxed = true)
    private val openRouterClient: OpenRouterClient = mockk(relaxed = true)
    private val prefsRepository: UserPreferencesRepository = mockk(relaxed = true)
    private val cloudflareClefClient: CloudflareClefClient = mockk(relaxed = true)
    private val liquidDecisionClient: LiquidDecisionClient = mockk(relaxed = true)
    private val localDecisionClassifier: LocalDecisionClassifier = mockk(relaxed = true)
    private val chatHistoryDao: ChatHistoryDao = mockk(relaxed = true)

    private lateinit var orchestrator: AutoReplyOrchestrator

    @Before
    fun setUp() {
        orchestrator = AutoReplyOrchestrator(
            incomingMessageDispatcher = mockk(relaxed = true),
            processor = mockk(relaxed = true),
            embeddingEngine = mockk(relaxed = true),
            semanticRetrievalRepository = mockk(relaxed = true),
            replyGenerationRepository = mockk(relaxed = true),
            directReplyExecutionUseCase = mockk(relaxed = true),
            auditLogRepository = mockk(relaxed = true),
            prefsRepository = prefsRepository,
            stylePostProcessor = StylePostProcessor(),
            normalizer = ArabicTextNormalizer(),
            failedQueryDao = mockk(relaxed = true),
            chatHistoryDao = chatHistoryDao,
            orderDao = mockk(relaxed = true),
            businessKnowledgeDao = mockk(relaxed = true),
            conversationMetadataDao = mockk(relaxed = true),
            context = context,
            scope = CoroutineScope(Dispatchers.Unconfined),
            cloudflareClefClient = cloudflareClefClient,
            liquidDecisionClient = liquidDecisionClient,
            localDecisionClassifier = localDecisionClassifier,
            openRouterClient = openRouterClient
        )
    }

    @Test
    fun `parseOpenRouterDecisionJson parses clean JSON payload`() {
        val json = """{"intent": "CATALOG_INQUIRY", "confidence": 0.98}"""
        val result = orchestrator.parseOpenRouterDecisionJson(json)
        assertNotNull(result)
        assertEquals(InboundIntent.CATALOG_INQUIRY, result!!.first)
        assertEquals(0.98f, result.second, 0.001f)
    }

    @Test
    fun `parseOpenRouterDecisionJson parses markdown fenced JSON`() {
        val markdown = "```json\n{\"intent\": \"GREETING\", \"confidence\": 0.90}\n```"
        val result = orchestrator.parseOpenRouterDecisionJson(markdown)
        assertNotNull(result)
        assertEquals(InboundIntent.GREETING, result!!.first)
        assertEquals(0.90f, result.second, 0.001f)
    }

    @Test
    fun `parseOpenRouterDecisionJson parses JSON wrapped in conversational preamble`() {
        val wrapped = "Here is the intent classification: {\"intent\": \"ORDER_STATUS\", \"confidence\": 0.85}"
        val result = orchestrator.parseOpenRouterDecisionJson(wrapped)
        assertNotNull(result)
        assertEquals(InboundIntent.ORDER_STATUS, result!!.first)
        assertEquals(0.85f, result.second, 0.001f)
    }

    @Test
    fun `parseOpenRouterDecisionJson maps category and banter variations`() {
        val banterJson = """{"category": "BANTER"}"""
        val result = orchestrator.parseOpenRouterDecisionJson(banterJson)
        assertNotNull(result)
        assertEquals(InboundIntent.BANTER, result!!.first)
        assertEquals(0.95f, result.second, 0.001f)
    }

    @Test
    fun `parseOpenRouterDecisionJson returns null for invalid JSON`() {
        val invalid = "I don't know how to classify this."
        val result = orchestrator.parseOpenRouterDecisionJson(invalid)
        assertNull(result)
    }

    @Test
    fun `decideWithOpenRouter executes call with OpenRouter client and returns mapped intent`() = runBlocking {
        val responseJson = """{"intent": "CATALOG_INQUIRY", "confidence": 0.96}"""
        coEvery {
            openRouterClient.executeSingleCall(
                model = "liquid/lfm-2.5-2.6b:free",
                messages = any(),
                maxTokens = any(),
                apiKey = "test_key"
            )
        } returns ReplyGenerationRepositoryImpl.CallResult.Success(
            content = responseJson,
            promptTokens = 15,
            completionTokens = 8,
            actualModel = "liquid/lfm-2.5-2.6b:free"
        )

        val outcome = orchestrator.decideWithOpenRouter(
            gatekeeperInput = "بكام سعر الهودي؟",
            models = listOf("liquid/lfm-2.5-2.6b:free"),
            keys = listOf("test_key")
        )

        assertNotNull(outcome)
        assertEquals(InboundIntent.CATALOG_INQUIRY, outcome!!.first)
        assertEquals(0.96f, outcome.second, 0.001f)
        assertEquals("liquid/lfm-2.5-2.6b:free", outcome.third)
    }

    @Test
    fun `decideWithOpenRouter queries secure credentials when keys parameter is omitted`() = runBlocking {
        val mockSecureCreds = mockk<SecureCredentialsRepository>(relaxed = true)
        coEvery { mockSecureCreds.getApiKeys(ModelProviders.OPENROUTER) } returns listOf("sk-custom-user-key")

        val customOrchestrator = AutoReplyOrchestrator(
            incomingMessageDispatcher = mockk(relaxed = true),
            processor = mockk(relaxed = true),
            embeddingEngine = mockk(relaxed = true),
            semanticRetrievalRepository = mockk(relaxed = true),
            replyGenerationRepository = mockk(relaxed = true),
            directReplyExecutionUseCase = mockk(relaxed = true),
            auditLogRepository = mockk(relaxed = true),
            prefsRepository = prefsRepository,
            stylePostProcessor = StylePostProcessor(),
            normalizer = ArabicTextNormalizer(),
            failedQueryDao = mockk(relaxed = true),
            chatHistoryDao = chatHistoryDao,
            orderDao = mockk(relaxed = true),
            businessKnowledgeDao = mockk(relaxed = true),
            conversationMetadataDao = mockk(relaxed = true),
            context = context,
            scope = CoroutineScope(Dispatchers.Unconfined),
            cloudflareClefClient = cloudflareClefClient,
            liquidDecisionClient = liquidDecisionClient,
            localDecisionClassifier = localDecisionClassifier,
            openRouterClient = openRouterClient,
            secureCredentialsRepository = mockSecureCreds
        )

        val responseJson = """{"intent": "GREETING", "confidence": 0.99}"""
        coEvery {
            openRouterClient.executeSingleCall(
                model = "test-model",
                messages = any(),
                maxTokens = any(),
                apiKey = "sk-custom-user-key"
            )
        } returns ReplyGenerationRepositoryImpl.CallResult.Success(
            content = responseJson,
            promptTokens = 10,
            completionTokens = 5,
            actualModel = "test-model"
        )

        val outcome = customOrchestrator.decideWithOpenRouter(
            gatekeeperInput = "السلام عليكم",
            models = listOf("test-model")
        )

        assertNotNull(outcome)
        assertEquals(InboundIntent.GREETING, outcome!!.first)
        io.mockk.verify(atLeast = 1) { mockSecureCreds.getApiKeys(ModelProviders.OPENROUTER) }
    }
}
