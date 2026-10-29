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

class CloudflareClefClientTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun createClient(mockEngine: MockEngine): CloudflareClefClient {
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
        return CloudflareClefClient(httpClient).apply {
            apiTokenOverride = "test-cf-token"
            accountIdOverride = "test-cf-account-id"
        }
    }

    @Test
    fun `decide parses GREETING decision correctly`() = runTest {
        val responseJson = """
            {
              "result": {
                "model": "clef-flash",
                "answers": {
                  "intent": {
                    "type": "choice",
                    "choice": "GREETING",
                    "confidence": 0.95,
                    "probabilities": { "GREETING": 0.95, "BANTER": 0.05 }
                  },
                  "is_urgent": {
                    "type": "choice",
                    "choice": "no",
                    "confidence": 0.99
                  }
                },
                "usage": { "input_tokens": 340, "output_tokens": 0 }
              },
              "success": true
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
        val result = client.decide("سلام عليكم يا غالي")

        assertEquals(InboundIntent.GREETING, result)
    }

    @Test
    fun `decide parses BANTER decision correctly`() = runTest {
        val responseJson = """
            {
              "result": {
                "model": "clef-flash",
                "answers": {
                  "intent": {
                    "type": "choice",
                    "choice": "BANTER",
                    "confidence": 0.94
                  }
                },
                "usage": { "input_tokens": 350, "output_tokens": 0 }
              },
              "success": true
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
        val result = client.decide("ههههه انت عبيط ولا بتفهم؟")

        assertEquals(InboundIntent.BANTER, result)
    }

    @Test
    fun `decide parses ORDER_STATUS decision correctly`() = runTest {
        val responseJson = """
            {
              "result": {
                "model": "clef-flash",
                "answers": {
                  "intent": {
                    "type": "choice",
                    "choice": "ORDER_STATUS",
                    "confidence": 0.98
                  }
                },
                "usage": { "input_tokens": 345, "output_tokens": 0 }
              },
              "success": true
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
        val result = client.decide("فين الاوردر بتاعي اتأخر اوي")

        assertEquals(InboundIntent.ORDER_STATUS, result)
    }

    @Test
    fun `decide maps BANTER, PRESENCE_CHECK, and CLOSING to InboundIntent BANTER`() = runTest {
        val choices = listOf("BANTER", "PRESENCE_CHECK", "CLOSING")
        for (choice in choices) {
            val responseJson = """
                {
                  "result": {
                    "model": "clef-flash",
                    "answers": {
                      "intent": {
                        "type": "choice",
                        "choice": "$choice",
                        "confidence": 0.94
                      }
                    }
                  },
                  "success": true
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
            val result = client.decide("test query for $choice")
            assertEquals("Choice $choice must map to InboundIntent.BANTER", InboundIntent.BANTER, result)
        }
    }

    @Test
    fun `decide maps ORDER_CREATE to CATALOG_INQUIRY and ORDER_STATUS, ORDER_UPDATE, ORDER_CANCEL to ORDER_STATUS`() = runTest {
        val cases = listOf(
            "ORDER_CREATE" to InboundIntent.CATALOG_INQUIRY,
            "ORDER_STATUS" to InboundIntent.ORDER_STATUS,
            "ORDER_UPDATE" to InboundIntent.ORDER_STATUS,
            "ORDER_CANCEL" to InboundIntent.ORDER_STATUS
        )

        for ((choice, expectedIntent) in cases) {
            val responseJson = """
                {
                  "result": {
                    "model": "clef-flash",
                    "answers": {
                      "intent": {
                        "type": "choice",
                        "choice": "$choice",
                        "confidence": 0.95
                      }
                    }
                  },
                  "success": true
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
            val result = client.decide("order query for $choice")
            assertEquals("Choice $choice must map to $expectedIntent", expectedIntent, result)
        }
    }

    @Test
    fun `mapChoiceToIntent maps CLOSING and PRESENCE_CHECK to BANTER, ORDER_CREATE to CATALOG_INQUIRY, and ORDER_UPDATE and ORDER_CANCEL to ORDER_STATUS`() {
        val client = createClient(MockEngine { respond("") })
        assertEquals(InboundIntent.BANTER, client.mapChoiceToIntent("CLOSING"))
        assertEquals(InboundIntent.BANTER, client.mapChoiceToIntent("closing"))
        assertEquals(InboundIntent.BANTER, client.mapChoiceToIntent("PRESENCE_CHECK"))
        assertEquals(InboundIntent.BANTER, client.mapChoiceToIntent("presence_check"))
        assertEquals(InboundIntent.BANTER, client.mapChoiceToIntent("BANTER"))

        assertEquals(InboundIntent.CATALOG_INQUIRY, client.mapChoiceToIntent("ORDER_CREATE"))
        assertEquals(InboundIntent.CATALOG_INQUIRY, client.mapChoiceToIntent("order_create"))

        assertEquals(InboundIntent.ORDER_STATUS, client.mapChoiceToIntent("ORDER_STATUS"))
        assertEquals(InboundIntent.ORDER_STATUS, client.mapChoiceToIntent("ORDER_UPDATE"))
        assertEquals(InboundIntent.ORDER_STATUS, client.mapChoiceToIntent("ORDER_CANCEL"))
    }

    @Test
    fun `decide maps CATALOG_INQUIRY and GENUINE_COMPLAINT_HITL correctly`() = runTest {
        val cases = listOf(
            "CATALOG_INQUIRY" to InboundIntent.CATALOG_INQUIRY,
            "GENUINE_COMPLAINT_HITL" to InboundIntent.CATALOG_INQUIRY
        )

        for ((choice, expectedIntent) in cases) {
            val responseJson = """
                {
                  "result": {
                    "model": "clef-flash",
                    "answers": {
                      "intent": {
                        "type": "choice",
                        "choice": "$choice",
                        "confidence": 0.92
                      }
                    }
                  },
                  "success": true
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
            val result = client.decide("test query for $choice")

            assertEquals("Choice $choice must map to $expectedIntent", expectedIntent, result)
        }
    }

    @Test
    fun `decide fails over from primary clef-flash to clef-omni on error`() = runTest {
        val capturedUrls = mutableListOf<String>()
        val capturedModels = mutableListOf<String>()

        val successFailoverJson = """
            {
              "result": {
                "model": "clef-omni",
                "answers": {
                  "intent": {
                    "type": "choice",
                    "choice": "ORDER_STATUS",
                    "confidence": 0.95
                  }
                },
                "usage": { "input_tokens": 330, "output_tokens": 0 }
              },
              "success": true
            }
        """.trimIndent()

        val mockEngine = MockEngine { request ->
            val url = request.url.toString()
            capturedUrls.add(url)
            val bodyString = (request.body as? io.ktor.http.content.TextContent)?.text ?: request.body.toString()
            val parsedBody = json.parseToJsonElement(bodyString).jsonObject
            val model = parsedBody["model"]?.jsonPrimitive?.content ?: ""
            capturedModels.add(model)

            if (url.contains("clef-flash")) {
                respond(
                    content = """{"success":false,"errors":[{"message":"Service Unavailable"}]}""",
                    status = HttpStatusCode.InternalServerError,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            } else {
                respond(
                    content = successFailoverJson,
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
        }

        val client = createClient(mockEngine)
        val result = client.decide("فين شحنتي؟")

        assertEquals(InboundIntent.ORDER_STATUS, result)
        assertTrue("Must attempt primary model clef-flash first", capturedUrls.any { it.contains("clef-flash") })
        assertTrue("Must failover to clef-omni", capturedUrls.any { it.contains("clef-omni") })
        assertEquals(listOf("clef-flash", "clef-omni"), capturedModels)
    }

    @Test
    fun `decide returns null when SLA timeout is exceeded`() = runTest {
        val mockEngine = MockEngine {
            delay(500L)
            respond(
                content = """{"result":{"answers":{"intent":{"choice":"GREETING"}}}}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val client = createClient(mockEngine)
        val result = client.decide("hello", timeoutMs = 100L)

        assertNull("Should safely return null when timeout is exceeded without throwing", result)
    }

    @Test
    fun `decide handles HTTP 429 and HTTP 500 gracefully without throwing`() = runTest {
        val statuses = listOf(HttpStatusCode.TooManyRequests, HttpStatusCode.InternalServerError)

        for (status in statuses) {
            val mockEngine = MockEngine {
                respond(
                    content = "Error $status",
                    status = status,
                    headers = headersOf(HttpHeaders.ContentType, "text/plain")
                )
            }

            val client = createClient(mockEngine)
            val result = client.decide("عايز اعرف مقاسات الهودي")

            assertNull("Status $status must be handled gracefully returning null", result)
        }
    }

    @Test
    fun `decide sends exact Clef JSON request structure and headers`() = runTest {
        var capturedPayload: String? = null
        var capturedAuthHeader: String? = null
        var capturedUrl: String? = null

        val mockEngine = MockEngine { request ->
            capturedPayload = (request.body as? io.ktor.http.content.TextContent)?.text ?: request.body.toString()
            capturedAuthHeader = request.headers[HttpHeaders.Authorization]
            capturedUrl = request.url.toString()

            respond(
                content = """{"result":{"answers":{"intent":{"type":"choice","choice":"GREETING"}}}}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val client = createClient(mockEngine)
        client.decide("سلام عليكم، بكام الهودي الاسود؟")

        assertNotNull(capturedPayload)
        assertEquals("Bearer test-cf-token", capturedAuthHeader)
        assertTrue(capturedUrl!!.contains("test-cf-account-id"))
        assertTrue(capturedUrl!!.contains("@cf/cloudflare/clef-flash"))

        val root = json.parseToJsonElement(capturedPayload!!).jsonObject
        assertEquals("clef-flash", root["model"]?.jsonPrimitive?.content)
        assertEquals("سلام عليكم، بكام الهودي الاسود؟", root["state"]?.jsonPrimitive?.content)

        val questions = root["questions"]?.jsonObject
        assertNotNull(questions)

        val intentQ = questions!!["intent"]?.jsonObject
        assertNotNull(intentQ)
        assertEquals("choice", intentQ!!["type"]?.jsonPrimitive?.content)
        assertNotNull(intentQ["instructions"])

        val intentCriteria = intentQ["criteria"]?.jsonObject
        assertNotNull(intentCriteria)
        assertTrue(intentCriteria!!.containsKey("GREETING"))
        assertTrue(intentCriteria.containsKey("CLOSING"))
        assertTrue(intentCriteria.containsKey("BANTER"))
        assertTrue(intentCriteria.containsKey("ORDER_STATUS"))
        assertTrue(intentCriteria.containsKey("CATALOG_INQUIRY"))
        assertTrue(intentCriteria.containsKey("GENUINE_COMPLAINT_HITL"))

        val urgentQ = questions["is_urgent"]?.jsonObject
        assertNotNull(urgentQ)
        assertEquals("choice", urgentQ!!["type"]?.jsonPrimitive?.content)
        assertNotNull(urgentQ["instructions"])

        val urgentCriteria = urgentQ["criteria"]?.jsonObject
        assertNotNull(urgentCriteria)
        assertTrue(urgentCriteria!!.containsKey("yes"))
        assertTrue(urgentCriteria.containsKey("no"))
    }

    @Test
    fun `decide respects default 1150ms timeout SLA`() = runTest {
        val mockEngine = MockEngine {
            delay(500L)
            respond(
                content = """{"result":{"answers":{"intent":{"type":"choice","choice":"GREETING"}}},"success":true}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val client = createClient(mockEngine)
        val result = client.decide("سلام عليكم")

        assertEquals(InboundIntent.GREETING, result)
    }

    @Test
    fun `decide returns null when credentials are blank without making network calls`() = runTest {
        var callCount = 0
        val mockEngine = MockEngine {
            callCount++
            respond(content = "{}", status = HttpStatusCode.OK)
        }

        val client = createClient(mockEngine).apply {
            apiTokenOverride = ""
            accountIdOverride = ""
        }

        val result = client.decide("hello")

        assertNull(result)
        assertEquals(0, callCount)
    }
}
