package com.example.persona.features.chats.presentation.detail

import com.example.persona.core.database.entity.ChatHistoryEntity
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pure Kotlin formatter and time-boundary calculator for chat transcript export and pruning.
 */
@Singleton
class ChatExportFormatter @Inject constructor() {

    companion object {
        const val TRANSCRIPT_DIVIDER = "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    }

    /**
     * Calculates the millisecond boundary timestamp for [ExportTimeRange].
     */
    fun calculateSinceTimestamp(
        range: ExportTimeRange,
        currentTimeMs: Long = System.currentTimeMillis()
    ): Long {
        return when (range) {
            ExportTimeRange.ALL_TIME -> 0L
            ExportTimeRange.TODAY -> {
                val cal = Calendar.getInstance().apply {
                    timeInMillis = currentTimeMs
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                cal.timeInMillis
            }
            ExportTimeRange.LAST_TWO_HOURS -> {
                currentTimeMs - (2 * 60 * 60 * 1000L)
            }
        }
    }

    /**
     * Calculates the millisecond boundary timestamp for [ClearChatTimeRange].
     */
    fun calculateClearSinceTimestamp(
        range: ClearChatTimeRange,
        currentTimeMs: Long = System.currentTimeMillis()
    ): Long {
        return when (range) {
            ClearChatTimeRange.ALL_TIME -> 0L
            ClearChatTimeRange.LAST_HOUR -> currentTimeMs - (1 * 60 * 60 * 1000L)
            ClearChatTimeRange.LAST_TWO_HOURS -> currentTimeMs - (2 * 60 * 60 * 1000L)
            ClearChatTimeRange.TODAY -> {
                val cal = Calendar.getInstance().apply {
                    timeInMillis = currentTimeMs
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                cal.timeInMillis
            }
        }
    }

    /**
     * Formats a structured multilingual (Arabic/English) transcript of [messages] with headers and dividers.
     */
    fun buildExportTranscript(
        contactName: String,
        sourceClass: String,
        storeName: String,
        botName: String,
        range: ExportTimeRange,
        exportTimestampMs: Long,
        messages: List<ChatHistoryEntity>
    ): String {
        val exportDateStr = formatExportTimestamp(exportTimestampMs)
        val lines = mutableListOf<String>()
        lines.add("سجل محادثة: $contactName ($sourceClass)")
        lines.add("المتجر: $storeName")
        lines.add("تاريخ التصدير: $exportDateStr")
        lines.add("نطاق التصدير: ${range.exportLabel}")
        lines.add(TRANSCRIPT_DIVIDER)

        for (msg in messages) {
            val timeStr = formatExportTimestamp(msg.timestamp)
            val senderLabel = when {
                msg.isFromUser || msg.senderType.equals("CUSTOMER", ignoreCase = true) -> "العميل"
                msg.senderType.equals("MERCHANT_MANUAL", ignoreCase = true) -> "التاجر (Manual)"
                else -> "$botName (AI)"
            }
            lines.add("[$timeStr] $senderLabel: ${msg.messageText}")
        }

        lines.add(TRANSCRIPT_DIVIDER)
        return lines.joinToString("\n")
    }

    /**
     * Formats a millisecond timestamp into `yyyy-MM-dd HH:mm:ss`.
     */
    fun formatExportTimestamp(timestampMs: Long): String {
        return try {
            val dt = Instant.fromEpochMilliseconds(timestampMs).toLocalDateTime(TimeZone.currentSystemDefault())
            String.format(
                Locale.US,
                "%04d-%02d-%02d %02d:%02d:%02d",
                dt.year,
                dt.monthNumber,
                dt.dayOfMonth,
                dt.hour,
                dt.minute,
                dt.second
            )
        } catch (e: Exception) {
            ""
        }
    }
}
