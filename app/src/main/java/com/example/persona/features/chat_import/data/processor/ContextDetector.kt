package com.example.persona.features.chat_import.data.processor

import com.example.persona.features.chat_import.data.normalizer.ArabicTextNormalizer
import com.example.persona.features.chat_import.domain.model.ConversationContext
import com.example.persona.features.chat_import.domain.model.IntentType
import com.example.persona.features.chat_import.domain.model.TopicType
import com.example.persona.features.chat_import.domain.model.SentimentType
import javax.inject.Inject

/**
 * Classifies incoming text into [IntentType], [TopicType], and [SentimentType].
 *
 * B2B expansion: the GREETING and CLOSING keyword sets have been significantly
 * expanded to cover Egyptian Arabic, Gulf Arabic, and Standard Arabic social
 * formulae, including common variants written without diacritics (post-normalisation).
 * This is essential because the Orchestrator short-circuits the entire RAG pipeline
 * based on these two intent classes.
 */
class ContextDetector @Inject constructor(
    private val normalizer: ArabicTextNormalizer
) {

    companion object {

        // ─────────────────────────────────────────────────────────────────────
        // GREETING patterns (Egyptian + Gulf + Standard Arabic, all normalised)
        // ─────────────────────────────────────────────────────────────────────
        private val GREETING_REGEX = Regex(
            "\\b(hi|hello|hey|morning|evening|assalam|salam|" +
                "howdy|greetings|welcome|good morning|good evening|good afternoon|good day|" +
                "مرحبا|مرحب|اهلا|اهلين|السلام عليكم|وعليكم السلام|" +
                "صباح الخير|صباح النور|صباح الورد|" +
                "مساء الخير|مساء النور|مساء الورد|" +
                "هلا|هلا والله|هلوو|هلو|" +
                "ازيك|ازيكم|ازيكي|كيف حالك|كيفك|كيف الاحوال|" +
                "ياهلا|ياهلا وسهلا|اهل وسهل|سهلا|" +
                "يا فندم|يا باشا|باشا|افندم|فندم|" +
                "احلا مسا|احلى مسا|احلا صبح|احلى صبح|" +
                "تحية طيبة|تحياتي|سلام عليكم|سلامات)\\b",
            RegexOption.IGNORE_CASE
        )

        private val GREETING_KEYWORDS = setOf(
            // English
            "hi", "hello", "hey", "morning", "evening", "good morning", "good evening",
            "what's up", "sup", "howdy", "greetings", "welcome", "good afternoon", "good day",
            "hey there", "hi there", "hello there",
            // Standard/Levantine
            "مرحبا", "مرحب", "اهلا", "السلام عليكم", "وعليكم السلام",
            "صباح الخير", "صباح النور", "صباح الورد",
            "مساء الخير", "مساء النور", "مساء الورد",
            "سلام", "سلامات", "تحية",
            // Egyptian
            "هلا", "هلوو", "ازيك", "ازيكي", "ازيكم",
            "عامل ايه", "عاملة ايه",
            "كيف حالك", "كيف الحال", "كيفك", "احوالك",
            "يا فندم", "فندم", "يا باشا", "باشا",
            "احلا مسا", "احلى مسا", "احلا صبح", "احلى صبح",
            // Gulf
            "هلا والله", "اهلين", "ياهلا", "يا هلا"
        )

        // ─────────────────────────────────────────────────────────────────────
        // FAREWELL / CLOSING patterns
        // ─────────────────────────────────────────────────────────────────────
        private val CLOSING_REGEX = Regex(
            "\\b(bye|goodbye|good night|see you|take care|" +
                "thanks|thank you|cheers|have a good one|have a nice day|have a great day|" +
                "مع السلامة|وداعا|باي|باي باي|" +
                "يلا سلام|يلا باي|يلا معلش|" +
                "شكرا|شكرًا|شكراً|تشكر|يسلمو|يسلموا|يسلم|" +
                "تسلم|تسلمي|تسلموا|" +
                "الله يخليك|الله يحفظك|الله يعافيك|" +
                "جزاك الله|جزاك الله خيرا|جزاكم الله|" +
                "تصبح على خير|تصبحي على خير|تصبحون على خير|" +
                "وانت بخير|وانتم بخير|الله يسعد|" +
                "مشكور|مشكوره|يعطيك العافية|عافية)\\b",
            RegexOption.IGNORE_CASE
        )

        private val CLOSING_KEYWORDS = setOf(
            // English
            "bye", "goodbye", "see you", "later", "good night", "ttyl", "take care", "cya",
            "thanks", "thank you", "cheers", "have a good one", "have a nice day", "have a great day",
            // Standard
            "مع السلامة", "وداعا", "باي", "تصبح على خير", "الله يحفظك",
            "الله يعافيك", "جزاك الله خيرا", "جزاكم الله",
            // Egyptian gratitude / farewell
            "شكرا", "شكراً", "شكرًا", "يسلمو", "يسلموا", "تسلم", "تسلمي",
            "يلا سلام", "يلا باي", "يلا",
            // Gulf
            "مشكور", "مشكوره", "يعطيك العافية", "الله يسعد",
            // Common closers
            "الله يخليك", "وانت بخير"
        )

        private val AGREEMENT_KEYWORDS = setOf(
            "ok", "okay", "yes", "sure", "indeed", "fine", "agree", "correct",
            "تمام", "اوك", "ماشي", "ان شاء الله", "اكيد", "طيب", "خلاص",
            "نعم", "ايوه", "صح", "حاضر", "امر", "بكل سرور"
        )

        private val REQUEST_KEYWORDS = setOf(
            "please", "pls", "could you", "can you", "would you", "send me", "give me",
            "من فضلك", "ممكن", "لو سمحت", "ارسل", "اعطيني", "طلب", "ابي", "اريد", "بدي"
        )

        private val INFORMATION_KEYWORDS = setOf(
            "info", "information", "details", "details of", "fact", "know", "status is",
            "معلومات", "تفاصيل", "عرفت", "علمت", "الوضع", "الحالة", "اخبرني", "وضحلي"
        )

        private val QUESTION_KEYWORDS = setOf(
            "what", "where", "when", "who", "why", "how", "can you", "could you",
            "do you", "did you", "is it", "are you", "will you",
            "which", "whose", "whom", "is there", "are there", "how much", "how many",
            "could i", "may i", "should i", "would it",
            "ايش", "وين", "متى", "ليش", "كيف", "هل", "شنو", "منو", "اشلون",
            "ليه", "فين", "مين", "كام", "ازاي", "انهي", "بتعمل", "شغال",
            "ايه", "ما هو", "ما هي", "ماهو", "ماهي"
        )
        private val AMBIGUOUS_QUESTION_PARTICLES = setOf("اي", "ايه")

        // Topic keywords
        private val LOCATION_KEYWORDS = setOf(
            "where", "location", "address", "map", "gps", "place", "home", "office",
            "فين", "وين", "مكان", "عنوان", "خريطة", "بيت", "موقع", "هنا", "هناك"
        )

        private val TIME_KEYWORDS = setOf(
            "when", "time", "date", "clock", "hour", "day", "tomorrow", "yesterday",
            "today", "schedule", "am", "pm",
            "متى", "وقت", "ساعة", "يوم", "بكرة", "امس", "تاريخ", "موعد", "صباح", "مساء"
        )

        private val STATUS_KEYWORDS = setOf(
            "how are you", "doing", "busy", "free", "active", "online", "available",
            "status", "mood", "tired", "sick",
            "كيفك", "شلونك", "اخبارك", "مشغول", "فاضي", "متصل", "متاح",
            "تعبان", "مريض", "كيف حالك", "أخبارك", "احوالك"
        )

        private val WORK_KEYWORDS = setOf(
            "work", "job", "office", "meeting", "project", "client", "task",
            "email", "manager", "business", "company",
            "شغل", "وظيفة", "مكتب", "اجتماع", "مشروع", "عميل", "مهمة",
            "ايميل", "مدير", "شركة"
        )

        private val STUDY_KEYWORDS = setOf(
            "study", "class", "exam", "test", "course", "university", "school",
            "book", "lecture", "homework", "student",
            "دراسة", "كلاس", "امتحان", "اختبار", "جامعة", "مدرسة",
            "كتاب", "محاضرة", "واجب", "طالب"
        )

        private val SOCIAL_KEYWORDS = setOf(
            "friend", "family", "dinner", "lunch", "coffee", "meet", "party", "call",
            "صديق", "اهل", "عشاء", "غداء", "قهوة", "مقابلة", "حفلة", "اتصال"
        )

        private val MEDIA_KEYWORDS = setOf(
            "photo", "image", "video", "audio", "voice", "file", "document",
            "pic", "link", "attachment",
            "صورة", "فيديو", "صوت", "ملف", "مقطع", "رابط", "مرفق"
        )

        private val POSITIVE_KEYWORDS = setOf(
            "good", "great", "excellent", "awesome", "perfect", "happy", "yes",
            "love", "thanks", "thank you", "nice", "ok", "okay", "glad",
            "جميل", "رائع", "ممتاز", "حلو", "نعم", "شكرا", "حب",
            "يسلمو", "موافق", "سعيد", "تمام", "اوك", "ماشي", "حاضر"
        )

        private val NEGATIVE_KEYWORDS = setOf(
            "bad", "sad", "angry", "no", "not", "cannot", "can't", "don't",
            "hate", "sorry", "unfortunately", "worst", "unhappy",
            "سيء", "حزين", "لا", "ما اقدر", "كره", "اسف", "للأسف", "غضبان"
        )
    }

    /**
     * Identifies conversation context across Intent, Topic, and Sentiment.
     * Assigns confidence based on match strength.
     *
     * The intent detection is optimised for the B2B short-circuit gate:
     * GREETING and CLOSING are evaluated first, with high-confidence patterns
     * (word count ≤ 5 + regex match) taking priority over general question detection.
     */
    fun detect(text: String): ConversationContext {
        val normalized = normalizer.normalize(text) ?: text
        val clean = normalized.trim()
        if (clean.isEmpty()) {
            return ConversationContext(IntentType.GENERAL, TopicType.GENERAL, SentimentType.NEUTRAL, 0.4f)
        }
        val lower = clean.lowercase()

        // 1. Detect Intent & its Match Confidence
        var intent = IntentType.GENERAL
        var intentConf = 0.4f

        val wordCount = clean.split("\\s+".toRegex()).size
        val cleanWords = clean.split("[\\s\\p{Punct}؟?]+".toRegex()).filter { it.isNotEmpty() }
        val cleanWordsLower = cleanWords.map { it.lowercase() }

        val hasUnambiguousQuestionWord = cleanWordsLower.any { QUESTION_KEYWORDS.contains(it) }

        var hasValidAmbiguousQuestion = false
        val ambiguousIndices = cleanWordsLower.mapIndexedNotNull { index, word ->
            if (AMBIGUOUS_QUESTION_PARTICLES.contains(word)) index else null
        }
        for (idx in ambiguousIndices) {
            if (idx == cleanWordsLower.size - 1) {
                hasValidAmbiguousQuestion = true
                break
            } else {
                val nextWord = cleanWordsLower[idx + 1]
                val relativeNouns = setOf(
                    "حد", "حاجه", "حاجة", "وقت", "مكان", "واحد",
                    "طريقة", "شخص", "حالة", "يوم", "مرة"
                )
                if (!relativeNouns.contains(nextWord)) {
                    hasValidAmbiguousQuestion = true
                    break
                }
            }
        }

        when {
            // ── GREETING (B2B critical — evaluated before QUESTION) ──────────
            (wordCount <= 6 && GREETING_REGEX.containsMatchIn(clean)) -> {
                intent = IntentType.GREETING
                intentConf = 0.95f
            }
            matchesKeywords(lower, GREETING_KEYWORDS) -> {
                intent = IntentType.GREETING
                intentConf = 0.80f
            }
            // ── CLOSING / FAREWELL ───────────────────────────────────────────
            (wordCount <= 6 && CLOSING_REGEX.containsMatchIn(clean)) -> {
                intent = IntentType.CLOSING
                intentConf = 0.95f
            }
            matchesKeywords(lower, CLOSING_KEYWORDS) -> {
                intent = IntentType.CLOSING
                intentConf = 0.80f
            }
            // ── QUESTION ─────────────────────────────────────────────────────
            clean.endsWith("?") || clean.endsWith("؟") -> {
                intent = IntentType.QUESTION
                intentConf = 0.9f
            }
            hasUnambiguousQuestionWord || hasValidAmbiguousQuestion -> {
                intent = IntentType.QUESTION
                intentConf = 0.7f
            }
            // ── REQUEST ──────────────────────────────────────────────────────
            matchesKeywords(lower, REQUEST_KEYWORDS) -> {
                intent = IntentType.REQUEST
                intentConf = 0.8f
            }
            // ── AGREEMENT ────────────────────────────────────────────────────
            matchesKeywords(lower, AGREEMENT_KEYWORDS) -> {
                intent = IntentType.AGREEMENT
                intentConf = if (wordCount <= 5) 0.8f else 0.6f
            }
            // ── INFORMATION ──────────────────────────────────────────────────
            matchesKeywords(lower, INFORMATION_KEYWORDS) -> {
                intent = IntentType.INFORMATION
                intentConf = 0.7f
            }
        }

        // 2. Detect Topic
        var topic = TopicType.GENERAL
        var topicConf = 0.4f

        when {
            matchesKeywords(lower, STATUS_KEYWORDS) -> { topic = TopicType.STATUS; topicConf = 0.8f }
            matchesKeywords(lower, WORK_KEYWORDS)   -> { topic = TopicType.WORK;   topicConf = 0.8f }
            matchesKeywords(lower, STUDY_KEYWORDS)  -> { topic = TopicType.STUDY;  topicConf = 0.8f }
            matchesKeywords(lower, LOCATION_KEYWORDS) -> { topic = TopicType.LOCATION; topicConf = 0.8f }
            matchesKeywords(lower, MEDIA_KEYWORDS)  -> { topic = TopicType.MEDIA;  topicConf = 0.8f }
            matchesKeywords(lower, SOCIAL_KEYWORDS) -> { topic = TopicType.SOCIAL; topicConf = 0.8f }
            matchesKeywords(lower, TIME_KEYWORDS)   -> { topic = TopicType.TIME;   topicConf = 0.8f }
        }

        // 3. Detect Sentiment
        var sentiment = SentimentType.NEUTRAL
        var sentimentConf = 0.4f

        when {
            matchesKeywords(lower, POSITIVE_KEYWORDS) -> { sentiment = SentimentType.POSITIVE; sentimentConf = 0.8f }
            matchesKeywords(lower, NEGATIVE_KEYWORDS) -> { sentiment = SentimentType.NEGATIVE; sentimentConf = 0.8f }
        }

        val confidence = maxOf(intentConf, topicConf, sentimentConf)
        return ConversationContext(intent, topic, sentiment, confidence)
    }

    private fun matchesKeywords(text: String, keywords: Set<String>): Boolean =
        keywords.any { text.contains(it) }
}
