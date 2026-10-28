package com.example.persona.features.auto_reply.data.repository

import android.content.Context
import com.example.persona.core.data.repository.UserPreferencesRepository
import com.example.persona.core.database.entity.ChatHistoryEntity
import com.example.persona.core.database.entity.OrderEntity
import com.example.persona.core.database.entity.OrderStatus
import com.example.persona.core.network.dto.AgenticLlmResponse
import com.example.persona.core.network.dto.ChatMessage
import com.example.persona.core.network.dto.OpenRouterRequest
import com.example.persona.features.auto_reply.domain.model.RetrievalCandidate
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

        assertTrue(dynamicBlock.contains("Order Status: IN_PREPARATION"))
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

        // Parity on order status template
        assertTrue(ReplyGenerationRepositoryImpl.SYSTEM_PROMPT_FIRST_PASS.contains("الأوردر بتاعك حالته [Status]"))
        assertTrue(ReplyGenerationRepositoryImpl.SYSTEM_PROMPT_FINAL_PASS.contains("الأوردر بتاعك حالته [Status]"))

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
    fun `synthetic canonical HITL fallback replies and their triggering user turns are excluded from history`() {
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
        assertEquals("Synthetic HITL messages should be excluded from prompt context", 0, assistantMessages.size)
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
    fun `buildOpenRouterMessages strips failed user and HITL-bot pairs cleanly without coalescing`() {
        val now = 10_000_000L
        val history = listOf(
            // Most recent first as returned by ChatHistoryDao
            ChatHistoryEntity(id = 4L, contactName = "c", messageText = "تمام يا فندم، ثواني وهيكون مع حضرتك ممثل خدمة العملاء فوراً لمساعدتك في أقرب وقت متاح! 🙏", isFromUser = false, timestamp = now - 1000L),
            ChatHistoryEntity(id = 3L, contactName = "c", messageText = "سؤال فشل الرد عليه", isFromUser = true, timestamp = now - 2000L),
            ChatHistoryEntity(id = 2L, contactName = "c", messageText = "{\"action\":\"REPLY\",\"final_reply_text\":\"أهلاً بك يا فندم\"}", isFromUser = false, timestamp = now - 10000L),
            ChatHistoryEntity(id = 1L, contactName = "c", messageText = "السلام عليكم", isFromUser = true, timestamp = now - 11000L)
        )

        val messages = ReplyGenerationRepositoryImpl.buildOpenRouterMessages(
            systemPrompt = "System Prompt",
            chatHistory = history,
            currentInboundText = "سؤال جديد",
            nowMs = now
        )

        // Expected messages:
        // Message 0: system
        // Message 1: user: "السلام عليكم"
        // Message 2: assistant: {"action":"REPLY"...}
        // Message 3: user: "سؤال جديد" (raw, not coalesced with "سؤال فشل الرد عليه")
        assertEquals(4, messages.size)
        assertEquals("system", messages[0].role)
        assertEquals("user", messages[1].role)
        assertEquals("[الآن] السلام عليكم", messages[1].content)
        assertEquals("assistant", messages[2].role)
        assertEquals("user", messages[3].role)
        assertEquals("[الآن] سؤال جديد", messages[3].content)
        assertFalse("Failed user query must not be in prompt", messages.any { it.content.contains("سؤال فشل الرد عليه") })
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
        assertEquals(listOf("nvidia/nemotron-3.5-super-vl:free"), ReplyGenerationRepositoryImpl.VISION_OPENROUTER_MODELS)
        assertFalse(ReplyGenerationRepositoryImpl.VISION_GROQ_MODELS.contains("llama-3.2-11b-vision-preview"))
        assertFalse(ReplyGenerationRepositoryImpl.VISION_OPENROUTER_MODELS.contains("meta-llama/llama-3.2-11b-vision-instruct:free"))
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
    fun `executeFailoverChain executes Groq Tier 1 first and returns immediately without touching OpenRouter`() = runTest {
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
    fun `executeFailoverChain cascades to Tier 2 OpenRouter when both Groq models and keys fail`() = runTest {
        val capturedUrls = mutableListOf<String>()
        val mockEngine = MockEngine { request ->
            val url = request.url.toString()
            capturedUrls.add(url)
            if (url.contains("groq.com")) {
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
            groqFallbackApiKey = "gsk_fallback_789012"
        )

        val reply = repo.executeFailoverChain(
            messages = listOf(ChatMessage(role = "user", content = "hi")),
            maxTokens = 100
        )

        assertEquals("{\"action\":\"REPLY\",\"final_reply_text\":\"رد من أوبن راوتر\"}", reply)
        assertTrue(capturedUrls.any { it.contains("groq.com") })
        assertTrue(capturedUrls.any { it.contains("openrouter.ai") })
    }
}
