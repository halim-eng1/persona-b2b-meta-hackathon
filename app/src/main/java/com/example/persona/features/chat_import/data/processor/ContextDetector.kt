package com.example.persona.features.chat_import.data.processor

import com.example.persona.features.chat_import.domain.model.ConversationContext
import com.example.persona.features.chat_import.domain.model.IntentType
import com.example.persona.features.chat_import.domain.model.TopicType
import com.example.persona.features.chat_import.domain.model.SentimentType
import javax.inject.Inject

class ContextDetector @Inject constructor() {

    companion object {
        private val GREETING_REGEX = Regex(
            "\\b(hi|hello|hey|morning|evening|assalam|salam|مرحبا|اهلا|السلام عليكم|صباح الخير|مساء الخير|اهلين|هلا)\\b",
            RegexOption.IGNORE_CASE
        )
        private val CLOSING_REGEX = Regex(
            "\\b(bye|goodbye|good night|see you|take care|مع السلامة|باي|تصبح على خير|الله يحفظك|يلا باي|سلامات)\\b",
            RegexOption.IGNORE_CASE
        )

        private val GREETING_KEYWORDS = setOf(
            "hi", "hello", "hey", "morning", "evening", "good morning", "good evening", "what's up", "sup",
            "مرحبا", "اهلا", "السلام عليكم", "صباح الخير", "مساء الخير", "هلا", "اهلين", "سلام"
        )
        
        private val CLOSING_KEYWORDS = setOf(
            "bye", "goodbye", "see you", "later", "good night", "ttyl", "take care", "cya",
            "مع السلامة", "باي", "تصبح على خير", "الله يحفظك", "يلا باي", "سلامات"
        )

        private val AGREEMENT_KEYWORDS = setOf(
            "ok", "okay", "yes", "sure", "indeed", "fine", "agree", "correct",
            "تمام", "اوك", "ماشي", "ان شاء الله", "اكيد", "طيب", "خلاص", "نعم", "ايوه", "صح"
        )

        private val REQUEST_KEYWORDS = setOf(
            "please", "pls", "could you", "can you", "would you", "send me", "give me",
            "من فضلك", "ممكن", "لو سمحت", "ارسل", "اعطيني", "طلب"
        )

        private val INFORMATION_KEYWORDS = setOf(
            "info", "information", "details", "details of", "fact", "know", "did you know", "status is",
            "معلومات", "تفاصيل", "عرفت", "علمت", "الوضع", "الحالة"
        )

        private val QUESTION_KEYWORDS = setOf(
            "what", "where", "when", "who", "why", "how", "can you", "could you", "do you", "did you", "is it", "are you", "will you",
            "ايش", "وين", "متى", "ليش", "كيف", "هل", "شنو", "منو", "اشلون"
        )

        // Topic keywords
        private val LOCATION_KEYWORDS = setOf(
            "where", "location", "address", "map", "gps", "place", "home", "office", "here", "there",
            "فين", "وين", "مكان", "عنوان", "خريطة", "بيت", "موقع", "هنا", "هناك"
        )

        private val TIME_KEYWORDS = setOf(
            "when", "time", "date", "clock", "hour", "day", "tomorrow", "yesterday", "today", "schedule", "o'clock", "am", "pm",
            "متى", "وقت", "ساعة", "يوم", "بكرة", "امس", "تاريخ", "موعد", "صباح", "مساء"
        )

        private val STATUS_KEYWORDS = setOf(
            "how are you", "doing", "busy", "free", "active", "online", "available", "status", "mood", "tired", "sick",
            "كيفك", "شلونك", "اخبارك", "مشغول", "فاضي", "متصل", "متاح", "تعبان", "مريض", "كيف", "حالك", "أخبارك", "احوالك", "اخبار"
        )

        private val WORK_KEYWORDS = setOf(
            "work", "job", "office", "meeting", "project", "client", "task", "email", "manager", "business", "company",
            "شغل", "وظيفة", "مكتب", "اجتماع", "مشروع", "عميل", "مهمة", "ايميل", "مدير", "شركة"
        )

        private val STUDY_KEYWORDS = setOf(
            "study", "class", "exam", "test", "course", "university", "school", "book", "lecture", "homework", "student",
            "دراسة", "كلاس", "امتحان", "اختبار", "جامعة", "مدرسة", "كتاب", "محاضرة", "واجب", "طالب"
        )

        private val SOCIAL_KEYWORDS = setOf(
            "friend", "family", "dinner", "lunch", "coffee", "meet", "party", "call", "people", "guy", "bro",
            "صديق", "اهل", "عشاء", "غداء", "قهوة", "مقابلة", "حفلة", "اتصال", "ناس", "يا خوي", "اخوي"
        )

        private val MEDIA_KEYWORDS = setOf(
            "photo", "image", "video", "audio", "voice", "file", "document", "pic", "link", "attachment",
            "صورة", "فيديو", "صوت", "ملف", "مقطع", "رابط", "مرفق"
        )

        // Sentiment keywords
        private val POSITIVE_KEYWORDS = setOf(
            "good", "great", "excellent", "awesome", "perfect", "happy", "yes", "love", "thanks", "thank you", "nice", "ok", "okay", "glad",
            "جميل", "رائع", "ممتاز", "حلو", "نعم", "شكرا", "حب", "يسلمو", "موافق", "سعيد", "تمام", "اوك", "ماشي", "حاضر"
        )

        private val NEGATIVE_KEYWORDS = setOf(
            "bad", "sad", "angry", "no", "not", "cannot", "can't", "don't", "hate", "sorry", "unfortunately", "worst", "unhappy",
            "سيء", "حزين", "لا", "ما اقدر", "كره", "اسف", "للأسف", "غضبان"
        )
    }

