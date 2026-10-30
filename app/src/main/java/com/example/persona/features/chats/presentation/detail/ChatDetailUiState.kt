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

data class ActiveOrderSummary(
    val id: Long,
    val status: String,
    val orderDetails: String,
    val totalAmount: Double,
    val deliveryAddress: String? = null
)

data class ChatMessageUiModel(
    val id: Long,
    val text: String,
    val isFromUser: Boolean,
    val senderType: String,
    val deliveryStatus: String,
    val timestampMs: Long,
    val formattedTime: String,
    val dateHeader: String,
    val hasMediaIndicator: Boolean,
    val isVoiceNote: Boolean = false
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
    val isAiGenerating: Boolean = false,
    val activeOrder: ActiveOrderSummary? = null,
    val contactPhoneNumber: String? = null,
    val hasMoreMessages: Boolean = false,
    val totalMessageCount: Int = 0
)
