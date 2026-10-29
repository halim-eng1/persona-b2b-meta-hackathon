package com.example.persona.features.auto_reply.data.providers

import android.util.Log
import com.example.persona.BuildConfig
import com.example.persona.core.logging.PersonaLogTags
import com.example.persona.core.logging.PersonaLogger
import com.example.persona.core.network.dto.ChatMessage
import com.example.persona.core.network.dto.NvidiaRequest
import com.example.persona.core.network.dto.NvidiaResponse
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
class NvidiaNimClient @Inject constructor(
    private val httpClient: HttpClient
) : LlmProviderClient {

    override val providerId: String = "nvidia"

    companion object {
        private const val TAG = PersonaLogTags.LLM
        val VISION_NVIDIA_MODELS = listOf(
            "meta/llama-3.2-11b-vision-instruct",
            "nvidia/nemotron-3.5-super-vl-120b-a12b"
        )
        val NVIDIA_TEXT_MODELS = listOf(
            "nvidia/nemotron-3-ultra-550b-a55b",
            "nvidia/nemotron-3.5-bolt-e4b"
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
        apiKey: String
    ): CallResult = withContext(Dispatchers.IO) {
        val trimmedKey = apiKey.trim()
        if (trimmedKey.isBlank()) {
            Log.w(TAG, "NVIDIA API key is blank; skipping request for model $model")
            return@withContext CallResult.Failed
        }

        val keyMasked = ReplyGenerationRepositoryImpl.maskKey(trimmedKey)
        val hasImage = messages.any { !it.imageUrl.isNullOrBlank() }
        val isVision = model in VISION_NVIDIA_MODELS || hasImage
        val timeoutMs = if (isVision) 25_000L else 10_000L
        val effectiveMaxTokens = if (model.contains("super-vl")) maxOf(maxTokens, 1500) else maxTokens
        val reasoningEffort = if (model.contains("ultra-550b")) "none" else null
        val temperature = if (model.contains("ultra-550b")) 1.0f else 0.5f
        PersonaLogger.i(TAG, "Attempting [NVIDIA NIM] model $model with key $keyMasked (max_tokens=$effectiveMaxTokens, timeout=${timeoutMs}ms)")
        Log.i(TAG, "Attempting [NVIDIA NIM] model $model with key $keyMasked (max_tokens=$effectiveMaxTokens, timeout=${timeoutMs}ms)")

        try {
            withTimeout(timeoutMs) {
                val response = httpClient.post(BuildConfig.NVIDIA_BASE_URL) {
                    contentType(ContentType.Application.Json)
                    bearerAuth(trimmedKey)
                    setBody(
                        NvidiaRequest(
                            model = model,
                            messages = messages,
                            temperature = temperature,
                            maxTokens = effectiveMaxTokens,
                            reasoningEffort = reasoningEffort
                        )
                    )
                }

                when {
                    response.status.value == 429 -> {
                        Log.w(TAG, "Rate limited (429) on [NVIDIA NIM] model $model (key $keyMasked)")
                        CallResult.RateLimited
                    }
                    response.status.value in listOf(401, 402, 403) -> {
                        Log.w(TAG, "Auth/quota error (${response.status.value}) on [NVIDIA NIM] model $model (key $keyMasked)")
                        CallResult.AuthOrQuotaError(response.status.value)
                    }
                    response.status.value in 500..599 -> {
                        Log.w(TAG, "Server error (${response.status.value}) on [NVIDIA NIM] model $model (key $keyMasked)")
                        CallResult.ServerError(response.status.value)
                    }
                    response.status.isSuccess() -> {
                        val responseBody = response.body<String>()
                        PersonaLogger.d(PersonaLogTags.LLM, "📥 Raw LLM Output from [NVIDIA NIM] $model: $responseBody")
                        val body = jsonParser.decodeFromString<NvidiaResponse>(responseBody)
                        val content = body.choices?.firstOrNull()?.message?.content
                        if (content.isNullOrBlank()) {
                            Log.w(TAG, "Empty content in successful response from [NVIDIA NIM] $model (key $keyMasked)")
                            CallResult.Failed
                        } else {
                            try {
                                ReasoningLeakSanitizer.decodeAgenticResponseSafely(content)
                                CallResult.Success(content)
                            } catch (e: Exception) {
                                Log.w(TAG, "Invalid JSON content in response from [NVIDIA NIM] $model (key $keyMasked): $content", e)
                                CallResult.Failed
                            }
                        }
                    }
                    else -> {
                        Log.w(TAG, "HTTP ${response.status.value} error from [NVIDIA NIM] $model (key $keyMasked)")
                        CallResult.Failed
                    }
                }
            }
        } catch (e: TimeoutCancellationException) {
            Log.w(TAG, "Timeout (${timeoutMs}ms) on [NVIDIA NIM] model $model (key $keyMasked)")
            CallResult.Timeout
        } catch (e: Exception) {
            when (e) {
                is java.net.SocketTimeoutException,
                is io.ktor.client.network.sockets.SocketTimeoutException -> {
                    Log.w(TAG, "Socket timeout on [NVIDIA NIM] model $model (key $keyMasked): ${e.message}")
                    CallResult.Timeout
                }
                else -> {
                    Log.e(TAG, "Network error on [NVIDIA NIM] model $model (key $keyMasked)", e)
                    CallResult.Failed
                }
            }
        }
    }

    override fun generateStream(apiKey: String, request: ProviderRequest): Flow<StreamChunk> = flow {
        val trimmedKey = apiKey.trim()
        if (trimmedKey.isBlank()) {
            emit(StreamChunk.Error(IllegalArgumentException("NVIDIA API key is blank"), isTransient = false))
            return@flow
        }

        val effectiveMaxTokens = if (request.model.contains("super-vl")) maxOf(request.maxTokens, 1500) else request.maxTokens
        val reasoningEffort = if (request.model.contains("ultra-550b")) "none" else null
        val temperature = if (request.model.contains("ultra-550b")) 1.0f else request.temperature

        try {
            val statement = httpClient.preparePost(BuildConfig.NVIDIA_BASE_URL) {
                contentType(ContentType.Application.Json)
                bearerAuth(trimmedKey)
                setBody(
                    NvidiaRequest(
                        model = request.model,
                        messages = request.toChatMessages(),
                        temperature = temperature,
                        maxTokens = effectiveMaxTokens,
                        reasoningEffort = reasoningEffort
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
                var insideThinkTag = false
                val buffer = StringBuilder()

                while (!channel.isClosedForRead) {
                    val line = channel.readUTF8Line() ?: break
                    if (line.startsWith("data: ")) {
                        val data = line.removePrefix("data: ").trim()
                        if (data == "[DONE]") {
                            emit(StreamChunk.Completed())
                            break
                        }
                        try {
                            val chunk = jsonParser.decodeFromString<NvidiaResponse>(data)
                            val rawContent = chunk.choices?.firstOrNull()?.message?.content
                                ?: chunk.choices?.firstOrNull()?.message?.reasoningContent
                            if (!rawContent.isNullOrEmpty()) {
                                buffer.append(rawContent)
                                val currentStr = buffer.toString()
                                if (!insideThinkTag && currentStr.contains("<think>")) {
                                    insideThinkTag = true
                                    val beforeThink = currentStr.substringBefore("<think>")
                                    if (beforeThink.isNotEmpty()) emit(StreamChunk.Text(beforeThink))
                                    buffer.clear()
                                    buffer.append(currentStr.substringAfter("<think>"))
                                } else if (insideThinkTag && currentStr.contains("</think>")) {
                                    insideThinkTag = false
                                    val thought = currentStr.substringBefore("</think>")
                                    if (thought.isNotEmpty()) emit(StreamChunk.Reasoning(thought))
                                    val afterThink = currentStr.substringAfter("</think>")
                                    buffer.clear()
                                    if (afterThink.isNotEmpty()) emit(StreamChunk.Text(afterThink))
                                } else {
                                    if (insideThinkTag) {
                                        emit(StreamChunk.Reasoning(rawContent))
                                        buffer.clear()
                                    } else {
                                        emit(StreamChunk.Text(rawContent))
                                        buffer.clear()
                                    }
                                }
                            }
                        } catch (_: Exception) {
                            // Heartbeat or malformed frame
                        }
                    }
                }
            }
        } catch (e: Exception) {
            emit(StreamChunk.Error(e, isTransient = e is TimeoutCancellationException))
        }
    }.flowOn(Dispatchers.IO)
}
