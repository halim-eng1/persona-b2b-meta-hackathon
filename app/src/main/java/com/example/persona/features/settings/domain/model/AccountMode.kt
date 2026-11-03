package com.example.persona.features.settings.domain.model

enum class AccountMode {
    BUSINESS,
    PERSONAL;

    companion object {
        fun fromString(value: String?): AccountMode = when (value?.trim()?.uppercase()) {
            "PERSONAL" -> PERSONAL
            else -> BUSINESS
        }
    }
}
