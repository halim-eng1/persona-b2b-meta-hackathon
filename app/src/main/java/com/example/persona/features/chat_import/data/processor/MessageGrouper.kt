package com.example.persona.features.chat_import.data.processor

import com.example.persona.features.chat_import.domain.model.ParsedMessage
import javax.inject.Inject

class MessageGrouper @Inject constructor() {

    companion object {
        /** Maximum time gap between messages to consider them part of the same group */
        private const val GROUP_WINDOW_MILLIS = 2 * 60 * 1000L // 2 minutes
    }

    /**
     * Merges consecutive messages from the same sender within [GROUP_WINDOW_MILLIS]
     * into a single [ParsedMessage] with combined text.
     *
     * The resulting message uses:
     * - timestamp of the FIRST message in the group
     * - combined text joined by newlines
     * - isGrouped = true if 2+ messages were merged
     */
    fun group(messages: List<ParsedMessage>): List<ParsedMessage> {
        if (messages.isEmpty()) return emptyList()

        val grouped = mutableListOf<ParsedMessage>()
        var currentGroup = mutableListOf(messages.first())

        for (i in 1 until messages.size) {
            val current = messages[i]
            val last = currentGroup.last()

            val sameSender = current.senderName == last.senderName
            val withinWindow = (current.timestamp.toEpochMilliseconds() -
                last.timestamp.toEpochMilliseconds()) <= GROUP_WINDOW_MILLIS

            if (sameSender && withinWindow) {
                currentGroup.add(current)
            } else {
                grouped.add(mergeGroup(currentGroup))
                currentGroup = mutableListOf(current)
            }
        }
        grouped.add(mergeGroup(currentGroup))

        return grouped
    }

    private fun mergeGroup(group: List<ParsedMessage>): ParsedMessage {
        if (group.size == 1) return group.first()

        val combinedText = group.joinToString("\n") { it.messageText }
        return group.first().copy(
            messageText = combinedText,
            isGrouped = true
        )
    }
}
