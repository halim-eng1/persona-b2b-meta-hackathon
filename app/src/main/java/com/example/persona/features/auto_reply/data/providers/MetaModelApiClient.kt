package com.example.persona.features.auto_reply.data.providers

import android.util.Base64
import android.util.Log
import com.example.persona.BuildConfig
import com.example.persona.core.logging.PersonaLogTags
import com.example.persona.core.logging.PersonaLogger
import com.example.persona.core.network.dto.ChatMessage
import com.example.persona.features.auto_reply.data.media.AudioWavConverter
import com.example.persona.features.auto_reply.data.repository.ReplyGenerationRepositoryImpl
import com.example.persona.features.auto_reply.data.repository.ReplyGenerationRepositoryImpl.CallResult
import com.example.persona.features.auto_reply.domain.orchestrator.ReasoningLeakSanitizer
import com.example.persona.features.settings.domain.repository.ModelProviders
import com.example.persona.features.settings.domain.repository.SecureCredentialsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import java.io.File
import java.io.IOException
import java.net.SocketTimeoutException
import javax.inject.Inject
import javax.inject.Singleton

class AuthOrQuotaException(val code: Int, message: String) : IOException(message)
class RateLimitException(message: String) : IOException(message)
class ServerException(val code: Int, message: String) : IOException(message)
class ApiTimeoutException(message: String) : IOException(message)

/**
 * Streaming multipart body for Meta's ASR endpoint.
 *
 * OkHttp's [okhttp3.MultipartBody] adds a per-part `Content-Length` header to every
 * part whose body has a known length. Meta's `voyager/transcribe` gateway cannot parse
 * such bodies and answers with HTTP 400 `Malformed multipart body` (verified live:
 * identical requests without per-part Content-Length return 200, any part carrying it
 * returns 400 — auth-blind, before key validation). This body emits the exact
 * gateway-friendly wire format instead:
 *
 * ```
 * --<boundary>                                            (unquoted boundary in Content-Type)
 * Content-Disposition: form-data; name="request"          (no filename, no Content-Length)
 * Content-Type: application/json; charset=utf-8
 * <requestJson>
 * --<boundary>
 * Content-Disposition: form-data; name="audio"; filename="<wav>"
 * Content-Type: audio/wav
 * <raw wav bytes>
 * --<boundary>--
 * ```
 */
private class AsrMultipartRequestBody(
    requestJson: String,
    private val audioFile: File
) : RequestBody() {

    private val boundary: String =
        "persona-asr-" + java.util.UUID.randomUUID().toString().replace("-", "")

    private val preamble: ByteArray = (
        "--$boundary\r\n" +
            "Content-Disposition: form-data; name=\"request\"\r\n" +
            "Content-Type: application/json; charset=utf-8\r\n" +
            "\r\n" +
            requestJson + "\r\n" +
            "--$boundary\r\n" +
            "Content-Disposition: form-data; name=\"audio\"; filename=\"${audioFile.name}\"\r\n" +
            "Content-Type: audio/wav\r\n" +
            "\r\n"
        ).toByteArray(Charsets.UTF_8)

    private val epilogue: ByteArray =
        "\r\n--$boundary--\r\n".toByteArray(Charsets.UTF_8)

    override fun contentType(): MediaType? =
        "multipart/form-data; boundary=$boundary".toMediaType()

    override fun contentLength(): Long =
        preamble.size.toLong() + audioFile.length() + epilogue.size.toLong()

    override fun writeTo(sink: BufferedSink) {
        sink.write(preamble)
        audioFile.inputStream().use { input ->
            sink.outputStream().use { output ->
                input.copyTo(output, DEFAULT_STREAM_BUFFER_SIZE)
            }
        }
        sink.write(epilogue)
    }

    private companion object {
        const val DEFAULT_STREAM_BUFFER_SIZE = 32 * 1024
    }
}

