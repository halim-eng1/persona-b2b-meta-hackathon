package com.example.persona.core.common

sealed class AppException(
    override val message: String,
    override val cause: Throwable? = null
) : Exception(message, cause) {

    // ── Network ──────────────────────────────────────────────
    data class NetworkUnavailable(override val cause: Throwable? = null) :
        AppException("No internet connection", cause)
    data class ServerError(val code: Int, val body: String?) :
        AppException("Server error: $code")
    data class Unauthorized(override val message: String = "Session expired") :
        AppException(message)
    data class Timeout(override val cause: Throwable? = null) :
        AppException("Request timed out", cause)

    // ── Database ─────────────────────────────────────────────
    data class DatabaseError(val operation: String, override val cause: Throwable? = null) :
        AppException("Database error during: $operation", cause)

    // ── AI / Inference ───────────────────────────────────────
    data class ModelLoadFailed(val modelName: String, override val cause: Throwable? = null) :
        AppException("Failed to load model: $modelName", cause)
    data class InferenceError(override val cause: Throwable? = null) :
        AppException("AI inference failed", cause)
    data class InsufficientMemory(val requiredMb: Int) :
        AppException("Insufficient memory: ${requiredMb}MB required")

    // ── Chat Import ──────────────────────────────────────────
    data class UnsupportedChatFormat(val detectedFormat: String?) :
        AppException("Unsupported chat export format: $detectedFormat")
    data class InsufficientMessages(val found: Int, val required: Int) :
        AppException("Insufficient messages: found $found, need $required")
    data class ParseError(val line: Int, val detail: String) :
        AppException("Parse error at line $line: $detail")
    data object OwnerNameNotConfigured :
        AppException("Owner name not configured. Please set your WhatsApp display name in Settings.")

    // ── Business Logic ───────────────────────────────────────
    data class ContactLimitReached(val limit: Int) :
        AppException("Contact limit reached: $limit")
    data class DailyReplyLimitReached(val limit: Int) :
        AppException("Daily reply limit reached: $limit")
    data class CooldownActive(val contactName: String) :
        AppException("Cooldown active for: $contactName")

    // ── Generic ──────────────────────────────────────────────
    data class Unknown(override val cause: Throwable? = null) :
        AppException("An unexpected error occurred", cause)
}
