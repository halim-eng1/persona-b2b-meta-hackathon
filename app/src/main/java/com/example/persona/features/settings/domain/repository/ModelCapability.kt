package com.example.persona.features.settings.domain.repository

/**
 * Describes what an AI model is capable of handling.
 * A single model may carry multiple capabilities (e.g. TEXT + VISION).
 *
 * Stored as a comma-separated set alongside the model ID in DataStore:
 *   "muse-spark-1.3|TEXT,VISION"
 * Legacy entries that have no pipe separator are treated as TEXT-only.
 */
enum class ModelCapability(val key: String, val label: String) {
    TEXT("TEXT", "نص"),
    VISION("VISION", "صور"),
    AUDIO("AUDIO", "صوت"),
    DECISION("DECISION", "قرار");

    companion object {
        fun fromKey(key: String): ModelCapability? = entries.firstOrNull { it.key == key }

        /** Parses a comma-separated capability string, e.g. "TEXT,VISION". */
        fun setFromString(raw: String): Set<ModelCapability> =
            raw.split(",")
                .mapNotNull { fromKey(it.trim()) }
                .toSet()
                .ifEmpty { setOf(TEXT) }

        fun setToString(caps: Set<ModelCapability>): String =
            caps.joinToString(",") { it.key }
    }
}

/**
 * A model ID bundled with its capability flags.
 * Serialized to DataStore as "modelId|CAP1,CAP2" or just "modelId" (legacy TEXT-only).
 */
data class ModelConfig(
    val modelId: String,
    val capabilities: Set<ModelCapability> = setOf(ModelCapability.TEXT)
) {
    /** Serialized form stored in DataStore. */
    fun serialize(): String {
        val capsStr = ModelCapability.setToString(capabilities)
        return "$modelId|$capsStr"
    }

    companion object {
        /**
         * Parse a DataStore line which may be:
         *  - "modelId|TEXT,VISION"  (new format)
         *  - "modelId"              (legacy, treated as TEXT)
         */
        fun parse(raw: String): ModelConfig {
            val trimmed = raw.trim()
            val pipeIdx = trimmed.indexOf('|')
            return if (pipeIdx == -1) {
                // Legacy: no capability annotation → TEXT
                ModelConfig(modelId = trimmed, capabilities = setOf(ModelCapability.TEXT))
            } else {
                val modelId = trimmed.substring(0, pipeIdx).trim()
                val caps = ModelCapability.setFromString(trimmed.substring(pipeIdx + 1))
                ModelConfig(modelId = modelId, capabilities = caps)
            }
        }

        /** Infer sensible default capabilities from a model ID string. */
        fun inferCapabilities(modelId: String): Set<ModelCapability> {
            val id = modelId.lowercase()
            val caps = mutableSetOf<ModelCapability>()

            // Meta Muse Spark models: Text, Vision, Decision
            if (id.contains("muse-spark")) {
                caps += ModelCapability.TEXT
                caps += ModelCapability.VISION
                caps += ModelCapability.DECISION
                return caps
            }

            // Meta Muse Voice models: Audio
            if (id.contains("voice") || id.contains("transcribe") || id.contains("asr") || id.contains("speech") || id.contains("stt")) {
                caps += ModelCapability.AUDIO
                return caps
            }

            // Decision / Gatekeeper models
            if (id.contains("decision") || id.contains("gatekeeper")) {
                caps += ModelCapability.DECISION
                return caps
            }

            // Vision / multimodal models
            if (id.contains("vision") || id.contains("vl") || id.contains("omni") || id.contains("visual") || id.contains("image")) {
                caps += ModelCapability.VISION
                caps += ModelCapability.TEXT
            }

            // Default fallback: TEXT
            if (caps.isEmpty()) caps += ModelCapability.TEXT
            return caps
        }
    }
}

