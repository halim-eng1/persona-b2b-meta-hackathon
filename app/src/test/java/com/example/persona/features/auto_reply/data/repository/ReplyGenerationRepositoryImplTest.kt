package com.example.persona.features.auto_reply.data.repository

import android.content.Context
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
        assertEquals("سلام عليكم", messages[1].content)

        assertEquals("assistant", messages[2].role)
        assertTrue(messages[2].content.contains("\"action\":\"REPLY\""))
        assertTrue(messages[2].content.contains("\"final_reply_text\":\"جار تجهيزه يا فندم\""))

        assertEquals("user", messages[3].role)
        assertEquals("وصل؟", messages[3].content)

        assertEquals("assistant", messages[4].role)
        assertTrue(messages[4].content.contains("\"action\":\"REPLY\""))
        assertTrue(messages[4].content.contains("\"final_reply_text\":\"تمام شكراً\""))

        assertEquals("user", messages[5].role)
        assertEquals("هيوصل امتى؟", messages[5].content)
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
        assertEquals("مساء الخير\nعاوز حذاء مقاس 42\nلونه أسود", messages[1].content)
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
        assertEquals("السلام عليكم", userMessages[0].content)
        assertEquals("بكام الشحن؟", userMessages[1].content)

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
            fallbackApiKey = "fallback-key-456"
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
            fallbackApiKey = "fallback-key-456"
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
            fallbackApiKey = "fallback-key-456"
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
            fallbackApiKey = "fallback-key-456"
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
            fallbackApiKey = "fallback-key-456"
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
            fallbackApiKey = "fallback-key-456"
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
        assertEquals("السلام عليكم", messages[1].content)
        assertEquals("assistant", messages[2].role)
        assertEquals("user", messages[3].role)
        assertEquals("سؤال جديد", messages[3].content)
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
        assertEquals("ممكن خصم؟", messages[1].content)
        assertEquals("assistant", messages[2].role)
        val json = Json { ignoreUnknownKeys = true }
        val decoded = json.decodeFromString<AgenticLlmResponse>(messages[2].content)
        assertEquals("تمام هبعت لحضرتك خصم 10%", decoded.finalReplyText)
        assertEquals("user", messages[3].role)
        assertEquals("شكرا جدا", messages[3].content)
    }
}
