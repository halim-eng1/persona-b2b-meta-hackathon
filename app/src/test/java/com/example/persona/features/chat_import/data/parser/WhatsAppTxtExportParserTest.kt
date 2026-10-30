package com.example.persona.features.chat_import.data.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class WhatsAppTxtExportParserTest {

    private val timeZone = TimeZone.getTimeZone("UTC")
    private val parser = WhatsAppTxtExportParser(defaultTimeZone = timeZone)

    @Test
    fun `parses Android 24h format single line message`() {
        val transcript = "15/04/2026, 14:30 - Ahmed: السلام عليكم يا فندم"
        val messages = parser.parse(transcript)

        assertEquals(1, messages.size)
        val msg = messages.first()
        assertEquals("Ahmed", msg.senderName)
        assertEquals("السلام عليكم يا فندم", msg.messageText)
        assertFalse(msg.isSystemMessage)
        assertFalse(msg.hasMediaAttachment)

        val cal = Calendar.getInstance(timeZone).apply { timeInMillis = msg.timestampMs }
        assertEquals(2026, cal.get(Calendar.YEAR))
        assertEquals(Calendar.APRIL, cal.get(Calendar.MONTH))
        assertEquals(15, cal.get(Calendar.DAY_OF_MONTH))
        assertEquals(14, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(30, cal.get(Calendar.MINUTE))
    }

    @Test
    fun `parses Android 12h format with PM and AM`() {
        val transcript = """
            15/04/2026, 02:30 PM - Ahmed: السعر كام؟
            15/04/2026, 09:15 AM - Store Bot: أهلاً بك يا فندم
        """.trimIndent()

        val messages = parser.parse(transcript)
        assertEquals(2, messages.size)

        val calPm = Calendar.getInstance(timeZone).apply { timeInMillis = messages[0].timestampMs }
        assertEquals(14, calPm.get(Calendar.HOUR_OF_DAY))

        val calAm = Calendar.getInstance(timeZone).apply { timeInMillis = messages[1].timestampMs }
        assertEquals(9, calAm.get(Calendar.HOUR_OF_DAY))
    }

    @Test
    fun `parses Android Arabic timestamps with Indic digits and meem or saad`() {
        val transcript = "١٥/٠٤/٢٠٢٦، ٠٢:٣٠ م - أحمد مصطفى: عايز اعرف مقاس القميص"
        val messages = parser.parse(transcript)

        assertEquals(1, messages.size)
        val msg = messages.first()
        assertEquals("أحمد مصطفى", msg.senderName)
        assertEquals("عايز اعرف مقاس القميص", msg.messageText)

        val cal = Calendar.getInstance(timeZone).apply { timeInMillis = msg.timestampMs }
        assertEquals(2026, cal.get(Calendar.YEAR))
        assertEquals(Calendar.APRIL, cal.get(Calendar.MONTH))
        assertEquals(15, cal.get(Calendar.DAY_OF_MONTH))
        assertEquals(14, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(30, cal.get(Calendar.MINUTE))
    }

    @Test
    fun `parses iOS format in brackets`() {
        val transcript = """
            [15/04/2026, 14:30:00] Ahmed: السعر كام؟
            [15/04/2026, 14:31:00] Store: ٢٥٠ جنيه يا فندم
        """.trimIndent()

        val messages = parser.parse(transcript)
        assertEquals(2, messages.size)
        assertEquals("Ahmed", messages[0].senderName)
        assertEquals("السعر كام؟", messages[0].messageText)
        assertEquals("Store", messages[1].senderName)
        assertEquals("٢٥٠ جنيه يا فندم", messages[1].messageText)
    }

    @Test
    fun `parses multi-line messages correctly`() {
        val transcript = """
            15/04/2026, 14:00 - Ahmed: السطر الأول
            السطر الثاني
            السطر الثالث
            15/04/2026, 14:05 - Bot: تمام
        """.trimIndent()

        val messages = parser.parse(transcript)
        assertEquals(2, messages.size)
        assertEquals("السطر الأول\nالسطر الثاني\nالسطر الثالث", messages[0].messageText)
        assertEquals("تمام", messages[1].messageText)
    }

    @Test
    fun `identifies system messages without sender colon`() {
        val transcript = """
            15/04/2026, 14:00 - Messages and calls are end-to-end encrypted.
            15/04/2026, 14:01 - تم تغيير رمز الأمان الخاص بك
            15/04/2026, 14:10 - Ahmed: السلام عليكم
        """.trimIndent()

        val messages = parser.parse(transcript)
        assertEquals(3, messages.size)

        assertTrue(messages[0].isSystemMessage)
        assertEquals(WhatsAppTxtExportParser.SYSTEM_SENDER, messages[0].senderName)

        assertTrue(messages[1].isSystemMessage)
        assertEquals(WhatsAppTxtExportParser.SYSTEM_SENDER, messages[1].senderName)

        assertFalse(messages[2].isSystemMessage)
        assertEquals("Ahmed", messages[2].senderName)
    }

    @Test
    fun `detects media attachments in English and Arabic`() {
        val transcript = """
            15/04/2026, 14:30 - Ahmed: <Media omitted>
            15/04/2026, 14:31 - Sarah: <تم استبعاد الوسائط>
            15/04/2026, 14:32 - Kareem: صورة المنتج
        """.trimIndent()

        val messages = parser.parse(transcript)
        assertEquals(3, messages.size)

        assertTrue(messages[0].hasMediaAttachment)
        assertTrue(messages[1].hasMediaAttachment)
        assertFalse(messages[2].hasMediaAttachment)
    }

    @Test
    fun `strips invisible BiDi Unicode marks from lines`() {
        val transcript = "\u200E15/04/2026, 14:30 - \u200FAhmed\u200E: مرحبا بكم"
        val messages = parser.parse(transcript)

        assertEquals(1, messages.size)
        assertEquals("Ahmed", messages[0].senderName)
        assertEquals("مرحبا بكم", messages[0].messageText)
    }

    @Test
    fun `streaming parseStream processes lazy sequence without buffering whole file`() {
        val sequence = sequenceOf(
            "15/04/2026, 10:00 - User1: Line 1",
            "15/04/2026, 10:01 - User2: Line 2"
        )
        val parsedSeq = parser.parseStream(sequence)
        val result = parsedSeq.take(1).toList()

        assertEquals(1, result.size)
        assertEquals("User1", result[0].senderName)
    }

    @Test
    fun `isSupported returns true for valid WhatsApp export and false for markdown`() {
        val waExport = """
            15/04/2026, 14:30 - Customer: السلام عليكم
            15/04/2026, 14:31 - Store: وعليكم السلام أهلاً بك
        """.trimIndent()
        assertTrue(parser.isSupported(waExport))

        val markdownText = """
            # سياسة الشحن والتوصيل
            الشحن للقاهرة والجيزة 40 جنيه خلال 48 ساعة.
            ## الاستبدال والاسترجاع
            الاسترجاع متاح خلال 14 يوم.
        """.trimIndent()
        assertFalse(parser.isSupported(markdownText))
    }

    @Test
    fun `extractKnowledgeChunks converts alternating conversation into structured QA chunks`() {
        val chat = """
            15/04/2026, 14:30 - Customer: السلام عليكم، التيشيرت الأسود بكام؟
            15/04/2026, 14:31 - Store: وعليكم السلام يا فندم، التيشيرت سعره 250 جنيه ومتاح مقاسات L و XL
            15/04/2026, 14:32 - Customer: <Media omitted>
            15/04/2026, 14:33 - Customer: وعندكم توصيل للإسكندرية؟
            15/04/2026, 14:34 - Store: أيوة متاح شحن للإسكندرية بـ 50 جنيه والتوصيل خلال 3 أيام
        """.trimIndent()

        val chunks = parser.extractKnowledgeChunks(chat, "chat_sample.txt")
        assertEquals(2, chunks.size)

        assertTrue(chunks[0].chunkText.contains("سؤال: السلام عليكم، التيشيرت الأسود بكام؟"))
        assertTrue(chunks[0].chunkText.contains("إجابة: وعليكم السلام يا فندم، التيشيرت سعره 250 جنيه ومتاح مقاسات L و XL"))
        assertEquals("CORE_DOCUMENT", chunks[0].sourceType)
        assertEquals("chat_sample.txt", chunks[0].sourceFileName)

        assertTrue(chunks[1].chunkText.contains("وعندكم توصيل للإسكندرية؟"))
        assertTrue(chunks[1].chunkText.contains("أيوة متاح شحن للإسكندرية بـ 50 جنيه والتوصيل خلال 3 أيام"))
    }
}
