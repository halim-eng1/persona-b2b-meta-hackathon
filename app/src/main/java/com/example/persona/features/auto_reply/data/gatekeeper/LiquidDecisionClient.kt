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
    var keyPool: List<String> = emptyList()

    fun setKeys(keys: List<String>) {
        keyPool = keys.map { it.trim() }.filter { it.isNotBlank() }
    }

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
            val keys = if (keyPool.isNotEmpty()) {
                keyPool
            } else {
                listOfNotNull(
                    primaryApiKey.trim().takeIf { it.isNotBlank() },
                    fallbackApiKey.trim().takeIf { it.isNotBlank() && it != primaryApiKey.trim() }
                )
            }

            if (keys.isEmpty()) {
                Log.w(TAG, "No Liquid AI API key configured (key pool and defaults are blank)")
                return@withTimeoutOrNull null
            }

            for ((index, key) in keys.withIndex()) {
                var outcome = executeCall(key, PRIMARY_MODEL, text)
                if (outcome.intent != null) {
                    return@withTimeoutOrNull outcome.intent
                }

                // If primary model failed on this key, attempt failover model
                outcome = executeCall(key, FAILOVER_MODEL, text)
                if (outcome.intent != null) {
                    return@withTimeoutOrNull outcome.intent
                }

                if (index < keys.size - 1) {
                    Log.w(TAG, "Liquid key failed (${outcome.errorSummary}). Rotating to next key in pool...")
                }
            }

            null
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

            val isUrgentComplaint = parsed.answers["is_urgent_complaint"]?.choice == "yes"
            val finalIntent = if (isUrgentComplaint) {
                Log.w(TAG, "🚨 Liquid AI flagged urgent complaint/anger for: \"${text.take(60)}\" -> CATALOG_INQUIRY")
                InboundIntent.CATALOG_INQUIRY
            } else {
                mappedIntent ?: InboundIntent.CATALOG_INQUIRY
            }

            PersonaLogger.d(
                PersonaLogTags.ORCHESTRATOR,
                "⚡ Gatekeeper Tier 2 (Liquid AI) resolved -> intent=$finalIntent (conf=$confidence, latency=${latency}ms)"
            )
            CallOutcome(finalIntent, isRateLimit = false, errorSummary = null)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val latency = System.currentTimeMillis() - t0
            Log.w(TAG, "Liquid AI network call failed (model=$model, latency=${latency}ms): ${e.message}")
            CallOutcome(null, isRateLimit = false, errorSummary = e.message)
        }
    }

    internal fun mapChoiceToIntent(choice: String?): InboundIntent? {
        if (choice == null) return null
        return when (choice.uppercase().trim()) {
            "GREETING" -> InboundIntent.GREETING
            "CLOSING", "PRESENCE_CHECK", "BANTER" -> InboundIntent.BANTER
            "ORDER_STATUS", "ORDER_UPDATE", "ORDER_CANCEL" -> InboundIntent.ORDER_STATUS
            "CATALOG_INQUIRY", "ORDER_CREATE", "GENUINE_COMPLAINT_HITL" -> InboundIntent.CATALOG_INQUIRY
            else -> InboundIntent.CATALOG_INQUIRY
        }
    }

    @Serializable
    private data class SystemOneDecisionRequest(
        val model: String,
        val state: String,
        val questions: Map<String, SystemOneQuestion>
    )

    @Serializable
    private data class SystemOneQuestion(
        val type: String,
        val instructions: String,
        val criteria: Map<String, String>
    )

    @Serializable
    private data class SystemOneDecisionResponse(
        val answers: Map<String, SystemOneAnswer> = emptyMap()
    )

    @Serializable
    private data class SystemOneAnswer(
        val choice: String? = null,
        val confidence: Float? = null
    )
}
