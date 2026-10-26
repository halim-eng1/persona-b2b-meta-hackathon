package com.example.persona.features.chat_import.domain.model

enum class IntentCategory {
    GREETING,
    QUESTION,
    CLOSING,
    AGREEMENT,
    GENERAL;

    companion object {
        val GREETING_KEYWORDS_EN = setOf(
            "hi", "hello", "hey", "morning", "evening",
            "good morning", "good evening", "what's up", "sup"
        )
        val GREETING_KEYWORDS_AR = setOf(
            "مرحبا", "اهلا", "السلام عليكم", "صباح الخير",
            "مساء الخير", "هلا", "اهلين", "سلام"
        )
        val CLOSING_KEYWORDS_EN = setOf(
            "bye", "goodbye", "see you", "later",
            "good night", "ttyl", "take care", "cya"
        )
        val CLOSING_KEYWORDS_AR = setOf(
            "مع السلامة", "باي", "تصبح على خير",
            "الله يحفظك", "يلا باي", "سلامات"
        )
        val AGREEMENT_KEYWORDS_AR = setOf(
            "تمام", "اوك", "ماشي", "ان شاء الله",
            "اكيد", "طيب", "خلاص"
        )
        val QUESTION_KEYWORDS = setOf(
            "what", "where", "when", "who", "why", "how",
            "can you", "could you", "do you", "did you",
            "is it", "are you", "will you",
            "ايش", "وين", "متى", "ليش", "كيف", "هل",
            "شنو", "منو", "اشلون"
        )
    }
}
