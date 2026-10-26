package com.example.persona.features.auto_reply.data.repository

import com.example.persona.features.auto_reply.domain.model.WhatsAppNotification
import com.example.persona.features.chat_import.data.normalizer.ArabicTextNormalizer
import com.example.persona.features.chat_import.data.processor.ContextDetector
import com.example.persona.features.chat_import.domain.model.IntentType
import com.example.persona.features.chat_import.domain.model.TopicType
import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationProcessorImplTest {

    private val normalizer = ArabicTextNormalizer()
    private val contextDetector = ContextDetector()
    private val notificationProcessor = NotificationProcessorImpl(normalizer, contextDetector)

    @Test
    fun process_englishNotification_extractsCorrectly() {
        val notification = WhatsAppNotification(
            title = "John Doe",
            text = "Where are you?",
            postTime = 123456789L,
            notificationKey = "key_john_123"
        )

        val processed = notificationProcessor.process(notification)

        assertEquals("key_john_123", processed.notificationKey)
        assertEquals("John Doe", processed.contactName)
        assertEquals("Where are you?", processed.originalText)
        assertEquals("Where are you?", processed.normalizedText)
        assertEquals(IntentType.QUESTION, processed.context.intent)
        assertEquals(TopicType.LOCATION, processed.context.topic)
        assertEquals(123456789L, processed.postTime)
    }

    @Test
    fun process_arabicNotification_normalizesAndDetectsContext() {
        val notification = WhatsAppNotification(
            title = "أحمد",
            text = "أهلاً بك، كَيْفَ حَالُكَ؟",
            postTime = 987654321L,
            notificationKey = "key_ahmed_456"
        )

        val processed = notificationProcessor.process(notification)

        assertEquals("key_ahmed_456", processed.notificationKey)
        assertEquals("أحمد", processed.contactName)
        assertEquals("أهلاً بك، كَيْفَ حَالُكَ؟", processed.originalText)
        assertEquals("اهلا بك، كيف حالك؟", processed.normalizedText)
        assertEquals(IntentType.QUESTION, processed.context.intent)
        assertEquals(TopicType.STATUS, processed.context.topic)
        assertEquals(987654321L, processed.postTime)
    }
}
