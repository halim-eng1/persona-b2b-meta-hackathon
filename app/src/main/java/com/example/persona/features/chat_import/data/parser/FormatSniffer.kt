package com.example.persona.features.chat_import.data.parser

import com.example.persona.features.chat_import.domain.model.ChatFormat
import javax.inject.Inject

class FormatSniffer @Inject constructor() {

    companion object {
        private const val SNIFF_LINE_COUNT = 10
        private val SYSTEM_MESSAGE_INDICATORS = listOf(
            "Messages and calls are end-to-end encrypted",
            "الرسائل والمكالمات مشفرة",
            "created group",
            "changed the subject",
            "added you",
            "left",
            "removed",
            "changed this group's icon",
            "Your security code changed"
        )
    }

    /**
     * Reads up to [SNIFF_LINE_COUNT] non-system, non-empty lines and
     * returns the detected [ChatFormat] with a confidence vote count.
     */
    fun detect(lines: List<String>): ChatFormat {
        val candidateLines = lines
            .map { it.trimStart('\uFEFF') }  // Strip BOM
            .filter { it.isNotBlank() }
            .filterNot { line -> SYSTEM_MESSAGE_INDICATORS.any { line.contains(it, ignoreCase = true) } }
            .take(SNIFF_LINE_COUNT)

        if (candidateLines.isEmpty()) return ChatFormat.UNKNOWN

        val votes = mutableMapOf(
            ChatFormat.WHATSAPP_ENGLISH_US to 0,
            ChatFormat.WHATSAPP_ARABIC to 0,
            ChatFormat.WHATSAPP_EUROPEAN to 0
        )

        for (line in candidateLines) {
            when {
                ChatFormat.REGEX_ENGLISH_US.containsMatchIn(line) ->
                    votes[ChatFormat.WHATSAPP_ENGLISH_US] = votes[ChatFormat.WHATSAPP_ENGLISH_US]!! + 1
                ChatFormat.REGEX_ARABIC.containsMatchIn(line) ->
                    votes[ChatFormat.WHATSAPP_ARABIC] = votes[ChatFormat.WHATSAPP_ARABIC]!! + 1
                ChatFormat.REGEX_EUROPEAN.containsMatchIn(line) ->
                    votes[ChatFormat.WHATSAPP_EUROPEAN] = votes[ChatFormat.WHATSAPP_EUROPEAN]!! + 1
            }
        }

        val winner = votes.maxByOrNull { it.value }
        return if (winner != null && winner.value >= 2) winner.key else ChatFormat.UNKNOWN
    }
}
