package com.example.persona.features.auto_reply.data.gatekeeper

import android.util.Log
import com.example.persona.BuildConfig
import com.example.persona.core.logging.PersonaLogger
import com.example.persona.core.logging.PersonaLogTags
import com.example.persona.features.auto_reply.domain.gatekeeper.InboundIntent
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LiquidDecisionClient @Inject constructor(
    private val httpClient: HttpClient
) {
    constructor() : this(createDefaultHttpClient())

    internal var apiKeyOverride: String? = null
    internal var apiKey2Override: String? = null
    internal var baseUrlOverride: String? = null

    private val primaryApiKey: String
        get() = apiKeyOverride ?: (runCatching {
            BuildConfig::class.java.getField("LIQUID_API_KEY").get(null) as? String
        }.getOrNull() ?: "")

    private val fallbackApiKey: String
        get() = apiKey2Override ?: (runCatching {
            BuildConfig::class.java.getField("LIQUID_API_KEY_2").get(null) as? String
        }.getOrNull() ?: "")

    private val baseUrl: String
        get() = baseUrlOverride ?: DEFAULT_BASE_URL

    companion object {
        private const val TAG = "LiquidDecisionClient"
        private const val DEFAULT_BASE_URL = "https://api.liquid.ai/decisions/v1/systemone"
        const val PRIMARY_MODEL = "d1:latest"
        const val FAILOVER_MODEL = "d1"

        val CATEGORY_CRITERIA: Map<String, String> = linkedMapOf(
            "GREETING" to "Hello, welcome, peace, good morning/evening",
            "CLOSING" to "Thanks, goodbye, parting, finishing conversation",
            "PRESENCE_CHECK" to "Checking if anyone is there/online (e.g. ف حد هنا, موجودين, الو)",
            "BANTER" to "Jokes, flirting, riddles, dinner banter, teasing",
            "CATALOG_INQUIRY" to "Inquiring about hoodies, t-shirts, prices, sizes, fabric, shipping fees",
            "ORDER_CREATE" to "Submitting order with address/specs",
            "ORDER_UPDATE" to "Changing address, size, or notes on existing order",
            "ORDER_CANCEL" to "Cancelling active order",
            "GENUINE_COMPLAINT_HITL" to "Severe anger, damaged item, fraud accusation, demanding human manager"
        )

        val URGENT_COMPLAINT_CRITERIA: Map<String, String> = mapOf(
            "urgent" to "Urgent complaint or anger",
            "normal" to "Standard query"
        )

        private fun createDefaultHttpClient(): HttpClient = HttpClient {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                    encodeDefaults = true
                    explicitNulls = false
                })
            }
        }
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        explicitNulls = false
    }

    suspend fun decide(text: String, timeoutMs: Long = 800L): InboundIntent? = withContext(Dispatchers.IO) {
        withTimeoutOrNull(timeoutMs) {
            val key1 = primaryApiKey.trim()
            val key2 = fallbackApiKey.trim()

            if (key1.isBlank() && key2.isBlank()) {
                Log.w(TAG, "No Liquid AI API key configured (primary and fallback are blank)")
                return@withTimeoutOrNull null
            }

            // Dual-key rotation: Try primaryApiKey first; if 429 or error, retry immediately with fallbackApiKey
            var currentKey = if (key1.isNotBlank()) key1 else key2

            var outcome = executeCall(currentKey, PRIMARY_MODEL, text)
            if (outcome.intent != null) {
                return@withTimeoutOrNull outcome.intent
            }

            // Retry with fallback key if primary key failed and fallback key is available
            if (key2.isNotBlank() && key2 != currentKey) {
                Log.w(TAG, "Primary Liquid key failed (${outcome.errorSummary}). Retrying immediately with fallback key.")
                outcome = executeCall(key2, PRIMARY_MODEL, text)
                if (outcome.intent != null) {
                    return@withTimeoutOrNull outcome.intent
                }
                currentKey = key2
            }

            // Failover model: If primary model d1:latest failed across available keys, failover to d1
            Log.w(TAG, "Primary model $PRIMARY_MODEL failed. Attempting failover model $FAILOVER_MODEL")
            val failoverOutcome = executeCall(currentKey, FAILOVER_MODEL, text)
            failoverOutcome.intent
        }
    }

    private data class CallOutcome(
        val intent: InboundIntent?,
        val isRateLimit: Boolean,
        val errorSummary: String?
    )

    private suspend fun executeCall(apiKey: String, model: String, text: String): CallOutcome {
        val t0 = System.currentTimeMillis()
        return try {
            val request = SystemOneDecisionRequest(
                model = model,
                state = text,
                questions = linkedMapOf(
                    "intent" to SystemOneQuestion(
                        type = "choice",
                        instructions = "Classify the Egyptian Arabic or Franco-Arab customer message into exactly one category.",
                        criteria = CATEGORY_CRITERIA
                    ),
                    "is_urgent_complaint" to SystemOneQuestion(
                        type = "choice",
                        instructions = "Is the user expressing severe anger, fraud accusations, or an urgent damaged package issue?",
                        criteria = URGENT_COMPLAINT_CRITERIA
                    )
                )
            )

            val requestJson = json.encodeToString(SystemOneDecisionRequest.serializer(), request)
            val response = httpClient.post(baseUrl) {
                header("Authorization", "Bearer $apiKey")
                contentType(ContentType.Application.Json)
                setBody(requestJson)
            }

            val latency = System.currentTimeMillis() - t0
            val status = response.status

            if (status == HttpStatusCode.TooManyRequests) {
                Log.w(TAG, "Liquid AI 429 Too Many Requests (model=$model, latency=${latency}ms)")
                return CallOutcome(null, isRateLimit = true, errorSummary = "HTTP 429 Too Many Requests")
            }

            if (!status.isSuccess()) {
                val errBody = runCatching { response.bodyAsText().take(200) }.getOrNull() ?: ""
                Log.w(TAG, "Liquid AI HTTP ${status.value} error (model=$model, latency=${latency}ms): $errBody")
                return CallOutcome(null, isRateLimit = false, errorSummary = "HTTP ${status.value}")
            }

            val responseBody = response.bodyAsText()
            val parsed = json.decodeFromString(SystemOneDecisionResponse.serializer(), responseBody)
            val choice = parsed.answers["intent"]?.choice
            val mappedIntent = mapChoiceToIntent(choice)
            val confidence = parsed.answers["intent"]?.confidence ?: 0.95f

            PersonaLogger.d(
                PersonaLogTags.ORCHESTRATOR,
                "⚡ Liquid AI decision: rawChoice=$choice -> intent=$mappedIntent (conf=$confidence, model=$model, latency=${latency}ms)"
            )
            CallOutcome(mappedIntent, isRateLimit = false, errorSummary = null)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val latency = System.currentTimeMillis() - t0
            Log.w(TAG, "Liquid AI call exception (model=$model, latency=${latency}ms): ${e.message}", e)
            CallOutcome(null, isRateLimit = false, errorSummary = e.message ?: "Exception")
        }
    }

    internal fun mapChoiceToIntent(choice: String?): InboundIntent {
        return when (choice?.trim()?.uppercase()) {
            "GREETING" -> InboundIntent.GREETING
            "BANTER", "PRESENCE_CHECK", "CLOSING" -> InboundIntent.BANTER
            "ORDER_CREATE" -> InboundIntent.CATALOG_INQUIRY
            "ORDER_STATUS", "ORDER_UPDATE", "ORDER_CANCEL" -> InboundIntent.ORDER_STATUS
            "CATALOG_INQUIRY" -> InboundIntent.CATALOG_INQUIRY
            "GENUINE_COMPLAINT_HITL" -> InboundIntent.CATALOG_INQUIRY
            else -> InboundIntent.CATALOG_INQUIRY
        }
    }
}

@Serializable
internal data class SystemOneDecisionRequest(
    @SerialName("model") val model: String,
    @SerialName("state") val state: String,
    @SerialName("questions") val questions: Map<String, SystemOneQuestion>
)

@Serializable
internal data class SystemOneQuestion(
    @SerialName("type") val type: String,
    @SerialName("instructions") val instructions: String? = null,
    @SerialName("criteria") val criteria: Map<String, String>? = null
)

@Serializable
internal data class SystemOneDecisionResponse(
    @SerialName("id") val id: String? = null,
    @SerialName("model") val model: String? = null,
    @SerialName("answers") val answers: Map<String, SystemOneAnswer> = emptyMap()
)

@Serializable
internal data class SystemOneAnswer(
    @SerialName("type") val type: String? = null,
    @SerialName("choice") val choice: String? = null,
    @SerialName("confidence") val confidence: Float? = null,
    @SerialName("number") val number: Double? = null,
    @SerialName("bool") val bool: Boolean? = null
)
