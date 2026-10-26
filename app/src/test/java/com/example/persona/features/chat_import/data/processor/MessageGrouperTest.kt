package com.example.persona.features.chat_import.data.processor

import com.example.persona.features.chat_import.domain.model.ChatFormat
import com.example.persona.features.chat_import.domain.model.ConversationContext
import com.example.persona.features.chat_import.domain.model.IntentType
import com.example.persona.features.chat_import.domain.model.TopicType
import com.example.persona.features.chat_import.domain.model.SentimentType
import com.example.persona.features.chat_import.domain.model.ParsedMessage
import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageGrouperTest {

    private val grouper = MessageGrouper()
    private val dummyContext = ConversationContext(IntentType.GENERAL, TopicType.GENERAL, SentimentType.NEUTRAL, 0.4f)

    @Test
    fun group_emptyList_returnsEmptyList() {
        val result = grouper.group(emptyList())
        assertTrue(result.isEmpty())
    }

    @Test
    fun group_singleMessage_returnsAsIs() {
        val message = ParsedMessage(
            senderName = "Alice",
            messageText = "Hello",
            timestamp = Instant.fromEpochMilliseconds(1000L),
            isUserMessage = false,
            context = dummyContext,
            sourceFormat = ChatFormat.WHATSAPP_ENGLISH_US,
            normalizedText = null
        )

        val result = grouper.group(listOf(message))
        assertEquals(1, result.size)
        assertEquals("Hello", result.first().messageText)
        assertFalse(result.first().isGrouped)
    }

    @Test
    fun group_consecutiveWithinWindow_mergesSuccessfully() {
        val baseTime = 1000000L
        val messages = listOf(
            createMessage("Alice", "Hello", baseTime),
            createMessage("Alice", "World", baseTime + 30000L), // 30s later
            createMessage("Alice", "How are you?", baseTime + 90000L) // 90s later
        )

        val result = grouper.group(messages)
        assertEquals(1, result.size)
        val merged = result.first()
        assertEquals("Hello\nWorld\nHow are you?", merged.messageText)
        assertTrue(merged.isGrouped)
        assertEquals(Instant.fromEpochMilliseconds(baseTime), merged.timestamp)
    }

    @Test
    fun group_consecutiveOutsideWindow_doesNotMerge() {
        val baseTime = 1000000L
        val messages = listOf(
            createMessage("Alice", "Hello", baseTime),
            createMessage("Alice", "World", baseTime + 180000L) // 3 mins later
        )

        val result = grouper.group(messages)
        assertEquals(2, result.size)
        assertFalse(result[0].isGrouped)
        assertFalse(result[1].isGrouped)
        assertEquals("Hello", result[0].messageText)
        assertEquals("World", result[1].messageText)
    }

    @Test
    fun group_alternatingSenders_doesNotMerge() {
        val baseTime = 1000000L
        val messages = listOf(
            createMessage("Alice", "Hello", baseTime),
            createMessage("Bob", "Hi", baseTime + 10000L),
            createMessage("Alice", "How are you?", baseTime + 20000L)
        )

        val result = grouper.group(messages)
        assertEquals(3, result.size)
        assertTrue(result.all { !it.isGrouped })
    }

    private fun createMessage(sender: String, text: String, timeMs: Long): ParsedMessage {
        return ParsedMessage(
            senderName = sender,
            messageText = text,
            timestamp = Instant.fromEpochMilliseconds(timeMs),
            isUserMessage = false,
            context = dummyContext,
            sourceFormat = ChatFormat.WHATSAPP_ENGLISH_US,
            normalizedText = null
        )
    }
}
