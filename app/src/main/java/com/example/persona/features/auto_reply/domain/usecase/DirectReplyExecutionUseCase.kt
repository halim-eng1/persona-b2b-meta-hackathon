package com.example.persona.features.auto_reply.domain.usecase

import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.example.persona.features.auto_reply.data.service.NotificationActionCache
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

sealed interface ReplyExecutionResult {
    object Success : ReplyExecutionResult
    data class Failed(val reason: String) : ReplyExecutionResult
}

class DirectReplyExecutionUseCase @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /**
     * Executes the direct reply action for a cached notification.
     * Returns a strongly-typed ReplyExecutionResult indicating success or reason for failure.
     */
    suspend fun execute(notificationKey: String, replyText: String): ReplyExecutionResult {
        // 1. Retrieve the cached action from the LRU cache
        val action = NotificationActionCache.get(notificationKey)
            ?: return ReplyExecutionResult.Failed("Notification action not found in cache (likely expired or dismissed)")

        // 2. Extract the RemoteInput actions
        val remoteInputs = action.remoteInputs
        if (remoteInputs.isNullOrEmpty()) {
            return ReplyExecutionResult.Failed("No RemoteInputs found on notification action")
        }

        val remoteInput = remoteInputs.firstOrNull { it.resultKey != null }
            ?: return ReplyExecutionResult.Failed("No valid RemoteInput with a resultKey found")

        try {
            // 3. Create Intent and insert replyText using resultKey
            val resultsBundle = Bundle().apply {
                putCharSequence(remoteInput.resultKey, replyText)
            }

            val fillInIntent = Intent()
            RemoteInput.addResultsToIntent(remoteInputs, fillInIntent, resultsBundle)

            // 4. Fire the direct reply intent
            action.actionIntent.send(context, 0, fillInIntent)
            return ReplyExecutionResult.Success
        } catch (e: Exception) {
            return ReplyExecutionResult.Failed("PendingIntent sending failed: ${e.message}")
        }
    }
}
