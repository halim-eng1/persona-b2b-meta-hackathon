package com.example.persona.features.auto_reply.data.gatekeeper

import com.example.persona.features.auto_reply.domain.gatekeeper.InboundIntent
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiquidDecisionClientTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun createClient(mockEngine: MockEngine): LiquidDecisionClient {
        val httpClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                    encodeDefaults = true
                    explicitNulls = false
                })
            }
        }
        return LiquidDecisionClient(httpClient).apply {
            apiKeyOverride = "test-primary-key"
            apiKey2Override = "test-fallback-key"
        }
    }

    @Test
    fun `decide parses GREETING decision correctly`() = runTest {
        val responseJson = """
            {
              "id": "dec_1",
              "model": "d1",
              "object": "decision.response",
              "answers": {
                "intent": {
                  "type": "choice",
                  "choice": "GREETING",
                  "confidence": 0.98,
                  "probabilities": { "GREETING": 0.98, "CATALOG_INQUIRY": 0.02 }
                },
                "is_urgent_complaint": {
                  "type": "choice",
                  "choice": "normal"
                }
              }
            }
        """.trimIndent()

        val mockEngine = MockEngine {
            respond(
                content = responseJson,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val client = createClient(mockEngine)
        val result = client.decide("صباح الخير يا باشا")

        assertEquals(InboundIntent.GREETING, result)
    }

    @Test
    fun `decide maps BANTER and PRESENCE_CHECK to InboundIntent BANTER`() = runTest {
        val banterJson = """
            {
              "answers": {
                "intent": { "type": "choice", "choice": "BANTER", "confidence": 0.95 }
              }
            }
        """.trimIndent()

        val presenceJson = """
            {
              "answers": {
                "intent": { "type": "choice", "choice": "PRESENCE_CHECK", "confidence": 0.99 }
              }
            }
        """.trimIndent()

        var callCount = 0
        val mockEngine = MockEngine {
            callCount++
            respond(
                content = if (callCount == 1) banterJson else presenceJson,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val client = createClient(mockEngine)
        val resultBanter = client.decide("هو انت مرتبط؟")
        val resultPresence = client.decide("ف حد هنا؟")

        assertEquals(InboundIntent.BANTER, resultBanter)
        assertEquals(InboundIntent.BANTER, resultPresence)
    }

    @Test
    fun `decide maps ORDER_CREATE, ORDER_UPDATE, and ORDER_CANCEL to InboundIntent ORDER_STATUS`() = runTest {
        val choices = listOf("ORDER_CREATE", "ORDER_UPDATE", "ORDER_CANCEL")

        for (choice in choices) {
            val responseJson = """
                {
                  "answers": {
                    "intent": { "type": "choice", "choice": "$choice", "confidence": 0.96 }
                  }
                }
            """.trimIndent()

            val mockEngine = MockEngine {
                respond(
                    content = responseJson,
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }

            val client = createClient(mockEngine)
            val result = client.decide("order message for $choice")
            assertEquals("Choice $choice must map to InboundIntent.ORDER_STATUS", InboundIntent.ORDER_STATUS, result)
        }
    }

    @Test
    fun `decide maps CATALOG_INQUIRY and unmatched categories to InboundIntent CATALOG_INQUIRY`() = runTest {
        val choices = listOf("CATALOG_INQUIRY", "GENUINE_COMPLAINT_HITL", "UNKNOWN_CATEGORY", null)

        for (choice in choices) {
            val choiceField = if (choice != null) "\"choice\": \"$choice\"," else ""
            val responseJson = """
                {
                  "answers": {
                    "intent": { "type": "choice", $choiceField "confidence": 0.90 }
                  }
                }
            """.trimIndent()

            val mockEngine = MockEngine {
                respond(
                    content = responseJson,
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }

            val client = createClient(mockEngine)
            val result = client.decide("query for $choice")
            assertEquals("Choice $choice must map to InboundIntent.CATALOG_INQUIRY", InboundIntent.CATALOG_INQUIRY, result)
        }
    }

    @Test
    fun `decide rotates from primary key to fallback key on HTTP 429`() = runTest {
        val capturedAuthHeaders = mutableListOf<String>()
        val successJson = """
            {
              "answers": {
                "intent": { "type": "choice", "choice": "GREETING", "confidence": 0.97 }
              }
            }
        """.trimIndent()

        val mockEngine = MockEngine { request ->
            val auth = request.headers[HttpHeaders.Authorization] ?: ""
            capturedAuthHeaders.add(auth)

            if (auth == "Bearer test-primary-key") {
                respond(
                    content = "Too Many Requests",
                    status = HttpStatusCode.TooManyRequests,
                    headers = headersOf(HttpHeaders.ContentType, "text/plain")
                )
            } else {
                respond(
                    content = successJson,
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
        }

        val client = createClient(mockEngine)
        val result = client.decide("صباح الفل")

        assertEquals(InboundIntent.GREETING, result)
        assertEquals(2, capturedAuthHeaders.size)
        assertEquals("Bearer test-primary-key", capturedAuthHeaders[0])
        assertEquals("Bearer test-fallback-key", capturedAuthHeaders[1])
    }

    @Test
    fun `decide fails over model to d1 if d1 latest fails with 500 error`() = runTest {
        val capturedModels = mutableListOf<String>()
        val successJson = """
            {
              "answers": {
                "intent": { "type": "choice", "choice": "ORDER_CREATE", "confidence": 0.99 }
              }
            }
        """.trimIndent()

        val mockEngine = MockEngine { request ->
            val bodyString = (request.body as? io.ktor.http.content.TextContent)?.text ?: request.body.toString()
            val parsedBody = json.parseToJsonElement(bodyString).jsonObject
            val model = parsedBody["model"]?.jsonPrimitive?.content ?: ""
            capturedModels.add(model)

            if (model == LiquidDecisionClient.PRIMARY_MODEL) {
                respond(
                    content = "Internal Server Error",
                    status = HttpStatusCode.InternalServerError,
                    headers = headersOf(HttpHeaders.ContentType, "text/plain")
                )
            } else {
                respond(
                    content = successJson,
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
        }

        val client = createClient(mockEngine)
        // Set both keys to the same so fallback key retry is skipped and it goes straight to model failover
        client.apiKeyOverride = "same-key"
        client.apiKey2Override = "same-key"

        val result = client.decide("عايز اطلب هودي")

        assertEquals(InboundIntent.ORDER_STATUS, result)
        assertTrue(capturedModels.contains(LiquidDecisionClient.PRIMARY_MODEL))
        assertTrue(capturedModels.contains(LiquidDecisionClient.FAILOVER_MODEL))
    }

    @Test
    fun `decide returns null when timeout is exceeded`() = runTest {
        val mockEngine = MockEngine {
            delay(400L)
            respond(
                content = """{"answers": {"intent": {"type": "choice", "choice": "GREETING"}}}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val client = createClient(mockEngine)
        val result = client.decide("hello", timeoutMs = 100L)

        assertNull("Should return null immediately when timeout is exceeded", result)
    }

    @Test
    fun `decide returns null when both keys are blank without network requests`() = runTest {
        var networkCalls = 0
        val mockEngine = MockEngine {
            networkCalls++
            respond(
                content = "{}",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val client = createClient(mockEngine).apply {
            apiKeyOverride = ""
            apiKey2Override = ""
        }

        val result = client.decide("hello")

        assertNull(result)
        assertEquals(0, networkCalls)
    }

    @Test
    fun `decide sends exact SystemOne payload structure`() = runTest {
        var capturedPayload: String? = null

        val mockEngine = MockEngine { request ->
            capturedPayload = (request.body as? io.ktor.http.content.TextContent)?.text ?: request.body.toString()
            respond(
                content = """{"answers": {"intent": {"type": "choice", "choice": "GREETING"}}}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val client = createClient(mockEngine)
        client.decide("ازيك يا باشا")

        assertNotNull(capturedPayload)
        val root = json.parseToJsonElement(capturedPayload!!).jsonObject
        assertEquals(LiquidDecisionClient.PRIMARY_MODEL, root["model"]?.jsonPrimitive?.content)
        assertEquals("ازيك يا باشا", root["state"]?.jsonPrimitive?.content)

        val questions = root["questions"]?.jsonObject
        assertNotNull(questions)
        val intentQ = questions!!["intent"]?.jsonObject
        assertNotNull(intentQ)
        assertEquals("choice", intentQ!!["type"]?.jsonPrimitive?.content)
        assertNotNull(intentQ["instructions"])
        assertNotNull(intentQ["criteria"])

        val criteria = intentQ["criteria"]?.jsonObject
        assertNotNull(criteria)
        assertTrue(criteria!!.containsKey("GREETING"))
        assertTrue(criteria.containsKey("CLOSING"))
        assertTrue(criteria.containsKey("PRESENCE_CHECK"))
        assertTrue(criteria.containsKey("BANTER"))
        assertTrue(criteria.containsKey("CATALOG_INQUIRY"))
        assertTrue(criteria.containsKey("ORDER_CREATE"))
        assertTrue(criteria.containsKey("ORDER_UPDATE"))
        assertTrue(criteria.containsKey("ORDER_CANCEL"))
        assertTrue(criteria.containsKey("GENUINE_COMPLAINT_HITL"))

        val urgentQ = questions["is_urgent_complaint"]?.jsonObject
        assertNotNull(urgentQ)
        assertEquals("choice", urgentQ!!["type"]?.jsonPrimitive?.content)
        val urgentCriteria = urgentQ["criteria"]?.jsonObject
        assertNotNull(urgentCriteria)
        assertTrue(urgentCriteria!!.containsKey("urgent"))
        assertTrue(urgentCriteria.containsKey("normal"))
    }
}
