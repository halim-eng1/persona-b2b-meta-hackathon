package com.example.persona.features.chats.presentation.detail

import com.example.persona.core.data.repository.UserPreferencesRepository

enum class ReplyCapability {
    READY_VIA_NOTIFICATION,
    AWAITING_ACTIVE_NOTIFICATION
}

enum class ExportTimeRange(
    val titleArabic: String,
    val titleEnglish: String,
    val subtitle: String,
    val exportLabel: String
) {
    ALL_TIME(
        titleArabic = "كامل المحادثة",
        titleEnglish = "All Time",
        subtitle = "All Time • كافة الرسائل المسجلة",
        exportLabel = "All Time (كامل المحادثة)"
    ),
    TODAY(
        titleArabic = "محادثة اليوم",
        titleEnglish = "Today",
        subtitle = "Today • رسائل اليوم",
        exportLabel = "Today (محادثة اليوم)"
    ),
    LAST_TWO_HOURS(
        titleArabic = "آخر ساعتين",
        titleEnglish = "Last 2 Hours",
        subtitle = "Last 2 Hours • آخر ساعتين",
        exportLabel = "Last 2 Hours (آخر ساعتين)"
    )
}

data class ChatMessageUiModel(
    val id: Long,
    val text: String,
    val isFromUser: Boolean,
    val senderType: String,
    val deliveryStatus: String,
    val timestampMs: Long,
    val formattedTime: String,
    val dateHeader: String,
    val hasMediaIndicator: Boolean
)

data class ChatDetailUiState(
    val contactName: String = "",
    val sourceClass: String = "BUSINESS",
    val messages: List<ChatMessageUiModel> = emptyList(),
    val isAiPaused: Boolean = false,
    val needsHumanAttention: Boolean = false,
    val replyCapability: ReplyCapability = ReplyCapability.AWAITING_ACTIVE_NOTIFICATION,
    val inputText: String = "",
    val isSending: Boolean = false,
    val fallbackTargetPackage: String? = null,
    val botName: String = UserPreferencesRepository.DEFAULT_BOT_NAME,
    val isAiGenerating: Boolean = false
)
