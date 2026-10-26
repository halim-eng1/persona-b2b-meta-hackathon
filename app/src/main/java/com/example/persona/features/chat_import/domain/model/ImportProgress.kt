package com.example.persona.features.chat_import.domain.model

data class ImportProgress(
    val phase: ImportPhase,
    val currentBatch: Int,
    val totalBatches: Int,
    val messagesProcessed: Int,
    val totalMessages: Int,
    val errorCount: Int = 0
) {
    val progressFraction: Float
        get() = if (totalMessages > 0) messagesProcessed.toFloat() / totalMessages else 0f

    enum class ImportPhase(val displayName: String) {
        READING_FILE("Reading file..."),
        DETECTING_FORMAT("Detecting format..."),
        PARSING_MESSAGES("Parsing messages..."),
        NORMALIZING_TEXT("Normalizing text..."),
        GROUPING_MESSAGES("Grouping messages..."),
        DETECTING_INTENT("Classifying intent..."),
        EMBEDDING("Generating embeddings..."),
        PERSISTING("Saving to database..."),
        COMPLETE("Import complete!")
    }
}
