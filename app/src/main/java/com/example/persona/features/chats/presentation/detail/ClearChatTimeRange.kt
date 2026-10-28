package com.example.persona.features.chats.presentation.detail

/**
 * Time range options for clearing local chat messages.
 */
enum class ClearChatTimeRange(val label: String) {
    LAST_HOUR("آخر ساعة"),
    LAST_TWO_HOURS("آخر ساعتين"),
    TODAY("اليوم"),
    ALL_TIME("كامل المحادثة")
}
