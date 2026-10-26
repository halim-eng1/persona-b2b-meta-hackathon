package com.example.persona.features.chat_import.data.parser

import com.example.persona.features.chat_import.domain.model.ChatFormat
import com.example.persona.features.chat_import.domain.model.GroupChatNotSupportedException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class ChatFileParserTest {

    private lateinit var sniffer: FormatSniffer
    private lateinit var lineParser: ChatLineParser
    private lateinit var fileParser: ChatFileParser

    @Before
    fun setUp() {
        sniffer = FormatSniffer()
        lineParser = ChatLineParser()
        fileParser = ChatFileParser(sniffer, lineParser)
    }

    @Test
    fun parse_validEnglishUs_parsesSuccessfully() {
        val lines = listOf(
            "3/25/24, 4:54 PM - Alice: Hello Bob",
            "3/25/24, 4:55 PM - Bob: Hi Alice",
            "3/25/24, 4:56 PM - Alice: How is it going?",
            "3/25/24, 4:57 PM - Bob: Good!",
            "3/25/24, 4:58 PM - Bob: Talk to you later"
        )

        val result = fileParser.parse(lines.asSequence())
        assertTrue(result is ParseFileResult.Success)
        val success = result as ParseFileResult.Success
        assertEquals(ChatFormat.WHATSAPP_ENGLISH_US, success.format)
        assertEquals(5, success.messages.size)
        assertEquals("Bob", success.userName)
        assertTrue(success.messages[1].isUserMessage) // Bob is user
        assertEquals("Hello Bob", success.messages[0].messageText)
        assertEquals("Alice", success.messages[0].senderName)
    }

    @Test
    fun parse_continuationLines_mergesCorrectly() {
        val lines = listOf(
            "3/25/24, 4:54 PM - Alice: First line of message",
            "Second line of message",
            "Third line",
            "3/25/24, 4:55 PM - Bob: Hello"
        )

        val result = fileParser.parse(lines.asSequence())
        assertTrue(result is ParseFileResult.Success)
        val success = result as ParseFileResult.Success
        assertEquals(2, success.messages.size)
        assertEquals("First line of message\nSecond line of message\nThird line", success.messages[0].messageText)
    }

    @Test
    fun parse_mediaOmitted_skipsThem() {
        val lines = listOf(
            "3/25/24, 4:54 PM - Alice: <Media omitted>",
            "3/25/24, 4:55 PM - Alice: image omitted",
            "3/25/24, 4:56 PM - Alice: This message was deleted",
            "3/25/24, 4:57 PM - Alice: Actual text message",
            "3/25/24, 4:58 PM - Bob: تم حذف هذه الرسالة"
        )

        val result = fileParser.parse(lines.asSequence())
        assertTrue(result is ParseFileResult.Success)
        val success = result as ParseFileResult.Success
        assertEquals(1, success.messages.size)
        assertEquals("Actual text message", success.messages[0].messageText)
    }

    @Test
    fun parse_knownUserNameOverride_setsCorrectly() {
        val lines = listOf(
            "3/25/24, 4:54 PM - Alice: Hello",
            "3/25/24, 4:55 PM - Bob: Hi"
        )

        val result = fileParser.parse(lines.asSequence(), knownUserName = "Alice")
        assertTrue(result is ParseFileResult.Success)
        val success = result as ParseFileResult.Success
        assertEquals("Alice", success.userName)
        assertTrue(success.messages[0].isUserMessage)
    }

    @Test
    fun parse_groupChatThreeSenders_throwsException() {
        val lines = listOf(
            "3/25/24, 4:54 PM - Alice: Hi",
            "3/25/24, 4:55 PM - Bob: Hello",
            "3/25/24, 4:56 PM - Charlie: Hey everyone"
        )

        try {
            fileParser.parse(lines.asSequence())
            fail("Should have thrown GroupChatNotSupportedException")
        } catch (e: GroupChatNotSupportedException) {
            assertTrue(e.message!!.contains("not supported"))
        }
    }

    @Test
    fun parse_emptyFile_returnsFormatError() {
        val result = fileParser.parse(emptySequence())
        assertTrue(result is ParseFileResult.FormatError)
    }

    @Test
    fun parse_largeFileInSequence_executesQuicklyAndOOMSafe() {
        val largeSequence = generateSequence(0) { it + 1 }
            .map {
                val sender = if (it % 2 == 0) "Alice" else "Bob"
                "3/25/24, 4:54 PM - $sender: message $it"
            }
            .take(10000)

        val start = System.currentTimeMillis()
        val result = fileParser.parse(largeSequence)
        val end = System.currentTimeMillis()

        assertTrue(result is ParseFileResult.Success)
        assertEquals(10000, (result as ParseFileResult.Success).messages.size)
        assertTrue("Execution took too long: ${end - start}ms", (end - start) < 2000)
    }
}
