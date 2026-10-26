package com.example.persona.core.common

object Constants {
    // Chat Import / Knowledge Base
    const val EMBEDDING_BATCH_SIZE = 200
    const val EMBEDDING_MAX_CHUNK_CHARS = 400
    const val CHUNK_OVERLAP_PERCENT = 0.12f

    // Queue / Offline Retry
    const val QUEUE_EXPIRY_HOURS = 24
    const val MAX_RETRY_ATTEMPTS = 3

    // Database
    const val DB_NAME = "persona_db"
}
