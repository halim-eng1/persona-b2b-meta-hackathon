package com.example.persona.features.chats.domain.usecase

import android.util.Log
import com.example.persona.core.database.Converters
import com.example.persona.core.database.dao.ChatHistoryDao
import com.example.persona.core.database.dao.ConversationMetadataDao
import com.example.persona.core.database.entity.ChatHistoryEntity
import com.example.persona.core.di.ApplicationScope
import com.example.persona.features.auto_reply.data.service.WhatsAppNotificationListener
import com.example.persona.features.auto_reply.domain.orchestrator.AutoReplyOrchestrator
import com.example.persona.features.auto_reply.domain.usecase.DirectReplyExecutionUseCase
import com.example.persona.features.auto_reply.domain.usecase.ReplyExecutionResult
import com.example.persona.features.chat_import.data.embedding.EmbeddingEngine
import dagger.Lazy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

sealed interface ManualReplyResult {
    data class Success(val messageId: Long) : ManualReplyResult
    data class Queued(val messageId: Long, val message: String) : ManualReplyResult
    data class Failed(val messageId: Long, val reason: String, val targetPackage: String) : ManualReplyResult
}

@Singleton
class SendManualReplyUseCase @Inject constructor(
    private val chatHistoryDao: ChatHistoryDao,
    private val conversationMetadataDao: ConversationMetadataDao,
    private val directReplyExecutionUseCase: DirectReplyExecutionUseCase,
    private val embeddingEngine: EmbeddingEngine,
    @ApplicationScope private val scope: CoroutineScope,
    private val autoReplyOrchestratorLazy: Lazy<AutoReplyOrchestrator>
) {
    // Secondary constructor for testing and backwards-compatibility
    constructor(
        chatHistoryDao: ChatHistoryDao,
        conversationMetadataDao: ConversationMetadataDao,
        directReplyExecutionUseCase: DirectReplyExecutionUseCase,
        embeddingEngine: EmbeddingEngine,
        scope: CoroutineScope,
        autoReplyOrchestrator: AutoReplyOrchestrator? = null
    ) : this(
        chatHistoryDao = chatHistoryDao,
        conversationMetadataDao = conversationMetadataDao,
        directReplyExecutionUseCase = directReplyExecutionUseCase,
        embeddingEngine = embeddingEngine,
        scope = scope,
        autoReplyOrchestratorLazy = Lazy { autoReplyOrchestrator ?: error("No orchestrator provided") }
    )

    companion object {
        private const val TAG = "SendManualReplyUseCase"
    }

    suspend operator fun invoke(
        contactName: String,
        sourceClass: String,
        replyText: String,
        retryMessageId: Long? = null
    ): ManualReplyResult {
        val trimmedText = replyText.trim()
        val now = System.currentTimeMillis()

        // 0. Cancel any in-flight AI turn in AutoReplyOrchestrator immediately
        try {
            autoReplyOrchestratorLazy.get()?.cancelInFlightTurn(contactName)
        } catch (e: Exception) {
            // Safe fallback when orchestrator is not provided in unit tests
        }

        // 1. Suppress self-reply echo in notification listener
        WhatsAppNotificationListener.registerMerchantReply(contactName, trimmedText)

        // 2. Either reuse existing failed message row or insert an optimistic turn
        val messageId = if (retryMessageId != null) {
            chatHistoryDao.updateDeliveryStatus(retryMessageId, "SENDING")
            retryMessageId
        } else {
            val optimisticEntity = ChatHistoryEntity(
                contactName = contactName,
                messageText = trimmedText,
                isFromUser = false,
                timestamp = now,
                embedding = null,
                embeddingState = "PENDING",
                sourceClass = sourceClass,
                senderType = "MERCHANT_MANUAL",
                deliveryStatus = "SENDING",
                isRead = true
            )
            chatHistoryDao.insert(optimisticEntity)
        }

        // 3. Human-AI race guard: unconditionally pause AI for this contact when merchant sends a manual reply.
        // Note: This is an intentional manual merchant takeover (distinct from automated HITL escalation
        // which respects the merchant's `auto_pause_on_hitl` preference).
        conversationMetadataDao.setAiPaused(contactName, sourceClass, true)

        // 4. Update conversation metadata snippet and timestamp, preserving isAiPaused = true
        conversationMetadataDao.recordMessageTurn(
            contactName = contactName,
            sourceClass = sourceClass,
            messageText = trimmedText,
            senderType = "MERCHANT_MANUAL",
            timestamp = now,
            notificationKey = null,
            incrementUnread = false,
            isAiPaused = true
        )

        // 5. Dispatch via DirectReplyExecutionUseCase
        val executionResult = directReplyExecutionUseCase.execute(
            notificationKey = null,
            replyText = trimmedText,
            contactName = contactName,
            sourceClass = sourceClass
        )

        val targetPackage = getTargetPackage(sourceClass)

        return when (executionResult) {
            is ReplyExecutionResult.Success -> {
                chatHistoryDao.updateDeliveryStatus(messageId, "SENT")
                conversationMetadataDao.setNeedsHumanAttention(contactName, sourceClass, false)

                // Background embedding computation for multi-turn LLM context
                scope.launch(Dispatchers.Default) {
                    val claimSuccess = chatHistoryDao.claimForProcessing(messageId)
                    if (claimSuccess > 0) {
                        try {
                            val embedding = embeddingEngine.embedForAutoReply(trimmedText)
                            if (embedding != null) {
                                val blob = Converters().fromFloatArray(embedding)
                                if (blob != null) {
                                    chatHistoryDao.updateEmbeddingRaw(messageId, blob)
                                } else {
                                    chatHistoryDao.updateEmbeddingState(messageId, "FAILED")
                                }
                            } else {
                                chatHistoryDao.updateEmbeddingState(messageId, "FAILED")
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Embedding failed for manual reply $messageId", e)
                            chatHistoryDao.updateEmbeddingState(messageId, "FAILED")
                        }
                    }
                }

                ManualReplyResult.Success(messageId)
            }

            is ReplyExecutionResult.Aborted -> {
                // Map Aborted -> FAILED (immediate user feedback, retry & open in WhatsApp available)
                chatHistoryDao.updateDeliveryStatus(messageId, "FAILED")
                ManualReplyResult.Failed(messageId, "Awaiting active notification from WhatsApp", targetPackage)
            }

            is ReplyExecutionResult.Failed -> {
                chatHistoryDao.updateDeliveryStatus(messageId, "FAILED")
                ManualReplyResult.Failed(messageId, executionResult.reason, targetPackage)
            }
        }
    }

    private fun getTargetPackage(sourceClass: String): String = when (sourceClass.uppercase()) {
        "MAIN_APP" -> "com.whatsapp"
        "BUSINESS" -> "com.whatsapp.w4b"
        else -> "com.whatsapp"
    }
}
