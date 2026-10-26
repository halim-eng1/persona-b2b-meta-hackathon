package com.example.persona.features.auto_reply.data.repository

import com.example.persona.features.auto_reply.domain.model.ProcessedNotification
import com.example.persona.features.auto_reply.domain.model.WhatsAppNotification
import com.example.persona.features.chat_import.data.normalizer.ArabicTextNormalizer
import com.example.persona.features.chat_import.data.processor.ContextDetector
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationProcessorImpl @Inject constructor(
    private val normalizer: ArabicTextNormalizer,
    private val contextDetector: ContextDetector
) {
    /**
     * Extracts text, normalizes it, detects conversation context,
     * and returns a context-enriched ProcessedNotification object.
     */
    fun process(notification: WhatsAppNotification): ProcessedNotification {
        val originalText = notification.text
        val normalizedText = normalizer.normalize(originalText) ?: originalText
        val context = contextDetector.detect(normalizedText)
        
        return ProcessedNotification(
            notificationKey = notification.notificationKey,
            contactName = notification.title,
            originalText = originalText,
            normalizedText = normalizedText,
            context = context,
            postTime = notification.postTime
        )
    }
}
