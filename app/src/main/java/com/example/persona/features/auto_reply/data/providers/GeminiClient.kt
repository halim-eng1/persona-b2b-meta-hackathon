package com.example.persona.features.auto_reply.data.providers

import android.util.Log
import com.example.persona.BuildConfig
import com.example.persona.core.logging.PersonaLogTags
import com.example.persona.core.logging.PersonaLogger
import com.example.persona.core.network.dto.ChatMessage
import com.example.persona.core.network.dto.OpenRouterResponse
import com.example.persona.features.auto_reply.data.repository.ReplyGenerationRepositoryImpl
import com.example.persona.features.auto_reply.data.repository.ReplyGenerationRepositoryImpl.CallResult
import com.example.persona.features.auto_reply.domain.orchestrator.ReasoningLeakSanitizer
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
internal data class GeminiChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Float = 0.5f,
    @SerialName("max_tokens")
    val maxTokens: Int = 1000
)

@Singleton
class GeminiClient @Inject constructor(
    private val httpClient: HttpClient
) : LlmProviderClient {

    override val providerId: String = "gemini"

    companion object {
        private const val TAG = PersonaLogTags.LLM
        val VISION_GEMINI_MODELS = listOf("gemini-3.8-flash")
        val GEMINI_TEXT_MODELS = listOf(
            "gemini-3.1-flash-lite",
            "gemini-3.5-flash-lite",
            "gemini-3.8-flash"
        )

        private val jsonParser = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
    }

    internal suspend fun call(
        model: String,
        messages: List<ChatMessage>,
        maxTokens: Int,
        primaryApiKey: String,
        fallbackApiKey: String = ""
    ): CallResult = withContext(Dispatchers.IO) {
        val primary = primaryApiKey.trim()
        val fallback = fallbackApiKey.trim()

        val effectivePrimary = primary.ifBlank { fallback }
        val hasDistinctFallback = fallback.isNotBlank() && fallback != effectivePrimary

        if (effectivePrimary.isBlank()) {
            Log.w(TAG, "No Gemini API keys configured; skipping Gemini model $model")
            return@withContext CallResult.Failed
        }

        val primaryMasked = ReplyGenerationRepositoryImpl.maskKey(effectivePrimary)
        PersonaLogger.i(TAG, "Attempting [Gemini] model $model with key $primaryMasked")
        Log.i(TAG, "Attempting [Gemini] model $model with key $primaryMasked")

        val primaryResult = executeSingleCall(
            model = model,
            messages = messages,
            maxTokens = maxTokens,
            apiKey = effectivePrimary
        )

        if (primaryResult is CallResult.Success) {
            return@withContext primaryResult
        }

        val isKeyOrError = primaryResult is CallResult.RateLimited ||
            primaryResult is CallResult.AuthOrQuotaError ||
            primaryResult is CallResult.ServerError ||
            primaryResult is CallResult.Failed ||
            primaryResult is CallResult.Timeout

        if (hasDistinctFallback && isKeyOrError) {
            val fallbackMasked = ReplyGenerationRepositoryImpl.maskKey(fallback)
            Log.w(
                TAG,
                "Primary Gemini key ($primaryMasked) failed on model $model with $primaryResult. " +
                    "Falling back to GEMINI_API_KEY_2 ($fallbackMasked)."
            )
            PersonaLogger.w(
                TAG,
                "Primary Gemini key ($primaryMasked) failed on model $model with $primaryResult -> fallback to GEMINI_API_KEY_2 ($fallbackMasked)"
            )
            PersonaLogger.i(TAG, "Attempting [Gemini] model $model with key $fallbackMasked")

            val fallbackResult = executeSingleCall(
                model = model,
                messages = messages,
                maxTokens = maxTokens,
                apiKey = fallback
            )

            if (fallbackResult is CallResult.Success) {
                Log.i(TAG, "Fallback GEMINI_API_KEY_2 succeeded on model $model")
                PersonaLogger.i(TAG, "Fallback GEMINI_API_KEY_2 succeeded on model $model")
                return@withContext fallbackResult
            }

            Log.w(
                TAG,
                "Fallback GEMINI_API_KEY_2 also failed on model $model with $fallbackResult"
            )
            return@withContext fallbackResult
        }

        primaryResult
    }

    internal suspend fun executeSingleCall(
        model: String,
        messages: List<ChatMessage>,
        maxTokens: Int,
        apiKey: String
    ): CallResult {
        if (apiKey.isBlank()) {
            Log.w(TAG, "Gemini API key is blank; skipping request for model $model")
            return CallResult.Failed
        }

        val keyMasked = ReplyGenerationRepositoryImpl.maskKey(apiKey)
        val hasImage = messages.any { !it.imageUrl.isNullOrBlank() }
        val isVision = model in VISION_GEMINI_MODELS || hasImage
        val timeoutMs = if (isVision) 25_000L else 10_000L
        val effectiveMaxTokens = if (isVision) maxOf(maxTokens, 1500) else maxTokens

        val baseUrl = BuildConfig.GEMINI_BASE_URL.trimEnd('/')
        val isNativeRest = baseUrl.contains(":generateContent") || baseUrl.contains("/models")

        val endpoint = when {
            isNativeRest -> {
                if (baseUrl.contains(":generateContent")) {
                    if (baseUrl.contains("key=")) baseUrl else "$baseUrl?key=$apiKey"
                } else {
                    "$baseUrl/models/$model:generateContent?key=$apiKey"
                }
            }
            baseUrl.endsWith("/chat/completions") -> baseUrl
            baseUrl.endsWith("/openai") -> "$baseUrl/chat/completions"
            else -> "$baseUrl/openai/chat/completions"
        }

        PersonaLogger.i(TAG, "Attempting [Gemini] model $model with key $keyMasked (max_tokens=$effectiveMaxTokens, timeout=${timeoutMs}ms)")
        Log.i(TAG, "Attempting [Gemini] model $model with key $keyMasked (max_tokens=$effectiveMaxTokens, timeout=${timeoutMs}ms)")

        return try {
            withTimeout(timeoutMs) {
                val response = httpClient.post(endpoint) {
                    contentType(ContentType.Application.Json)
                    if (!isNativeRest) {
                        bearerAuth(apiKey)
                        setBody(
                            GeminiChatRequest(
                                model = model,
                                messages = messages,
                                temperature = 0.5f,
                                maxTokens = effectiveMaxTokens
                            )
                        )
                    } else {
                        setBody(
                            buildJsonObject {
                                put("contents", buildJsonArray {
                                    for (msg in messages) {
                                        addJsonObject {
                                            put("role", if (msg.role == "assistant") "model" else "user")
                                            put("parts", buildJsonArray {
                                                addJsonObject {
                                                    put("text", msg.content)
                                                }
                                                msg.imageUrl?.let { imgUrl ->
                                                    if (imgUrl.isNotBlank()) {
                                                        addJsonObject {
                                                            putJsonObject("inline_data") {
                                                                put("mime_type", "image/jpeg")
                                                                put("data", imgUrl.substringAfter("base64,"))
                                                            }
                                                        }
                                                    }
                                                }
                                            })
                                        }
                                    }
                                })
                                putJsonObject("generationConfig") {
                                    put("maxOutputTokens", effectiveMaxTokens)
                                    put("temperature", 0.5)
                                }
                            }.toString()
                        )
                    }
                }

                val status = response.status.value
                when {
                    status == 429 -> {
                        PersonaLogger.w(TAG, "Rate limited (429) on [Gemini] model $model (key $keyMasked)")
                        Log.w(TAG, "Rate limited (429) on [Gemini] model $model (key $keyMasked)")
                        CallResult.RateLimited
                    }
                    status in listOf(401, 402, 403) -> {
                        val errorBody = runCatching { response.body<String>() }.getOrNull() ?: ""
                        if (errorBody.contains("RESOURCE_EXHAUSTED", ignoreCase = true)) {
                            PersonaLogger.w(TAG, "Resource exhausted (429/quota) on [Gemini] model $model (key $keyMasked)")
                            Log.w(TAG, "Resource exhausted (429/quota) on [Gemini] model $model (key $keyMasked)")
                            CallResult.RateLimited
                        } else {
                            PersonaLogger.w(TAG, "Auth/quota error ($status) on [Gemini] model $model (key $keyMasked)")
                            Log.w(TAG, "Auth/quota error ($status) on [Gemini] model $model (key $keyMasked)")
                            CallResult.AuthOrQuotaError(status)
                        }
                    }
                    status in 500..599 -> {
                        Log.w(TAG, "Server error ($status) on [Gemini] model $model (key $keyMasked)")
                        CallResult.ServerError(status)
                    }
                    response.status.isSuccess() -> {
                        val responseBody = response.body<String>()
                        PersonaLogger.d(PersonaLogTags.LLM, "📥 Raw LLM Output from [Gemini] $model: $responseBody")
                        val content = extractGeminiContent(responseBody)
                        if (content.isNullOrBlank()) {
                            Log.w(TAG, "Empty content in successful response from [Gemini] $model (key $keyMasked)")
                            CallResult.Failed
                        } else {
                            try {
                                ReasoningLeakSanitizer.decodeAgenticResponseSafely(content)
                                CallResult.Success(content)
                            } catch (e: Exception) {
                                Log.w(TAG, "Invalid JSON content in response from [Gemini] $model (key $keyMasked): $content", e)
                                CallResult.Failed
                            }
                        }
                    }
                    else -> {
                        val errorBody = runCatching { response.body<String>() }.getOrNull() ?: ""
                        if (errorBody.contains("RESOURCE_EXHAUSTED", ignoreCase = true)) {
                            CallResult.RateLimited
                        } else {
                            Log.w(TAG, "HTTP $status error from [Gemini] $model (key $keyMasked)")
                            CallResult.Failed
                        }
                    }
                }
            }
        } catch (e: TimeoutCancellationException) {
            Log.w(TAG, "Timeout (${timeoutMs}ms) on [Gemini] model $model (key $keyMasked)")
            CallResult.Timeout
        } catch (e: Exception) {
            when (e) {
                is java.net.SocketTimeoutException,
                is io.ktor.client.network.sockets.SocketTimeoutException -> {
                    Log.w(TAG, "Socket timeout on [Gemini] model $model (key $keyMasked): ${e.message}")
                    CallResult.Timeout
                }
                else -> {
                    Log.e(TAG, "Network error on [Gemini] model $model (key $keyMasked)", e)
                    CallResult.Failed
                }
            }
        }
    }

    fun extractGeminiContent(responseBody: String): String? {
        return try {
            // 1. Try OpenAI-compatible chat completions schema: choices[0].message.content
            val openAiBody = jsonParser.decodeFromString<OpenRouterResponse>(responseBody)
            val openAiContent = openAiBody.choices?.firstOrNull()?.message?.content
            if (!openAiContent.isNullOrBlank()) {
                return openAiContent
            }

            // 2. Try native Gemini REST schema: candidates[0].content.parts[0].text
            val jsonElement = jsonParser.parseToJsonElement(responseBody)
            val candidates = jsonElement.jsonObject["candidates"]?.jsonArray
            val parts = candidates?.firstOrNull()?.jsonObject
                ?.get("content")?.jsonObject
                ?.get("parts")?.jsonArray
            val textPart = parts?.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.content
            textPart
        } catch (e: Exception) {
            null
        }
    }

    override fun generateStream(apiKey: String, request: ProviderRequest): Flow<StreamChunk> = flow {
        val trimmedKey = apiKey.trim()
        if (trimmedKey.isBlank()) {
            emit(StreamChunk.Error(IllegalArgumentException("Gemini API key is blank"), isTransient = false))
            return@flow
        }

        try {
            val baseUrl = BuildConfig.GEMINI_BASE_URL.trimEnd('/')
            val endpoint = if (baseUrl.contains(":generateContent") || baseUrl.contains("/models")) {
                "$baseUrl/models/${request.model}:streamGenerateContent?alt=sse&key=$trimmedKey"
            } else if (baseUrl.endsWith("/chat/completions")) {
                baseUrl
            } else {
                "$baseUrl/chat/completions"
            }

            val statement = httpClient.preparePost(endpoint) {
                contentType(ContentType.Application.Json)
                bearerAuth(trimmedKey)
                setBody(
                    GeminiChatRequest(
                        model = request.model,
                        messages = request.toChatMessages(),
                        temperature = request.temperature,
                        maxTokens = request.maxTokens
                    )
                )
            }

            statement.execute { response ->
                if (!response.status.isSuccess()) {
                    val isTransient = response.status.value == 429 || response.status.value in 500..599
                    emit(StreamChunk.Error(IllegalStateException("HTTP ${response.status.value}"), isTransient))
                    return@execute
                }

                val channel = response.bodyAsChannel()
                while (!channel.isClosedForRead) {
                    val line = channel.readUTF8Line() ?: break
                    if (line.startsWith("data: ")) {
                        val data = line.removePrefix("data: ").trim()
                        if (data == "[DONE]") {
                            emit(StreamChunk.Completed())
                            break
                        }
                        val text = extractGeminiContent(data)
                        if (!text.isNullOrEmpty()) {
                            emit(StreamChunk.Text(text))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            emit(StreamChunk.Error(e, isTransient = e is TimeoutCancellationException))
        }
    }.flowOn(Dispatchers.IO)
}
