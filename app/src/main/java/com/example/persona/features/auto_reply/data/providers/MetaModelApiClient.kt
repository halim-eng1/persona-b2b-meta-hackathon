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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.net.SocketTimeoutException
import javax.inject.Inject
import javax.inject.Singleton

class AuthOrQuotaException(val code: Int, message: String) : IOException(message)
class RateLimitException(message: String) : IOException(message)
class ServerException(val code: Int, message: String) : IOException(message)
class ApiTimeoutException(message: String) : IOException(message)

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

        private val jsonParser = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val WAV_MEDIA_TYPE = "audio/wav".toMediaType()
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

        when (val result = executeSingleCall(model, messages, maxTokens = 1024, apiKey = apiKey, reasoningEffort = reasoningEffort, temperature = temperature)) {
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
            }.toString()

            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("request", null, requestJson.toRequestBody(JSON_MEDIA_TYPE))
                .addFormDataPart("audio", "recording.wav", wavFile.asRequestBody(WAV_MEDIA_TYPE))
                .build()

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
     */
    suspend fun executeSingleCall(
        model: String,
        messages: List<ChatMessage>,
        maxTokens: Int,
        apiKey: String,
        reasoningEffort: String? = "low",
        temperature: Double = 0.7
    ): CallResult = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            Log.w(TAG, "Meta API key is blank; skipping request for model $model")
            return@withContext CallResult.Failed
        }

        val keyMasked = ReplyGenerationRepositoryImpl.maskKey(apiKey)
        val hasImage = messages.any { !it.imageUrl.isNullOrBlank() }
        val targetModel = model.ifBlank { DEFAULT_MODEL }

        try {
            val url = "$baseUrl/chat/completions"
            val payload = buildJsonObject {
                put("model", targetModel)
                put("temperature", temperature)
                put("max_tokens", maxTokens)
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

