package com.example.persona.features.chat_import.domain.model

data class ImportResult(
    val contactName: String,
    val userName: String,
    val format: ChatFormat,
    val totalMessages: Int,
    val userMessages: Int,
    val contactMessages: Int,
    val skippedSystemMessages: Int,
    val warnings: List<String>
)
