package com.example.persona.features.chat_import.domain.model

/**
 * Summary of a completed business knowledge base ingestion run.
 */
data class ImportResult(
    val fileName: String?,
    val format: String, // e.g., "MARKDOWN" or "TEXT"
    val totalChunks: Int,
    val processedEmbeddings: Int,
    val warnings: List<String>
)
