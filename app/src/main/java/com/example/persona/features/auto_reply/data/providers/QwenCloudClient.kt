package com.example.persona.features.auto_reply.data.providers

import android.util.Log
import com.example.persona.BuildConfig
import com.example.persona.core.logging.PersonaLogTags
import com.example.persona.core.logging.PersonaLogger
import com.example.persona.core.network.dto.ChatMessage
import com.example.persona.core.network.dto.QwenChatRequest
import com.example.persona.core.network.dto.QwenChatResponse
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
class QwenCloudClient @Inject constructor(
    private val httpClient: HttpClient
) : LlmProviderClient {

    override val providerId: String = "qwen"

    companion object {
        private const val TAG = PersonaLogTags.LLM
        const val MODEL_TIMEOUT_MS = 10_000L
        const val VISION_MODEL_TIMEOUT_MS = 25_000L
        const val QWEN_TTFT_TIMEOUT_MS = 5_000L
        const val QWEN_STREAM_TIMEOUT_MS = 25_000L

        val VISION_QWEN_MODELS = listOf(
            "qwen3.8-omni-flash",
            "qwen3.8-flash"
        )

        val REASONING_QWEN_MODELS = listOf(
            "deepseek-v4.1-flash",
            "deepseek-v4-pro-0813",
            "qwen3.8-flash",
            "qwen3.8-max"
        )

        /**
         * MANDATORY ARCHITECTURAL OVERRIDE:
         * Strictly FORBIDDEN to reduce experimental Qwen models!
         * All 10 default Qwen models MUST remain.
         */
        val QWEN_TEXT_MODELS = listOf(
            "deepseek-v4.1-flash",
            "qwen3.8-max",
            "deepseek-v4-pro-0813",
            "qwen3.8-flash",
            "qwen3.7-plus",
            "qwen3.7-flash",
            "qwen3.6-flash",
            "qwen3.5-flash",
            "qwen3-coder-flash",
            "qwen3.6-27b"
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
            Log.w(TAG, "Qwen API key is blank; skipping request for model $model")
            return@withContext CallResult.Failed
        }

        var lastResult: CallResult = CallResult.Failed
        for ((index, key) in validKeys.withIndex()) {
            val keyMasked = ReplyGenerationRepositoryImpl.maskKey(key)
            PersonaLogger.i(TAG, "Attempting [QwenCloud] model $model with key $keyMasked (key ${index + 1}/${validKeys.size})")
            Log.i(TAG, "Attempting [QwenCloud] model $model with key $keyMasked (key ${index + 1}/${validKeys.size})")

            val result = executeSingleCall(model, messages, maxTokens, key)
            if (result is CallResult.Success) {
                return@withContext result
            }

            lastResult = result
            val isRotateWorthy = result is CallResult.RateLimited ||
                result is CallResult.AuthOrQuotaError ||
                result is CallResult.ServerError

            if (isRotateWorthy && index < validKeys.size - 1) {
                Log.w(TAG, "Qwen key ($keyMasked) failed with $result. Rotating to next key in pool...")
            }
        }

        lastResult
    }

        internal suspend fun executeSingleCall(
        model: String,
        messages: List<ChatMessage>,
        maxTokens: Int,
        trimmedKey: String
    ): CallResult = withContext(Dispatchers.IO) {
        val keyMasked = ReplyGenerationRepositoryImpl.maskKey(trimmedKey)
        val hasImage = messages.any { !it.imageUrl.isNullOrBlank() }
        val isVision = model in VISION_QWEN_MODELS || hasImage
        val timeoutMs = if (isVision) VISION_MODEL_TIMEOUT_MS else MODEL_TIMEOUT_MS
        val effectiveMaxTokens = if (isVision) maxOf(maxTokens, 1500) else maxOf(maxTokens, 1200)

        val isReasoningModel = model in REASONING_QWEN_MODELS ||
            model.contains("deepseek", ignoreCase = true) ||
            model.contains("qwen3.8", ignoreCase = true) ||
            model.contains("reason", ignoreCase = true)

        try {
            withTimeout(timeoutMs) {
                val response = httpClient.post(BuildConfig.QWEN_BASE_URL) {
                    contentType(ContentType.Application.Json)
                    bearerAuth(trimmedKey)
                    setBody(
                        QwenChatRequest(
                            model = model,
                            messages = messages,
                            temperature = 0.5f,
                            maxTokens = effectiveMaxTokens,
                            stream = false,
                            enableThinking = if (isReasoningModel) false else null
                        )
                    )
                }

                when {
                    response.status.value == 429 -> {
                        PersonaLogger.w(TAG, "Rate limited (429) on [QwenCloud] model $model (key $keyMasked)")
                        Log.w(TAG, "Rate limited (429) on [QwenCloud] model $model (key $keyMasked)")
                        CallResult.RateLimited
                    }
                    response.status.value in listOf(401, 402, 403) -> {
                        PersonaLogger.w(TAG, "Auth/quota error (${response.status.value}) on [QwenCloud] model $model (key $keyMasked)")
                        Log.w(TAG, "Auth/quota error (${response.status.value}) on [QwenCloud] model $model (key $keyMasked)")
                        CallResult.AuthOrQuotaError(response.status.value)
                    }
                    response.status.value in 500..599 -> {
                        Log.w(TAG, "Server error (${response.status.value}) on [QwenCloud] model $model (key $keyMasked)")
                        CallResult.ServerError(response.status.value)
                    }
                    response.status.isSuccess() -> {
                        val responseBody = response.body<String>()
                        PersonaLogger.d(PersonaLogTags.LLM, "📥 Raw LLM Output from [QwenCloud] $model: $responseBody")
                        val body = jsonParser.decodeFromString<QwenChatResponse>(responseBody)
                        val content = body.choices?.firstOrNull()?.message?.content
                        if (content.isNullOrBlank()) {
                            Log.w(TAG, "Empty content in successful response from [QwenCloud] $model (key $keyMasked)")
                            CallResult.Failed
                        } else {
                            try {
                                ReasoningLeakSanitizer.decodeAgenticResponseSafely(content)
                                val pTokens = body.usage?.effectivePromptTokens ?: 0
                                val cTokens = body.usage?.effectiveCompletionTokens ?: 0
                                val respModel = body.model?.takeIf { it.isNotBlank() } ?: model
                                CallResult.Success(
                                    content = content,
                                    promptTokens = pTokens,
                                    completionTokens = cTokens,
                                    actualModel = respModel
                                )
                            } catch (e: Exception) {
                                Log.w(TAG, "Invalid JSON content in response from [QwenCloud] $model (key $keyMasked): $content", e)
                                CallResult.Failed
                            }
                        }
                    }
                    else -> {
                        Log.w(TAG, "HTTP ${response.status.value} error from [QwenCloud] $model (key $keyMasked)")
                        CallResult.Failed
                    }
                }
            }
        } catch (e: TimeoutCancellationException) {
            Log.w(TAG, "Timeout (${timeoutMs}ms) on [QwenCloud] model $model (key $keyMasked)")
            CallResult.Timeout
        } catch (e: Exception) {
            when (e) {
                is java.net.SocketTimeoutException,
                is io.ktor.client.network.sockets.SocketTimeoutException -> {
                    Log.w(TAG, "Socket timeout on [QwenCloud] model $model (key $keyMasked): ${e.message}")
                    CallResult.Timeout
                }
                else -> {
                    Log.e(TAG, "Network error on [QwenCloud] model $model (key $keyMasked)", e)
                    CallResult.Failed
                }
            }
        }

    }
    override fun generateStream(apiKey: String, request: ProviderRequest): Flow<StreamChunk> = flow {
        val trimmedKey = apiKey.trim()
        if (trimmedKey.isBlank()) {
            emit(StreamChunk.Error(IllegalArgumentException("Qwen API key is blank"), isTransient = false))
            return@flow
        }

        try {
            // 2-Phase timeout: Phase 1 (TTFT connection <= 5s)
            val statement = httpClient.preparePost(BuildConfig.QWEN_BASE_URL) {
                contentType(ContentType.Application.Json)
                bearerAuth(trimmedKey)
                setBody(
                    QwenChatRequest(
                        model = request.model,
                        messages = request.toChatMessages(),
                        temperature = request.temperature,
                        maxTokens = request.maxTokens,
                        stream = true
                    )
                )
            }

            withTimeout(QWEN_TTFT_TIMEOUT_MS) {
                statement.execute { response ->
                    if (!response.status.isSuccess()) {
                        val isTransient = response.status.value == 429 || response.status.value in 500..599
                        emit(StreamChunk.Error(IllegalStateException("HTTP ${response.status.value}"), isTransient))
                        return@execute
                    }

                    val channel = response.bodyAsChannel()
                    // Phase 2: Streaming duration timeout
                    withTimeout(QWEN_STREAM_TIMEOUT_MS) {
                        while (!channel.isClosedForRead) {
                            val line = channel.readUTF8Line() ?: break
                            if (line.startsWith("data: ")) {
                                val data = line.removePrefix("data: ").trim()
                                if (data == "[DONE]") {
                                    emit(StreamChunk.Completed())
                                    break
                                }
                                try {
                                    val chunk = jsonParser.decodeFromString<QwenChatResponse>(data)
                                    val delta = chunk.choices?.firstOrNull()?.delta
                                    delta?.reasoningContent?.let { reasoning ->
                                        if (reasoning.isNotEmpty()) emit(StreamChunk.Reasoning(reasoning))
                                    }
                                    delta?.content?.let { text ->
                                        if (text.isNotEmpty()) emit(StreamChunk.Text(text))
                                    }
                                } catch (_: Exception) {
                                    // Skip unparseable heartbeat frames
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            emit(StreamChunk.Error(e, isTransient = e is TimeoutCancellationException))
        }
    }.flowOn(Dispatchers.IO)
}
