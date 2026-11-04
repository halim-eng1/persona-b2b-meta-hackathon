package com.example.persona.features.audit_log.domain.model

import kotlinx.datetime.Instant

/**
 * Enterprise forensic audit item combining reply execution telemetry,
 * gatekeeper routing classification, and RAG knowledge citations.
 */
data class AuditLogItem(
    val id: Long,
    val contactName: String,
    val incomingMessage: String,
    val generatedReply: String,
    val similarityScore: Float,
    val wasAutoSent: Boolean,
    val timestamp: Instant,
    val sourceClass: String = "BUSINESS",
    val gatekeeperTier: String = "MODEL", // RULE, HEURISTIC, MODEL, FALLBACK
    val providerModel: String = "Meta / muse-spark-1.3",
    val latencyMs: Long = 650L,
    val promptTokens: Int = 120,
    val completionTokens: Int = 45,
    val retrievedChunkIds: List<String> = emptyList(),
    val status: String = "SUCCESS", // SUCCESS, ESCALATED_HITL, ERROR
    val decisionReason: String? = null
)
