package com.example.persona.features.auto_reply.data.repository

import com.example.persona.features.auto_reply.domain.model.WhatsAppNotification
import com.example.persona.features.chat_import.data.normalizer.ArabicTextNormalizer
import com.example.persona.features.chat_import.data.processor.ContextDetector
import com.example.persona.features.chat_import.domain.model.IntentType
import com.example.persona.features.chat_import.domain.model.TopicType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationProcessorImplTest {

    private val normalizer = ArabicTextNormalizer()
    private val contextDetector = ContextDetector(normalizer)
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
        assertEquals(IntentType.GREETING, processed.context.intent)
        assertEquals(TopicType.STATUS, processed.context.topic)
        assertEquals(987654321L, processed.postTime)
    }

    @Test
    fun `conversational text mentioning photo does not flag media indicator`() {
        val conversationalText = "بس انا كنت قصدي ب الصورة اني بحبك انت 😂🫣🤩"
        assertFalse(
            "Conversational text with photo must not flag containsMediaIndicators",
            notificationProcessor.containsMediaIndicators(conversationalText)
        )
        assertFalse(
            "Conversational text with photo must not flag isImageIndicator",
            notificationProcessor.isImageIndicator(conversationalText)
        )

        val englishConversational = "I will send a photo later today"
        assertFalse(notificationProcessor.containsMediaIndicators(englishConversational))
        assertFalse(notificationProcessor.isImageIndicator(englishConversational))
    }

    @Test
    fun `system emojis and system strings correctly flag media indicators`() {
        assertTrue(notificationProcessor.containsMediaIndicators("📷 Sent a photo"))
        assertTrue(notificationProcessor.containsMediaIndicators("Sent a photo"))
        assertTrue(notificationProcessor.containsMediaIndicators("Photo"))
        assertTrue(notificationProcessor.containsMediaIndicators("صورة"))
        assertTrue(notificationProcessor.containsMediaIndicators("📸"))
        assertTrue(notificationProcessor.containsMediaIndicators("🎤 Voice message (0:02)"))
        assertTrue(notificationProcessor.containsMediaIndicators("Voice message"))
        assertTrue(notificationProcessor.containsMediaIndicators("رسالة صوتية"))
    }
}
