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
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CloudflareClefClient @Inject constructor(
    private val httpClient: HttpClient
) {
    constructor() : this(createDefaultHttpClient())

    internal var apiTokenOverride: String? = null
    internal var accountIdOverride: String? = null
    internal var baseUrlOverride: String? = null

    private val apiToken: String
        get() = apiTokenOverride ?: (runCatching {
            BuildConfig::class.java.getField("CLOUDFLARE_API_TOKEN").get(null) as? String
        }.getOrNull() ?: "")

    private val accountId: String
        get() = accountIdOverride ?: (runCatching {
            BuildConfig::class.java.getField("CLOUDFLARE_ACCOUNT_ID").get(null) as? String
        }.getOrNull() ?: "")

    companion object {
        private const val TAG = "CloudflareClefClient"
        const val PRIMARY_MODEL = "@cf/cloudflare/clef-flash"
        const val FAILOVER_MODEL = "@cf/cloudflare/clef-omni"

        val INTENT_CRITERIA: Map<String, String> = linkedMapOf(
            "GREETING" to "Hello, peace, welcome, good morning/evening",
            "CLOSING" to "Thanks, goodbye, parting, finishing conversation",
            "BANTER" to "Jokes, flirting, riddles, dinner banter, teasing",
            "ORDER_STATUS" to "Checking active order status, tracking, courier",
            "CATALOG_INQUIRY" to "Inquiring about hoodies, t-shirts, prices, sizes, fabric, shipping fees",
            "GENUINE_COMPLAINT_HITL" to "Severe anger, damaged item, fraud accusation, demanding human manager"
        )

        val URGENT_CRITERIA: Map<String, String> = linkedMapOf(
            "yes" to "Urgent problem, fraud accusation, or damaged item",
            "no" to "Not urgent, standard commercial query"
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

    internal fun resolveUrl(model: String): String {
        return if (baseUrlOverride != null) {
            val base = baseUrlOverride!!.trimEnd('/')
            if (base.contains("@cf/")) base else "$base/$model"
        } else {
            val accId = accountId.trim()
            "https://api.cloudflare.com/client/v4/accounts/$accId/ai/run/$model"
        }
    }

    suspend fun decide(text: String, timeoutMs: Long = 1150L): InboundIntent? = withContext(Dispatchers.IO) {
        withTimeoutOrNull(timeoutMs) {
            val token = apiToken.trim()
            val account = accountId.trim()

            if (token.isBlank() || (account.isBlank() && baseUrlOverride == null)) {
                Log.w(TAG, "Cloudflare credentials not configured (token or account blank)")
                return@withTimeoutOrNull null
            }

            // 1. Try PRIMARY_MODEL (@cf/cloudflare/clef-flash) with an allocated budget within the SLA
            val primaryTimeout = (timeoutMs * 5 / 8).coerceAtLeast(400L)
            val primaryOutcome = withTimeoutOrNull(primaryTimeout) {
                executeCall(token, PRIMARY_MODEL, text)
            }

            if (primaryOutcome != null && primaryOutcome.intent != null) {
                return@withTimeoutOrNull primaryOutcome.intent
            }

            // 2. Dual-model failover: Try FAILOVER_MODEL (@cf/cloudflare/clef-omni)
            Log.w(
                TAG,
                "Primary model $PRIMARY_MODEL failed or timed out (${primaryOutcome?.errorSummary ?: "timeout"}). Failing over to $FAILOVER_MODEL"
            )
            val failoverOutcome = executeCall(token, FAILOVER_MODEL, text)
            failoverOutcome.intent
        }
    }

    internal data class CallOutcome(
        val intent: InboundIntent?,
        val isRateLimit: Boolean,
        val errorSummary: String?
    )

    internal suspend fun executeCall(token: String, model: String, text: String): CallOutcome {
        val t0 = System.currentTimeMillis()
        return try {
            val bodyModel = model.substringAfterLast("/")
            val request = ClefDecisionRequest(
                model = bodyModel,
                state = text,
                questions = linkedMapOf(
                    "intent" to ClefQuestion(
                        type = "choice",
                        instructions = "Classify the Egyptian Arabic customer turn into exactly one intent category.",
                        criteria = INTENT_CRITERIA
                    ),
                    "is_urgent" to ClefQuestion(
                        type = "choice",
                        instructions = "Determine if the message requires urgent attention.",
                        criteria = URGENT_CRITERIA
                    )
                )
            )

            val requestJson = json.encodeToString(ClefDecisionRequest.serializer(), request)
            val targetUrl = resolveUrl(model)

            val response = httpClient.post(targetUrl) {
                header("Authorization", "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody(requestJson)
            }

            val latency = System.currentTimeMillis() - t0
            val status = response.status

            if (status == HttpStatusCode.TooManyRequests) {
                Log.w(TAG, "Cloudflare Clef 429 Too Many Requests (model=$model, latency=${latency}ms)")
                return CallOutcome(null, isRateLimit = true, errorSummary = "HTTP 429 Too Many Requests")
            }

            if (!status.isSuccess()) {
                val errBody = runCatching { response.bodyAsText().take(200) }.getOrNull() ?: ""
                Log.w(TAG, "Cloudflare Clef HTTP ${status.value} error (model=$model, latency=${latency}ms): $errBody")
                return CallOutcome(null, isRateLimit = false, errorSummary = "HTTP ${status.value}")
            }

            val responseBody = response.bodyAsText()
            val parsed = json.decodeFromString(ClefResponse.serializer(), responseBody)

            val intentAnswer = parsed.result?.answers?.get("intent")
            val choice = intentAnswer?.choice
            val mappedIntent = mapChoiceToIntent(choice)
            val confidence = intentAnswer?.confidence ?: 0.90f
            val usage = parsed.result?.usage

            PersonaLogger.d(
                PersonaLogTags.ORCHESTRATOR,
                "⚡ Cloudflare Clef decision: rawChoice=$choice -> intent=$mappedIntent (conf=$confidence, model=$model, latency=${latency}ms, inTokens=${usage?.input_tokens}, outTokens=${usage?.output_tokens})"
            )

            CallOutcome(mappedIntent, isRateLimit = false, errorSummary = null)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val latency = System.currentTimeMillis() - t0
            Log.w(TAG, "Cloudflare Clef call exception (model=$model, latency=${latency}ms): ${e.message}", e)
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
internal data class ClefDecisionRequest(
    val model: String,
    val state: String,
    val questions: Map<String, ClefQuestion>
)

@Serializable
internal data class ClefQuestion(
    val type: String,
    val instructions: String? = null,
    val criteria: Map<String, String>? = null
)

@Serializable
internal data class ClefResponse(
    val result: ClefResult? = null,
    val success: Boolean = false,
    val errors: List<ClefError> = emptyList(),
    val messages: List<String> = emptyList()
)

@Serializable
internal data class ClefResult(
    val model: String? = null,
    val answers: Map<String, ClefAnswer> = emptyMap(),
    val usage: ClefUsage? = null
)

@Serializable
internal data class ClefAnswer(
    val type: String? = null,
    val choice: String? = null,
    val confidence: Float? = null,
    val probabilities: Map<String, Float> = emptyMap()
)

@Serializable
internal data class ClefUsage(
    val input_tokens: Int? = null,
    val output_tokens: Int? = null
)

@Serializable
internal data class ClefError(
    val code: Int? = null,
    val message: String? = null
)
