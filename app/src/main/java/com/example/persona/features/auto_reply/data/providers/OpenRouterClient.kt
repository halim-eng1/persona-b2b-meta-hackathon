package com.example.persona.features.auto_reply.data.providers

import android.util.Log
import com.example.persona.BuildConfig
import com.example.persona.core.logging.PersonaLogTags
import com.example.persona.core.logging.PersonaLogger
import com.example.persona.core.network.dto.ChatMessage
import com.example.persona.core.network.dto.OpenRouterRequest
import com.example.persona.core.network.dto.OpenRouterResponse
import com.example.persona.core.network.dto.ReasoningConfig
import com.example.persona.features.auto_reply.data.repository.ReplyGenerationRepositoryImpl
import com.example.persona.features.auto_reply.data.repository.ReplyGenerationRepositoryImpl.CallResult
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
class OpenRouterClient @Inject constructor(
    private val httpClient: HttpClient
) : LlmProviderClient {

    override val providerId: String = "openrouter"

    companion object {
        private const val TAG = PersonaLogTags.LLM
        val VISION_OPENROUTER_MODELS = listOf("nvidia/nemotron-3.5-super-vl:free")
        val OPENROUTER_FAILOVER_MODELS = listOf(
            "nvidia/nemotron-3-ultra-550b-a55b:free",
            "liquid/lfm-2.5-2.6b:free",
            "openrouter/free"
        )
        val REASONING_MODELS = setOf(
            "nvidia/nemotron-3-ultra-550b-a55b:free",
            "liquid/lfm-2.5-2.6b:free",
            "cohere/north-mini-code:free"
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
        val keys = listOfNotNull(
            primaryApiKey.trim().takeIf { it.isNotBlank() },
            fallbackApiKey.trim().takeIf { it.isNotBlank() && it != primaryApiKey.trim() }
        )
        callWithKeys(model, messages, maxTokens, keys)
    }

    suspend fun callWithKeys(
        model: String,
        messages: List<ChatMessage>,
        maxTokens: Int,
        keys: List<String>
    ): CallResult = withContext(Dispatchers.IO) {
        val validKeys = keys.map { it.trim() }.filter { it.isNotBlank() }
        if (validKeys.isEmpty()) {
            Log.w(TAG, "No OpenRouter API keys configured; skipping model $model")
            return@withContext CallResult.Failed
        }

        var lastResult: CallResult = CallResult.Failed
        for ((index, key) in validKeys.withIndex()) {
            val keyMasked = ReplyGenerationRepositoryImpl.maskKey(key)
            PersonaLogger.i(TAG, "Attempting [OpenRouter] model $model with key $keyMasked (key ${index + 1}/${validKeys.size})")

            val result = executeSingleCall(
                model = model,
                messages = messages,
                maxTokens = maxTokens,
                apiKey = key
            )

            if (result is CallResult.Success) {
                return@withContext result
            }

            lastResult = result
            val isKeyOrQuotaError = result is CallResult.RateLimited || result is CallResult.AuthOrQuotaError
            if (!isKeyOrQuotaError) {
                return@withContext result
            }

            if (index < validKeys.size - 1) {
                Log.w(TAG, "OpenRouter key ($keyMasked) failed with $result. Rotating to next key in pool...")
            }
        }

        lastResult
    }

        internal suspend fun executeSingleCall(
        model: String,
        messages: List<ChatMessage>,
        maxTokens: Int,
        apiKey: String
    ): CallResult = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            Log.w(TAG, "API key is blank; skipping request for model $model")
            return@withContext CallResult.Failed
        }

        val keyMasked = ReplyGenerationRepositoryImpl.maskKey(apiKey)
        val hasImage = messages.any { !it.imageUrl.isNullOrBlank() }
        val isVision = model in VISION_OPENROUTER_MODELS || hasImage
        val timeoutMs = if (isVision) 25_000L else 10_000L
        val effectiveMaxTokens = if (model.contains("super-vl")) maxOf(maxTokens, 1500) else maxTokens

        try {
            withTimeout(timeoutMs) {
                val reasoning = if (model in REASONING_MODELS && !hasImage) ReasoningConfig(effort = "low") else null
                var response = httpClient.post(BuildConfig.OPENROUTER_BASE_URL) {
                    contentType(ContentType.Application.Json)
                    bearerAuth(apiKey)
                    setBody(
                        OpenRouterRequest(
                            model = model,
                            messages = messages,
                            maxTokens = effectiveMaxTokens,
                            reasoning = reasoning,
                            stream = false
                        )
                    )
                }

                if (response.status.value == 400 && reasoning != null) {
                    Log.w(TAG, "HTTP 400 on $model with reasoning object. Retrying without reasoning payload.")
                    response = httpClient.post(BuildConfig.OPENROUTER_BASE_URL) {
                        contentType(ContentType.Application.Json)
                        bearerAuth(apiKey)
                        setBody(
                            OpenRouterRequest(
                                model = model,
                                messages = messages,
                                maxTokens = effectiveMaxTokens,
                                reasoning = null,
                                stream = false
                            )
                        )
                    }
                }

                when {
                    response.status.value == 429 -> {
                        Log.w(TAG, "Rate limited (429) on model $model (key $keyMasked)")
                        CallResult.RateLimited
                    }
                    response.status.value in listOf(401, 402, 403) -> {
                        Log.w(TAG, "Auth/quota error (${response.status.value}) on model $model (key $keyMasked)")
                        CallResult.AuthOrQuotaError(response.status.value)
                    }
                    response.status.value in 500..599 -> {
                        Log.w(TAG, "Server error (${response.status.value}) on model $model (key $keyMasked)")
                        CallResult.ServerError(response.status.value)
                    }
                    response.status.isSuccess() -> {
                        val responseBody = response.body<String>()
                        PersonaLogger.d(PersonaLogTags.LLM, "📥 Raw LLM Output from $model: $responseBody")
                        val body = jsonParser.decodeFromString<OpenRouterResponse>(responseBody)
                        val content = body.choices?.firstOrNull()?.message?.content
                        if (content.isNullOrBlank()) {
                            Log.w(TAG, "Empty content in successful response from $model (key $keyMasked)")
                            CallResult.Failed
                        } else {
                            CallResult.Success(content)
                        }
                    }
                    else -> {
                        Log.w(TAG, "HTTP ${response.status.value} error from $model (key $keyMasked)")
                        CallResult.Failed
                    }
                }
            }
        } catch (e: TimeoutCancellationException) {
            Log.w(TAG, "Timeout (${timeoutMs}ms) on model $model (key $keyMasked)")
            CallResult.Timeout
        } catch (e: Exception) {
            when (e) {
                is java.net.SocketTimeoutException,
                is io.ktor.client.network.sockets.SocketTimeoutException -> {
                    Log.w(TAG, "Socket timeout on model $model (key $keyMasked): ${e.message}")
                    CallResult.Timeout
                }
                else -> {
                    Log.e(TAG, "Network error on model $model (key $keyMasked)", e)
                    CallResult.Failed
                }
            }
        }

    }
    override fun generateStream(apiKey: String, request: ProviderRequest): Flow<StreamChunk> = flow {
        val trimmedKey = apiKey.trim()
        if (trimmedKey.isBlank()) {
            emit(StreamChunk.Error(IllegalArgumentException("OpenRouter API key is blank"), isTransient = false))
            return@flow
        }

        try {
            val statement = httpClient.preparePost(BuildConfig.OPENROUTER_BASE_URL) {
                contentType(ContentType.Application.Json)
                bearerAuth(trimmedKey)
                setBody(
                    OpenRouterRequest(
                        model = request.model,
                        messages = request.toChatMessages(),
                        maxTokens = request.maxTokens,
                        reasoning = null,
                        stream = true
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
                            val chunk = jsonParser.decodeFromString<OpenRouterResponse>(data)
                            val choice = chunk.choices?.firstOrNull()
                            val text = choice?.delta?.content ?: choice?.message?.content
                            if (!text.isNullOrEmpty()) {
                                emit(StreamChunk.Text(text))
                            }
                        } catch (_: Exception) {
                            // Ignore heartbeat frame
                        }
                    }
                }
            }
        } catch (e: Exception) {
            emit(StreamChunk.Error(e, isTransient = e is TimeoutCancellationException))
        }
    }.flowOn(Dispatchers.IO)
}
