package com.example.persona.core.common

import kotlinx.serialization.Serializable

@Serializable
data class ErrorEnvelope(
    val code: Int,
    val message: String,
    val details: String? = null
) {
    fun toAppException(): AppException = when (code) {
        401 -> AppException.Unauthorized(message)
        in 500..599 -> AppException.ServerError(code, details)
        else -> AppException.ServerError(code, message)
    }
}