@Singleton
class MetaModelApiClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val secureCredentialsRepository: SecureCredentialsRepository
) {

    companion object {
        private const val TAG = PersonaLogTags.LLM

        const val MODEL_MUSE_SPARK_1_3_CONTRIBUTOR = "muse-spark-1.3-contributor"
        const val MODEL_MUSE_SPARK_1_3 = "muse-spark-1.3"
        const val MODEL_MUSE_SPARK_1_2_CONTRIBUTOR = "muse-spark-1.2-contributor"
        const val MODEL_MUSE_SPARK_1_2 = "muse-spark-1.2"
        const val MODEL_MUSE_VOICE_TRANSCRIBE_1_0 = "muse-voice-transcribe-1.0"

        val DEFAULT_MODEL = MODEL_MUSE_SPARK_1_3_CONTRIBUTOR
        val DEFAULT_ASR_MODEL = MODEL_MUSE_VOICE_TRANSCRIBE_1_0

        val META_TEXT_MODELS = listOf(
            MODEL_MUSE_SPARK_1_3_CONTRIBUTOR,
            MODEL_MUSE_SPARK_1_3,
            MODEL_MUSE_SPARK_1_2_CONTRIBUTOR,
            MODEL_MUSE_SPARK_1_2
        )

        val META_VISION_MODELS = listOf(
            MODEL_MUSE_SPARK_1_3_CONTRIBUTOR,
            MODEL_MUSE_SPARK_1_3,
            MODEL_MUSE_SPARK_1_2_CONTRIBUTOR,
            MODEL_MUSE_SPARK_1_2
        )

        /** Resolves the transparent fallback counterpart model between standard and contributor variants. */
        fun getCounterpartModel(model: String): String? {
            val trimmed = model.trim()
            return when {
                trimmed.endsWith("-contributor") -> trimmed.removeSuffix("-contributor")
                trimmed.startsWith("muse-spark") -> "$trimmed-contributor"
                else -> null
            }
        }

        private val jsonParser = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    private val baseUrl: String
        get() = runCatching { BuildConfig.META_BASE_URL.trim().removeSuffix("/") }
            .getOrNull()?.takeIf { it.isNotBlank() } ?: "https://api.meta.ai/v1"

    fun resolveApiKey(): String {
        return secureCredentialsRepository.getApiKeys(ModelProviders.META)
            .firstOrNull()?.trim()?.takeIf { it.isNotBlank() }
            ?: runCatching { BuildConfig.META_API_KEY }.getOrNull()?.trim() ?: ""
    }

    /**
     * Executes single chat completion request with [reasoningEffort] = "low" to maintain
     * sub-second conversational latency. Automatically strips reasoning / <think> tags.
     */
    suspend fun generateChatCompletion(
        model: String = DEFAULT_MODEL,
        systemPrompt: String,
        userPrompt: String,
        temperature: Double = 0.7,
        reasoningEffort: String = "low"
    ): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = resolveApiKey()
        if (apiKey.isBlank()) {
            return@withContext Result.failure(AuthOrQuotaException(401, "No Meta API key configured"))
        }

        val messages = listOf(
            ChatMessage(role = "system", content = systemPrompt),
            ChatMessage(role = "user", content = userPrompt)
        )

        val targetMaxTokens = if (model.contains("muse-spark") || model.contains("reasoning")) 2000 else 1500
        when (val result = executeSingleCall(model, messages, maxTokens = targetMaxTokens, apiKey = apiKey, reasoningEffort = reasoningEffort, temperature = temperature)) {
            is CallResult.Success -> Result.success(result.content)
            is CallResult.AuthOrQuotaError -> Result.failure(AuthOrQuotaException(result.code, "Meta API Auth/Quota Error ${result.code}"))
            is CallResult.RateLimited -> Result.failure(RateLimitException("Meta API Rate Limited (429)"))
            is CallResult.ServerError -> Result.failure(ServerException(result.code, "Meta API Server Error ${result.code}"))
            is CallResult.Timeout -> Result.failure(ApiTimeoutException("Meta API Request Timed Out"))
            else -> Result.failure(IOException("Meta API request failed"))
        }
    }

    /**
     * Executes multimodal vision chat completion passing raw image bytes encoded in base64.
     */
    suspend fun generateVisionCompletion(
        model: String = DEFAULT_MODEL,
        systemPrompt: String,
        imageBytes: ByteArray,
        mimeType: String = "image/jpeg"
    ): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = resolveApiKey()
        if (apiKey.isBlank()) {
            return@withContext Result.failure(AuthOrQuotaException(401, "No Meta API key configured"))
        }

        val base64Image = Base64.encodeToString(imageBytes, Base64.NO_WRAP)
        val dataUri = "data:$mimeType;base64,$base64Image"

        val messages = listOf(
            ChatMessage(role = "system", content = systemPrompt),
            ChatMessage(role = "user", content = "استخرج تفاصيل المنتج أو الإيصال من الصورة", imageUrl = dataUri)
        )

        when (val result = executeSingleCall(model, messages, maxTokens = 1500, apiKey = apiKey, reasoningEffort = "low")) {
            is CallResult.Success -> Result.success(result.content)
            is CallResult.AuthOrQuotaError -> Result.failure(AuthOrQuotaException(result.code, "Meta API Auth/Quota Error ${result.code}"))
            is CallResult.RateLimited -> Result.failure(RateLimitException("Meta API Rate Limited (429)"))
            is CallResult.ServerError -> Result.failure(ServerException(result.code, "Meta API Server Error ${result.code}"))
            is CallResult.Timeout -> Result.failure(ApiTimeoutException("Meta API Request Timed Out"))
            else -> Result.failure(IOException("Meta API vision request failed"))
        }
    }

    /**
     * Transcribes audio using Meta's `muse-voice-transcribe-1.0` model at `POST /v1/asr/transcribe`.
     * Automatically converts incoming audio to 16kHz mono WAV if necessary.
     */
    suspend fun transcribeAudio(
        audioFile: File,
        language: String = "ar"
    ): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = resolveApiKey()
        if (apiKey.isBlank()) {
            return@withContext Result.failure(AuthOrQuotaException(401, "No Meta API key configured for audio transcription"))
        }

        if (!audioFile.exists() || audioFile.length() == 0L) {
            return@withContext Result.failure(IOException("Audio file does not exist or is empty: ${audioFile.absolutePath}"))
        }

        // Reuse audio conversion / WAV utilities to ensure 16kHz mono PCM WAV format
        val wavFile = AudioWavConverter.convertTo16kHzMonoWav(audioFile)
        val isTempWav = (wavFile != audioFile)

        try {
            val url = "$baseUrl/asr/transcribe"
            val requestJson = buildJsonObject {
                put("model", DEFAULT_ASR_MODEL)
                put("audioEncoding", "WAV")
                put("mode", "PUSH_TO_TALK")
                if (language.isNotBlank()) {
                    put("language", language)
                }
            }.toString()

            // Meta ASR expects two distinct multipart parts:
            //  - `request`: JSON config sent as a plain form field (no filename)
            //  - `audio`:   raw 16kHz mono WAV bytes with a real filename
            // IMPORTANT: Meta's gateway rejects the well-formed OkHttp multipart with
            // "Malformed multipart body" because OkHttp adds a per-part `Content-Length`
            // header to each part. Verified against the live endpoint: identical requests
            // without per-part Content-Length return 200, any part carrying it returns 400.
            // Therefore the multipart bytes are assembled manually here (no MultipartBody).
            val requestBody = AsrMultipartRequestBody(requestJson, wavFile)

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Accept", "application/json")
                .post(requestBody)
                .build()

            val startTime = System.currentTimeMillis()
            PersonaLogger.i(TAG, "Attempting [Meta ASR] transcription with model $DEFAULT_ASR_MODEL for ${wavFile.name} (${wavFile.length()}B)")

            okHttpClient.newCall(request).execute().use { response ->
                val durationMs = System.currentTimeMillis() - startTime
                val code = response.code
                val responseBody = response.body?.string().orEmpty()

                when {
                    code in listOf(401, 402, 403) -> {
                        PersonaLogger.w(TAG, "Meta ASR Auth/Quota error ($code): $responseBody")
                        Result.failure(AuthOrQuotaException(code, "Meta ASR Auth/Quota error ($code)"))
                    }
                    code == 429 -> {
                        PersonaLogger.w(TAG, "Meta ASR Rate Limited (429)")
                        Result.failure(RateLimitException("Meta ASR Rate Limited (429)"))
                    }
                    code in 500..599 -> {
                        PersonaLogger.w(TAG, "Meta ASR Server error ($code): $responseBody")
                        Result.failure(ServerException(code, "Meta ASR Server error ($code)"))
                    }
                    response.isSuccessful -> {
                        val parsed = runCatching {
                            val root = jsonParser.parseToJsonElement(responseBody).jsonObject
                            root["transcript"]?.jsonPrimitive?.content?.trim()
                        }.getOrNull()

                        if (!parsed.isNullOrBlank()) {
                            val cleanText = ReasoningLeakSanitizer.stripReasoningTags(parsed)
                            PersonaLogger.i(TAG, "🎙️ Meta Voice Transcribe succeeded in ${durationMs}ms: '$cleanText'")
                            Result.success(cleanText)
                        } else {
                            PersonaLogger.w(TAG, "Meta ASR returned blank transcript in body: $responseBody")
                            Result.failure(IOException("Empty transcript returned by Meta ASR"))
                        }
                    }
                    else -> {
                        PersonaLogger.w(TAG, "Meta ASR HTTP $code error: $responseBody")
                        Result.failure(IOException("Meta ASR HTTP $code error"))
                    }
                }
            }
        } catch (e: SocketTimeoutException) {
            PersonaLogger.w(TAG, "Meta ASR SocketTimeoutException: ${e.message}")
            Result.failure(ApiTimeoutException("Meta ASR Socket Timeout"))
        } catch (e: Exception) {
            PersonaLogger.e(TAG, "Meta ASR Exception: ${e.message}", e)
            Result.failure(e)
        } finally {
            if (isTempWav && wavFile.exists()) {
                runCatching { wavFile.delete() }
            }
        }
    }

    /**
     * Executes single chat completion matching [ReplyGenerationRepositoryImpl]'s cascade interface.
     * Maps HTTP status codes to [CallResult] variants to trigger circuit breaker logic.
     * Includes transparent HTTP 404 fallback between contributor and standard model variants.
     */
    suspend fun executeSingleCall(
        model: String,
        messages: List<ChatMessage>,
        maxTokens: Int = 2000,
        apiKey: String,
        reasoningEffort: String? = "low",
        temperature: Double = 0.7
    ): CallResult = executeSingleCallInternal(
        model = model,
        messages = messages,
        maxTokens = maxTokens,
        apiKey = apiKey,
        reasoningEffort = reasoningEffort,
        temperature = temperature,
        attemptedModels = emptySet()
    )

    private suspend fun executeSingleCallInternal(
        model: String,
        messages: List<ChatMessage>,
        maxTokens: Int,
        apiKey: String,
        reasoningEffort: String?,
        temperature: Double,
        attemptedModels: Set<String>
    ): CallResult = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            Log.w(TAG, "Meta API key is blank; skipping request for model $model")
            return@withContext CallResult.Failed
        }

        val keyMasked = ReplyGenerationRepositoryImpl.maskKey(apiKey)
        val targetModel = model.ifBlank { DEFAULT_MODEL }
        val isReasoningModel = targetModel.contains("muse-spark") || targetModel.contains("reasoning")
        val effectiveMaxTokens = if (isReasoningModel) {
            maxOf(maxTokens, 2000)
        } else {
            maxOf(maxTokens, 1500)
        }

        try {
            val url = "$baseUrl/chat/completions"
            val payload = buildJsonObject {
                put("model", targetModel)
                put("temperature", temperature)
                put("max_tokens", effectiveMaxTokens)
                if (!reasoningEffort.isNullOrBlank()) {
                    put("reasoning_effort", reasoningEffort)
                }
                putJsonArray("messages") {
                    for (msg in messages) {
                        addJsonObject {
                            put("role", msg.role)
                            if (msg.imageUrl.isNullOrBlank()) {
                                put("content", msg.content)
                            } else {
                                putJsonArray("content") {
                                    if (msg.content.isNotBlank()) {
                                        addJsonObject {
                                            put("type", "text")
                                            put("text", msg.content)
                                        }
                                    }
                                    addJsonObject {
                                        put("type", "image_url")
                                        putJsonObject("image_url") {
                                            put("url", msg.imageUrl)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }.toString()

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(payload.toRequestBody(JSON_MEDIA_TYPE))
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                val code = response.code
                val responseBody = response.body?.string().orEmpty()

                when {
                    code == 404 -> {
                        PersonaLogger.w(TAG, "HTTP 404 on [Meta] model $targetModel (key $keyMasked): $responseBody")
                        val counterpart = getCounterpartModel(targetModel)
                        if (counterpart != null && !attemptedModels.contains(counterpart)) {
                            val logMsg = "🔄 Transparent fallback triggered: '$targetModel' returned 404, retrying immediately with '$counterpart'"
                            Log.i(TAG, logMsg)
                            PersonaLogger.i(TAG, logMsg)
                            return@withContext executeSingleCallInternal(
                                model = counterpart,
                                messages = messages,
                                maxTokens = maxTokens,
                                apiKey = apiKey,
                                reasoningEffort = reasoningEffort,
                                temperature = temperature,
                                attemptedModels = attemptedModels + targetModel
                            )
                        }
                        CallResult.Failed
                    }
                    code == 429 -> {
                        PersonaLogger.w(TAG, "Rate limited (429) on [Meta] model $targetModel (key $keyMasked)")
                        CallResult.RateLimited
                    }
                    code in listOf(401, 402, 403) -> {
                        PersonaLogger.w(TAG, "Auth/quota error ($code) on [Meta] model $targetModel (key $keyMasked)")
                        CallResult.AuthOrQuotaError(code)
                    }
                    code in 500..599 -> {
                        Log.w(TAG, "Server error ($code) on [Meta] model $targetModel (key $keyMasked)")
                        CallResult.ServerError(code)
                    }
                    response.isSuccessful -> {
                        PersonaLogger.d(PersonaLogTags.LLM, "📥 Raw LLM Output from [Meta] $targetModel: $responseBody")
                        val root = jsonParser.parseToJsonElement(responseBody).jsonObject
                        val choices = root["choices"]?.jsonArray
                        val firstChoice = choices?.firstOrNull()?.jsonObject
                        val messageObj = firstChoice?.get("message")?.jsonObject
                        val rawContent = messageObj?.get("content")?.jsonPrimitive?.content

                        if (rawContent.isNullOrBlank()) {
                            Log.w(TAG, "Empty content in successful response from [Meta] $targetModel (key $keyMasked)")
                            CallResult.Failed
                        } else {
                            try {
                                val cleanedContent = ReasoningLeakSanitizer.stripReasoningTags(rawContent)
                                val usageObj = root["usage"]?.jsonObject
                                val pTokens = usageObj?.get("prompt_tokens")?.jsonPrimitive?.intOrNull ?: 0
                                val cTokens = usageObj?.get("completion_tokens")?.jsonPrimitive?.intOrNull ?: 0
                                val respModel = root["model"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() } ?: targetModel

                                CallResult.Success(
                                    content = cleanedContent,
                                    promptTokens = pTokens,
                                    completionTokens = cTokens,
                                    actualModel = respModel
                                )
                            } catch (e: Exception) {
                                Log.w(TAG, "Error sanitizing response from [Meta] $targetModel: ${e.message}", e)
                                CallResult.Failed
                            }
                        }
                    }
                    else -> {
                        Log.w(TAG, "HTTP $code error from [Meta] $targetModel (key $keyMasked): $responseBody")
                        CallResult.Failed
                    }
                }
            }
        } catch (e: SocketTimeoutException) {
            Log.w(TAG, "Socket timeout on [Meta] model $targetModel (key $keyMasked): ${e.message}")
            CallResult.Timeout
        } catch (e: Exception) {
            Log.e(TAG, "Network error on [Meta] model $targetModel (key $keyMasked)", e)
            CallResult.Failed
        }
    }
}

