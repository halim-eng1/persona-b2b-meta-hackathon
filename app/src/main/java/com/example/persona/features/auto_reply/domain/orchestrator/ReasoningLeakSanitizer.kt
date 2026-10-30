package com.example.persona.features.auto_reply.domain.orchestrator

import com.example.persona.core.network.dto.AgenticLlmResponse
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles LLM output sanitization, markdown fence removal, JSON quote repair,
 * reasoning leakage detection, <think> tag stripping, and safe fallback response building.
 */
@Singleton
class ReasoningLeakSanitizer @Inject constructor() {

    companion object {
        const val CANONICAL_HITL_MESSAGE =
            "تمام يا فندم، ثواني وهيكون مع حضرتك ممثل خدمة العملاء فوراً لمساعدتك في أقرب وقت متاح! 🙏"

        private val THINK_TAG_REGEX = Regex("""<think>[\s\S]*?</think>""", RegexOption.IGNORE_CASE)
        private val UNCLOSED_THINK_TAG_REGEX = Regex("""<think>[\s\S]*$""", RegexOption.IGNORE_CASE)

        private val REASONING_LEAK_MARKERS = listOf(
            // English markers
            "the user is asking",
            "looking at the knowledge",
            "as an ai",
            "context provided",
            "thinking process",
            "i need to",
            // Arabic reasoning markers
            "التفكير",
            "تحليل",
            "أفكر في",
            "أفكر",
            "المستخدم يسأل",
            "أنا نموذج ذكاء",
            "ذكاء اصطناعي",
            "كذكاء اصطناعي",
            "أحتاج إلى مراجعة",
            "تفكير النموذج",
            "خطوات التفكير",
            "خطوات الحل",
            "سأقوم بمراجعة",
            "سأقوم بالرد",
            "المستخدم يريد",
            "تحليل الطلب",
            "تفكير",
            "<think>",
            "</think>"
        )

        fun stripThinkTags(text: String): String = stripReasoningTags(text)

        fun stripReasoningTags(text: String): String {
            var result = THINK_TAG_REGEX.replace(text, "")
            result = UNCLOSED_THINK_TAG_REGEX.replace(result, "")
            return result.trim()
        }

        val agenticJson = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

        fun sanitizeLlmJson(raw: String): String {
            val stripped = stripReasoningTags(raw)
            var cleaned = stripped.trim()
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

        /**
         * Normalizes malformed LLM JSON keys where the model emitted spaces, hyphens,
         * or missed underscores (e.g. "final_reply text", "final reply text", "finalreplytext").
         */
        fun normalizeJsonKeys(json: String): String {
            var result = json
            // 1. final_reply_text
            result = result.replace(Regex("\"final[\\s_-]*reply[\\s_-]*text\"\\s*:", RegexOption.IGNORE_CASE), "\"final_reply_text\":")
            // 2. refined_search_keyword
            result = result.replace(Regex("\"refined[\\s_-]*search[\\s_-]*keyword\"\\s*:", RegexOption.IGNORE_CASE), "\"refined_search_keyword\":")
            // 3. order_action
            result = result.replace(Regex("\"order[\\s_-]*action\"\\s*:", RegexOption.IGNORE_CASE), "\"order_action\":")
            // 4. extracted_order
            result = result.replace(Regex("\"extracted[\\s_-]*order\"\\s*:", RegexOption.IGNORE_CASE), "\"extracted_order\":")
            // 5. customer_name
            result = result.replace(Regex("\"customer[\\s_-]*name\"\\s*:", RegexOption.IGNORE_CASE), "\"customer_name\":")
            // 6. delivery_address
            result = result.replace(Regex("\"delivery[\\s_-]*address\"\\s*:", RegexOption.IGNORE_CASE), "\"delivery_address\":")
            // 7. order_details
            result = result.replace(Regex("\"order[\\s_-]*details\"\\s*:", RegexOption.IGNORE_CASE), "\"order_details\":")
            // 8. custom_notes
            result = result.replace(Regex("\"custom[\\s_-]*notes\"\\s*:", RegexOption.IGNORE_CASE), "\"custom_notes\":")
            return result
        }

        fun decodeAgenticResponseSafely(raw: String): AgenticLlmResponse {
            val sanitized = sanitizeLlmJson(raw)
            val normalizedKeys = normalizeJsonKeys(sanitized)
            val normalized = normalizeRefinedSearchKeywordArray(normalizedKeys)
            val decoded = try {
                agenticJson.decodeFromString<AgenticLlmResponse>(normalized)
            } catch (e: Exception) {
                val repaired = repairUnescapedJsonQuotes(normalized)
                agenticJson.decodeFromString<AgenticLlmResponse>(repaired)
            }

            return if (decoded.finalReplyText != null) {
                val cleanedReply = stripReasoningTags(decoded.finalReplyText)
                if (cleanedReply != decoded.finalReplyText) {
                    decoded.copy(finalReplyText = cleanedReply)
                } else {
                    decoded
                }
            } else {
                decoded
            }
        }

        fun isLeakedReasoningText(text: String?): Boolean {
            if (text.isNullOrBlank()) return false
            val lowered = text.lowercase()
            if (lowered.contains("<think>") || lowered.contains("</think>")) return true
            return REASONING_LEAK_MARKERS.any { marker ->
                lowered.contains(marker.lowercase())
            }
        }

        fun buildUnsafeOutputHitlResponse(hitlMessage: String = CANONICAL_HITL_MESSAGE): AgenticLlmResponse = AgenticLlmResponse(
            action = "HITL",
            finalReplyText = hitlMessage
        )

        fun ensureHitlSuffix(text: String, hitlSuffix: String = CANONICAL_HITL_MESSAGE): String {
            val trimmed = text.trim()
            if (trimmed.isEmpty() || trimmed == hitlSuffix) {
                return hitlSuffix
            }
            if (trimmed.contains(hitlSuffix) || trimmed.contains("ثواني وهيكون مع حضرتك")) {
                return trimmed
            }
            return "$trimmed\n$hitlSuffix"
        }
    }
}
