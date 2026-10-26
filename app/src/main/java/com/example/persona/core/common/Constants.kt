package com.example.persona.core.common

object Constants {
    // Chat Import
    const val MIN_MESSAGES_REQUIRED = 50
    const val EMBEDDING_BATCH_SIZE = 200
    const val EMBEDDING_DIMENSION = 384

    // Auto-Reply
    const val AUTO_SEND_THRESHOLD = 0.85f
    const val SUGGEST_THRESHOLD = 0.60f
    const val DEBOUNCE_MILLIS = 8_000L
    const val MAX_CONTEXT_WINDOW_MESSAGES = 4
    const val COOLDOWN_MAX_REPLIES = 3
    const val COOLDOWN_WINDOW_MINUTES = 15

    // Free Tier Limits
    const val FREE_TIER_MAX_CONTACTS = 1
    const val FREE_TIER_MAX_DAILY_REPLIES = 10
    const val PRO_TIER_MAX_CONTACTS = 5

    // Queue
    const val QUEUE_EXPIRY_HOURS = 24
    const val MAX_RETRY_ATTEMPTS = 3

    // Database
    const val DB_NAME = "persona_db"
}
