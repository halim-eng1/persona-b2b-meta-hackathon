package com.example.persona.features.auto_reply.domain.model

data class ExecutionTelemetry(
    val providerModel: String = "Unknown",
    val gatekeeperTier: String = "MODEL",
    val latencyMs: Long = 0L,
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val decisionReason: String? = null
)

