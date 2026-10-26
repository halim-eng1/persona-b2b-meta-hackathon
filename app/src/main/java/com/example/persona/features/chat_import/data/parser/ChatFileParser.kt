package com.example.persona.features.chat_import.data.parser

import com.example.persona.features.chat_import.domain.model.ChatFormat
import com.example.persona.features.chat_import.domain.model.ConversationContext
import com.example.persona.features.chat_import.domain.model.IntentType
import com.example.persona.features.chat_import.domain.model.TopicType
import com.example.persona.features.chat_import.domain.model.SentimentType
import com.example.persona.features.chat_import.domain.model.ParsedMessage
import com.example.persona.features.chat_import.domain.model.GroupChatNotSupportedException
import javax.inject.Inject

class ChatFileParser @Inject constructor(
    private val formatSniffer: FormatSniffer,
    private val lineParser: ChatLineParser
) {

    /**
     * Parses the entire file as a sequence of lines.
     * [lineSequence] is processed lazily to avoid loading the entire file into RAM.
     *
     * Returns [ParseFileResult] containing all parsed messages and metadata.
     * Throws [GroupChatNotSupportedException] if there are more than 2 distinct senders.
     */
    fun parse(
        lineSequence: Sequence<String>,
        knownUserName: String? = null
    ): ParseFileResult {
        val lineIterator = lineSequence.iterator()
        val sniffBuffer = mutableListOf<String>()

        // Sniff first 20 lines to detect format (providing a buffer in case of system messages)
        while (lineIterator.hasNext() && sniffBuffer.size < 20) {
            sniffBuffer.add(lineIterator.next())
        }

        val format = formatSniffer.detect(sniffBuffer)
        if (format == ChatFormat.UNKNOWN) {
            return ParseFileResult.FormatError(sniffBuffer.take(5))
        }

        val messages = mutableListOf<ParsedMessage>()
        var currentRaw: RawParsedLine? = null
        var continuationBuffer = StringBuilder()
        var skippedSystemCount = 0
        val distinctSenders = mutableSetOf<String>()

        val flushCurrentRaw = {
            currentRaw?.let { raw ->
                val fullText = if (continuationBuffer.isNotEmpty()) {
                    "${raw.messageText}\n${continuationBuffer}"
                } else {
                    raw.messageText
                }
                distinctSenders.add(raw.senderName)
                messages.add(
                    ParsedMessage(
                        senderName = raw.senderName,
                        messageText = fullText.trim(),
                        timestamp = raw.timestamp,
                        isUserMessage = false, // resolved later
                        context = ConversationContext(IntentType.GENERAL, TopicType.GENERAL, SentimentType.NEUTRAL, 0.4f),
                        sourceFormat = format,
                        normalizedText = null
                    )
                )
            }
        }

        fun processLine(line: String) {
            val hasTimestamp = when (format) {
                ChatFormat.WHATSAPP_ENGLISH_US -> ChatFormat.REGEX_ENGLISH_US.containsMatchIn(line)
                ChatFormat.WHATSAPP_ARABIC -> ChatFormat.REGEX_ARABIC.containsMatchIn(line)
                ChatFormat.WHATSAPP_EUROPEAN -> ChatFormat.REGEX_EUROPEAN.containsMatchIn(line)
                ChatFormat.UNKNOWN -> false
            }

            val parsed = lineParser.parseLine(line, format)
            if (parsed != null) {
                flushCurrentRaw()
                currentRaw = parsed
                continuationBuffer = StringBuilder()
            } else {
                if (hasTimestamp) {
                    // Starts a new message but is skipped (e.g. media placeholder or ignored system message).
                    // We must flush the previous active message.
                    flushCurrentRaw()
                    currentRaw = null
                    continuationBuffer = StringBuilder()
                    skippedSystemCount++
                } else {
                    // True continuation line
                    val clean = line.replace(Regex("[\u200E\u200F\uFEFF]"), "").trim()
                    if (clean.isNotBlank() && currentRaw != null) {
                        if (continuationBuffer.isNotEmpty()) continuationBuffer.append("\n")
                        continuationBuffer.append(clean)
                    } else if (currentRaw == null && clean.isNotBlank()) {
                        skippedSystemCount++
                    }
                }
            }
        }

        // Process the sniff buffer lines
        for (line in sniffBuffer) {
            processLine(line)
        }

        // Process the remaining lines from the iterator
        while (lineIterator.hasNext()) {
            processLine(lineIterator.next())
        }

        // Flush last message
        flushCurrentRaw()

        // CRITICAL GUARD: Throws GroupChatNotSupportedException if more than 2 distinct senders are detected
        if (distinctSenders.size > 2) {
            throw GroupChatNotSupportedException(
                "Group chats with ${distinctSenders.size} senders ($distinctSenders) are not supported."
            )
        }

        if (messages.isEmpty()) {
            return ParseFileResult.Success(
                messages = emptyList(),
                format = format,
                userName = "",
                skippedSystemMessages = skippedSystemCount
            )
        }

        // Resolve user identity: the most frequent sender is the user
        val userName = knownUserName ?: resolveMostFrequentSender(messages)
        val resolved = messages.map { it.copy(isUserMessage = it.senderName == userName) }

        return ParseFileResult.Success(
            messages = resolved,
            format = format,
            userName = userName,
            skippedSystemMessages = skippedSystemCount
        )
    }

    private fun resolveMostFrequentSender(messages: List<ParsedMessage>): String {
        return messages.groupingBy { it.senderName }
            .eachCount()
            .maxByOrNull { it.value }
            .let { it?.key ?: "" }
    }
}

sealed class ParseFileResult {
    data class Success(
        val messages: List<ParsedMessage>,
        val format: ChatFormat,
        val userName: String,
        val skippedSystemMessages: Int
    ) : ParseFileResult()

    data class FormatError(val sampleLines: List<String>) : ParseFileResult()
}
