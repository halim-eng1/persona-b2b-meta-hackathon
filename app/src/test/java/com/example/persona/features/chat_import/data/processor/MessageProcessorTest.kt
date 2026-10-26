package com.example.persona.features.chat_import.data.processor

import com.example.persona.features.chat_import.data.normalizer.ArabicTextNormalizer
import com.example.persona.features.chat_import.domain.model.ChatFormat
import com.example.persona.features.chat_import.domain.model.ConversationContext
import com.example.persona.features.chat_import.domain.model.IntentType
import com.example.persona.features.chat_import.domain.model.TopicType
import com.example.persona.features.chat_import.domain.model.SentimentType
import com.example.persona.features.chat_import.domain.model.ParsedMessage
import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MessageProcessorTest {

    private lateinit var normalizer: ArabicTextNormalizer
    private lateinit var grouper: MessageGrouper
    private lateinit var contextDetector: ContextDetector
    private lateinit var processor: MessageProcessor

    @Before
    fun setUp() {
        normalizer = ArabicTextNormalizer()
        grouper = MessageGrouper()
        contextDetector = ContextDetector()
        processor = MessageProcessor(normalizer, grouper, contextDetector)
    }

    @Test
    fun process_mixedArabicAndEnglish_normalizesGroupsAndDetectsContext() {
        val baseTime = 1000000000L
        val messages = listOf(
            // Consecutive messages from Alice: Greeting then question (should group and resolve to QUESTION)
            createMessage("Alice", "Hello", baseTime),
            createMessage("Alice", "Where are you?", baseTime + 10000L), // 10s gap

            // Bob: Agreement (Arabic normalized)
            createMessage("Bob", "مَرْحَبًا", baseTime + 30000L),
            createMessage("Bob", "أنا في البيت", baseTime + 40000L) // 10s gap
        )

        val result = processor.process(messages)

        // Should group Alice's 2 messages into 1, and Bob's 2 messages into 1
        assertEquals(2, result.size)

        val aliceGroup = result[0]
        assertEquals("Hello\nWhere are you?", aliceGroup.messageText)
        assertTrue(aliceGroup.isGrouped)
        assertEquals(IntentType.QUESTION, aliceGroup.context.intent)
        assertEquals(TopicType.LOCATION, aliceGroup.context.topic)
        assertEquals(0.9f, aliceGroup.context.confidence, 0.001f) // Question mark should set it to 0.9f

        val bobGroup = result[1]
        assertEquals("مرحبا انا في البيت", bobGroup.normalizedText) // Arabic normalized
        assertTrue(bobGroup.isGrouped)
        assertEquals(TopicType.LOCATION, bobGroup.context.topic) // "البيت" is location
    }

    private fun createMessage(sender: String, text: String, timeMs: Long): ParsedMessage {
        return ParsedMessage(
            senderName = sender,
            messageText = text,
            timestamp = Instant.fromEpochMilliseconds(timeMs),
            isUserMessage = false,
            context = ConversationContext(IntentType.GENERAL, TopicType.GENERAL, SentimentType.NEUTRAL, 0.4f),
            sourceFormat = ChatFormat.WHATSAPP_ENGLISH_US,
            normalizedText = null
        )
    }
}
