package com.example.persona.features.auto_reply.domain.orchestrator

import com.example.persona.core.network.dto.AgenticLlmResponse
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles LLM output sanitization, markdown fence removal, JSON quote repair,
 * reasoning leakage detection, and safe fallback response building.
 */
@Singleton
class ReasoningLeakSanitizer @Inject constructor() {

    companion object {
        const val CANONICAL_HITL_MESSAGE =
            "تمام يا فندم، ثواني وهيكون مع حضرتك ممثل خدمة العملاء فوراً لمساعدتك في أقرب وقت متاح! 🙏"

        private val REASONING_LEAK_MARKERS = listOf(
            "the user is asking",
            "looking at the knowledge",
            "as an ai",
            "context provided",
            "thinking process",
            "i need to"
        )

        val agenticJson = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

        fun sanitizeLlmJson(raw: String): String {
            var cleaned = raw.trim()
            cleaned = cleaned.removePrefix("```json").removePrefix("```JSON").removePrefix("```")
            if (cleaned.endsWith("```")) {
                cleaned = cleaned.substring(0, cleaned.length - 3)
            }
            cleaned = cleaned.trim()
            val firstBrace = cleaned.indexOf('{')
            val lastBrace = cleaned.lastIndexOf('}')
            if (firstBrace != -1 && lastBrace != -1 && lastBrace > firstBrace) {
                cleaned = cleaned.substring(firstBrace, lastBrace + 1)
            }
            return cleaned.trim()
        }

        fun repairUnescapedJsonQuotes(json: String): String {
            val sb = StringBuilder()
            var inString = false
            var i = 0
            while (i < json.length) {
                val c = json[i]
                if (c == '\\' && inString && i + 1 < json.length) {
                    sb.append(c)
                    sb.append(json[i + 1])
                    i += 2
                    continue
                }
                if (c == '"') {
                    if (!inString) {
                        inString = true
                        sb.append(c)
                    } else {
                        var j = i + 1
                        while (j < json.length && json[j].isWhitespace()) {
                            j++
                        }
                        val nextChar = if (j < json.length) json[j] else null
                        if (nextChar == ',' || nextChar == '}' || nextChar == ']' || nextChar == ':') {
                            inString = false
                            sb.append(c)
                        } else {
                            sb.append("\\\"")
                        }
                    }
                } else {
                    sb.append(c)
                }
                i++
            }
            return sb.toString()
        }

        private val REFINED_KEYWORD_ARRAY_REGEX = Regex(
            """("refined_search_keyword"\s*:\s*)\[(.*?)\]""",
            RegexOption.DOT_MATCHES_ALL
        )
        private val STRING_ITEM_REGEX = Regex("""["']((?:\\.|[^"'\\])*)["']""")

        /**
         * Normalizes malformed LLM outputs where `refined_search_keyword` was emitted
         * as a JSON array `["keyword1", "keyword2"]` instead of a flat string `"keyword1 keyword2"`.
         */
        fun normalizeRefinedSearchKeywordArray(json: String): String {
            if (!json.contains("refined_search_keyword")) return json
            return REFINED_KEYWORD_ARRAY_REGEX.replace(json) { matchResult ->
                val prefix = matchResult.groupValues[1]
                val arrayContent = matchResult.groupValues[2].trim()
                if (arrayContent.isEmpty()) {
                    "${prefix}null"
                } else {
                    val quotedMatches = STRING_ITEM_REGEX.findAll(arrayContent)
                        .map { it.groupValues[1].trim() }
                        .filter { it.isNotEmpty() }
                        .toList()

                    val combined = if (quotedMatches.isNotEmpty()) {
                        quotedMatches.joinToString(" ")
                    } else {
                        arrayContent.split(",")
                            .map { it.trim().trim('"', '\'') }
                            .filter { it.isNotEmpty() }
                            .joinToString(" ")
                    }
                    val safeCombined = combined.replace("\"", "\\\"")
                    "$prefix\"$safeCombined\""
                }
            }
        }

        fun decodeAgenticResponseSafely(raw: String): AgenticLlmResponse {
            val sanitized = sanitizeLlmJson(raw)
            val normalized = normalizeRefinedSearchKeywordArray(sanitized)
            return try {
                agenticJson.decodeFromString<AgenticLlmResponse>(normalized)
            } catch (e: Exception) {
                val repaired = repairUnescapedJsonQuotes(normalized)
                agenticJson.decodeFromString<AgenticLlmResponse>(repaired)
            }
        }

        fun isLeakedReasoningText(text: String?): Boolean {
            if (text.isNullOrBlank()) return false
            val lowered = text.lowercase()
            return REASONING_LEAK_MARKERS.any { lowered.contains(it) }
        }

        fun buildUnsafeOutputHitlResponse(): AgenticLlmResponse = AgenticLlmResponse(
            action = "HITL",
            finalReplyText = CANONICAL_HITL_MESSAGE
        )

        fun ensureHitlSuffix(text: String): String {
            val trimmed = text.trim()
            if (trimmed.isEmpty() || trimmed == CANONICAL_HITL_MESSAGE) {
                return CANONICAL_HITL_MESSAGE
            }
            if (trimmed.contains(CANONICAL_HITL_MESSAGE) || trimmed.contains("ثواني وهيكون مع حضرتك")) {
                return trimmed
            }
            return "$trimmed\n$CANONICAL_HITL_MESSAGE"
        }
    }
}
