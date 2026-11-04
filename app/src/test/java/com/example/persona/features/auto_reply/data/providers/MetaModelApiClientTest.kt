package com.example.persona.features.auto_reply.data.providers

import com.example.persona.core.network.dto.ChatMessage
import com.example.persona.features.auto_reply.data.repository.ReplyGenerationRepositoryImpl
import com.example.persona.features.settings.data.repository.InMemorySecureCredentialsRepository
import com.example.persona.features.settings.domain.repository.ModelProviders
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okio.Buffer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class MetaModelApiClientTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private fun createClient(interceptor: (okhttp3.Interceptor.Chain) -> Response): Pair<MetaModelApiClient, InMemorySecureCredentialsRepository> {
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor { chain -> interceptor(chain) }
            .build()
        val secureRepo = InMemorySecureCredentialsRepository().apply {
            setApiKeys(ModelProviders.META, listOf("LLM_test_meta_key_123456"))
        }
        val client = MetaModelApiClient(okHttpClient, secureRepo)
        return client to secureRepo
    }

    @Test
    fun `generateChatCompletion succeeds and strips reasoning tags`() = runTest {
        val (client, _) = createClient { chain ->
            val req = chain.request()
            assertEquals("Bearer LLM_test_meta_key_123456", req.header("Authorization"))
            assertTrue(req.url.encodedPath.contains("/chat/completions"))

            Response.Builder()
                .request(req)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(
                    """
                    {
                        "id": "chatcmpl-test",
                        "choices": [
                            {
                                "index": 0,
                                "message": {
                                    "role": "assistant",
                                    "content": "<think>Thinking about customer greeting</think>أهلاً بحضرتك يا فندم! مواعيدنا من 10 لـ 11"
                                }
                            }
                        ],
                        "usage": { "prompt_tokens": 10, "completion_tokens": 25 }
                    }
                    """.trimIndent().toResponseBody(jsonMediaType)
                )
                .build()
        }

        val result = client.generateChatCompletion(
            model = MetaModelApiClient.MODEL_MUSE_SPARK_1_3_CONTRIBUTOR,
            systemPrompt = "System",
            userPrompt = "User",
            reasoningEffort = "low"
        )

        assertTrue(result.isSuccess)
        val text = result.getOrNull()
        assertNotNull(text)
        assertTrue(text!!.contains("أهلاً بحضرتك"))
        assertTrue(!text.contains("<think>"))
    }

    @Test
    fun `generateVisionCompletion succeeds with multimodal image payload`() = runTest {
        val (client, _) = createClient { chain ->
            val req = chain.request()
            assertTrue(req.url.encodedPath.contains("/chat/completions"))

            Response.Builder()
                .request(req)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(
                    """
                    {
                        "id": "chatcmpl-vision",
                        "choices": [
                            {
                                "message": {
                                    "role": "assistant",
                                    "content": "هودي أبيض مع طباعة هاكر"
                                }
                            }
                        ]
                    }
                    """.trimIndent().toResponseBody(jsonMediaType)
                )
                .build()
        }

        val dummyImage = ByteArray(64) { 1 }
        val result = client.generateVisionCompletion(
            model = MetaModelApiClient.MODEL_MUSE_SPARK_1_3_CONTRIBUTOR,
            systemPrompt = "System",
            imageBytes = dummyImage,
            mimeType = "image/jpeg"
        )

        assertTrue(result.isSuccess)
        assertEquals("هودي أبيض مع طباعة هاكر", result.getOrNull())
    }

    @Test
    fun `transcribeAudio calls asr transcribe endpoint and returns transcript`() = runTest {
        val (client, _) = createClient { chain ->
            val req = chain.request()
            assertEquals("Bearer LLM_test_meta_key_123456", req.header("Authorization"))
            assertTrue(req.url.encodedPath.contains("/asr/transcribe"))

            Response.Builder()
                .request(req)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(
                    """
                    {
                        "sessionId": "voyager/transcribe/123",
                        "transcript": "هو المفروض في حد موجود هنا دلوقتي ولا ايه?",
                        "audioDurationMs": 3440
                    }
                    """.trimIndent().toResponseBody(jsonMediaType)
                )
                .build()
        }

        val testAudio = tempFolder.newFile("test_audio.wav").apply {
            writeBytes(ByteArray(100) { 0 })
        }

        val result = client.transcribeAudio(testAudio)
        assertTrue(result.isSuccess)
        assertEquals("هو المفروض في حد موجود هنا دلوقتي ولا ايه?", result.getOrNull())
    }

    @Test
    fun `executeSingleCall maps error codes correctly to CallResult`() = runTest {
        // Test 401 Auth error
        val (client401, _) = createClient { chain ->
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(401)
                .message("Unauthorized")
                .body("{}".toResponseBody(jsonMediaType))
                .build()
        }
        val res401 = client401.executeSingleCall("muse-spark-1.3", listOf(ChatMessage(role = "user", content = "Hi")), 100, "bad-key")
        assertTrue(res401 is ReplyGenerationRepositoryImpl.CallResult.AuthOrQuotaError)
        assertEquals(401, (res401 as ReplyGenerationRepositoryImpl.CallResult.AuthOrQuotaError).code)

        // Test 429 Rate limited
        val (client429, _) = createClient { chain ->
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(429)
                .message("Too Many Requests")
                .body("{}".toResponseBody(jsonMediaType))
                .build()
        }
        val res429 = client429.executeSingleCall("muse-spark-1.3", listOf(ChatMessage(role = "user", content = "Hi")), 100, "key")
        assertTrue(res429 is ReplyGenerationRepositoryImpl.CallResult.RateLimited)

        // Test 500 Server error
        val (client500, _) = createClient { chain ->
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(500)
                .message("Server Error")
                .body("{}".toResponseBody(jsonMediaType))
                .build()
        }
        val res500 = client500.executeSingleCall("muse-spark-1.3", listOf(ChatMessage(role = "user", content = "Hi")), 100, "key")
        assertTrue(res500 is ReplyGenerationRepositoryImpl.CallResult.ServerError)
    }

    @Test
    fun `executeSingleCall enforces minimum 2000 max_tokens for reasoning models and 1500 for standard models`() = runTest {
        var capturedMaxTokens: Int? = null
        val (client, _) = createClient { chain ->
            val req = chain.request()
            val buffer = Buffer()
            req.body?.writeTo(buffer)
            val json = Json.parseToJsonElement(buffer.readUtf8()).jsonObject
            capturedMaxTokens = json["max_tokens"]?.jsonPrimitive?.intOrNull

            Response.Builder()
                .request(req)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("""{"choices":[{"message":{"content":"{\"action\":\"REPLY\"}"}}]}""".toResponseBody(jsonMediaType))
                .build()
        }

        // Test reasoning model with small maxTokens (e.g. 500) -> should be scaled to 2000
        client.executeSingleCall(
            model = "muse-spark-1.3-contributor",
            messages = listOf(ChatMessage(role = "user", content = "تمام")),
            maxTokens = 500,
            apiKey = "key"
        )
        assertEquals(2000, capturedMaxTokens)

        // Test non-reasoning model with small maxTokens (e.g. 500) -> should be scaled to 1500
        client.executeSingleCall(
            model = "meta-llama-3",
            messages = listOf(ChatMessage(role = "user", content = "Hi")),
            maxTokens = 500,
            apiKey = "key"
        )
        assertEquals(1500, capturedMaxTokens)
    }

    @Test
    fun `getCounterpartModel maps between standard and contributor variants`() {
        assertEquals(
            "muse-spark-1.3-contributor",
            MetaModelApiClient.getCounterpartModel("muse-spark-1.3")
        )
        assertEquals(
            "muse-spark-1.3",
            MetaModelApiClient.getCounterpartModel("muse-spark-1.3-contributor")
        )
        assertEquals(
            "muse-spark-1.2-contributor",
            MetaModelApiClient.getCounterpartModel("muse-spark-1.2")
        )
        assertEquals(
            "muse-spark-1.2",
            MetaModelApiClient.getCounterpartModel("muse-spark-1.2-contributor")
        )
        assertNull(MetaModelApiClient.getCounterpartModel("muse-voice-transcribe-1.0"))
    }

    @Test
    fun `executeSingleCall transparently falls back to the counterpart model on HTTP 404`() = runTest {
        val requestedModels = mutableListOf<String>()
        val (client, _) = createClient { chain ->
            val req = chain.request()
            val buffer = Buffer()
            req.body?.writeTo(buffer)
            val model = Json.parseToJsonElement(buffer.readUtf8())
                .jsonObject["model"]?.jsonPrimitive?.content
            requestedModels += model!!
            val code = if (model == "muse-spark-1.3") 404 else 200
            Response.Builder()
                .request(req)
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message(if (code == 404) "Not Found" else "OK")
                .body(
                    (
                        if (code == 404) """{"error":"model not found"}"""
                        else """{"choices":[{"message":{"content":"{\"action\":\"REPLY\"}"}}]}"""
                        ).toResponseBody(jsonMediaType)
                )
                .build()
        }

        val result = client.executeSingleCall(
            model = "muse-spark-1.3",
            messages = listOf(ChatMessage(role = "user", content = "Hi")),
            maxTokens = 100,
            apiKey = "key"
        )

        assertEquals(
            listOf("muse-spark-1.3", "muse-spark-1.3-contributor"),
            requestedModels
        )
        assertTrue(result is ReplyGenerationRepositoryImpl.CallResult.Success)
    }

    @Test
    fun `executeSingleCall does not retry endlessly when both variants return 404`() = runTest {
        val requestedModels = mutableListOf<String>()
        val (client, _) = createClient { chain ->
            val req = chain.request()
            val buffer = Buffer()
            req.body?.writeTo(buffer)
            requestedModels += Json.parseToJsonElement(buffer.readUtf8())
                .jsonObject["model"]!!.jsonPrimitive.content
            Response.Builder()
                .request(req)
                .protocol(Protocol.HTTP_1_1)
                .code(404)
                .message("Not Found")
                .body("""{"error":"model not found"}""".toResponseBody(jsonMediaType))
                .build()
        }

        val result = client.executeSingleCall(
            model = "muse-spark-1.3",
            messages = listOf(ChatMessage(role = "user", content = "Hi")),
            maxTokens = 100,
            apiKey = "key"
        )

        assertEquals(
            listOf("muse-spark-1.3", "muse-spark-1.3-contributor"),
            requestedModels
        )
        assertTrue(result is ReplyGenerationRepositoryImpl.CallResult.Failed)
    }
}

