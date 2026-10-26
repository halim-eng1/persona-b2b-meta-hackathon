package com.example.persona.features.chat_import.data.processor

import com.example.persona.features.chat_import.domain.model.IntentType
import com.example.persona.features.chat_import.domain.model.TopicType
import com.example.persona.features.chat_import.domain.model.SentimentType
import com.example.persona.features.chat_import.data.normalizer.ArabicTextNormalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextDetectorTest {

    private val detector = ContextDetector(ArabicTextNormalizer())

    @Test
    fun detect_simpleGreeting_returnsGreetingIntent() {
        val context = detector.detect("Hello")
        assertEquals(IntentType.GREETING, context.intent)
        assertTrue(context.confidence >= 0.7f)
    }

    @Test
    fun detect_questionMarkQuestion_returnsQuestionWithHighConfidence() {
        val context = detector.detect("Where are you?")
        assertEquals(IntentType.QUESTION, context.intent)
        assertEquals(TopicType.LOCATION, context.topic)
        assertEquals(0.9f, context.confidence, 0.001f)
    }

    @Test
    fun detect_arabicQuestionMark_returnsQuestion() {
        val context = detector.detect("شلونك اليوم؟")
        assertEquals(IntentType.QUESTION, context.intent)
        assertEquals(TopicType.STATUS, context.topic)
        assertEquals(0.9f, context.confidence, 0.001f)
    }

    @Test
    fun detect_simpleClosing_returnsClosingIntent() {
        val context = detector.detect("see you later")
        assertEquals(IntentType.CLOSING, context.intent)
        assertTrue(context.confidence >= 0.7f)
    }

    @Test
    fun detect_arabicAgreement_returnsAgreementIntent() {
        val context = detector.detect("تمام")
        assertEquals(IntentType.AGREEMENT, context.intent)
        assertEquals(SentimentType.POSITIVE, context.sentiment)
        assertEquals(0.8f, context.confidence, 0.001f)
    }

    @Test
    fun detect_arbitraryStatement_returnsGeneralFallback() {
        val context = detector.detect("I went to the store and bought groceries")
        assertEquals(IntentType.GENERAL, context.intent)
        assertEquals(TopicType.GENERAL, context.topic)
        assertEquals(SentimentType.NEUTRAL, context.sentiment)
        assertEquals(0.4f, context.confidence, 0.001f)
    }

    @Test
    fun detect_questionWithGreeting_greetingTakesPriority() {
        val context = detector.detect("مرحبا كيف حالك؟")
        assertEquals(IntentType.GREETING, context.intent)
        assertEquals(TopicType.STATUS, context.topic)
        assertEquals(0.8f, context.confidence, 0.001f)
    }

    @Test
    fun detect_locationQuestion_returnsLocationTopic() {
        val context = detector.detect("فين البيت؟")
        assertEquals(IntentType.QUESTION, context.intent)
        assertEquals(TopicType.LOCATION, context.topic)
    }

    @Test
    fun detect_timeQuestion_returnsTimeTopic() {
        val context = detector.detect("متى الموعد؟")
        assertEquals(IntentType.QUESTION, context.intent)
        assertEquals(TopicType.TIME, context.topic)
    }

    @Test
    fun detect_workStatement_returnsWorkTopic() {
        val context = detector.detect("عندي اجتماع")
        assertEquals(TopicType.WORK, context.topic)
        assertEquals(0.8f, context.confidence, 0.001f)
    }

    @Test
    fun detect_studyStatement_returnsStudyTopic() {
        val context = detector.detect("لازم ادرس للامتحان")
        assertEquals(TopicType.STUDY, context.topic)
        assertEquals(0.8f, context.confidence, 0.001f)
    }
}
