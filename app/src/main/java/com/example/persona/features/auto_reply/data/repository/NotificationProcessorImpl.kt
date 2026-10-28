package com.example.persona.features.auto_reply.data.repository

import android.util.Log
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

        val hasImage = isImageIndicator(originalText)
        val hasAudio = isVoiceMessageIndicator(originalText)
        if (hasImage || hasAudio || containsMediaIndicators(originalText)) {
            Log.d("PersonaMedia", "📥 Inbound media turn detected: text='$originalText', hasImageIndicator=$hasImage, hasAudioIndicator=$hasAudio")
        }

        return ProcessedNotification(
            notificationKey = notification.notificationKey,
            contactName = notification.title,
            originalText = originalText,
            normalizedText = normalizedText,
            context = context,
            postTime = notification.postTime,
            sourceClass = notification.sourceClass,
            mediaFilePath = notification.mediaFilePath
        )
    }

    fun containsMediaIndicators(text: String): Boolean = Companion.containsMediaIndicators(text)
    fun isImageIndicator(text: String): Boolean = Companion.isImageIndicator(text)
    fun isVoiceMessageIndicator(text: String): Boolean = Companion.isVoiceMessageIndicator(text)

    companion object {
        private val MEDIA_SYSTEM_EMOJIS = listOf("📷", "📸", "🎤", "🎵", "📹", "🎥", "📄", "🎬", "🎙️")

        fun isImageIndicator(text: String): Boolean {
            val imageEmojis = listOf("📷", "📸")
            if (imageEmojis.any { text.contains(it) }) return true
            val trimmed = text.trim()
            val lower = trimmed.lowercase()
            return lower == "photo" ||
                   lower == "صورة" ||
                   lower.startsWith("sent a photo") ||
                   lower.contains("sent a photo")
        }

        fun isVoiceMessageIndicator(text: String): Boolean {
            val voiceEmojis = listOf("🎤", "🎙️", "🎵")
            if (voiceEmojis.any { text.contains(it) }) return true
            val trimmed = text.trim()
            val lower = trimmed.lowercase()
            return lower == "voice message" ||
                   lower.startsWith("voice message") ||
                   lower == "voice note" ||
                   lower.startsWith("voice note") ||
                   lower == "رسالة صوتية" ||
                   lower.startsWith("رسالة صوتية") ||
                   lower == "مقطع صوتي" ||
                   lower.startsWith("مقطع صوتي") ||
                   lower == "audio" ||
                   lower.startsWith("audio")
        }

        fun containsMediaIndicators(text: String): Boolean {
            if (text.startsWith("[رسالة صوتية من العميل]:")) return false
            if (MEDIA_SYSTEM_EMOJIS.any { text.contains(it) }) return true
            val trimmed = text.trim()
            val lower = trimmed.lowercase()
            return lower == "photo" ||
                   lower == "صورة" ||
                   lower.startsWith("sent a photo") ||
                   lower.contains("sent a photo") ||
                   lower.startsWith("voice message") ||
                   lower.contains("voice message") ||
                   lower == "voice note" ||
                   lower.startsWith("voice note") ||
                   lower == "رسالة صوتية" ||
                   lower.startsWith("رسالة صوتية") ||
                   lower == "مقطع صوتي" ||
                   lower.startsWith("مقطع صوتي") ||
                   lower == "audio" ||
                   lower.startsWith("audio")
        }
    }
}
