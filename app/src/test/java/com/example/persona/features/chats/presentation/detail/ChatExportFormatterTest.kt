package com.example.persona.features.chats.presentation.detail

import com.example.persona.core.database.entity.ChatHistoryEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class ChatExportFormatterTest {

    private val formatter = ChatExportFormatter()

    @Test
    fun `calculateSinceTimestamp returns 0 for ALL_TIME`() {
        val now = 1730000000000L
        assertEquals(0L, formatter.calculateSinceTimestamp(ExportTimeRange.ALL_TIME, now))
    }

    @Test
    fun `calculateSinceTimestamp returns 2 hours ago for LAST_TWO_HOURS`() {
        val now = 1730000000000L
        val expected = now - (2 * 60 * 60 * 1000L)
        assertEquals(expected, formatter.calculateSinceTimestamp(ExportTimeRange.LAST_TWO_HOURS, now))
    }

    @Test
    fun `calculateSinceTimestamp returns start of day for TODAY`() {
        val now = 1730000000000L
        val cal = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        assertEquals(cal.timeInMillis, formatter.calculateSinceTimestamp(ExportTimeRange.TODAY, now))
    }

    @Test
    fun `calculateClearSinceTimestamp returns expected boundaries`() {
        val now = 1730000000000L
        assertEquals(0L, formatter.calculateClearSinceTimestamp(ClearChatTimeRange.ALL_TIME, now))
        assertEquals(now - (1 * 60 * 60 * 1000L), formatter.calculateClearSinceTimestamp(ClearChatTimeRange.LAST_HOUR, now))
        assertEquals(now - (2 * 60 * 60 * 1000L), formatter.calculateClearSinceTimestamp(ClearChatTimeRange.LAST_TWO_HOURS, now))

        val cal = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        assertEquals(cal.timeInMillis, formatter.calculateClearSinceTimestamp(ClearChatTimeRange.TODAY, now))
    }

    @Test
    fun `buildExportTranscript contains all required multilingual labels and messages`() {
        val messages = listOf(
            ChatHistoryEntity(
                id = 1L,
                contactName = "كريم",
                sourceClass = "BUSINESS",
                messageText = "مرحبا، هل المنتج متوفر؟",
                isFromUser = true,
                timestamp = 1000000L,
                senderType = "CUSTOMER"
            ),
            ChatHistoryEntity(
                id = 2L,
                contactName = "كريم",
                sourceClass = "BUSINESS",
                messageText = "نعم متوفر بسعر 100 ج.م",
                isFromUser = false,
                timestamp = 2000000L,
                senderType = "AI_AGENT"
            ),
            ChatHistoryEntity(
                id = 3L,
                contactName = "كريم",
                sourceClass = "BUSINESS",
                messageText = "تم إرسال الرابط",
                isFromUser = false,
                timestamp = 3000000L,
                senderType = "MERCHANT_MANUAL"
            )
        )

        val transcript = formatter.buildExportTranscript(
            contactName = "كريم",
            sourceClass = "BUSINESS",
            storeName = "متجر الأناقة",
            botName = "سارة",
            range = ExportTimeRange.ALL_TIME,
            exportTimestampMs = 1730000000000L,
            messages = messages
        )

        assertTrue(transcript.contains("سجل محادثة: كريم"))
        assertTrue(transcript.contains("المتجر: متجر الأناقة"))
        assertTrue(transcript.contains("كامل المحادثة"))
        assertTrue(transcript.contains(ChatExportFormatter.TRANSCRIPT_DIVIDER))
        assertTrue(transcript.contains("العميل: مرحبا، هل المنتج متوفر؟"))
        assertTrue(transcript.contains("سارة: نعم متوفر بسعر 100 ج.م"))
        assertTrue(transcript.contains("التاجر: تم إرسال الرابط"))
    }
}