    /**
     * Identifies conversation context across Intent, Topic, and Sentiment.
     * Assigns confidence based on match strength.
     */
    fun detect(text: String): ConversationContext {
        val clean = text.trim()
        if (clean.isEmpty()) {
            return ConversationContext(IntentType.GENERAL, TopicType.GENERAL, SentimentType.NEUTRAL, 0.4f)
        }
        val lower = clean.lowercase()

        // 1. Detect Intent & its Match Confidence
        var intent = IntentType.GENERAL
        var intentConf = 0.4f

        val wordCount = clean.split("\\s+".toRegex()).size

        when {
            // QUESTION (Highest Priority)
            clean.endsWith("?") || clean.endsWith("؟") -> {
                intent = IntentType.QUESTION
                intentConf = 0.9f
            }
            matchesKeywords(lower, QUESTION_KEYWORDS) -> {
                intent = IntentType.QUESTION
                intentConf = 0.7f
            }
            // GREETING
            (wordCount <= 5 && GREETING_REGEX.containsMatchIn(clean)) -> {
                intent = IntentType.GREETING
                intentConf = 0.9f
            }
            matchesKeywords(lower, GREETING_KEYWORDS) -> {
                intent = IntentType.GREETING
                intentConf = 0.7f
            }
            // CLOSING
            (wordCount <= 5 && CLOSING_REGEX.containsMatchIn(clean)) -> {
                intent = IntentType.CLOSING
                intentConf = 0.9f
            }
            matchesKeywords(lower, CLOSING_KEYWORDS) -> {
                intent = IntentType.CLOSING
                intentConf = 0.7f
            }
            // REQUEST
            matchesKeywords(lower, REQUEST_KEYWORDS) -> {
                intent = IntentType.REQUEST
                intentConf = 0.8f
            }
            // AGREEMENT
            matchesKeywords(lower, AGREEMENT_KEYWORDS) -> {
                intent = IntentType.AGREEMENT
                intentConf = if (wordCount <= 5) 0.8f else 0.6f
            }
            // INFORMATION
            matchesKeywords(lower, INFORMATION_KEYWORDS) -> {
                intent = IntentType.INFORMATION
                intentConf = 0.7f
            }
        }

        // 2. Detect Topic & its Match Confidence
        var topic = TopicType.GENERAL
        var topicConf = 0.4f

        when {
            matchesKeywords(lower, STATUS_KEYWORDS) -> {
                topic = TopicType.STATUS
                topicConf = 0.8f
            }
            matchesKeywords(lower, WORK_KEYWORDS) -> {
                topic = TopicType.WORK
                topicConf = 0.8f
            }
            matchesKeywords(lower, STUDY_KEYWORDS) -> {
                topic = TopicType.STUDY
                topicConf = 0.8f
            }
            matchesKeywords(lower, LOCATION_KEYWORDS) -> {
                topic = TopicType.LOCATION
                topicConf = 0.8f
            }
            matchesKeywords(lower, MEDIA_KEYWORDS) -> {
                topic = TopicType.MEDIA
                topicConf = 0.8f
            }
            matchesKeywords(lower, SOCIAL_KEYWORDS) -> {
                topic = TopicType.SOCIAL
                topicConf = 0.8f
            }
            matchesKeywords(lower, TIME_KEYWORDS) -> {
                topic = TopicType.TIME
                topicConf = 0.8f
            }
        }

        // 3. Detect Sentiment & its Match Confidence
        var sentiment = SentimentType.NEUTRAL
        var sentimentConf = 0.4f

        when {
            matchesKeywords(lower, POSITIVE_KEYWORDS) -> {
                sentiment = SentimentType.POSITIVE
                sentimentConf = 0.8f
            }
            matchesKeywords(lower, NEGATIVE_KEYWORDS) -> {
                sentiment = SentimentType.NEGATIVE
                sentimentConf = 0.8f
            }
        }

        // 4. Calculate Combined Confidence Score
        val confidence = maxOf(intentConf, topicConf, sentimentConf)

        return ConversationContext(intent, topic, sentiment, confidence)
    }

    private fun matchesKeywords(text: String, keywords: Set<String>): Boolean {
        return keywords.any { text.contains(it) }
    }
}
