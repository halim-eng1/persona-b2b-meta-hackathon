package com.example.persona.features.auto_reply.data.repository

import android.content.Context
import com.example.persona.core.data.repository.UserPreferencesRepository
import com.example.persona.core.database.entity.ChatHistoryEntity
import com.example.persona.core.database.entity.OrderEntity
import com.example.persona.core.database.entity.OrderStatus
import com.example.persona.core.network.dto.AgenticLlmResponse
import com.example.persona.core.network.dto.ChatMessage
import com.example.persona.core.network.dto.GroqRequest
import com.example.persona.core.network.dto.NvidiaRequest
import com.example.persona.core.network.dto.OpenRouterRequest
import com.example.persona.core.network.dto.QwenChatRequest
import com.example.persona.features.auto_reply.domain.model.RetrievalCandidate
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplyGenerationRepositoryImplTest {

    @Test
    fun `buildDynamicContextBlock compacts personal user state when no active orders`() {
        val dynamicBlock = ReplyGenerationRepositoryImpl.buildDynamicContextBlock(
            notificationText = "بكام الشحن؟",
            candidates = emptyList(),
            activeOrders = emptyList(),
            personalChunks = emptyList()
        )

        assertTrue(dynamicBlock.contains("=== CUSTOMER ORDER STATE ==="))
        assertTrue(dynamicBlock.contains("Active Order: None"))
        // Must NOT contain 5 lines of None
        assertFalse(dynamicBlock.contains("Order Status: No Active Order"))
        assertFalse(dynamicBlock.contains("Order Details: None"))
        assertFalse(dynamicBlock.contains("Delivery Address: None"))
        assertFalse(dynamicBlock.contains("Custom Notes: None"))
        assertFalse(dynamicBlock.contains("Merchant Notes: None"))

        // Relevant Customer Records must be completely omitted when empty
        assertFalse(dynamicBlock.contains("Relevant Customer Records"))
    }

    @Test
    fun `buildDynamicContextBlock formats active order and relevant records when present`() {
        val activeOrder = OrderEntity(
            id = 101L,
            contactName = "عميل تجريبي",
            customerNameFromLlm = "أحمد",
            deliveryAddress = "المعادي، القاهرة",
            orderDetails = "حذاء جلدي مقاس 43",
            customNotes = "التسليم بعد الساعة 5",
            merchantNote = "عميل مميز",
            status = OrderStatus.IN_PREPARATION.name
        )

        val personalChunk = ChatHistoryEntity(
            id = 12L,
            contactName = "عميل تجريبي",
            messageText = "رقم التليفون الثاني 01012345678",
            isFromUser = true,
            timestamp = 1000L
        )

        val dynamicBlock = ReplyGenerationRepositoryImpl.buildDynamicContextBlock(
            notificationText = "فين الأوردر؟",
            candidates = emptyList(),
            activeOrders = listOf(activeOrder),
            personalChunks = listOf(personalChunk)
        )

        assertTrue(dynamicBlock.contains("Order Status: قيد التجهيز"))
        assertTrue(dynamicBlock.contains("Order Details: حذاء جلدي مقاس 43"))
        assertTrue(dynamicBlock.contains("Delivery Address: المعادي، القاهرة"))
        assertTrue(dynamicBlock.contains("Custom Notes: التسليم بعد الساعة 5"))
        assertTrue(dynamicBlock.contains("Merchant Notes: عميل مميز"))
        assertTrue(dynamicBlock.contains("Relevant Customer Records:"))
        assertTrue(dynamicBlock.contains("- رقم التليفون الثاني 01012345678"))
    }

    @Test
    fun `buildDynamicContextBlock does not duplicate header and strips stray horizontal rules`() {
        val candidate1 = RetrievalCandidate(
            chunkId = "c1",
            headerContext = "سياسة الاسترجاع - الشروط",
            chunkText = "سياسة الاسترجاع - الشروط: يمكن الاسترجاع خلال 14 يوم.\n---\nشرط سريان الفاتورة.",
            similarityScore = 0.65f,
            baseSimilarityScore = 0.65f
        )
        val candidate2 = RetrievalCandidate(
            chunkId = "c2",
            headerContext = "أسعار الشحن",
            chunkText = "الشحن للقاهرة 50 جنيه وللإسكندرية 60 جنيه.---",
            similarityScore = 0.60f,
            baseSimilarityScore = 0.60f
        )

        val dynamicBlock = ReplyGenerationRepositoryImpl.buildDynamicContextBlock(
            notificationText = "عايز أسترجع",
            candidates = listOf(candidate1, candidate2),
            activeOrders = emptyList(),
            personalChunks = emptyList()
        )

        assertTrue(dynamicBlock.contains("=== STORE KNOWLEDGE ==="))
        // Verify no duplicate line like "سياسة الاسترجاع - الشروط\nسياسة الاسترجاع - الشروط: ..."
        assertFalse(dynamicBlock.contains("سياسة الاسترجاع - الشروط\nسياسة الاسترجاع - الشروط"))
        // Verify no double horizontal rules
        assertFalse(dynamicBlock.contains("---\n---"))
        assertTrue(dynamicBlock.contains("يمكن الاسترجاع خلال 14 يوم."))
        assertTrue(dynamicBlock.contains("شرط سريان الفاتورة."))
        assertTrue(dynamicBlock.contains("الشحن للقاهرة 50 جنيه"))
    }

    @Test
    fun `buildOpenRouterMessages creates prefix-cache friendly multi-turn list with strict alternation`() {
        val historyTurns = listOf(
            // Descending chronological order from DB (most recent first)
            ChatHistoryEntity(id = 4L, contactName = "c", messageText = "تمام شكراً", isFromUser = false, timestamp = 400L),
            ChatHistoryEntity(id = 3L, contactName = "c", messageText = "وصل؟", isFromUser = true, timestamp = 300L),
            ChatHistoryEntity(id = 2L, contactName = "c", messageText = "جار تجهيزه يا فندم", isFromUser = false, timestamp = 200L),
            ChatHistoryEntity(id = 1L, contactName = "c", messageText = "سلام عليكم", isFromUser = true, timestamp = 100L)
        )

        val dynamicBlock = "=== STORE KNOWLEDGE ===\nNone\n\n=== CURRENT CUSTOMER MESSAGE ===\nهيوصل امتى؟"

        val messages = ReplyGenerationRepositoryImpl.buildOpenRouterMessages(
            systemPrompt = ReplyGenerationRepositoryImpl.SYSTEM_PROMPT_FIRST_PASS,
            chatHistory = historyTurns,
            currentInboundText = dynamicBlock
        )

        // 0: system
        // 1: user (سلام عليكم)
        // 2: assistant (جار تجهيزه يا فندم wrapped in AgenticLlmResponse JSON)
        // 3: user (وصل؟)
        // 4: assistant (تمام شكراً wrapped in AgenticLlmResponse JSON)
        // 5: user (final customer message without metadata markers)
        assertEquals(6, messages.size)
        assertEquals("system", messages[0].role)
        assertEquals(ReplyGenerationRepositoryImpl.SYSTEM_PROMPT_FIRST_PASS, messages[0].content)

        assertEquals("user", messages[1].role)
        assertEquals("[منذ فترة] سلام عليكم", messages[1].content)

        assertEquals("assistant", messages[2].role)
        assertTrue(messages[2].content.contains("\"action\":\"REPLY\""))
        assertTrue(messages[2].content.contains("\"final_reply_text\":\"جار تجهيزه يا فندم\""))

        assertEquals("user", messages[3].role)
        assertEquals("[منذ فترة] وصل؟", messages[3].content)

        assertEquals("assistant", messages[4].role)
        assertTrue(messages[4].content.contains("\"action\":\"REPLY\""))
        assertTrue(messages[4].content.contains("\"final_reply_text\":\"تمام شكراً\""))

        assertEquals("user", messages[5].role)
        assertEquals("[الآن] هيوصل امتى؟", messages[5].content)
        assertFalse(messages[5].content.contains("=== CURRENT CUSTOMER MESSAGE ==="))
    }

    @Test
    fun `buildOpenRouterMessages coalesces consecutive same-role messages`() {
        val historyTurns = listOf(
            // Most recent first: two user messages in a row in history
            ChatHistoryEntity(id = 2L, contactName = "c", messageText = "عاوز حذاء مقاس 42", isFromUser = true, timestamp = 200L),
            ChatHistoryEntity(id = 1L, contactName = "c", messageText = "مساء الخير", isFromUser = true, timestamp = 100L)
        )

        val dynamicBlock = "=== STORE KNOWLEDGE ===\nNone\n\n=== CURRENT CUSTOMER MESSAGE ===\nلونه أسود"

        val messages = ReplyGenerationRepositoryImpl.buildOpenRouterMessages(
            systemPrompt = ReplyGenerationRepositoryImpl.SYSTEM_PROMPT_FIRST_PASS,
            chatHistory = historyTurns,
            currentInboundText = dynamicBlock
        )

        // Since all turns were from user and final is user, they coalesce into 1 final user turn to strictly alternate with system
        assertEquals(2, messages.size)
        assertEquals("system", messages[0].role)
        assertEquals("user", messages[1].role)
        assertEquals("[منذ فترة] مساء الخير\n[منذ فترة] عاوز حذاء مقاس 42\n[الآن] لونه أسود", messages[1].content)
        assertFalse(messages[1].content.contains("=== CURRENT CUSTOMER MESSAGE ==="))
    }

    @Test
    fun `system prompts have parity and no raw string quote escapes`() {
        // Assert raw string bug is fixed (no trailing \")
        assertFalse(ReplyGenerationRepositoryImpl.SYSTEM_PROMPT_FIRST_PASS.endsWith("\\\""))
        assertFalse(ReplyGenerationRepositoryImpl.SYSTEM_PROMPT_FINAL_PASS.endsWith("\\\""))

        // Assert essential sections are in both
        assertTrue(ReplyGenerationRepositoryImpl.SYSTEM_PROMPT_FIRST_PASS.contains("IDENTITY & ZERO HALLUCINATION:"))
        assertTrue(ReplyGenerationRepositoryImpl.SYSTEM_PROMPT_FINAL_PASS.contains("IDENTITY & ZERO HALLUCINATION:"))

        assertTrue(ReplyGenerationRepositoryImpl.SYSTEM_PROMPT_FIRST_PASS.contains("CONCISENESS & TONE:"))
        assertTrue(ReplyGenerationRepositoryImpl.SYSTEM_PROMPT_FINAL_PASS.contains("CONCISENESS & TONE:"))

        assertTrue(ReplyGenerationRepositoryImpl.SYSTEM_PROMPT_FIRST_PASS.contains("GENERALIZED OUT-OF-BOUNDS MARKETING RECOVERY:"))
        assertTrue(ReplyGenerationRepositoryImpl.SYSTEM_PROMPT_FINAL_PASS.contains("GENERALIZED OUT-OF-BOUNDS MARKETING RECOVERY:"))

        // Parity on order extraction in schema
        assertTrue(ReplyGenerationRepositoryImpl.SYSTEM_PROMPT_FIRST_PASS.contains("\"order_action\": \"CREATE\" | \"CANCEL\" | \"UPDATE\" | null"))
        assertTrue(ReplyGenerationRepositoryImpl.SYSTEM_PROMPT_FINAL_PASS.contains("\"order_action\": \"CREATE\" | \"CANCEL\" | \"UPDATE\" | null"))
        assertTrue(ReplyGenerationRepositoryImpl.SYSTEM_PROMPT_FINAL_PASS.contains("\"extracted_order\": {"))

        // Parity on order status instruction
        assertTrue(ReplyGenerationRepositoryImpl.SYSTEM_PROMPT_FIRST_PASS.contains("ORDER STATUS: When the customer asks about their order status"))
        assertTrue(ReplyGenerationRepositoryImpl.SYSTEM_PROMPT_FINAL_PASS.contains("ORDER STATUS: When the customer asks about their order status"))

        // Difference: First pass allows RE_QUERY, Final pass strictly forbids RE_QUERY
        assertTrue(ReplyGenerationRepositoryImpl.SYSTEM_PROMPT_FIRST_PASS.contains("\"action\": \"REPLY\" | \"RE_QUERY\" | \"HITL\""))
        assertTrue(ReplyGenerationRepositoryImpl.SYSTEM_PROMPT_FINAL_PASS.contains("\"action\": \"REPLY\" | \"HITL\""))
        assertTrue(ReplyGenerationRepositoryImpl.SYSTEM_PROMPT_FINAL_PASS.contains("\"RE_QUERY\" is completely forbidden"))

        // Factuality and extraction purity checks
        assertTrue(ReplyGenerationRepositoryImpl.SYSTEM_PROMPT_FIRST_PASS.contains("STRICT BUSINESS & CONTACT FACTUALITY:"))
        assertTrue(ReplyGenerationRepositoryImpl.SYSTEM_PROMPT_FINAL_PASS.contains("STRICT BUSINESS & CONTACT FACTUALITY:"))
        assertTrue(ReplyGenerationRepositoryImpl.SYSTEM_PROMPT_FIRST_PASS.contains("EXTRACTION PURITY:"))
        assertTrue(ReplyGenerationRepositoryImpl.SYSTEM_PROMPT_FINAL_PASS.contains("EXTRACTION PURITY:"))
    }

    @Test
    fun `outgoing OpenRouterRequest does not serialize reasoning_content`() {
        val json = Json {
            encodeDefaults = true
            explicitNulls = false
            ignoreUnknownKeys = true
            isLenient = true
        }
        val request = OpenRouterRequest(
            model = "test-model",
            messages = listOf(
                ChatMessage(role = "system", content = "sys"),
                ChatMessage(role = "user", content = "user"),
                ChatMessage(role = "assistant", content = "bot")
            )
        )
        val serialized = json.encodeToString(OpenRouterRequest.serializer(), request)
        assertFalse("reasoning_content must not be serialized when null", serialized.contains("reasoning_content"))
    }

    @Test
    fun `user messages contain only raw text and dynamic context lives in system prompt Message 0`() {
        val staticPrompt = ReplyGenerationRepositoryImpl.SYSTEM_PROMPT_FIRST_PASS
        val candidates = listOf(
            RetrievalCandidate(
                chunkId = "c1",
                headerContext = "المنتجات",
                chunkText = "لدينا أحذية وملابس",
                similarityScore = 0.7f,
                baseSimilarityScore = 0.7f
            )
        )
        val orders = listOf(
            OrderEntity(
                id = 1L,
                contactName = "test",
                status = OrderStatus.PENDING.name,
                orderDetails = "حذاء أسود"
            )
        )
        val systemPromptWithContext = ReplyGenerationRepositoryImpl.buildSystemPromptWithContext(
            staticSystemPrompt = staticPrompt,
            candidates = candidates,
            activeOrders = orders
        )

        assertTrue(systemPromptWithContext.contains("<store_knowledge>"))
        assertTrue(systemPromptWithContext.contains("لدينا أحذية وملابس"))
        assertTrue(systemPromptWithContext.contains("</store_knowledge>"))
        assertTrue(systemPromptWithContext.contains("<customer_order_state>"))
        assertTrue(systemPromptWithContext.contains("Order Details: حذاء أسود"))
        assertTrue(systemPromptWithContext.contains("</customer_order_state>"))

        val history = listOf(
            ChatHistoryEntity(id = 2L, contactName = "c", messageText = "أهلاً بك", isFromUser = false, timestamp = 200L),
            ChatHistoryEntity(id = 1L, contactName = "c", messageText = "السلام عليكم", isFromUser = true, timestamp = 100L)
        )

        val messages = ReplyGenerationRepositoryImpl.buildOpenRouterMessages(
            systemPrompt = systemPromptWithContext,
            chatHistory = history,
            currentInboundText = "بكام الشحن؟"
        )

        // Message 0 is system with store_knowledge and customer_order_state
        assertEquals("system", messages[0].role)
        assertTrue(messages[0].content.contains("<store_knowledge>"))
        assertTrue(messages[0].content.contains("<customer_order_state>"))

        // All user messages contain only raw text
        val userMessages = messages.filter { it.role == "user" }
        assertEquals(2, userMessages.size)
        assertEquals("[منذ فترة] السلام عليكم", userMessages[0].content)
        assertEquals("[الآن] بكام الشحن؟", userMessages[1].content)

        userMessages.forEach { msg ->
            assertFalse(msg.content.contains("<store_knowledge>"))
            assertFalse(msg.content.contains("=== STORE KNOWLEDGE ==="))
            assertFalse(msg.content.contains("<customer_order_state>"))
            assertFalse(msg.content.contains("=== CUSTOMER ORDER STATE ==="))
            assertFalse(msg.content.contains("=== CURRENT CUSTOMER MESSAGE ==="))
        }
    }

    @Test
    fun `historical assistant turns are formatted as valid AgenticLlmResponse JSON`() {
        val history = listOf(
            ChatHistoryEntity(id = 2L, contactName = "c", messageText = "أهلاً وسهلاً بحضرتك", isFromUser = false, timestamp = 200L),
            ChatHistoryEntity(id = 1L, contactName = "c", messageText = "مرحبا", isFromUser = true, timestamp = 100L)
        )
        val messages = ReplyGenerationRepositoryImpl.buildOpenRouterMessages(
            systemPrompt = "sys",
            chatHistory = history,
            currentInboundText = "عايز أسأل"
        )

        val assistantMessage = messages.first { it.role == "assistant" }
        val json = Json { ignoreUnknownKeys = true }
        val decoded = json.decodeFromString<AgenticLlmResponse>(assistantMessage.content)

        assertEquals("REPLY", decoded.action)
        assertEquals("أهلاً وسهلاً بحضرتك", decoded.finalReplyText)
        assertEquals(null, decoded.refinedSearchKeyword)
        assertEquals(null, decoded.orderAction)
        assertEquals(null, decoded.extractedOrder)
    }

    @Test
    fun `canonical HITL fallback replies and triggering user turns are preserved in history with HITL action`() {
        val history = listOf(
            // Descending order (most recent first): HITL fallback turns from outages
            ChatHistoryEntity(id = 3L, contactName = "c", messageText = ReplyGenerationRepositoryImpl.CANONICAL_HITL_MESSAGE, isFromUser = false, timestamp = 300L),
            ChatHistoryEntity(id = 2L, contactName = "c", messageText = ReplyGenerationRepositoryImpl.CANONICAL_HITL_MESSAGE, isFromUser = false, timestamp = 200L),
            ChatHistoryEntity(id = 1L, contactName = "c", messageText = "سلام عليكم", isFromUser = true, timestamp = 100L)
        )

        val messages = ReplyGenerationRepositoryImpl.buildOpenRouterMessages(
            systemPrompt = "sys",
            chatHistory = history,
            currentInboundText = "حد يرد"
        )

        val assistantMessages = messages.filter { it.role == "assistant" }
        assertEquals("Canonical HITL messages should be preserved in prompt context", 1, assistantMessages.size)
        assertTrue(assistantMessages[0].content.contains("\"action\":\"HITL\""))
        assertTrue(assistantMessages[0].content.contains(ReplyGenerationRepositoryImpl.CANONICAL_HITL_MESSAGE))
    }

    @Test
    fun `callOpenRouter uses primary key when request succeeds`() = runTest {
        val capturedAuthHeaders = mutableListOf<String>()
        val mockEngine = MockEngine { request ->
            capturedAuthHeaders.add(request.headers[HttpHeaders.Authorization] ?: "")
            respond(
                content = """{"choices":[{"message":{"content":"{\"action\":\"REPLY\",\"final_reply_text\":\"تمام\"}"}}]}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true })
            }
        }
        val repo = ReplyGenerationRepositoryImpl(
            httpClient = client,
            context = mockk<Context>(relaxed = true),
            primaryApiKey = "primary-key-123",
            fallbackApiKey = "fallback-key-456",
            userPreferencesRepository = mockk<UserPreferencesRepository>(relaxed = true)
        )

        val result = repo.callOpenRouter(
            model = "test-model",
            messages = listOf(ChatMessage(role = "user", content = "hi")),
            maxTokens = 100
        )

        assertTrue(result is ReplyGenerationRepositoryImpl.CallResult.Success)
        assertEquals(1, capturedAuthHeaders.size)
        assertEquals("Bearer primary-key-123", capturedAuthHeaders[0])
    }

    @Test
    fun `callOpenRouter falls back to V2 key when primary key encounters 429`() = runTest {
        val capturedAuthHeaders = mutableListOf<String>()
        val mockEngine = MockEngine { request ->
            val auth = request.headers[HttpHeaders.Authorization] ?: ""
            capturedAuthHeaders.add(auth)
            if (auth.contains("primary-key-123")) {
                respond(
                    content = """{"error":{"message":"Rate limit exceeded"}}""",
                    status = HttpStatusCode.TooManyRequests,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            } else {
                respond(
                    content = """{"choices":[{"message":{"content":"{\"action\":\"REPLY\",\"final_reply_text\":\"رد بديل\"}"}}]}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
        }
        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true })
            }
        }
        val repo = ReplyGenerationRepositoryImpl(
            httpClient = client,
            context = mockk<Context>(relaxed = true),
            primaryApiKey = "primary-key-123",
            fallbackApiKey = "fallback-key-456",
            userPreferencesRepository = mockk<UserPreferencesRepository>(relaxed = true)
        )

        val result = repo.callOpenRouter(
            model = "test-model",
            messages = listOf(ChatMessage(role = "user", content = "hi")),
            maxTokens = 100
        )

        assertTrue(result is ReplyGenerationRepositoryImpl.CallResult.Success)
        assertEquals(2, capturedAuthHeaders.size)
        assertEquals("Bearer primary-key-123", capturedAuthHeaders[0])
        assertEquals("Bearer fallback-key-456", capturedAuthHeaders[1])
    }

    @Test
    fun `callOpenRouter skips V2 key on HTTP 502 server error and fails immediately`() = runTest {
        val capturedAuthHeaders = mutableListOf<String>()
        val mockEngine = MockEngine { request ->
            val auth = request.headers[HttpHeaders.Authorization] ?: ""
            capturedAuthHeaders.add(auth)
            respond(
                content = """Bad Gateway""",
                status = HttpStatusCode.BadGateway,
                headers = headersOf(HttpHeaders.ContentType, "text/plain")
            )
        }
        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true })
            }
        }
        val repo = ReplyGenerationRepositoryImpl(
            httpClient = client,
            context = mockk<Context>(relaxed = true),
            primaryApiKey = "primary-key-123",
            fallbackApiKey = "fallback-key-456",
            userPreferencesRepository = mockk<UserPreferencesRepository>(relaxed = true)
        )

        val result = repo.callOpenRouter(
            model = "test-model",
            messages = listOf(ChatMessage(role = "user", content = "hi")),
            maxTokens = 100
        )

        assertTrue(result is ReplyGenerationRepositoryImpl.CallResult.ServerError)
        assertEquals(1, capturedAuthHeaders.size)
        assertEquals("Bearer primary-key-123", capturedAuthHeaders[0])
    }

    @Test
    fun `callOpenRouter falls back to V2 key when primary key encounters 401 Unauthorized`() = runTest {
        val capturedAuthHeaders = mutableListOf<String>()
        val mockEngine = MockEngine { request ->
            val auth = request.headers[HttpHeaders.Authorization] ?: ""
            capturedAuthHeaders.add(auth)
            if (auth.contains("primary-key-123")) {
                respond(
                    content = """{"error":{"message":"Invalid API key"}}""",
                    status = HttpStatusCode.Unauthorized,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            } else {
                respond(
                    content = """{"choices":[{"message":{"content":"{\"action\":\"REPLY\",\"final_reply_text\":\"نجاح المفتاح البديل\"}"}}]}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
        }
        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true })
            }
        }
        val repo = ReplyGenerationRepositoryImpl(
            httpClient = client,
            context = mockk<Context>(relaxed = true),
            primaryApiKey = "primary-key-123",
            fallbackApiKey = "fallback-key-456",
            userPreferencesRepository = mockk<UserPreferencesRepository>(relaxed = true)
        )

        val result = repo.callOpenRouter(
            model = "test-model",
            messages = listOf(ChatMessage(role = "user", content = "hi")),
            maxTokens = 100
        )

        assertTrue(result is ReplyGenerationRepositoryImpl.CallResult.Success)
        assertEquals(2, capturedAuthHeaders.size)
        assertEquals("Bearer primary-key-123", capturedAuthHeaders[0])
        assertEquals("Bearer fallback-key-456", capturedAuthHeaders[1])
    }

    @Test
    fun `callOpenRouter returns RateLimited when both primary and fallback keys return 429`() = runTest {
        val capturedAuthHeaders = mutableListOf<String>()
        val mockEngine = MockEngine { request ->
            capturedAuthHeaders.add(request.headers[HttpHeaders.Authorization] ?: "")
            respond(
                content = """{"error":{"message":"Quota exceeded"}}""",
                status = HttpStatusCode.TooManyRequests,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true })
            }
        }
        val repo = ReplyGenerationRepositoryImpl(
            httpClient = client,
            context = mockk<Context>(relaxed = true),
            primaryApiKey = "primary-key-123",
            fallbackApiKey = "fallback-key-456",
            userPreferencesRepository = mockk<UserPreferencesRepository>(relaxed = true)
        )

        val result = repo.callOpenRouter(
            model = "test-model",
            messages = listOf(ChatMessage(role = "user", content = "hi")),
            maxTokens = 100
        )

        assertTrue(result is ReplyGenerationRepositoryImpl.CallResult.RateLimited)
        assertEquals(2, capturedAuthHeaders.size)
    }

    @Test
    fun `executeFailoverChain returns success when fallback key succeeds on model 1 after primary key 429`() = runTest {
        val mockEngine = MockEngine { request ->
            val auth = request.headers[HttpHeaders.Authorization] ?: ""
            if (auth.contains("primary-key-123")) {
                respond(
                    content = """{"error":{"message":"Rate limit exceeded"}}""",
                    status = HttpStatusCode.TooManyRequests,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            } else {
                respond(
                    content = """{"choices":[{"message":{"content":"{\"action\":\"REPLY\",\"final_reply_text\":\"مرحبا بك\"}"}}]}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
        }
        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true })
            }
        }
        val repo = ReplyGenerationRepositoryImpl(
            httpClient = client,
            context = mockk<Context>(relaxed = true),
            primaryApiKey = "primary-key-123",
            fallbackApiKey = "fallback-key-456",
            userPreferencesRepository = mockk<UserPreferencesRepository>(relaxed = true)
        )

        val reply = repo.executeFailoverChain(
            messages = listOf(ChatMessage(role = "user", content = "hi")),
            maxTokens = 100
        )

        assertEquals("{\"action\":\"REPLY\",\"final_reply_text\":\"مرحبا بك\"}", reply)
        assertFalse(reply == ReplyGenerationRepositoryImpl.HITL_FALLBACK_JSON)
    }

    @Test
    fun `OpenRouterRequest serialization contains zero reasoning_content keys even with encodeDefaults true`() {
        val serializer = Json {
            encodeDefaults = true
            explicitNulls = false
            ignoreUnknownKeys = true
            isLenient = true
        }

        val request = OpenRouterRequest(
            model = "nvidia/nemotron-3.5-super-vl:free",
            messages = listOf(
                ChatMessage(role = "system", content = "sys"),
                ChatMessage(role = "user", content = "user_msg"),
                ChatMessage(role = "assistant", content = "{\"action\":\"REPLY\"}")
            ),
            maxTokens = 600
        )

        val serialized = serializer.encodeToString(OpenRouterRequest.serializer(), request)
        assertFalse("Serialized request must not contain 'reasoning_content'", serialized.contains("reasoning_content"))
    }

    @Test
    fun `buildOpenRouterMessages preserves historical user and HITL handoff turns with action HITL`() {
        val now = 10_000_000L
        val history = listOf(
            // Most recent first as returned by ChatHistoryDao
            ChatHistoryEntity(id = 4L, contactName = "c", messageText = "تمام يا فندم، ثواني وهيكون مع حضرتك ممثل خدمة العملاء فوراً لمساعدتك في أقرب وقت متاح! 🙏", isFromUser = false, timestamp = now - 1000L),
            ChatHistoryEntity(id = 3L, contactName = "c", messageText = "سؤال يحتاج دعم", isFromUser = true, timestamp = now - 2000L),
            ChatHistoryEntity(id = 2L, contactName = "c", messageText = "{\"action\":\"REPLY\",\"final_reply_text\":\"أهلاً بك يا فندم\"}", isFromUser = false, timestamp = now - 10000L),
            ChatHistoryEntity(id = 1L, contactName = "c", messageText = "السلام عليكم", isFromUser = true, timestamp = now - 11000L)
        )

        val messages = ReplyGenerationRepositoryImpl.buildOpenRouterMessages(
            systemPrompt = "System Prompt",
            chatHistory = history,
            currentInboundText = "متبعتش لحد خلاص",
            nowMs = now
        )

        assertEquals(6, messages.size)
        assertEquals("system", messages[0].role)
        assertEquals("user", messages[1].role)
        assertTrue(messages[1].content.contains("السلام عليكم"))
        assertEquals("assistant", messages[2].role)
        assertEquals("user", messages[3].role)
        assertTrue(messages[3].content.contains("سؤال يحتاج دعم"))
        assertEquals("assistant", messages[4].role)
        assertTrue(messages[4].content.contains("\"action\":\"HITL\""))
        assertEquals("user", messages[5].role)
        assertTrue(messages[5].content.contains("متبعتش لحد خلاص"))
    }

    @Test
    fun `buildOpenRouterMessages formats MERCHANT_MANUAL history turns as assistant messages`() {
        val history = listOf(
            // Most recent first: merchant manually replied to customer
            ChatHistoryEntity(
                id = 2L,
                contactName = "عميل",
                messageText = "تمام هبعت لحضرتك خصم 10%",
                isFromUser = false,
                senderType = "MERCHANT_MANUAL",
                timestamp = 200L
            ),
            ChatHistoryEntity(
                id = 1L,
                contactName = "عميل",
                messageText = "ممكن خصم؟",
                isFromUser = true,
                senderType = "CUSTOMER",
                timestamp = 100L
            )
        )

        val messages = ReplyGenerationRepositoryImpl.buildOpenRouterMessages(
            systemPrompt = "System Prompt",
            chatHistory = history,
            currentInboundText = "شكرا جدا"
        )

        assertEquals(4, messages.size)
        assertEquals("system", messages[0].role)
        assertEquals("user", messages[1].role)
        assertEquals("[منذ فترة] ممكن خصم؟", messages[1].content)
        assertEquals("assistant", messages[2].role)
        val json = Json { ignoreUnknownKeys = true }
        val decoded = json.decodeFromString<AgenticLlmResponse>(messages[2].content)
        assertEquals("تمام هبعت لحضرتك خصم 10%", decoded.finalReplyText)
        assertEquals("user", messages[3].role)
        assertEquals("[الآن] شكرا جدا", messages[3].content)
    }

    @Test
    fun `formatRelativeTime formats elapsed intervals accurately in Egyptian Arabic`() {
        val now = 1_000_000_000L
        assertEquals("الآن", PromptContextBuilder.formatRelativeTime(now - 10_000L, now))
        assertEquals("منذ 5 دقيقة", PromptContextBuilder.formatRelativeTime(now - 5 * 60 * 1000L, now))
        assertEquals("منذ 2 ساعة", PromptContextBuilder.formatRelativeTime(now - 2 * 60 * 60 * 1000L, now))
        assertEquals("أمس", PromptContextBuilder.formatRelativeTime(now - 24 * 60 * 60 * 1000L, now))
        assertEquals("منذ 3 أيام", PromptContextBuilder.formatRelativeTime(now - 3 * 24 * 60 * 60 * 1000L, now))
        assertEquals("منذ فترة", PromptContextBuilder.formatRelativeTime(now - 10 * 24 * 60 * 60 * 1000L, now))
    }

    @Test
    fun `REASONING_MODELS configuration includes only benchmark-proven bounded reasoning models`() {
        assertTrue(ReplyGenerationRepositoryImpl.REASONING_MODELS.contains("nvidia/nemotron-3-ultra-550b-a55b:free"))
        assertTrue(ReplyGenerationRepositoryImpl.REASONING_MODELS.contains("liquid/lfm-2.5-2.6b:free"))
        assertTrue(ReplyGenerationRepositoryImpl.REASONING_MODELS.contains("cohere/north-mini-code:free"))
        assertFalse(ReplyGenerationRepositoryImpl.REASONING_MODELS.contains("nvidia/nemotron-3.5-super-vl:free"))
        assertFalse(ReplyGenerationRepositoryImpl.REASONING_MODELS.contains("nvidia/nemotron-3.5-lightning:free"))
        assertFalse(ReplyGenerationRepositoryImpl.REASONING_MODELS.contains("openrouter/free"))
    }

    @Test
    fun `vision models are verified and deprecated slugs are purged`() {
        assertEquals(listOf("qwen/qwen3.8-27b"), ReplyGenerationRepositoryImpl.VISION_GROQ_MODELS)
        assertEquals(
            listOf("meta/llama-3.2-11b-vision-instruct", "nvidia/nemotron-3.5-super-vl-120b-a12b"),
            ReplyGenerationRepositoryImpl.VISION_NVIDIA_MODELS
        )
        assertEquals(listOf("nvidia/nemotron-3.5-super-vl:free"), ReplyGenerationRepositoryImpl.VISION_OPENROUTER_MODELS)
        assertFalse(ReplyGenerationRepositoryImpl.VISION_GROQ_MODELS.contains("llama-3.2-11b-vision-preview"))
        assertFalse(ReplyGenerationRepositoryImpl.VISION_OPENROUTER_MODELS.contains("meta-llama/llama-3.2-11b-vision-instruct:free"))
    }

    @Test
    fun `model tier lists prioritize benchmark winning models`() {
        assertEquals(
            listOf(
                "deepseek-v4.1-flash",
                "qwen3.8-max",
                "deepseek-v4-pro-0813",
                "qwen3.8-flash",
                "qwen3.7-plus",
                "qwen3.7-flash",
                "qwen3.6-flash",
                "qwen3.5-flash",
                "qwen3-coder-flash",
                "qwen3.6-27b"
            ),
            ReplyGenerationRepositoryImpl.QWEN_TEXT_MODELS
        )
        assertEquals(
            listOf(
                "nvidia/nemotron-3-ultra-550b-a55b",
                "nvidia/nemotron-3.5-bolt-e4b"
            ),
            ReplyGenerationRepositoryImpl.NVIDIA_TEXT_MODELS
        )
        assertEquals(
            listOf("openai/gpt-oss-120b", "openai/gpt-oss-20b"),
            ReplyGenerationRepositoryImpl.GROQ_MODELS
        )
        assertEquals(
            listOf(
                "nvidia/nemotron-3-ultra-550b-a55b:free",
                "liquid/lfm-2.5-2.6b:free",
                "openrouter/free"
            ),
            ReplyGenerationRepositoryImpl.OPENROUTER_FAILOVER_MODELS
        )
    }

    @Test
    fun `explicit vision directive is included in system prompt when isMultimodal is true`() {
        val multimodalPrompt = PromptContextBuilder.buildFirstPassSystemPrompt(isMultimodal = true)
        assertTrue(multimodalPrompt.contains("VISION CAPABILITIES ENABLED"))
        assertTrue(multimodalPrompt.contains("Analyze and inspect the attached image directly"))

        val textOnlyPrompt = PromptContextBuilder.buildFirstPassSystemPrompt(isMultimodal = false)
        assertFalse(textOnlyPrompt.contains("VISION CAPABILITIES ENABLED"))
    }

    @Test
    fun `system prompt constants derive from single-source builders with default identities`() {
        val first = PromptContextBuilder.buildFirstPassSystemPrompt()
        val final = PromptContextBuilder.buildFinalPassSystemPrompt()

        assertEquals(first, ReplyGenerationRepositoryImpl.SYSTEM_PROMPT_FIRST_PASS)
        assertEquals(final, ReplyGenerationRepositoryImpl.SYSTEM_PROMPT_FINAL_PASS)

        // Identity interpolation must come from the single-source-of-truth constants
        assertTrue(first.contains("You are ${UserPreferencesRepository.DEFAULT_BOT_NAME}"))
        assertTrue(first.contains(UserPreferencesRepository.DEFAULT_STORE_NAME))
        assertTrue(final.contains("You are ${UserPreferencesRepository.DEFAULT_BOT_NAME}"))
        assertTrue(final.contains(UserPreferencesRepository.DEFAULT_STORE_NAME))

        // Authored-once business rules must appear in both passes
        assertTrue(first.contains("GENERALIZED OUT-OF-BOUNDS MARKETING RECOVERY"))
        assertTrue(final.contains("GENERALIZED OUT-OF-BOUNDS MARKETING RECOVERY"))
    }

    @Test
    fun `system prompts reference the store topics index rules in both passes`() {
        val first = ReplyGenerationRepositoryImpl.SYSTEM_PROMPT_FIRST_PASS
        val final = ReplyGenerationRepositoryImpl.SYSTEM_PROMPT_FINAL_PASS

        listOf(first, final).forEach { prompt ->
            assertTrue("STORE TOPICS INDEX rule must be present", prompt.contains("STORE TOPICS INDEX (<store_topics>):"))
            assertTrue(
                "Foreign-category clarification rule must be present",
                prompt.contains("completely foreign to <store_topics>")
            )
        }

        // RE_QUERY alignment rule only makes sense in the first pass (RE_QUERY allowed there)
        assertTrue(
            ReplyGenerationRepositoryImpl.SYSTEM_PROMPT_FIRST_PASS.contains(
                "align your search query with the most relevant topic header from <store_topics>"
            )
        )
    }

    @Test
    fun `buildSystemPromptWithContext renders the store_topics block with real topics before store_knowledge`() {
        val topics = listOf("تعريف النشاط التجاري", "كتالوج المنتجات والأسعار الرسمية", "سياسات الشحن والتوصيل")
        val candidates = listOf(
            RetrievalCandidate(
                chunkId = "c1",
                headerContext = "سياسات الشحن والتوصيل > نطاق القاهرة الكبرى",
                chunkText = "شحن القاهرة 50 جنيه",
                similarityScore = 0.7f,
                baseSimilarityScore = 0.7f
            )
        )

        val systemPromptWithContext = ReplyGenerationRepositoryImpl.buildSystemPromptWithContext(
            staticSystemPrompt = ReplyGenerationRepositoryImpl.SYSTEM_PROMPT_FIRST_PASS,
            candidates = candidates,
            activeOrders = emptyList(),
            storeTopics = topics
        )

        val expectedBlock =
            "<store_topics>\n" +
                "- تعريف النشاط التجاري\n" +
                "- كتالوج المنتجات والأسعار الرسمية\n" +
                "- سياسات الشحن والتوصيل\n" +
                "</store_topics>"
        assertTrue(systemPromptWithContext.contains(expectedBlock))
        assertFalse("Real topics must not render the NONE placeholder", systemPromptWithContext.contains("\nNONE\n"))

        // The topics block must sit between the static rules and <store_knowledge>.
        // lastIndexOf() targets the dynamically injected blocks, not mentions of these
        // tags inside the static rule text.
        val topicsIndex = systemPromptWithContext.lastIndexOf("<store_topics>")
        val knowledgeIndex = systemPromptWithContext.lastIndexOf("<store_knowledge>")
        assertTrue(topicsIndex != -1)
        assertTrue("store_topics must precede store_knowledge", topicsIndex < knowledgeIndex)

        // store_knowledge content remains intact
        assertTrue(systemPromptWithContext.contains("شحن القاهرة 50 جنيه"))
        assertTrue(systemPromptWithContext.contains("<customer_order_state>"))
    }

    @Test
    fun `buildSystemPromptWithContext renders NONE placeholder when store topics are absent`() {
        val systemPromptWithContext = ReplyGenerationRepositoryImpl.buildSystemPromptWithContext(
            staticSystemPrompt = ReplyGenerationRepositoryImpl.SYSTEM_PROMPT_FIRST_PASS,
            candidates = emptyList(),
            activeOrders = emptyList(),
            storeTopics = emptyList()
        )

        assertTrue(systemPromptWithContext.contains("<store_topics>\nNONE\n</store_topics>"))
        assertTrue(systemPromptWithContext.contains("NO_MATCHING_CHUNKS_RETRIEVED"))

        // lastIndexOf() targets the dynamically injected block, not mentions of these
        // tags inside the static rule text.
        val topicsIndex = systemPromptWithContext.lastIndexOf("<store_topics>")
        val knowledgeIndex = systemPromptWithContext.lastIndexOf("<store_knowledge>")
        assertTrue("store_topics must precede store_knowledge", topicsIndex in 0 until knowledgeIndex)
        assertTrue(systemPromptWithContext.contains("<store_website>NONE</store_website>"))
    }

    @Test
    fun `buildSystemPromptWithContext renders store_website block when website is provided`() {
        val systemPromptWithContext = ReplyGenerationRepositoryImpl.buildSystemPromptWithContext(
            staticSystemPrompt = ReplyGenerationRepositoryImpl.SYSTEM_PROMPT_FIRST_PASS,
            candidates = emptyList(),
            activeOrders = emptyList(),
            storeTopics = listOf("الهوديز", "الماجات"),
            storeWebsite = "https://velo.ordersapps.com/ar"
        )

        assertTrue(systemPromptWithContext.contains("<store_website>\nhttps://velo.ordersapps.com/ar\n</store_website>"))
        val topicsIndex = systemPromptWithContext.lastIndexOf("<store_topics>")
        val websiteIndex = systemPromptWithContext.lastIndexOf("<store_website>")
        val knowledgeIndex = systemPromptWithContext.lastIndexOf("<store_knowledge>")
        assertTrue(topicsIndex < websiteIndex)
        assertTrue(websiteIndex < knowledgeIndex)
    }

    @Test
    fun `system prompts enforce zero price guessing rule and authorized website redirection in both passes`() {
        val first = ReplyGenerationRepositoryImpl.SYSTEM_PROMPT_FIRST_PASS
        val final = ReplyGenerationRepositoryImpl.SYSTEM_PROMPT_FINAL_PASS

        listOf(first, final).forEach { prompt ->
            assertTrue(
                "Strict zero price guessing rule must be present",
                prompt.contains("NEVER estimate, guess, or hallucinate prices (e.g., NEVER say 'سعره 450 جنيه تقريباً'")
            )
            assertTrue(
                "Authorized website redirection rule must be present",
                prompt.contains("If <store_website> is provided and not NONE, this is the store's verified official online website.")
            )
            assertTrue(
                "Store website ordering channels header must be present",
                prompt.contains("STORE WEBSITE & ORDERING CHANNELS:")
            )
        }
    }

    @Test
    fun `maskKey masks long and short keys safely`() {
        assertEquals("gsk_12...cdef", ReplyGenerationRepositoryImpl.maskKey("gsk_1234567890abcdef1234567890abcdef1234567890cdef"))
        assertEquals("...1234", ReplyGenerationRepositoryImpl.maskKey("1234"))
        assertEquals("[BLANK]", ReplyGenerationRepositoryImpl.maskKey(""))
    }

    @Test
    fun `callGroq returns success when Groq primary key succeeds on openai gpt-oss-120b`() = runTest {
        val capturedAuthHeaders = mutableListOf<String>()
        val mockEngine = MockEngine { request ->
            capturedAuthHeaders.add(request.headers[HttpHeaders.Authorization] ?: "")
            respond(
                content = """{"choices":[{"message":{"content":"{\"action\":\"REPLY\",\"final_reply_text\":\"منور يا فندم\"}","reasoning":"quick test"}}]}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true })
            }
        }
        val repo = ReplyGenerationRepositoryImpl(
            httpClient = client,
            context = mockk<Context>(relaxed = true),
            primaryApiKey = "primary-or-key",
            fallbackApiKey = "fallback-or-key",
            userPreferencesRepository = mockk<UserPreferencesRepository>(relaxed = true),
            groqPrimaryApiKey = "gsk_primary_test_key_1",
            groqFallbackApiKey = "gsk_fallback_test_key_2"
        )

        val result = repo.callGroq(
            model = "openai/gpt-oss-120b",
            messages = listOf(ChatMessage(role = "user", content = "hi")),
            maxTokens = 100
        )

        assertTrue(result is ReplyGenerationRepositoryImpl.CallResult.Success)
        assertEquals("{\"action\":\"REPLY\",\"final_reply_text\":\"منور يا فندم\"}", (result as ReplyGenerationRepositoryImpl.CallResult.Success).content)
        assertEquals(1, capturedAuthHeaders.size)
        assertEquals("Bearer gsk_primary_test_key_1", capturedAuthHeaders[0])
    }

    @Test
    fun `callGroq falls back to GROQ_API_KEY_V2 when primary key returns 429`() = runTest {
        val capturedAuthHeaders = mutableListOf<String>()
        val mockEngine = MockEngine { request ->
            val auth = request.headers[HttpHeaders.Authorization] ?: ""
            capturedAuthHeaders.add(auth)
            if (auth.contains("gsk_primary")) {
                respond(
                    content = """{"error":{"message":"Rate limit exceeded","type":"rate_limit_exceeded"}}""",
                    status = HttpStatusCode.TooManyRequests,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            } else {
                respond(
                    content = """{"choices":[{"message":{"content":"{\"action\":\"REPLY\",\"final_reply_text\":\"أهلاً بك\"}"}}]}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
        }
        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true })
            }
        }
        val repo = ReplyGenerationRepositoryImpl(
            httpClient = client,
            context = mockk<Context>(relaxed = true),
            primaryApiKey = "primary-or-key",
            fallbackApiKey = "fallback-or-key",
            userPreferencesRepository = mockk<UserPreferencesRepository>(relaxed = true),
            groqPrimaryApiKey = "gsk_primary_123456",
            groqFallbackApiKey = "gsk_fallback_789012"
        )

        val result = repo.callGroq(
            model = "openai/gpt-oss-120b",
            messages = listOf(ChatMessage(role = "user", content = "hi")),
            maxTokens = 100
        )

        assertTrue(result is ReplyGenerationRepositoryImpl.CallResult.Success)
        assertEquals(2, capturedAuthHeaders.size)
        assertEquals("Bearer gsk_primary_123456", capturedAuthHeaders[0])
        assertEquals("Bearer gsk_fallback_789012", capturedAuthHeaders[1])
    }

    @Test
    fun `callNvidiaNim returns success when NVIDIA NIM succeeds on nemotron-3-5-bolt-e4b`() = runTest {
        val capturedAuthHeaders = mutableListOf<String>()
        var capturedModel: String? = null
        var capturedMaxTokens: Int? = null
        val mockEngine = MockEngine { request ->
            capturedAuthHeaders.add(request.headers[HttpHeaders.Authorization] ?: "")
            val bodyText = (request.body as? io.ktor.http.content.TextContent)?.text ?: request.body.toString()
            val parsed = Json { ignoreUnknownKeys = true }.decodeFromString<NvidiaRequest>(bodyText)
            capturedModel = parsed.model
            capturedMaxTokens = parsed.maxTokens
            respond(
                content = """{"choices":[{"message":{"content":"{\"action\":\"REPLY\",\"final_reply_text\":\"منور المتجر يا غالي\"}"}}]}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true })
            }
        }
        val repo = ReplyGenerationRepositoryImpl(
            httpClient = client,
            context = mockk<Context>(relaxed = true),
            primaryApiKey = "primary-or-key",
            fallbackApiKey = "fallback-or-key",
            userPreferencesRepository = mockk<UserPreferencesRepository>(relaxed = true),
            groqPrimaryApiKey = "gsk_primary",
            groqFallbackApiKey = "gsk_fallback",
            nvidiaApiKey = "nvapi-test-key-1"
        )

        val result = repo.callNvidiaNim(
            model = "nvidia/nemotron-3.5-bolt-e4b",
            messages = listOf(ChatMessage(role = "user", content = "سلام عليكم")),
            maxTokens = 1000
        )

        assertTrue(result is ReplyGenerationRepositoryImpl.CallResult.Success)
        assertEquals("{\"action\":\"REPLY\",\"final_reply_text\":\"منور المتجر يا غالي\"}", (result as ReplyGenerationRepositoryImpl.CallResult.Success).content)
        assertEquals(1, capturedAuthHeaders.size)
        assertEquals("Bearer nvapi-test-key-1", capturedAuthHeaders[0])
        assertEquals("nvidia/nemotron-3.5-bolt-e4b", capturedModel)
        assertEquals(1000, capturedMaxTokens)
    }

    @Test
    fun `callNvidiaNim enforces minimum 1500 max_tokens on super-vl model`() = runTest {
        var capturedMaxTokens: Int? = null
        val mockEngine = MockEngine { request ->
            val bodyText = (request.body as? io.ktor.http.content.TextContent)?.text ?: request.body.toString()
            val parsed = Json { ignoreUnknownKeys = true }.decodeFromString<NvidiaRequest>(bodyText)
            capturedMaxTokens = parsed.maxTokens
            respond(
                content = """{"choices":[{"message":{"content":"{\"action\":\"REPLY\",\"final_reply_text\":\"شايف الهودي تمام\"}"}}]}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true })
            }
        }
        val repo = ReplyGenerationRepositoryImpl(
            httpClient = client,
            context = mockk<Context>(relaxed = true),
            primaryApiKey = "primary-or-key",
            fallbackApiKey = "fallback-or-key",
            userPreferencesRepository = mockk<UserPreferencesRepository>(relaxed = true),
            groqPrimaryApiKey = "gsk_primary",
            groqFallbackApiKey = "gsk_fallback",
            nvidiaApiKey = "nvapi-test-key-1"
        )

        val result = repo.callNvidiaNim(
            model = "nvidia/nemotron-3.5-super-vl-120b-a12b",
            messages = listOf(ChatMessage(role = "user", content = "بكام ده؟", imageUrl = "data:image/jpeg;base64,123")),
            maxTokens = 500
        )

        assertTrue(result is ReplyGenerationRepositoryImpl.CallResult.Success)
        assertEquals(1500, capturedMaxTokens)
    }

    @Test
    fun `callNvidiaNim handles 429 rate limit and server errors correctly`() = runTest {
        var statusCode = HttpStatusCode.TooManyRequests
        val mockEngine = MockEngine { _ ->
            respond(
                content = """{"error":{"message":"Rate limit exceeded"}}""",
                status = statusCode,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true })
            }
        }
        val repo = ReplyGenerationRepositoryImpl(
            httpClient = client,
            context = mockk<Context>(relaxed = true),
            primaryApiKey = "primary-or-key",
            fallbackApiKey = "fallback-or-key",
            userPreferencesRepository = mockk<UserPreferencesRepository>(relaxed = true),
            nvidiaApiKey = "nvapi-test-key"
        )

        val result429 = repo.callNvidiaNim("nvidia/nemotron-3.5-bolt-e4b", listOf(ChatMessage(role = "user", content = "hi")), 100)
        assertTrue(result429 is ReplyGenerationRepositoryImpl.CallResult.RateLimited)

        statusCode = HttpStatusCode.ServiceUnavailable
        val result503 = repo.callNvidiaNim("nvidia/nemotron-3.5-bolt-e4b", listOf(ChatMessage(role = "user", content = "hi")), 100)
        assertTrue(result503 is ReplyGenerationRepositoryImpl.CallResult.ServerError)
        assertEquals(503, (result503 as ReplyGenerationRepositoryImpl.CallResult.ServerError).code)
    }

    @Test
    fun `executeFailoverChain executes NVIDIA NIM Tier 1 first and returns immediately without touching Groq or OpenRouter`() = runTest {
        val capturedUrls = mutableListOf<String>()
        val mockEngine = MockEngine { request ->
            capturedUrls.add(request.url.toString())
            respond(
                content = """{"choices":[{"message":{"content":"{\"action\":\"REPLY\",\"final_reply_text\":\"رد من إنفيديا\"}"}}]}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true })
            }
        }
        val repo = ReplyGenerationRepositoryImpl(
            httpClient = client,
            context = mockk<Context>(relaxed = true),
            primaryApiKey = "primary-or-key",
            fallbackApiKey = "fallback-or-key",
            userPreferencesRepository = mockk<UserPreferencesRepository>(relaxed = true),
            groqPrimaryApiKey = "gsk_primary_123456",
            groqFallbackApiKey = "gsk_fallback_789012",
            nvidiaApiKey = "nvapi-primary-123456"
        )

        val reply = repo.executeFailoverChain(
            messages = listOf(ChatMessage(role = "user", content = "hi")),
            maxTokens = 100
        )

        assertEquals("{\"action\":\"REPLY\",\"final_reply_text\":\"رد من إنفيديا\"}", reply)
        assertEquals(1, capturedUrls.size)
        assertTrue(capturedUrls[0].contains("integrate.api.nvidia.com"))
    }

    @Test
    fun `executeFailoverChain cascades to Groq Tier 2 when NVIDIA NIM fails`() = runTest {
        val capturedUrls = mutableListOf<String>()
        val mockEngine = MockEngine { request ->
            val url = request.url.toString()
            capturedUrls.add(url)
            if (url.contains("nvidia.com")) {
                respond(
                    content = """{"error":{"message":"Internal server error"}}""",
                    status = HttpStatusCode.InternalServerError,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            } else {
                respond(
                    content = """{"choices":[{"message":{"content":"{\"action\":\"REPLY\",\"final_reply_text\":\"رد من جروك بعد فشل إنفيديا\"}"}}]}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
        }
        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true })
            }
        }
        val repo = ReplyGenerationRepositoryImpl(
            httpClient = client,
            context = mockk<Context>(relaxed = true),
            primaryApiKey = "primary-or-key",
            fallbackApiKey = "fallback-or-key",
            userPreferencesRepository = mockk<UserPreferencesRepository>(relaxed = true),
            groqPrimaryApiKey = "gsk_primary_123456",
            groqFallbackApiKey = "gsk_fallback_789012",
            nvidiaApiKey = "nvapi-primary-123456"
        )

        val reply = repo.executeFailoverChain(
            messages = listOf(ChatMessage(role = "user", content = "hi")),
            maxTokens = 100
        )

        assertEquals("{\"action\":\"REPLY\",\"final_reply_text\":\"رد من جروك بعد فشل إنفيديا\"}", reply)
        assertTrue(capturedUrls.any { it.contains("nvidia.com") })
        assertTrue(capturedUrls.any { it.contains("groq.com") })
        assertFalse(capturedUrls.any { it.contains("openrouter.ai") })
    }

    @Test
    fun `executeFailoverChain executes Groq Tier when NVIDIA key is absent and returns immediately without touching OpenRouter`() = runTest {
        val capturedUrls = mutableListOf<String>()
        val mockEngine = MockEngine { request ->
            capturedUrls.add(request.url.toString())
            respond(
                content = """{"choices":[{"message":{"content":"{\"action\":\"REPLY\",\"final_reply_text\":\"رد من جروك\"}"}}]}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true })
            }
        }
        val repo = ReplyGenerationRepositoryImpl(
            httpClient = client,
            context = mockk<Context>(relaxed = true),
            primaryApiKey = "primary-or-key",
            fallbackApiKey = "fallback-or-key",
            userPreferencesRepository = mockk<UserPreferencesRepository>(relaxed = true),
            groqPrimaryApiKey = "gsk_primary_123456",
            groqFallbackApiKey = "gsk_fallback_789012"
        )

        val reply = repo.executeFailoverChain(
            messages = listOf(ChatMessage(role = "user", content = "hi")),
            maxTokens = 100
        )

        assertEquals("{\"action\":\"REPLY\",\"final_reply_text\":\"رد من جروك\"}", reply)
        assertEquals(1, capturedUrls.size)
        assertTrue(capturedUrls[0].contains("groq.com"))
    }

    @Test
    fun `executeFailoverChain cascades to Tier 3 OpenRouter when both NVIDIA and Groq fail`() = runTest {
        val capturedUrls = mutableListOf<String>()
        val mockEngine = MockEngine { request ->
            val url = request.url.toString()
            capturedUrls.add(url)
            if (url.contains("nvidia.com") || url.contains("groq.com")) {
                respond(
                    content = """{"error":{"message":"Internal server error"}}""",
                    status = HttpStatusCode.InternalServerError,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            } else {
                respond(
                    content = """{"choices":[{"message":{"content":"{\"action\":\"REPLY\",\"final_reply_text\":\"رد من أوبن راوتر\"}"}}]}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
        }
        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true })
            }
        }
        val repo = ReplyGenerationRepositoryImpl(
            httpClient = client,
            context = mockk<Context>(relaxed = true),
            primaryApiKey = "primary-or-key",
            fallbackApiKey = "fallback-or-key",
            userPreferencesRepository = mockk<UserPreferencesRepository>(relaxed = true),
            groqPrimaryApiKey = "gsk_primary_123456",
            groqFallbackApiKey = "gsk_fallback_789012",
            nvidiaApiKey = "nvapi-primary-123456"
        )

        val reply = repo.executeFailoverChain(
            messages = listOf(ChatMessage(role = "user", content = "hi")),
            maxTokens = 100
        )

        assertEquals("{\"action\":\"REPLY\",\"final_reply_text\":\"رد من أوبن راوتر\"}", reply)
        assertTrue(capturedUrls.any { it.contains("nvidia.com") })
        assertTrue(capturedUrls.any { it.contains("groq.com") })
        assertTrue(capturedUrls.any { it.contains("openrouter.ai") })
    }

    @Test
    fun `executeFailoverChain with image executes Vision Groq first, then cascades to Vision NVIDIA NIM`() = runTest {
        val capturedUrls = mutableListOf<String>()
        val capturedModels = mutableListOf<String>()
        val mockEngine = MockEngine { request ->
            val url = request.url.toString()
            capturedUrls.add(url)
            val bodyText = (request.body as? io.ktor.http.content.TextContent)?.text ?: request.body.toString()
            if (url.contains("groq.com")) {
                val parsed = runCatching { Json { ignoreUnknownKeys = true }.decodeFromString<GroqRequest>(bodyText) }.getOrNull()
                parsed?.model?.let { capturedModels.add(it) }
                respond(
                    content = """{"error":{"message":"Rate limit"}}""",
                    status = HttpStatusCode.TooManyRequests,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            } else if (url.contains("nvidia.com")) {
                val parsed = runCatching { Json { ignoreUnknownKeys = true }.decodeFromString<NvidiaRequest>(bodyText) }.getOrNull()
                parsed?.model?.let { capturedModels.add(it) }
                respond(
                    content = """{"choices":[{"message":{"content":"{\"action\":\"REPLY\",\"final_reply_text\":\"هودي أسود مقاس لارج\"}"}}]}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            } else {
                respond(
                    content = """{"choices":[{"message":{"content":"{\"action\":\"REPLY\",\"final_reply_text\":\"من أوبن راوتر\"}"}}]}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
        }
        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true })
            }
        }
        val repo = ReplyGenerationRepositoryImpl(
            httpClient = client,
            context = mockk<Context>(relaxed = true),
            primaryApiKey = "primary-or-key",
            fallbackApiKey = "fallback-or-key",
            userPreferencesRepository = mockk<UserPreferencesRepository>(relaxed = true),
            groqPrimaryApiKey = "gsk_primary_123456",
            groqFallbackApiKey = "gsk_fallback_789012",
            nvidiaApiKey = "nvapi-primary-123456"
        )

        val reply = repo.executeFailoverChain(
            messages = listOf(ChatMessage(role = "user", content = "بكام؟", imageUrl = "data:image/jpeg;base64,12345")),
            maxTokens = 500
        )

        assertEquals("{\"action\":\"REPLY\",\"final_reply_text\":\"هودي أسود مقاس لارج\"}", reply)
        assertTrue(capturedUrls.any { it.contains("groq.com") })
        assertTrue(capturedUrls.any { it.contains("nvidia.com") })
        assertEquals(listOf("qwen/qwen3.8-27b", "meta/llama-3.2-11b-vision-instruct"), capturedModels.distinct())
    }

    @Test
    fun `resolveImageUri correctly handles data uri, remote http, and local files`() {
        val repo = ReplyGenerationRepositoryImpl(
            httpClient = HttpClient(MockEngine { respond("") }),
            context = mockk<Context>(relaxed = true),
            primaryApiKey = "primary-key",
            fallbackApiKey = "fallback-key",
            userPreferencesRepository = mockk<UserPreferencesRepository>(relaxed = true),
            groqPrimaryApiKey = "gsk_primary",
            groqFallbackApiKey = "gsk_fallback"
        )

        assertNull(repo.resolveImageUri(null))
        assertNull(repo.resolveImageUri(""))
        assertNull(repo.resolveImageUri("   "))

        assertEquals("data:image/jpeg;base64,abc123", repo.resolveImageUri("data:image/jpeg;base64,abc123"))
        assertEquals("https://example.com/photo.jpg", repo.resolveImageUri("https://example.com/photo.jpg"))

        val tempFile = java.io.File.createTempFile("test_resolve", ".jpg").apply {
            writeBytes("IMAGE_DATA".toByteArray(Charsets.UTF_8))
            deleteOnExit()
        }
        val resolved = repo.resolveImageUri(tempFile.absolutePath)
        assertNotNull(resolved)
        assertTrue(resolved!!.startsWith("data:image/jpeg;base64,"))
    }

    @Test
    fun `resolveImageUri strictly returns null for audio file paths and non-image extensions`() {
        val repo = ReplyGenerationRepositoryImpl(
            httpClient = HttpClient(MockEngine { respond("") }),
            context = mockk<Context>(relaxed = true),
            primaryApiKey = "primary-key",
            fallbackApiKey = "fallback-key",
            userPreferencesRepository = mockk<UserPreferencesRepository>(relaxed = true)
        )

        val opusFile = java.io.File.createTempFile("voice", ".opus").apply {
            writeBytes(ByteArray(256) { 1 })
            deleteOnExit()
        }
        val oggFile = java.io.File.createTempFile("voice", ".ogg").apply {
            writeBytes(ByteArray(256) { 2 })
            deleteOnExit()
        }
        val mp3File = java.io.File.createTempFile("audio", ".mp3").apply {
            writeBytes(ByteArray(256) { 3 })
            deleteOnExit()
        }
        val txtFile = java.io.File.createTempFile("doc", ".txt").apply {
            writeBytes("text content".toByteArray())
            deleteOnExit()
        }

        assertNull(repo.resolveImageUri(opusFile.absolutePath))
        assertNull(repo.resolveImageUri(oggFile.absolutePath))
        assertNull(repo.resolveImageUri(mp3File.absolutePath))
        assertNull(repo.resolveImageUri(txtFile.absolutePath))
        assertNull(repo.compressAndScaleImage(opusFile.absolutePath))
        assertNull(repo.compressAndScaleImage(oggFile.absolutePath))
    }

    @Test
    fun `when vision models fail, executeFailoverChain falls back to text models if visual description is present`() = runTest {
        var visionTried = false
        var textModelTried = false

        val mockEngine = MockEngine { request ->
            val bodyText = (request.body as? io.ktor.http.content.TextContent)?.text ?: request.body.toString()
            if (bodyText.contains("qwen/qwen3.8-27b") || bodyText.contains("nvidia/nemotron-3.5-super-vl:free")) {
                visionTried = true
                respond(
                    content = """{"error": {"message": "Vision rate limited"}}""",
                    status = HttpStatusCode.TooManyRequests,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            } else {
                textModelTried = true
                respond(
                    content = """{"choices":[{"message":{"content":"{\"action\":\"REPLY\",\"final_reply_text\":\"تم الرد بنجاح من نموذج النصوص\"}"}}]}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
        }

        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true })
            }
        }

        val repo = ReplyGenerationRepositoryImpl(
            httpClient = client,
            context = mockk<Context>(relaxed = true),
            primaryApiKey = "or-key",
            fallbackApiKey = "or-key-2",
            userPreferencesRepository = mockk<UserPreferencesRepository>(relaxed = true),
            groqPrimaryApiKey = "gsk_primary",
            groqFallbackApiKey = "gsk_fallback"
        )

        val messages = listOf(
            ChatMessage(
                role = "user",
                content = "بكام ده؟\n[وصف الصورة المرفقة من العميل]: هودي أسود أوفر سايز",
                imageUrl = "data:image/jpeg;base64,12345"
            )
        )

        val result = repo.executeFailoverChain(messages, 500)

        assertTrue(visionTried)
        assertTrue(textModelTried)
        assertNotNull(result)
        assertTrue(result!!.contains("تم الرد بنجاح من نموذج النصوص"))
    }

    @Test
    fun `generateReply caps max_tokens to 500 for multimodal vision requests`() = runTest {
        var requestedMaxTokens: Int? = null

        val mockEngine = MockEngine { request ->
            val bodyText = (request.body as? io.ktor.http.content.TextContent)?.text ?: request.body.toString()
            val parsedGroq = runCatching { Json { ignoreUnknownKeys = true }.decodeFromString<GroqRequest>(bodyText) }.getOrNull()
            val parsedOr = runCatching { Json { ignoreUnknownKeys = true }.decodeFromString<OpenRouterRequest>(bodyText) }.getOrNull()
            requestedMaxTokens = parsedGroq?.maxCompletionTokens ?: parsedOr?.maxTokens
            respond(
                content = """{"choices":[{"message":{"content":"{\"action\":\"REPLY\",\"final_reply_text\":\"تمام يا فندم\"}"}}]}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true })
            }
        }

        val mockPrefs = mockk<UserPreferencesRepository>(relaxed = true) {
            every { storeKnowledgeTopicsFlow } returns kotlinx.coroutines.flow.flowOf(listOf("هوديز"))
            every { storeWebsiteFlow } returns kotlinx.coroutines.flow.flowOf("https://example.com")
            every { stylePreferences } returns kotlinx.coroutines.flow.flowOf(
                com.example.persona.features.auto_reply.domain.model.StylePreferences()
            )
            coEvery { getBotNameSync() } returns "Persona"
            coEvery { getStoreNameSync() } returns "Velo"
        }

        val repo = ReplyGenerationRepositoryImpl(
            httpClient = client,
            context = mockk<Context>(relaxed = true),
            primaryApiKey = "or-key",
            fallbackApiKey = "or-key-2",
            userPreferencesRepository = mockPrefs,
            groqPrimaryApiKey = "gsk_primary",
            groqFallbackApiKey = "gsk_fallback"
        )

        val tempFile = java.io.File.createTempFile("vision_cap", ".jpg").apply {
            writeBytes("DUMMY_IMAGE".toByteArray())
            deleteOnExit()
        }

        val notification = com.example.persona.features.auto_reply.domain.model.ProcessedNotification(
            notificationKey = "key_img",
            contactName = "Test Contact",
            originalText = "📷 Sent a photo",
            normalizedText = "📷 Sent a photo",
            context = com.example.persona.features.chat_import.domain.model.ConversationContext(
                intent = com.example.persona.features.chat_import.domain.model.IntentType.QUESTION,
                topic = com.example.persona.features.chat_import.domain.model.TopicType.MEDIA,
                sentiment = com.example.persona.features.chat_import.domain.model.SentimentType.NEUTRAL,
                confidence = 1.0f
            ),
            postTime = System.currentTimeMillis(),
            sourceClass = com.example.persona.features.auto_reply.data.diagnostics.NotificationSourceClass.BUSINESS,
            mediaFilePath = tempFile.absolutePath
        )

        val reply = repo.generateReply(
            notification = notification,
            candidates = emptyList(),
            isOngoingConversation = false,
            chatHistory = emptyList(),
            activeOrders = emptyList(),
            personalChunks = emptyList(),
            maxTokens = 7000
        )

        assertNotNull(reply)
        assertEquals(500, requestedMaxTokens)
    }

    @Test
    fun `generateReply uses lightweight isolated vision extraction prompt without chat history or candidates when image present`() = runTest {
        var requestedMaxTokens: Int? = null
        var sentMessages: List<com.example.persona.core.network.dto.OpenRouterChatMessage>? = null

        val mockEngine = MockEngine { request ->
            val bodyText = (request.body as? io.ktor.http.content.TextContent)?.text ?: request.body.toString()
            val parsedGroq = runCatching { Json { ignoreUnknownKeys = true }.decodeFromString<GroqRequest>(bodyText) }.getOrNull()
            requestedMaxTokens = parsedGroq?.maxCompletionTokens
            sentMessages = parsedGroq?.messages
            respond(
                content = """{"choices":[{"message":{"content":"{\"action\":\"RE_QUERY\",\"visual_product_description\":\"هودي أسود\"}"}}]}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true })
            }
        }

        val mockPrefs = mockk<UserPreferencesRepository>(relaxed = true) {
            every { storeKnowledgeTopicsFlow } returns kotlinx.coroutines.flow.flowOf(listOf("الهوديز"))
            coEvery { getStoreNameSync() } returns "Velo Store"
        }

        val repo = ReplyGenerationRepositoryImpl(
            httpClient = client,
            context = mockk<Context>(relaxed = true),
            primaryApiKey = "or-key",
            fallbackApiKey = "or-key-2",
            userPreferencesRepository = mockPrefs,
            groqPrimaryApiKey = "gsk_primary",
            groqFallbackApiKey = "gsk_fallback"
        )

        val tempFile = java.io.File.createTempFile("vision_diet", ".jpg").apply {
            writeBytes("DUMMY_IMAGE".toByteArray())
            deleteOnExit()
        }

        val notification = com.example.persona.features.auto_reply.domain.model.ProcessedNotification(
            notificationKey = "key_img",
            contactName = "Test Contact",
            originalText = "📷 Sent a photo",
            normalizedText = "📷 Sent a photo",
            context = com.example.persona.features.chat_import.domain.model.ConversationContext(
                intent = com.example.persona.features.chat_import.domain.model.IntentType.QUESTION,
                topic = com.example.persona.features.chat_import.domain.model.TopicType.MEDIA,
                sentiment = com.example.persona.features.chat_import.domain.model.SentimentType.NEUTRAL,
                confidence = 1.0f
            ),
            postTime = System.currentTimeMillis(),
            sourceClass = com.example.persona.features.auto_reply.data.diagnostics.NotificationSourceClass.BUSINESS,
            mediaFilePath = tempFile.absolutePath
        )

        val dummyHistory = listOf(
            ChatHistoryEntity(id = 1L, contactName = "c", messageText = "سلام عليكم", isFromUser = true, timestamp = 100L)
        )
        val dummyCandidate = listOf(
            RetrievalCandidate(chunkId = "c1", headerContext = "h", chunkText = "heavy store knowledge text", similarityScore = 0.9f, baseSimilarityScore = 0.9f)
        )

        val reply = repo.generateReply(
            notification = notification,
            candidates = dummyCandidate,
            isOngoingConversation = true,
            chatHistory = dummyHistory,
            activeOrders = emptyList(),
            personalChunks = emptyList(),
            maxTokens = 7000
        )

        assertNotNull(reply)
        assertEquals(500, requestedMaxTokens)
        assertNotNull(sentMessages)
        assertEquals(2, sentMessages!!.size) // strictly system + single user turn with image
        val systemMsg = sentMessages!![0].content
        assertTrue(systemMsg.contains("AI Visual Extraction Assistant for Velo Store"))
        assertTrue(systemMsg.contains("<store_topics>\n- الهوديز\n</store_topics>"))
        assertFalse(systemMsg.contains("heavy store knowledge text"))
        assertFalse(systemMsg.contains("<customer_order_state>"))
        val userMsg = sentMessages!![1]
        assertNotNull(userMsg.imageUrl)
    }

    @Test
    fun `vision requests utilize 25-second timeout window while text requests use 10 seconds`() {
        assertEquals(25_000L, ReplyGenerationRepositoryImpl.VISION_MODEL_TIMEOUT_MS)
        assertEquals(10_000L, ReplyGenerationRepositoryImpl.MODEL_TIMEOUT_MS)
    }

    @Test
    fun `filterAndPairChatHistory purges past media refusal messages when assembling prompt history`() {
        val now = 10_000_000L
        val historyWithRefusal = listOf(
            ChatHistoryEntity(id = 3L, contactName = "c", messageText = "عفواً، لا يمكنني معالجة الوسائط. يرجى كتابة استفسارك في رسالة نصية.", isFromUser = false, timestamp = now - 500L),
            ChatHistoryEntity(id = 2L, contactName = "c", messageText = "بكام ده؟", isFromUser = true, timestamp = now - 1000L),
            ChatHistoryEntity(id = 1L, contactName = "c", messageText = "السلام عليكم", isFromUser = true, timestamp = now - 2000L)
        )

        val filtered = PromptContextBuilder.filterAndPairChatHistory(historyWithRefusal, now)

        assertEquals(2, filtered.size)
        assertFalse("Media refusal turn must be purged from history", filtered.any { it.messageText.contains("لا يمكنني معالجة الوسائط") })
        assertEquals("السلام عليكم", filtered[0].messageText)
        assertEquals("بكام ده؟", filtered[1].messageText)
    }

    @Test
    fun `formatHistoricalAssistantTurn returns empty string for media refusal message`() {
        val refusal = "عفواً، لا يمكنني معالجة الوسائط. يرجى كتابة استفسارك في رسالة نصية."
        val formatted = PromptContextBuilder.formatHistoricalAssistantTurn(refusal)
        assertEquals("", formatted)
        assertTrue(PromptContextBuilder.isMediaRefusalMessage(refusal))
    }

    @Test
    fun `isMediaRefusalMessage and filterAndPairChatHistory strip custom media fallback message`() {
        val customFallback = "نعتذر، المتجر لا يستقبل صوراً حالياً. فضلاً اكتب طلبك."
        assertTrue(PromptContextBuilder.isMediaRefusalMessage(customFallback, customFallback))

        val now = 10_000_000L
        val historyWithCustomRefusal = listOf(
            ChatHistoryEntity(id = 2L, contactName = "c", messageText = customFallback, isFromUser = false, timestamp = now - 500L),
            ChatHistoryEntity(id = 1L, contactName = "c", messageText = "السلام عليكم", isFromUser = true, timestamp = now - 1000L)
        )
        val filtered = PromptContextBuilder.filterAndPairChatHistory(historyWithCustomRefusal, now, customFallback)
        assertEquals(1, filtered.size)
        assertEquals("السلام عليكم", filtered[0].messageText)
    }

    @Test
    fun `generateReply recognizes Arabic photo indicator and applies visual extraction prompt`() = runTest {
        var capturedPrompt: String? = null
        val mockEngine = MockEngine { request ->
            val bodyText = (request.body as? io.ktor.http.content.TextContent)?.text ?: request.body.toString()
            val parsedGroq = Json { ignoreUnknownKeys = true }.decodeFromString<GroqRequest>(bodyText)
            capturedPrompt = parsedGroq.messages.find { it.role == "user" }?.content
            respond(
                content = """{"choices":[{"message":{"content":"{\"action\":\"RE_QUERY\",\"visual_product_description\":\"هودي\"}"}}]}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true })
            }
        }

        val mockPrefs = mockk<UserPreferencesRepository>(relaxed = true) {
            every { storeKnowledgeTopicsFlow } returns kotlinx.coroutines.flow.flowOf(listOf("الهوديز"))
            every { storeWebsiteFlow } returns kotlinx.coroutines.flow.flowOf("https://velo.eg")
            every { stylePreferences } returns kotlinx.coroutines.flow.flowOf(
                com.example.persona.features.auto_reply.domain.model.StylePreferences(
                    personaTone = com.example.persona.features.auto_reply.domain.model.PersonaTone.WITTY_EGYPTIAN
                )
            )
            coEvery { getStoreNameSync() } returns "Velo Store"
            coEvery { getBotNameSync() } returns "فيلو"
            coEvery { getMediaFallbackMessageSync() } returns "عفواً، لا يمكنني معالجة الوسائط."
        }

        val repo = ReplyGenerationRepositoryImpl(
            httpClient = client,
            context = mockk<Context>(relaxed = true),
            primaryApiKey = "or-key",
            fallbackApiKey = "or-key-2",
            userPreferencesRepository = mockPrefs,
            groqPrimaryApiKey = "gsk_primary",
            groqFallbackApiKey = "gsk_fallback"
        )

        val tempFile = java.io.File.createTempFile("vision_ar", ".jpg").apply {
            writeBytes("DUMMY".toByteArray())
            deleteOnExit()
        }

        val notification = com.example.persona.features.auto_reply.domain.model.ProcessedNotification(
            notificationKey = "key_img_ar",
            contactName = "Test Contact",
            originalText = "📷 صورة",
            normalizedText = "📷 صورة",
            context = com.example.persona.features.chat_import.domain.model.ConversationContext(
                intent = com.example.persona.features.chat_import.domain.model.IntentType.QUESTION,
                topic = com.example.persona.features.chat_import.domain.model.TopicType.MEDIA,
                sentiment = com.example.persona.features.chat_import.domain.model.SentimentType.NEUTRAL,
                confidence = 1.0f
            ),
            postTime = System.currentTimeMillis(),
            sourceClass = com.example.persona.features.auto_reply.data.diagnostics.NotificationSourceClass.BUSINESS,
            mediaFilePath = tempFile.absolutePath
        )

        repo.generateReply(
            notification = notification,
            candidates = emptyList(),
            isOngoingConversation = false,
            chatHistory = emptyList(),
            activeOrders = emptyList(),
            personalChunks = emptyList(),
            maxTokens = 500
        )

        assertNotNull(capturedPrompt)
        assertTrue(capturedPrompt!!.contains("العميل أرسل هذه الصورة لمنتج"))
    }

    @Test
    fun `generateFinalReply allows 1500 tokens when image is present`() = runTest {
        var requestedMaxTokens: Int? = null
        val mockEngine = MockEngine { request ->
            val bodyText = (request.body as? io.ktor.http.content.TextContent)?.text ?: request.body.toString()
            val parsedGroq = Json { ignoreUnknownKeys = true }.decodeFromString<GroqRequest>(bodyText)
            requestedMaxTokens = parsedGroq.maxCompletionTokens
            respond(
                content = """{"choices":[{"message":{"content":"{\"action\":\"REPLY\",\"final_reply_text\":\"تمام\"}"}}]}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true })
            }
        }

        val mockPrefs = mockk<UserPreferencesRepository>(relaxed = true) {
            every { storeKnowledgeTopicsFlow } returns kotlinx.coroutines.flow.flowOf(listOf("الهوديز"))
            every { storeWebsiteFlow } returns kotlinx.coroutines.flow.flowOf("https://velo.eg")
            every { stylePreferences } returns kotlinx.coroutines.flow.flowOf(
                com.example.persona.features.auto_reply.domain.model.StylePreferences(
                    personaTone = com.example.persona.features.auto_reply.domain.model.PersonaTone.WITTY_EGYPTIAN
                )
            )
            coEvery { getStoreNameSync() } returns "Velo Store"
            coEvery { getBotNameSync() } returns "فيلو"
            coEvery { getMediaFallbackMessageSync() } returns "عفواً، لا يمكنني معالجة الوسائط."
        }

        val repo = ReplyGenerationRepositoryImpl(
            httpClient = client,
            context = mockk<Context>(relaxed = true),
            primaryApiKey = "or-key",
            fallbackApiKey = "or-key-2",
            userPreferencesRepository = mockPrefs,
            groqPrimaryApiKey = "gsk_primary",
            groqFallbackApiKey = "gsk_fallback"
        )

        val tempFile = java.io.File.createTempFile("pass2_img", ".jpg").apply {
            writeBytes("DUMMY".toByteArray())
            deleteOnExit()
        }

        repo.generateFinalReply(
            queryText = "بكام ده؟",
            chunks = emptyList(),
            isOngoingConversation = false,
            chatHistory = emptyList(),
            activeOrders = emptyList(),
            personalChunks = emptyList(),
            maxTokens = 1500,
            imageUrl = tempFile.absolutePath
        )

        assertEquals(1500, requestedMaxTokens)
    }

    @Test
    fun `compressAndScaleImage scales down large images and compresses to JPEG`() {
        val repo = ReplyGenerationRepositoryImpl(
            httpClient = mockk(relaxed = true),
            context = mockk(relaxed = true),
            primaryApiKey = "k1",
            fallbackApiKey = "k2",
            userPreferencesRepository = mockk(relaxed = true)
        )

        assertNull(repo.compressAndScaleImage("non_existent_file.jpg"))

        val tempFile = java.io.File.createTempFile("raw_test", ".jpg").apply {
            writeBytes("SAMPLE_PIXELS".toByteArray())
            deleteOnExit()
        }
        val resultBytes = repo.compressAndScaleImage(tempFile.absolutePath, maxDimension = 1024, quality = 80)
        assertNotNull(resultBytes)
        assertTrue(resultBytes!!.isNotEmpty())
    }

    @Test
    fun `callNvidiaNim passes reasoning_effort='none' and temperature=1_0 on ultra-550b model`() = runTest {
        var capturedReasoningEffort: String? = null
        var capturedTemperature: Float? = null
        val mockEngine = MockEngine { request ->
            val bodyText = (request.body as? io.ktor.http.content.TextContent)?.text ?: request.body.toString()
            val parsed = Json { ignoreUnknownKeys = true }.decodeFromString<NvidiaRequest>(bodyText)
            capturedReasoningEffort = parsed.reasoningEffort
            capturedTemperature = parsed.temperature
            respond(
                content = """{"choices":[{"message":{"content":"{\"action\":\"REPLY\",\"final_reply_text\":\"رد بدون reasoning\"}"}}]}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true })
            }
        }
        val repo = ReplyGenerationRepositoryImpl(
            httpClient = client,
            context = mockk<Context>(relaxed = true),
            primaryApiKey = "primary-key",
            fallbackApiKey = "fallback-key",
            userPreferencesRepository = mockk<UserPreferencesRepository>(relaxed = true),
            nvidiaApiKey = "nvapi-test-key"
        )

        val result = repo.callNvidiaNim(
            model = "nvidia/nemotron-3-ultra-550b-a55b",
            messages = listOf(ChatMessage(role = "user", content = "هلا")),
            maxTokens = 500
        )

        assertTrue(result is ReplyGenerationRepositoryImpl.CallResult.Success)
        assertEquals("none", capturedReasoningEffort)
        assertEquals(1.0f, capturedTemperature)
    }

    @Test
    fun `callQwenCloud returns success on deepseek-v4-1-flash with valid JSON`() = runTest {
        val capturedAuthHeaders = mutableListOf<String>()
        var capturedModel: String? = null
        var capturedRequest: QwenChatRequest? = null
        val mockEngine = MockEngine { request ->
            capturedAuthHeaders.add(request.headers[HttpHeaders.Authorization] ?: "")
            val bodyText = (request.body as? io.ktor.http.content.TextContent)?.text ?: request.body.toString()
            val parsed = Json { ignoreUnknownKeys = true }.decodeFromString<QwenChatRequest>(bodyText)
            capturedModel = parsed.model
            capturedRequest = parsed
            respond(
                content = """{"choices":[{"message":{"content":"{\"action\":\"REPLY\",\"final_reply_text\":\"منور المتجر يا غالي من كوين\"}"}}]}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true })
            }
        }
        val repo = ReplyGenerationRepositoryImpl(
            httpClient = client,
            context = mockk<Context>(relaxed = true),
            primaryApiKey = "primary-key",
            fallbackApiKey = "fallback-key",
            userPreferencesRepository = mockk<UserPreferencesRepository>(relaxed = true),
            qwenApiKey = "qwen-test-key-123"
        )

        val result = repo.callQwenCloud(
            model = "deepseek-v4.1-flash",
            messages = listOf(ChatMessage(role = "user", content = "سلام")),
            maxTokens = 600
        )

        assertTrue(result is ReplyGenerationRepositoryImpl.CallResult.Success)
        assertEquals("{\"action\":\"REPLY\",\"final_reply_text\":\"منور المتجر يا غالي من كوين\"}", (result as ReplyGenerationRepositoryImpl.CallResult.Success).content)
        assertEquals("Bearer qwen-test-key-123", capturedAuthHeaders[0])
        assertEquals("deepseek-v4.1-flash", capturedModel)
        assertEquals(false, capturedRequest?.enableThinking)
        assertEquals(1200, capturedRequest?.maxTokens)
    }

    @Test
    fun `callQwenCloud leaves enableThinking null for standard non-reasoning models and floors maxTokens`() = runTest {
        var capturedRequest: QwenChatRequest? = null
        val mockEngine = MockEngine { request ->
            val bodyText = (request.body as? io.ktor.http.content.TextContent)?.text ?: request.body.toString()
            capturedRequest = Json { ignoreUnknownKeys = true }.decodeFromString<QwenChatRequest>(bodyText)
            respond(
                content = """{"choices":[{"message":{"content":"{\"action\":\"REPLY\",\"final_reply_text\":\"رد قياسي\"}"}}]}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true })
            }
        }
        val repo = ReplyGenerationRepositoryImpl(
            httpClient = client,
            context = mockk<Context>(relaxed = true),
            primaryApiKey = "primary-key",
            fallbackApiKey = "fallback-key",
            userPreferencesRepository = mockk<UserPreferencesRepository>(relaxed = true),
            qwenApiKey = "qwen-test-key-123"
        )

        val result = repo.callQwenCloud(
            model = "qwen3.7-flash",
            messages = listOf(ChatMessage(role = "user", content = "مرحبا")),
            maxTokens = 800
        )

        assertTrue(result is ReplyGenerationRepositoryImpl.CallResult.Success)
        assertEquals(null, capturedRequest?.enableThinking)
        assertEquals(1200, capturedRequest?.maxTokens)
    }

    @Test
    fun `executeFailoverChain prioritizes QwenCloud Tier 1 when Qwen key is present`() = runTest {
        val capturedUrls = mutableListOf<String>()
        val mockEngine = MockEngine { request ->
            capturedUrls.add(request.url.toString())
            respond(
                content = """{"choices":[{"message":{"content":"{\"action\":\"REPLY\",\"final_reply_text\":\"رد كوين الفوري\"}"}}]}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true })
            }
        }
        val repo = ReplyGenerationRepositoryImpl(
            httpClient = client,
            context = mockk<Context>(relaxed = true),
            primaryApiKey = "primary-key",
            fallbackApiKey = "fallback-key",
            userPreferencesRepository = mockk<UserPreferencesRepository>(relaxed = true),
            groqPrimaryApiKey = "gsk_key",
            nvidiaApiKey = "nvapi_key",
            qwenApiKey = "qwen_key"
        )

        val reply = repo.executeFailoverChain(
            messages = listOf(ChatMessage(role = "user", content = "هلا")),
            maxTokens = 500
        )

        assertEquals("{\"action\":\"REPLY\",\"final_reply_text\":\"رد كوين الفوري\"}", reply)
        assertEquals(1, capturedUrls.size)
        assertTrue(capturedUrls[0].contains("qwencloudapi.com"))
    }

    @Test
    fun `callQwenCloud handles 403 quota error and 429 rate limit correctly`() = runTest {
        var statusCode = HttpStatusCode.Forbidden
        val mockEngine = MockEngine { _ ->
            respond(
                content = """{"error":{"message":"Allocation quota exhausted","type":"insufficient_quota","code":"quota_exhausted"}}""",
                status = statusCode,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true })
            }
        }
        val repo = ReplyGenerationRepositoryImpl(
            httpClient = client,
            context = mockk<Context>(relaxed = true),
            primaryApiKey = "primary-key",
            fallbackApiKey = "fallback-key",
            userPreferencesRepository = mockk<UserPreferencesRepository>(relaxed = true),
            qwenApiKey = "qwen-test-key"
        )

        val result403 = repo.callQwenCloud("deepseek-v4.1-flash", listOf(ChatMessage(role = "user", content = "hi")), 100)
        assertTrue(result403 is ReplyGenerationRepositoryImpl.CallResult.AuthOrQuotaError)
        assertEquals(403, (result403 as ReplyGenerationRepositoryImpl.CallResult.AuthOrQuotaError).code)

        statusCode = HttpStatusCode.TooManyRequests
        val result429 = repo.callQwenCloud("deepseek-v4.1-flash", listOf(ChatMessage(role = "user", content = "hi")), 100)
        assertTrue(result429 is ReplyGenerationRepositoryImpl.CallResult.RateLimited)
    }

    @Test
    fun `executeFailoverChain cascades to next Qwen model immediately when first model returns 403 quota error`() = runTest {
        val attemptedModels = mutableListOf<String>()
        val mockEngine = MockEngine { request ->
            val bodyText = (request.body as? io.ktor.http.content.TextContent)?.text ?: request.body.toString()
            val parsed = Json { ignoreUnknownKeys = true }.decodeFromString<QwenChatRequest>(bodyText)
            attemptedModels.add(parsed.model)

            if (parsed.model == "deepseek-v4.1-flash") {
                respond(
                    content = """{"error":{"message":"Quota exhausted","code":"quota_exhausted"}}""",
                    status = HttpStatusCode.Forbidden,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            } else {
                respond(
                    content = """{"choices":[{"message":{"content":"{\"action\":\"REPLY\",\"final_reply_text\":\"رد من qwen3.8-max بعد كوتا 403\"}"}}]}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
        }
        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true })
            }
        }
        val repo = ReplyGenerationRepositoryImpl(
            httpClient = client,
            context = mockk<Context>(relaxed = true),
            primaryApiKey = "primary-key",
            fallbackApiKey = "fallback-key",
            userPreferencesRepository = mockk<UserPreferencesRepository>(relaxed = true),
            qwenApiKey = "qwen_key"
        )

        val reply = repo.executeFailoverChain(
            messages = listOf(ChatMessage(role = "user", content = "هلا")),
            maxTokens = 500
        )

        assertEquals("{\"action\":\"REPLY\",\"final_reply_text\":\"رد من qwen3.8-max بعد كوتا 403\"}", reply)
        assertEquals(listOf("deepseek-v4.1-flash", "qwen3.8-max"), attemptedModels)
    }

    @Test
    fun `executeFailoverChain cascades to next Qwen model immediately when first model returns 429 rate limit`() = runTest {
        val attemptedModels = mutableListOf<String>()
        val mockEngine = MockEngine { request ->
            val bodyText = (request.body as? io.ktor.http.content.TextContent)?.text ?: request.body.toString()
            val parsed = Json { ignoreUnknownKeys = true }.decodeFromString<QwenChatRequest>(bodyText)
            attemptedModels.add(parsed.model)

            if (parsed.model == "deepseek-v4.1-flash") {
                respond(
                    content = """{"error":{"message":"Rate limit exceeded"}}""",
                    status = HttpStatusCode.TooManyRequests,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            } else {
                respond(
                    content = """{"choices":[{"message":{"content":"{\"action\":\"REPLY\",\"final_reply_text\":\"رد من qwen3.8-max بعد ليمت 429\"}"}}]}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
        }
        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true })
            }
        }
        val repo = ReplyGenerationRepositoryImpl(
            httpClient = client,
            context = mockk<Context>(relaxed = true),
            primaryApiKey = "primary-key",
            fallbackApiKey = "fallback-key",
            userPreferencesRepository = mockk<UserPreferencesRepository>(relaxed = true),
            qwenApiKey = "qwen_key"
        )

        val reply = repo.executeFailoverChain(
            messages = listOf(ChatMessage(role = "user", content = "هلا")),
            maxTokens = 500
        )

        assertEquals("{\"action\":\"REPLY\",\"final_reply_text\":\"رد من qwen3.8-max بعد ليمت 429\"}", reply)
        assertEquals(listOf("deepseek-v4.1-flash", "qwen3.8-max"), attemptedModels)
    }

    @Test
    fun `executeFailoverChain cascades from Qwen Tier 1 to NVIDIA Tier 2 when all Qwen models return 403`() = runTest {
        val capturedUrls = mutableListOf<String>()
        val mockEngine = MockEngine { request ->
            val url = request.url.toString()
            capturedUrls.add(url)

            if (url.contains("qwencloudapi.com")) {
                respond(
                    content = """{"error":{"message":"Quota exhausted"}}""",
                    status = HttpStatusCode.Forbidden,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            } else {
                respond(
                    content = """{"choices":[{"message":{"content":"{\"action\":\"REPLY\",\"final_reply_text\":\"رد من إنفيديا بعد استنفاد كوين\"}"}}]}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
        }
        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true })
            }
        }
        val repo = ReplyGenerationRepositoryImpl(
            httpClient = client,
            context = mockk<Context>(relaxed = true),
            primaryApiKey = "primary-key",
            fallbackApiKey = "fallback-key",
            userPreferencesRepository = mockk<UserPreferencesRepository>(relaxed = true),
            nvidiaApiKey = "nvapi_key",
            qwenApiKey = "qwen_key"
        )

        val reply = repo.executeFailoverChain(
            messages = listOf(ChatMessage(role = "user", content = "هلا")),
            maxTokens = 500
        )

        assertEquals("{\"action\":\"REPLY\",\"final_reply_text\":\"رد من إنفيديا بعد استنفاد كوين\"}", reply)
        val qwenAttempts = capturedUrls.count { it.contains("qwencloudapi.com") }
        assertEquals(ReplyGenerationRepositoryImpl.QWEN_TEXT_MODELS.size, qwenAttempts)
        assertTrue(capturedUrls.any { it.contains("nvidia.com") })
    }

    @Test
    fun `callGemini returns success on gemini-3_1-flash-lite with valid response`() = runTest {
        val capturedAuth = mutableListOf<String>()
        var capturedUrl: String? = null
        val mockEngine = MockEngine { request ->
            capturedAuth.add(request.headers[HttpHeaders.Authorization] ?: "")
            capturedUrl = request.url.toString()
            respond(
                content = """{"choices":[{"message":{"content":"{\"action\":\"REPLY\",\"final_reply_text\":\"رد من جيميني 3.1\"}"}}]}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true })
            }
        }
        val repo = ReplyGenerationRepositoryImpl(
            httpClient = client,
            context = mockk<Context>(relaxed = true),
            primaryApiKey = "primary-key",
            fallbackApiKey = "fallback-key",
            userPreferencesRepository = mockk<UserPreferencesRepository>(relaxed = true),
            geminiApiKey = "gemini-primary-key",
            geminiApiKey2 = "gemini-fallback-key"
        )

        val result = repo.callGemini(
            model = "gemini-3.1-flash-lite",
            messages = listOf(ChatMessage(role = "user", content = "سلام")),
            maxTokens = 600
        )

        assertTrue(result is ReplyGenerationRepositoryImpl.CallResult.Success)
        assertEquals("{\"action\":\"REPLY\",\"final_reply_text\":\"رد من جيميني 3.1\"}", (result as ReplyGenerationRepositoryImpl.CallResult.Success).content)
        assertEquals(listOf("Bearer gemini-primary-key"), capturedAuth)
        assertTrue(capturedUrl!!.contains("openai/chat/completions"))
    }

    @Test
    fun `callGemini dual-key rotation immediately falls back to geminiApiKey2 on 429`() = runTest {
        val capturedAuth = mutableListOf<String>()
        val mockEngine = MockEngine { request ->
            val auth = request.headers[HttpHeaders.Authorization] ?: ""
            capturedAuth.add(auth)

            if (auth.contains("gemini-primary-key")) {
                respond(
                    content = """{"error":{"code":429,"message":"Resource exhausted","status":"RESOURCE_EXHAUSTED"}}""",
                    status = HttpStatusCode.TooManyRequests,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            } else {
                respond(
                    content = """{"choices":[{"message":{"content":"{\"action\":\"REPLY\",\"final_reply_text\":\"رد من المفتاح البديل بعد ليمت 429\"}"}}]}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
        }
        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true })
            }
        }
        val repo = ReplyGenerationRepositoryImpl(
            httpClient = client,
            context = mockk<Context>(relaxed = true),
            primaryApiKey = "primary-key",
            fallbackApiKey = "fallback-key",
            userPreferencesRepository = mockk<UserPreferencesRepository>(relaxed = true),
            geminiApiKey = "gemini-primary-key",
            geminiApiKey2 = "gemini-fallback-key"
        )

        val result = repo.callGemini(
            model = "gemini-3.1-flash-lite",
            messages = listOf(ChatMessage(role = "user", content = "سلام")),
            maxTokens = 600
        )

        assertTrue(result is ReplyGenerationRepositoryImpl.CallResult.Success)
        assertEquals("{\"action\":\"REPLY\",\"final_reply_text\":\"رد من المفتاح البديل بعد ليمت 429\"}", (result as ReplyGenerationRepositoryImpl.CallResult.Success).content)
        assertEquals(2, capturedAuth.size)
        assertEquals("Bearer gemini-primary-key", capturedAuth[0])
        assertEquals("Bearer gemini-fallback-key", capturedAuth[1])
    }

    @Test
    fun `callGemini dual-key rotation falls back to geminiApiKey2 on server error`() = runTest {
        val capturedAuth = mutableListOf<String>()
        val mockEngine = MockEngine { request ->
            val auth = request.headers[HttpHeaders.Authorization] ?: ""
            capturedAuth.add(auth)

            if (auth.contains("gemini-primary-key")) {
                respond(
                    content = """{"error":{"code":500,"message":"Internal Server Error"}}""",
                    status = HttpStatusCode.InternalServerError,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            } else {
                respond(
                    content = """{"choices":[{"message":{"content":"{\"action\":\"REPLY\",\"final_reply_text\":\"رد من المفتاح البديل بعد خطأ 500\"}"}}]}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
        }
        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true })
            }
        }
        val repo = ReplyGenerationRepositoryImpl(
            httpClient = client,
            context = mockk<Context>(relaxed = true),
            primaryApiKey = "primary-key",
            fallbackApiKey = "fallback-key",
            userPreferencesRepository = mockk<UserPreferencesRepository>(relaxed = true),
            geminiApiKey = "gemini-primary-key",
            geminiApiKey2 = "gemini-fallback-key"
        )

        val result = repo.callGemini(
            model = "gemini-3.5-flash-lite",
            messages = listOf(ChatMessage(role = "user", content = "سلام")),
            maxTokens = 600
        )

        assertTrue(result is ReplyGenerationRepositoryImpl.CallResult.Success)
        assertEquals("{\"action\":\"REPLY\",\"final_reply_text\":\"رد من المفتاح البديل بعد خطأ 500\"}", (result as ReplyGenerationRepositoryImpl.CallResult.Success).content)
        assertEquals(listOf("Bearer gemini-primary-key", "Bearer gemini-fallback-key"), capturedAuth)
    }

    @Test
    fun `executeFailoverChain text cascade prioritizes Gemini Tier 1_5 when Qwen models fail`() = runTest {
        val capturedUrls = mutableListOf<String>()
        val mockEngine = MockEngine { request ->
            val url = request.url.toString()
            capturedUrls.add(url)

            if (url.contains("qwencloudapi.com")) {
                respond(
                    content = """{"error":{"message":"Quota exhausted"}}""",
                    status = HttpStatusCode.Forbidden,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            } else if (url.contains("generativelanguage.googleapis.com")) {
                respond(
                    content = """{"choices":[{"message":{"content":"{\"action\":\"REPLY\",\"final_reply_text\":\"رد جيميني تير 1.5 الفوري\"}"}}]}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            } else {
                respond(
                    content = """{"choices":[{"message":{"content":"{\"action\":\"REPLY\",\"final_reply_text\":\"رد من إنفيديا غير المتوقع\"}"}}]}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
        }
        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true })
            }
        }
        val repo = ReplyGenerationRepositoryImpl(
            httpClient = client,
            context = mockk<Context>(relaxed = true),
            primaryApiKey = "primary-key",
            fallbackApiKey = "fallback-key",
            userPreferencesRepository = mockk<UserPreferencesRepository>(relaxed = true),
            nvidiaApiKey = "nvapi_key",
            qwenApiKey = "qwen_key",
            geminiApiKey = "gemini_key"
        )

        val reply = repo.executeFailoverChain(
            messages = listOf(ChatMessage(role = "user", content = "هلا")),
            maxTokens = 500
        )

        assertEquals("{\"action\":\"REPLY\",\"final_reply_text\":\"رد جيميني تير 1.5 الفوري\"}", reply)
        val qwenAttempts = capturedUrls.count { it.contains("qwencloudapi.com") }
        assertEquals(ReplyGenerationRepositoryImpl.QWEN_TEXT_MODELS.size, qwenAttempts)
        assertTrue("Must call Gemini Tier 1.5 after Qwen exhaustion", capturedUrls.any { it.contains("generativelanguage.googleapis.com") })
        assertFalse("NVIDIA Tier 2 must not be called when Gemini succeeds", capturedUrls.any { it.contains("nvidia.com") })
    }

    @Test
    fun `executeFailoverChain vision cascade prioritizes Gemini Tier 2_5 when Qwen and Groq fail`() = runTest {
        val capturedUrls = mutableListOf<String>()
        val mockEngine = MockEngine { request ->
            val url = request.url.toString()
            capturedUrls.add(url)

            if (url.contains("qwencloudapi.com")) {
                respond(
                    content = """{"error":{"message":"Quota exhausted"}}""",
                    status = HttpStatusCode.Forbidden,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            } else if (url.contains("api.groq.com")) {
                respond(
                    content = """{"error":{"message":"Rate limit exceeded"}}""",
                    status = HttpStatusCode.TooManyRequests,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            } else if (url.contains("generativelanguage.googleapis.com")) {
                respond(
                    content = """{"choices":[{"message":{"content":"{\"action\":\"REPLY\",\"final_reply_text\":\"رد فيجن من جيميني 3.8 فلاش\"}"}}]}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            } else {
                respond(
                    content = """{"choices":[{"message":{"content":"{\"action\":\"REPLY\",\"final_reply_text\":\"رد إنفيديا\"}"}}]}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
        }
        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true })
            }
        }
        val repo = ReplyGenerationRepositoryImpl(
            httpClient = client,
            context = mockk<Context>(relaxed = true),
            primaryApiKey = "primary-key",
            fallbackApiKey = "fallback-key",
            userPreferencesRepository = mockk<UserPreferencesRepository>(relaxed = true),
            groqPrimaryApiKey = "groq_key",
            nvidiaApiKey = "nvapi_key",
            qwenApiKey = "qwen_key",
            geminiApiKey = "gemini_key"
        )

        val reply = repo.executeFailoverChain(
            messages = listOf(ChatMessage(role = "user", content = "بكام ده؟", imageUrl = "data:image/jpeg;base64,12345")),
            maxTokens = 500
        )

        assertEquals("{\"action\":\"REPLY\",\"final_reply_text\":\"رد فيجن من جيميني 3.8 فلاش\"}", reply)
        assertTrue("Must call Qwen first", capturedUrls.any { it.contains("qwencloudapi.com") })
        assertTrue("Must call Groq second", capturedUrls.any { it.contains("api.groq.com") })
        assertTrue("Must call Gemini Tier 2.5 third", capturedUrls.any { it.contains("generativelanguage.googleapis.com") })
        assertFalse("NVIDIA Tier 3 must not be called when Gemini succeeds", capturedUrls.any { it.contains("nvidia.com") })
    }

    @Test
    fun `callGemini parses native Gemini REST candidates schema safely`() = runTest {
        val mockEngine = MockEngine { request ->
            respond(
                content = """{"candidates":[{"content":{"parts":[{"text":"{\"action\":\"REPLY\",\"final_reply_text\":\"منور من جيميني ريست النيتيف\"}"}]}}]}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true })
            }
        }
        val repo = ReplyGenerationRepositoryImpl(
            httpClient = client,
            context = mockk<Context>(relaxed = true),
            primaryApiKey = "primary-key",
            fallbackApiKey = "fallback-key",
            userPreferencesRepository = mockk<UserPreferencesRepository>(relaxed = true),
            geminiApiKey = "gemini-primary-key"
        )

        val result = repo.callGemini(
            model = "gemini-3.8-flash",
            messages = listOf(ChatMessage(role = "user", content = "سلام")),
            maxTokens = 600
        )

        assertTrue(result is ReplyGenerationRepositoryImpl.CallResult.Success)
        assertEquals("{\"action\":\"REPLY\",\"final_reply_text\":\"منور من جيميني ريست النيتيف\"}", (result as ReplyGenerationRepositoryImpl.CallResult.Success).content)
    }
}
