package com.example.persona.features.auto_reply.domain.gatekeeper

/**
 * Domain-level inbound customer intent types.
 */
enum class InboundIntent {
    GREETING,
    BANTER,
    ORDER_STATUS,
    CATALOG_INQUIRY
}

data class DecisionResult(
    val intent: InboundIntent,
    val confidence: Float,
    val latencyMs: Long
)
