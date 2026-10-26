package com.example.persona.core.database.entity

/**
 * Tracks the lifecycle state of a background embedding computation job
 * for a [ChatHistoryEntity] row.
 *
 * Persisted as a TEXT column via the [com.example.persona.core.database.Converters]
 * TypeConverter pair.
 *
 * State machine transitions:
 *   [PENDING] → [PROCESSING]  (via atomic claimForProcessing update)
 *   [PROCESSING] → [DONE]     (on successful ONNX inference)
 *   [PROCESSING] → [FAILED]   (on inference exception — text remains safely persisted)
 *
 * The check-before-compute pattern using [PROCESSING] prevents duplicate
 * vector calculations under notification burst backpressure.
 */
enum class EmbeddingJobState {
    PENDING,
    PROCESSING,
    DONE,
    FAILED;

    companion object {
        /**
         * Safe deserializer. Returns [PENDING] for any null, blank, or
         * unrecognised string value — never throws.
         */
        fun fromString(value: String?): EmbeddingJobState {
            if (value.isNullOrBlank()) return PENDING
            return entries.firstOrNull { it.name.equals(value.trim(), ignoreCase = true) }
                ?: PENDING
        }
    }
}
