package com.example.persona.features.auto_reply.data.providers

import android.util.Log
import com.example.persona.core.logging.PersonaLogTags
import com.example.persona.core.logging.PersonaLogger
import com.example.persona.core.network.dto.ChatMessage
import com.example.persona.core.network.dto.OpenRouterRequest
import com.example.persona.core.network.dto.OpenRouterResponse
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

@Singleton
class NaraRouterClient @Inject constructor(
    private val httpClient: HttpClient
) : LlmProviderClient {

    override val providerId: String = "nara"

    companion object {
        private const val TAG = PersonaLogTags.LLM
        const val NARA_BASE_URL = "https://router.bynara.id/v1/chat/completions"
        val NARA_TEXT_MODELS = listOf("laguna-s-2.1")
        private const val MAX_RETRIES = 3

        private val jsonParser = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
    }

    suspend fun call(
        model: String,
        messages: List<ChatMessage>,
        maxTokens: Int,
        apiKey: String
    ): CallResult = withContext(Dispatchers.IO) {
        callWithKeys(model, messages, maxTokens, listOfNotNull(apiKey.trim().takeIf { it.isNotBlank() }))
    }

    suspend fun callWithKeys(
        model: String,
        messages: List<ChatMessage>,
        maxTokens: Int,
        keys: List<String>
    ): CallResult = withContext(Dispatchers.IO) {
        val validKeys = keys.map { it.trim() }.filter { it.isNotBlank() }
        if (validKeys.isEmpty()) {
            Log.w(TAG, "Nara API key is blank; skipping request for model $model")
            return@withContext CallResult.Failed
        }

        var lastResult: CallResult = CallResult.Failed
        for ((index, key) in validKeys.withIndex()) {
            val keyMasked = ReplyGenerationRepositoryImpl.maskKey(key)
            PersonaLogger.i(TAG, "Attempting [Nara Router] model $model with key $keyMasked (key ${index + 1}/${validKeys.size})")

            val result = executeSingleCallWithRetry(model, messages, maxTokens, key)
            if (result is CallResult.Success) {
                return@withContext result
            }

            lastResult = result
            val isRotateWorthy = result is CallResult.RateLimited ||
                result is CallResult.AuthOrQuotaError ||
                result is CallResult.ServerError

            if (isRotateWorthy && index < validKeys.size - 1) {
                Log.w(TAG, "Nara key ($keyMasked) failed with $result. Rotating to next key in pool...")
            }
        }

        lastResult
    }

    private suspend fun executeSingleCallWithRetry(
        model: String,
        messages: List<ChatMessage>,
        maxTokens: Int,
        trimmedKey: String
    ): CallResult {
        var lastResult: CallResult = CallResult.Failed

        for (attempt in 1..MAX_RETRIES) {
            try {
                withTimeout(15_000L) {
                    val response = httpClient.post(NARA_BASE_URL) {
                        contentType(ContentType.Application.Json)
                        bearerAuth(trimmedKey)
                        setBody(
                            OpenRouterRequest(
                                model = model,
                                messages = messages,
                                maxTokens = maxTokens,
                                reasoning = null,
                                stream = false
                            )
                        )
                    }

                    when {
                        response.status.value == 429 -> {
                            Log.w(TAG, "Rate limited (429) on [Nara Router] model $model (attempt $attempt/$MAX_RETRIES)")
                            lastResult = CallResult.RateLimited
                        }
                        response.status.value in listOf(401, 402, 403) -> {
                            Log.w(TAG, "Auth/quota error (${response.status.value}) on [Nara Router] model $model")
                            return@withTimeout CallResult.AuthOrQuotaError(response.status.value)
                        }
                        response.status.value in 500..599 -> {
                            Log.w(TAG, "Server error (${response.status.value}) on [Nara Router] model $model (attempt $attempt/$MAX_RETRIES)")
                            lastResult = CallResult.ServerError(response.status.value)
                        }
                        response.status.isSuccess() -> {
                            val responseBody = response.body<String>()
                            PersonaLogger.d(PersonaLogTags.LLM, "📥 Raw LLM Output from [Nara Router] $model: $responseBody")
                            val body = jsonParser.decodeFromString<OpenRouterResponse>(responseBody)
                            val content = body.choices?.firstOrNull()?.message?.content
                            if (content.isNullOrBlank()) {
                                lastResult = CallResult.Failed
                            } else {
                                return@withTimeout CallResult.Success(content)
                            }
                        }
                        else -> {
                            lastResult = CallResult.Failed
                        }
                    }
                }
            } catch (e: TimeoutCancellationException) {
                Log.w(TAG, "Timeout on [Nara Router] model $model (attempt $attempt/$MAX_RETRIES)")
                lastResult = CallResult.Timeout
            } catch (e: Exception) {
                Log.w(TAG, "Network error on [Nara Router] model $model (attempt $attempt/$MAX_RETRIES): ${e.message}")
                lastResult = CallResult.Failed
            }

            if (lastResult is CallResult.Success || lastResult is CallResult.AuthOrQuotaError) {
                return lastResult
            }

            // Exponential jittered backoff before retry on transient server errors or rate limits
            if (attempt < MAX_RETRIES && (lastResult is CallResult.ServerError || lastResult is CallResult.RateLimited || lastResult is CallResult.Timeout)) {
                val jitterMs = Random.nextLong(100L, 300L)
                val backoffMs = (500L * (1 shl (attempt - 1))) + jitterMs
                Log.d(TAG, "[Nara Router] Retrying in ${backoffMs}ms...")
                delay(backoffMs)
            }
        }

        return lastResult
    }

    override fun generateStream(apiKey: String, request: ProviderRequest): Flow<StreamChunk> = flow {
        val trimmedKey = apiKey.trim()
        if (trimmedKey.isBlank()) {
            emit(StreamChunk.Error(IllegalArgumentException("Nara API key is blank"), isTransient = false))
            return@flow
        }

        try {
            val statement = httpClient.preparePost(NARA_BASE_URL) {
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
