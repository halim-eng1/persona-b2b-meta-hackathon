package com.example.persona.features.chat_import.data.parser

import com.example.persona.features.chat_import.domain.model.ChatFormat
import org.junit.Assert.assertEquals
import org.junit.Test

class FormatSnifferTest {

    private val sniffer = FormatSniffer()

    @Test
    fun detect_englishUsLines_returnsEnglishUs() {
        val lines = List(10) { "3/25/24, 4:54 PM - Sender: Message $it" }
        val result = sniffer.detect(lines)
        assertEquals(ChatFormat.WHATSAPP_ENGLISH_US, result)
    }

    @Test
    fun detect_arabicLines_returnsArabic() {
        val lines = List(10) { "[12/05/2024, 11:30 ص] Sender: Message $it" }
        val result = sniffer.detect(lines)
        assertEquals(ChatFormat.WHATSAPP_ARABIC, result)
    }

    @Test
    fun detect_europeanLines_returnsEuropean() {
        val lines = List(10) { "05.12.2024, 11:30 - Sender: Message $it" }
        val result = sniffer.detect(lines)
        assertEquals(ChatFormat.WHATSAPP_EUROPEAN, result)
    }

    @Test
    fun detect_allSystemMessages_returnsUnknown() {
        val lines = listOf(
            "Messages and calls are end-to-end encrypted",
            "الرسائل والمكالمات مشفرة",
            "Sender created group 'Test'",
            "Sender changed the subject to 'New Name'",
            "added you",
            "left",
            "removed",
            "changed this group's icon",
            "Your security code changed"
        )
        val result = sniffer.detect(lines)
        assertEquals(ChatFormat.UNKNOWN, result)
    }

    @Test
    fun detect_emptyInput_returnsUnknown() {
        val result = sniffer.detect(emptyList())
        assertEquals(ChatFormat.UNKNOWN, result)
    }

    @Test
    fun detect_bomPrefix_detectsCorrectly() {
        val lines = listOf(
            "\uFEFF3/25/24, 4:54 PM - Sender: Hello",
            "3/25/24, 4:54 PM - Sender: How are you?",
            "3/25/24, 4:55 PM - Sender: Bye"
        )
        val result = sniffer.detect(lines)
        assertEquals(ChatFormat.WHATSAPP_ENGLISH_US, result)
    }

    @Test
    fun detect_mixedFormats_returnsHighestVote() {
        val lines = listOf(
            "3/25/24, 4:54 PM - Sender: EN 1",
            "3/25/24, 4:54 PM - Sender: EN 2",
            "3/25/24, 4:54 PM - Sender: EN 3",
            "[12/05/2024, 11:30 ص] Sender: AR 1",
            "[12/05/2024, 11:30 ص] Sender: AR 2",
            "[12/05/2024, 11:30 ص] Sender: AR 3",
            "05.12.2024, 11:30 - Sender: EU 1",
            "05.12.2024, 11:30 - Sender: EU 2",
            "05.12.2024, 11:30 - Sender: EU 3",
            "05.12.2024, 11:30 - Sender: EU 4"
        )
        val result = sniffer.detect(lines)
        assertEquals(ChatFormat.WHATSAPP_EUROPEAN, result)
    }

    @Test
    fun detect_onlyOneMatchingLine_returnsUnknown() {
        val lines = listOf(
            "3/25/24, 4:54 PM - Sender: Only one EN match",
            "This is a continuation line without a timestamp",
            "Another line that does not match any format"
        )
        val result = sniffer.detect(lines)
        assertEquals(ChatFormat.UNKNOWN, result)
    }
}
