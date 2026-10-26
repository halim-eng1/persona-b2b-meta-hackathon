package com.example.persona.features.knowledge.domain.model

data class KnowledgeEntry(
    val chunkId: String,
    val headerContext: String?,
    val chunkText: String,
    val sourceType: String, // 'CORE_DOCUMENT', 'MANUAL_ENTRY', 'TEMPORARY_OFFER'
    val timestamp: Long
)
