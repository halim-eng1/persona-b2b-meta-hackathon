package com.example.persona.features.chat_import.domain.model

data class ConversationContext(
    val intent: IntentType,
    val topic: TopicType,
    val sentiment: SentimentType,
    val confidence: Float
)

enum class IntentType {
    GREETING,
    QUESTION,
    AGREEMENT,
    CLOSING,
    REQUEST,
    INFORMATION,
    GENERAL
}

enum class TopicType {
    LOCATION,
    TIME,
    STATUS,
    WORK,
    STUDY,
    SOCIAL,
    MEDIA,
    GENERAL
}

enum class SentimentType {
    POSITIVE,
    NEGATIVE,
    NEUTRAL
}
