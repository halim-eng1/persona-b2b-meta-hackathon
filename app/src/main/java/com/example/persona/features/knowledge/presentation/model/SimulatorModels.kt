package com.example.persona.features.knowledge.presentation.model

import com.example.persona.core.network.dto.ExtractedOrder
import com.example.persona.features.auto_reply.domain.gatekeeper.InboundIntent
import com.example.persona.features.auto_reply.domain.model.RetrievalCandidate
import java.util.UUID

enum class SimulatorSender {
    USER,
    BOT
}

enum class SimulatorMediaType {
    IMAGE,
    AUDIO
}

data class SimulatorMessage(
    val id: String = UUID.randomUUID().toString(),
    val sender: SimulatorSender,
    val text: String,
    val mediaUri: String? = null,
    val mediaType: SimulatorMediaType? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val trace: ExecutionTrace? = null
)

data class ExecutionTrace(
    val totalDurationMs: Long = 0L,
    val intent: InboundIntent = InboundIntent.CATALOG_INQUIRY,
    val intentSourceTier: String = "On-Device",
    val intentConfidence: Float = 0.95f,
    val intentLatencyMs: Long = 0L,
    val ragCandidates: List<RetrievalCandidate> = emptyList(),
    val ragBestScore: Float = 0f,
    val reQueryTriggered: Boolean = false,
    val reQueryKeyword: String? = null,
    val reQueryVisualDescription: String? = null,
    val reQueryCandidates: List<RetrievalCandidate> = emptyList(),
    val llmProvider: String = "OpenRouter / Gemini / Qwen",
    val llmLatencyMs: Long = 0L,
    val llmAction: String = "REPLY",
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val totalTokens: Int = 0,
    val orderAction: String = "NONE",
    val extractedOrder: ExtractedOrder? = null,
    val systemPrompt: String = "",
    val asrTranscription: String? = null,
    val visionDescription: String? = null
)
