package com.example.persona.features.auto_reply.data.repository

import android.content.Context
import com.example.persona.core.database.entity.ChatHistoryEntity
import com.example.persona.core.database.entity.OrderEntity
import com.example.persona.core.database.entity.OrderStatus
import com.example.persona.core.network.dto.ChatMessage
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
            dynamicContextBlock = dynamicBlock
        )

        // 0: system
        // 1: user (سلام عليكم)
        // 2: assistant (جار تجهيزه يا فندم)
        // 3: user (وصل؟)
        // 4: assistant (تمام شكراً)
        // 5: user (final dynamic context)
        assertEquals(6, messages.size)
        assertEquals("system", messages[0].role)
        assertEquals(ReplyGenerationRepositoryImpl.SYSTEM_PROMPT_FIRST_PASS, messages[0].content)

        assertEquals("user", messages[1].role)
        assertEquals("سلام عليكم", messages[1].content)

        assertEquals("assistant", messages[2].role)
        assertEquals("جار تجهيزه يا فندم", messages[2].content)

        assertEquals("user", messages[3].role)
        assertEquals("وصل؟", messages[3].content)

        assertEquals("assistant", messages[4].role)
        assertEquals("تمام شكراً", messages[4].content)

        assertEquals("user", messages[5].role)
        assertTrue(messages[5].content.contains("=== CURRENT CUSTOMER MESSAGE ==="))
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
            dynamicContextBlock = dynamicBlock
        )

        // Since all turns were from user and final is user, they coalesce into 1 final user turn to strictly alternate with system
        assertEquals(2, messages.size)
        assertEquals("system", messages[0].role)
        assertEquals("user", messages[1].role)
        assertTrue(messages[1].content.contains("مساء الخير\nعاوز حذاء مقاس 42"))
        assertTrue(messages[1].content.contains("=== CURRENT CUSTOMER MESSAGE ==="))
    }

    @Test
    fun `system prompts have parity and no raw string quote escapes`() {
        // Assert raw string bug is fixed (no trailing \")
        assertFalse(ReplyGenerationRepositoryImpl.SYSTEM_PROMPT_FIRST_PASS.endsWith("\\\""))
        assertFalse(ReplyGenerationRepositoryImpl.SYSTEM_PROMPT_FINAL_PASS.endsWith("\\\""))

        // Assert essential sections are in both
        assertTrue(ReplyGenerationRepositoryImpl.SYSTEM_PROMPT_FIRST_PASS.contains("IDENTITY & CONTINUITY:"))
        assertTrue(ReplyGenerationRepositoryImpl.SYSTEM_PROMPT_FINAL_PASS.contains("IDENTITY & CONTINUITY:"))

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
    fun `callOpenRouter falls back to V2 key when primary key encounters 500 server error`() = runTest {
        val capturedAuthHeaders = mutableListOf<String>()
        val mockEngine = MockEngine { request ->
            val auth = request.headers[HttpHeaders.Authorization] ?: ""
            capturedAuthHeaders.add(auth)
            if (auth.contains("primary-key-123")) {
                respond(
                    content = """Internal Server Error""",
                    status = HttpStatusCode.InternalServerError,
                    headers = headersOf(HttpHeaders.ContentType, "text/plain")
                )
            } else {
                respond(
                    content = """{"choices":[{"message":{"content":"{\"action\":\"REPLY\",\"final_reply_text\":\"نجح بعد العطل\"}"}}]}""",
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
}
