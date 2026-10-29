package com.example.persona.features.chat_import.data.parser

import com.example.persona.features.chat_import.data.normalizer.ArabicTextNormalizer
import com.example.persona.features.chat_import.domain.model.ParsedChatMessage
import java.io.BufferedReader
import java.io.Reader
import java.io.StringReader
import java.util.Calendar
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

typealias ParsedChatMessage = com.example.persona.features.chat_import.domain.model.ParsedChatMessage

/**
 * Pure Kotlin streaming parser for WhatsApp exported chat text transcripts.
 *
 * Supports:
 * - Android export formats (both 24-hour and 12-hour AM/PM and Arabic ص/م)
 * - iOS bracketed export formats (both with seconds and without)
 * - Eastern Arabic-Indic numerals (٠-٩) and Persian numerals (۰-۹)
 * - Unicode BiDi control mark stripping (LRM, RLM, isolates)
 * - Multi-line message continuation streaming via [Sequence]
 * - System messages and media attachment indicators
 * - Zero Android Context coupling
 */
@Singleton
class WhatsAppTxtExportParser @Inject constructor(
    private val normalizer: ArabicTextNormalizer = ArabicTextNormalizer(),
    private val defaultTimeZone: TimeZone = TimeZone.getDefault()
) {

    private data class HeaderMatch(
        val timestampMs: Long,
        val senderName: String,
        val messageText: String,
        val isSystemMessage: Boolean,
        val rawTimestamp: String
    )

    private class MutableParsedMessage(
        val timestampMs: Long,
        val senderName: String,
        initialText: String,
        val isSystemMessage: Boolean,
        val rawTimestamp: String?
    ) {
        private val textBuilder = StringBuilder(initialText)

        fun appendLine(line: String) {
            textBuilder.append('\n').append(line)
        }

        fun toParsedChatMessage(): ParsedChatMessage {
            val fullText = textBuilder.toString()
            return ParsedChatMessage(
                timestampMs = timestampMs,
                senderName = senderName,
                messageText = fullText,
                isSystemMessage = isSystemMessage,
                hasMediaAttachment = isMediaAttachment(fullText),
                rawTimestamp = rawTimestamp
            )
        }
    }

    /**
     * Parses a lazy [Sequence] of lines into a lazy [Sequence] of [ParsedChatMessage] records.
     * Memory consumption is strictly O(1) relative to total file size.
     */
    fun parseStream(lines: Sequence<String>): Sequence<ParsedChatMessage> = sequence {
        var current: MutableParsedMessage? = null

        for (rawLine in lines) {
            val cleanedLine = sanitizeLine(rawLine)
            if (cleanedLine.isEmpty()) continue

            val match = tryMatchHeader(cleanedLine)
            if (match != null) {
                current?.let { yield(it.toParsedChatMessage()) }
                current = MutableParsedMessage(
                    timestampMs = match.timestampMs,
                    senderName = match.senderName,
                    initialText = match.messageText,
                    isSystemMessage = match.isSystemMessage,
                    rawTimestamp = match.rawTimestamp
                )
            } else {
                current?.appendLine(rawLine)
            }
        }

        current?.let { yield(it.toParsedChatMessage()) }
    }

    /**
     * Parses all messages from a full transcript string.
     */
    fun parse(content: String): List<ParsedChatMessage> {
        return parse(StringReader(content)).toList()
    }

    /**
     * Parses all messages from a [Reader] as a streaming sequence.
     */
    fun parse(reader: Reader): Sequence<ParsedChatMessage> {
        val bufferedReader = if (reader is BufferedReader) reader else BufferedReader(reader)
        return parseStream(bufferedReader.lineSequence())
    }

    private fun tryMatchHeader(line: String): HeaderMatch? {
        // 1. Try iOS bracketed format: [date, time] Sender: message OR [date, time] System message
        if (line.startsWith('[')) {
            val closingBracket = line.indexOf(']')
            if (closingBracket in 1..40) {
                val headerPart = line.substring(1, closingBracket).trim()
                val remainder = line.substring(closingBracket + 1).trim()
                val timestampMs = parseTimestamp(headerPart)
                if (timestampMs != null) {
                    val colonIndex = remainder.indexOf(':')
                    return if (colonIndex > 0) {
                        val sender = remainder.substring(0, colonIndex).trim()
                        val text = remainder.substring(colonIndex + 1).trim()
                        HeaderMatch(
                            timestampMs = timestampMs,
                            senderName = sender,
                            messageText = text,
                            isSystemMessage = false,
                            rawTimestamp = headerPart
                        )
                    } else {
                        HeaderMatch(
                            timestampMs = timestampMs,
                            senderName = SYSTEM_SENDER,
                            messageText = remainder,
                            isSystemMessage = true,
                            rawTimestamp = headerPart
                        )
                    }
                }
            }
        }

        // 2. Try Android format: date, time - Sender: message OR date, time - System message
        // Look for separator " - " or " – " or " — "
        val dashIndex = findDashSeparator(line)
        if (dashIndex != -1) {
            val headerPart = line.substring(0, dashIndex).trim()
            val remainder = line.substring(dashIndex + 3).trim()
            val timestampMs = parseTimestamp(headerPart)
            if (timestampMs != null) {
                val colonIndex = remainder.indexOf(':')
                return if (colonIndex > 0) {
                    val sender = remainder.substring(0, colonIndex).trim()
                    val text = remainder.substring(colonIndex + 1).trim()
                    HeaderMatch(
                        timestampMs = timestampMs,
                        senderName = sender,
                        messageText = text,
                        isSystemMessage = false,
                        rawTimestamp = headerPart
                    )
                } else {
                    HeaderMatch(
                        timestampMs = timestampMs,
                        senderName = SYSTEM_SENDER,
                        messageText = remainder,
                        isSystemMessage = true,
                        rawTimestamp = headerPart
                    )
                }
            }
        }

        return null
    }

    private fun findDashSeparator(line: String): Int {
        val limit = minOf(line.length - 3, 50)
        for (i in 0..limit) {
            val c1 = line[i]
            val c2 = line[i + 1]
            val c3 = line[i + 2]
            if (c1 == ' ' && (c2 == '-' || c2 == '–' || c2 == '—') && c3 == ' ') {
                return i
            }
        }
        return -1
    }

    /**
     * Parses a date/time header substring such as:
     * "15/04/2026, 14:30"
     * "15/04/2026, 2:30 م"
     * "15/04/2026, 02:30:15 PM"
     * "2026-10-26 14:30:00"
     */
    fun parseTimestamp(rawHeader: String): Long? {
        val sanitized = sanitizeDigits(rawHeader)
            .replace(',', ' ')
            .replace('\u060C', ' ') // Arabic comma
            .trim()

        val tokens = sanitized.split("\\s+".toRegex()).filter { it.isNotBlank() }
        if (tokens.size < 2) return null

        val datePart = tokens[0]
        val timePart = tokens[1]
        val periodPart = if (tokens.size >= 3) tokens[2] else null

        val dateNumbers = datePart.split("[/\\-\\.]".toRegex()).mapNotNull { it.toIntOrNull() }
        if (dateNumbers.size != 3) return null

        val timeNumbers = timePart.split(':').mapNotNull { it.toIntOrNull() }
        if (timeNumbers.size < 2) return null

        var year: Int
        var month: Int
        var day: Int

        val n1 = dateNumbers[0]
        val n2 = dateNumbers[1]
        val n3 = dateNumbers[2]

        if (n1 > 1000) {
            // yyyy-MM-dd
            year = n1
            month = n2
            day = n3
        } else {
            // dd/MM/yyyy or MM/dd/yyyy or dd/MM/yy
            year = if (n3 < 100) 2000 + n3 else n3
            if (n1 > 12) {
                day = n1
                month = n2
            } else if (n2 > 12) {
                day = n2
                month = n1
            } else {
                // Default: day/month/year (standard in Egypt and MENA)
                day = n1
                month = n2
            }
        }

        if (month !in 1..12 || day !in 1..31) return null

        var hour = timeNumbers[0]
        val minute = timeNumbers[1]
        val second = if (timeNumbers.size >= 3) timeNumbers[2] else 0

        if (periodPart != null) {
            val p = periodPart.lowercase().replace(".", "")
            if (p == "pm" || p == "م" || p == "مساء" || p == "مساءً") {
                if (hour in 1..11) hour += 12
            } else if (p == "am" || p == "ص" || p == "صباحا" || p == "صباحاً") {
                if (hour == 12) hour = 0
            }
        }

        return try {
            val cal = Calendar.getInstance(defaultTimeZone)
            cal.clear()
            cal.set(Calendar.YEAR, year)
            cal.set(Calendar.MONTH, month - 1)
            cal.set(Calendar.DAY_OF_MONTH, day)
            cal.set(Calendar.HOUR_OF_DAY, hour)
            cal.set(Calendar.MINUTE, minute)
            cal.set(Calendar.SECOND, second)
            cal.set(Calendar.MILLISECOND, 0)
            cal.timeInMillis
        } catch (e: Exception) {
            null
        }
    }

    private fun sanitizeLine(line: String): String {
        return line
            .replace(BIDI_REGEX, "")
            .replace('\u00A0', ' ')
            .replace('\u202F', ' ')
            .trim()
    }

    private fun sanitizeDigits(input: String): String {
        val sb = StringBuilder(input.length)
        for (ch in input) {
            when (ch) {
                in '٠'..'٩' -> sb.append((ch - '٠' + '0'.code).toChar())
                in '۰'..'۹' -> sb.append((ch - '۰' + '0'.code).toChar())
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    companion object {
        const val SYSTEM_SENDER = "System"

        private val BIDI_REGEX = Regex("[\u200E\u200F\u202A-\u202E\u2066-\u2069\uFEFF]")

        private val MEDIA_MARKERS = listOf(
            "<media omitted>",
            "<تم استبعاد الوسائط>",
            "<الملف الصوتي مفقود>",
            "image omitted",
            "audio omitted",
            "video omitted",
            "sticker omitted",
            "document omitted",
            "contact card omitted",
            "مكالمة صوتية فائتة",
            "missed voice call",
            "missed video call"
        )

        fun isMediaAttachment(text: String): Boolean {
            val lower = text.lowercase().trim()
            return MEDIA_MARKERS.any { lower.contains(it) }
        }
    }
}
