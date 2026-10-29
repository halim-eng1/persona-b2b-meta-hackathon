package com.example.persona.features.auto_reply.data.providers

import android.util.Log
import com.example.persona.BuildConfig
import com.example.persona.core.logging.PersonaLogTags
import com.example.persona.core.logging.PersonaLogger
import com.example.persona.core.network.dto.ChatMessage
import com.example.persona.core.network.dto.GroqRequest
import com.example.persona.core.network.dto.GroqResponse
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
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GroqClient @Inject constructor(
    private val httpClient: HttpClient
) : LlmProviderClient {

    override val providerId: String = "groq"

    companion object {
        private const val TAG = PersonaLogTags.LLM
        val VISION_GROQ_MODELS = listOf("qwen/qwen3.8-27b")
        val GROQ_MODELS = listOf(
            "openai/gpt-oss-120b",
            "openai/gpt-oss-20b"
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
            Log.w(TAG, "No Groq API keys configured; skipping Groq model $model")
            return@withContext CallResult.Failed
        }

        val primaryMasked = ReplyGenerationRepositoryImpl.maskKey(effectivePrimary)
        PersonaLogger.i(TAG, "Attempting [Groq] model $model with key $primaryMasked")
        Log.i(TAG, "Attempting [Groq] model $model with key $primaryMasked")

        val primaryResult = executeSingleCall(
            model = model,
            messages = messages,
            maxTokens = maxTokens,
            apiKey = effectivePrimary
        )

        if (primaryResult is CallResult.Success) {
            return@withContext primaryResult
        }

        val isKeyOrServerError = primaryResult is CallResult.RateLimited ||
            primaryResult is CallResult.AuthOrQuotaError ||
            primaryResult is CallResult.ServerError

        if (hasDistinctFallback && isKeyOrServerError) {
            val fallbackMasked = ReplyGenerationRepositoryImpl.maskKey(fallback)
            Log.w(
                TAG,
                "Primary Groq key ($primaryMasked) failed on model $model with $primaryResult. " +
                    "Falling back to GROQ_API_KEY_V2 ($fallbackMasked)."
            )
            PersonaLogger.w(
                TAG,
                "Primary Groq key ($primaryMasked) failed on model $model with $primaryResult -> fallback to GROQ_API_KEY_V2 ($fallbackMasked)"
            )
            PersonaLogger.i(TAG, "Attempting [Groq] model $model with key $fallbackMasked")

            val fallbackResult = executeSingleCall(
                model = model,
                messages = messages,
                maxTokens = maxTokens,
                apiKey = fallback
            )

            if (fallbackResult is CallResult.Success) {
                Log.i(TAG, "Fallback GROQ_API_KEY_V2 succeeded on model $model")
                PersonaLogger.i(TAG, "Fallback GROQ_API_KEY_V2 succeeded on model $model")
                return@withContext fallbackResult
            }

            Log.w(
                TAG,
                "Fallback GROQ_API_KEY_V2 also failed on model $model with $fallbackResult"
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
            Log.w(TAG, "Groq API key is blank; skipping request for model $model")
            return CallResult.Failed
        }

        val keyMasked = ReplyGenerationRepositoryImpl.maskKey(apiKey)
        val hasImage = messages.any { !it.imageUrl.isNullOrBlank() }
        val isVision = model in VISION_GROQ_MODELS || hasImage
        val timeoutMs = if (isVision) 25_000L else 10_000L
        val reasoningEffort = if (isVision) null else "low"

        return try {
            withTimeout(timeoutMs) {
                var response = httpClient.post(BuildConfig.GROQ_BASE_URL) {
                    contentType(ContentType.Application.Json)
                    bearerAuth(apiKey)
                    setBody(
                        GroqRequest(
                            model = model,
                            messages = messages,
                            maxCompletionTokens = maxTokens,
                            reasoningEffort = reasoningEffort
                        )
                    )
                }

                if (response.status.value == 400 && !isVision) {
                    Log.w(TAG, "HTTP 400 on [Groq] $model with reasoning_effort. Retrying without reasoning payload.")
                    response = httpClient.post(BuildConfig.GROQ_BASE_URL) {
                        contentType(ContentType.Application.Json)
                        bearerAuth(apiKey)
                        setBody(
                            GroqRequest(
                                model = model,
                                messages = messages,
                                maxCompletionTokens = maxTokens,
                                reasoningEffort = null
                            )
                        )
                    }
                }

                when {
                    response.status.value == 429 -> {
                        Log.w(TAG, "Rate limited (429) on [Groq] model $model (key $keyMasked)")
                        CallResult.RateLimited
                    }
                    response.status.value in listOf(401, 402, 403) -> {
                        Log.w(TAG, "Auth/quota error (${response.status.value}) on [Groq] model $model (key $keyMasked)")
                        CallResult.AuthOrQuotaError(response.status.value)
                    }
                    response.status.value in 500..599 -> {
                        Log.w(TAG, "Server error (${response.status.value}) on [Groq] model $model (key $keyMasked)")
                        CallResult.ServerError(response.status.value)
                    }
                    response.status.isSuccess() -> {
                        val responseBody = response.body<String>()
                        PersonaLogger.d(PersonaLogTags.LLM, "📥 Raw LLM Output from [Groq] $model: $responseBody")
                        val body = jsonParser.decodeFromString<GroqResponse>(responseBody)
                        val content = body.choices?.firstOrNull()?.message?.content
                        if (content.isNullOrBlank()) {
                            Log.w(TAG, "Empty content in successful response from [Groq] $model (key $keyMasked)")
                            CallResult.Failed
                        } else {
                            try {
                                ReasoningLeakSanitizer.decodeAgenticResponseSafely(content)
                                CallResult.Success(content)
                            } catch (e: Exception) {
                                Log.w(TAG, "Invalid JSON content in response from [Groq] $model (key $keyMasked): $content", e)
                                CallResult.Failed
                            }
                        }
                    }
                    else -> {
                        Log.w(TAG, "HTTP ${response.status.value} error from [Groq] $model (key $keyMasked)")
                        CallResult.Failed
                    }
                }
            }
        } catch (e: TimeoutCancellationException) {
            Log.w(TAG, "Timeout (${timeoutMs}ms) on [Groq] model $model (key $keyMasked)")
            CallResult.Timeout
        } catch (e: Exception) {
            when (e) {
                is java.net.SocketTimeoutException,
                is io.ktor.client.network.sockets.SocketTimeoutException -> {
                    Log.w(TAG, "Socket timeout on [Groq] model $model (key $keyMasked): ${e.message}")
                    CallResult.Timeout
                }
                else -> {
                    Log.e(TAG, "Network error on [Groq] model $model (key $keyMasked)", e)
                    CallResult.Failed
                }
            }
        }
    }

    override fun generateStream(apiKey: String, request: ProviderRequest): Flow<StreamChunk> = flow {
        val trimmedKey = apiKey.trim()
        if (trimmedKey.isBlank()) {
            emit(StreamChunk.Error(IllegalArgumentException("Groq API key is blank"), isTransient = false))
            return@flow
        }

        try {
            val statement = httpClient.preparePost(BuildConfig.GROQ_BASE_URL) {
                contentType(ContentType.Application.Json)
                bearerAuth(trimmedKey)
                setBody(
                    GroqRequest(
                        model = request.model,
                        messages = request.toChatMessages(),
                        maxCompletionTokens = request.maxTokens,
                        reasoningEffort = null
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
                        try {
                            val chunk = jsonParser.decodeFromString<GroqResponse>(data)
                            val text = chunk.choices?.firstOrNull()?.message?.content
                            if (!text.isNullOrEmpty()) {
                                emit(StreamChunk.Text(text))
                            }
                        } catch (_: Exception) {
                            // Heartbeat frame
                        }
                    }
                }
            }
        } catch (e: Exception) {
            emit(StreamChunk.Error(e, isTransient = e is TimeoutCancellationException))
        }
    }.flowOn(Dispatchers.IO)
}
