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
    val personaTone: PersonaTone = PersonaTone.WITTY_EGYPTIAN,
    val similarityThreshold: Float = 0.75f, // Balanced default
    val cooldownMins: Int = 5,
    val replyDelaySeconds: Int = 2,
    @Deprecated("Legacy post-processing style profile; prompt-level PersonaTone is now used")
    val styleProfile: StyleProfile = StyleProfile.CASUAL
) {
    /** Secondary constructor for backward compatibility with legacy tests using (StyleProfile, Float, Int, Int) */
    constructor(
        styleProfile: StyleProfile,
        similarityThreshold: Float = 0.75f,
        cooldownMins: Int = 5,
        replyDelaySeconds: Int = 2
    ) : this(
        personaTone = PersonaTone.WITTY_EGYPTIAN,
        similarityThreshold = similarityThreshold,
        cooldownMins = cooldownMins,
        replyDelaySeconds = replyDelaySeconds,
        styleProfile = styleProfile
    )
}
