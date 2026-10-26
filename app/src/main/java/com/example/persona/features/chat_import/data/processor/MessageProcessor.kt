package com.example.persona.features.chat_import.data.processor

import com.example.persona.features.chat_import.data.normalizer.ArabicTextNormalizer
import com.example.persona.features.chat_import.domain.model.ParsedMessage
import javax.inject.Inject

class MessageProcessor @Inject constructor(
    private val normalizer: ArabicTextNormalizer,
    private val grouper: MessageGrouper,
    private val contextDetector: ContextDetector
) {
    /**
     * Full processing pipeline:
     * 1. Normalize Arabic text
     * 2. Detect context for each message
     * 3. Group consecutive same-sender messages
     * 4. Re-classify context on grouped messages (combined text)
     */
    fun process(messages: List<ParsedMessage>): List<ParsedMessage> {
        // Step 1 & 2: Normalize + detect context on individual messages
        val enriched = messages.map { msg ->
            val norm = normalizer.normalize(msg.messageText)
            msg.copy(
                normalizedText = norm,
                context = contextDetector.detect(norm ?: msg.messageText)
            )
        }

        // Step 3: Group consecutive messages
        val grouped = grouper.group(enriched)

        // Step 4: Re-classify context on grouped messages (the combined text may change context)
        return grouped.map { msg ->
            if (msg.isGrouped) {
                val norm = normalizer.normalize(msg.messageText)
                msg.copy(
                    normalizedText = norm,
                    context = contextDetector.detect(norm ?: msg.messageText)
                )
            } else {
                msg
            }
        }
    }
}
