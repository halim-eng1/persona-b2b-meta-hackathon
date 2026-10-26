package com.example.persona.features.auto_reply.domain.model

enum class StyleProfile(val displayName: String) {
    PROFESSIONAL("Professional"),
    CASUAL("Casual"),
    STRICT_MATCH("Strict Match"),
    SHORT_DIRECT("Short & Direct"),
    EMOJI_HEAVY("Emoji Heavy");

    companion object {
        fun fromString(value: String): StyleProfile {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) || it.displayName.equals(value, ignoreCase = true) } ?: CASUAL
        }
    }
}

data class StylePreferences(
    val styleProfile: StyleProfile = StyleProfile.CASUAL,
    val similarityThreshold: Float = 0.75f, // Balanced default
    val cooldownMins: Int = 5,
    val replyDelaySeconds: Int = 2
)
