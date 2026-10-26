package com.example.persona.features.chat_import.data.parser

import com.example.persona.features.chat_import.domain.model.ChatFormat
import kotlinx.datetime.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

class ChatLineParser @Inject constructor() {

    companion object {
        private val LTR_RTL_CHARS = Regex("[\u200E\u200F\uFEFF]")
    }

    /**
     * Parses a single raw line against the given [format].
     * Returns a [RawParsedLine] if the line starts a new message,
     * or null if it's a continuation of the previous message.
     */
    fun parseLine(line: String, format: ChatFormat): RawParsedLine? {
        val cleanLine = line.replace(LTR_RTL_CHARS, "").trim()
        if (cleanLine.isBlank()) return null

        val regex = when (format) {
            ChatFormat.WHATSAPP_ENGLISH_US -> ChatFormat.REGEX_ENGLISH_US
            ChatFormat.WHATSAPP_ARABIC -> ChatFormat.REGEX_ARABIC
            ChatFormat.WHATSAPP_EUROPEAN -> ChatFormat.REGEX_EUROPEAN
            ChatFormat.UNKNOWN -> return null
        }

        val timestampMatch = regex.find(cleanLine) ?: return null  // continuation line
        val afterTimestamp = cleanLine.substring(timestampMatch.range.last + 1).trim()

        // Extract sender:message — format is "Sender: Message text"
        val colonIndex = afterTimestamp.indexOf(':')
        if (colonIndex == -1) return null  // system message or malformed

        val sender = afterTimestamp.substring(0, colonIndex).trim()
        val messageText = afterTimestamp.substring(colonIndex + 1).trim()

        // Skip media placeholders and empty messages
        if (messageText.isBlank()) return null
        if (isMediaPlaceholder(messageText)) return null

        val timestamp = parseTimestamp(timestampMatch.groupValues[1], format)
            ?: return null

        return RawParsedLine(
            senderName = sender,
            messageText = messageText,
            timestamp = timestamp
        )
    }

    private fun isMediaPlaceholder(text: String): Boolean {
        val cleanText = text.replace(LTR_RTL_CHARS, "").trim().lowercase()
        return cleanText == "<media omitted>" ||
               cleanText == "image omitted" ||
               cleanText == "video omitted" ||
               cleanText == "sticker omitted" ||
               cleanText == "audio omitted" ||
               cleanText == "document omitted" ||
               cleanText == "gif omitted" ||
               cleanText.contains("this message was deleted") ||
               cleanText.contains("you deleted this message") ||
               cleanText.contains("تم حذف هذه الرسالة") ||
               cleanText.contains("تم حذف الرسالة") ||
               cleanText.contains("الوسائط محذوفة") ||
               cleanText.contains("الوسائط غير موجودة") ||
               cleanText == "مستند" ||
               cleanText == "صورة" ||
               cleanText == "فيديو" ||
               cleanText == "ملصق" ||
               cleanText == "رسالة صوتية"
    }

    private fun parseTimestamp(raw: String, format: ChatFormat): Instant? {
        return try {
            when (format) {
                ChatFormat.WHATSAPP_ENGLISH_US -> parseEnglishUsTimestamp(raw)
                ChatFormat.WHATSAPP_ARABIC -> parseArabicTimestamp(raw)
                ChatFormat.WHATSAPP_EUROPEAN -> parseEuropeanTimestamp(raw)
                ChatFormat.UNKNOWN -> null
            }
        } catch (_: Exception) { null }
    }

    private fun parseEnglishUsTimestamp(raw: String): Instant? {
        // Examples: "3/25/24, 4:54 PM", "03/25/2024, 4:54:12 PM", "3/25/24 4:54 PM"
        val clean = raw.replace(LTR_RTL_CHARS, "").replace(",", "").replace(Regex("\\s+"), " ").trim()
        val formatters = listOf(
            "M/d/yy h:mm a",
            "M/d/yyyy h:mm a",
            "M/d/yy h:mm:ss a",
            "M/d/yyyy h:mm:ss a",
            "MM/dd/yy hh:mm a",
            "MM/dd/yyyy hh:mm a",
            "MM/dd/yy hh:mm:ss a",
            "MM/dd/yyyy hh:mm:ss a"
        )
        for (pattern in formatters) {
            try {
                val formatter = DateTimeFormatter.ofPattern(pattern, Locale.US)
                val ldt = LocalDateTime.parse(clean, formatter)
                return Instant.fromEpochMilliseconds(
                    ldt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                )
            } catch (_: Exception) {}
        }
        return null
    }

    private fun parseArabicTimestamp(raw: String): Instant? {
        // Examples: "12/05/2024, 11:30 ص", "12/05/24 11:30 م"
        val clean = raw.replace(LTR_RTL_CHARS, "")
        val translated = clean.replace('ص', 'A').replace('م', 'P').replace("A", "AM").replace("P", "PM")
        return parseEnglishUsTimestamp(translated)
    }

    private fun parseEuropeanTimestamp(raw: String): Instant? {
        // Examples: "05.12.2024, 11:30", "5.12.24, 11:30:15"
        val clean = raw.replace(LTR_RTL_CHARS, "").replace(",", "").replace(Regex("\\s+"), " ").trim()
        val formatters = listOf(
            "d.M.yy H:mm",
            "d.M.yyyy H:mm",
            "d.M.yy H:mm:ss",
            "d.M.yyyy H:mm:ss",
            "dd.MM.yy HH:mm",
            "dd.MM.yyyy HH:mm",
            "dd.MM.yy HH:mm:ss",
            "dd.MM.yyyy HH:mm:ss"
        )
        for (pattern in formatters) {
            try {
                val formatter = DateTimeFormatter.ofPattern(pattern, Locale.US)
                val ldt = LocalDateTime.parse(clean, formatter)
                return Instant.fromEpochMilliseconds(
                    ldt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                )
            } catch (_: Exception) {}
        }
        return null
    }
}

data class RawParsedLine(
    val senderName: String,
    val messageText: String,
    val timestamp: Instant
)
