package com.example.persona.features.auto_reply.domain.usecase

import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.example.persona.core.logging.PersonaLogger
import com.example.persona.core.logging.PersonaLogTags
import com.example.persona.features.auto_reply.data.service.NotificationActionCache
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

sealed interface ReplyExecutionResult {
    object Success : ReplyExecutionResult
    object Aborted : ReplyExecutionResult
    data class Failed(val reason: String) : ReplyExecutionResult
}

class DirectReplyExecutionUseCase @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /**
     * Executes the direct reply action for a cached notification.
     * Returns a strongly-typed ReplyExecutionResult indicating success or reason for failure.
     */
    suspend fun execute(
        notificationKey: String,
        replyText: String,
        contactName: String
    ): ReplyExecutionResult {
        PersonaLogger.d(
            PersonaLogTags.REPLY,
            "Reply generated: length=${replyText.length}, hash=${replyText.hashCode().toString(16)}"
        )

        // 1. Check live notifications in status bar and extract fresh reply action
        val listenerService = com.example.persona.features.auto_reply.data.service.WhatsAppNotificationListener.instance?.get()
        val action = if (listenerService != null) {
            val activeNotifications = listenerService.activeNotifications ?: emptyArray()

            // Always prioritize exact notificationKey matching
            var liveSbn = activeNotifications.firstOrNull { it.key == notificationKey }

            // Fallback to contact name matching if key match not found
            if (liveSbn == null) {
                // Find all child notifications that match the contactName directly
                val directChildMatches = activeNotifications.filter { sbn ->
                    (sbn.notification.flags and android.app.Notification.FLAG_GROUP_SUMMARY) == 0 &&
                    matchesContact(sbn, contactName)
                }

                // Find group summaries
                val groupSummaries = activeNotifications.filter { sbn ->
                    sbn.isGroup && (sbn.notification.flags and android.app.Notification.FLAG_GROUP_SUMMARY) != 0
                }

                // Find child notifications of those group summaries that match the contactName
                val summaryChildMatches = mutableListOf<android.service.notification.StatusBarNotification>()
                for (summary in groupSummaries) {
                    val children = activeNotifications.filter { sbn ->
                        sbn.groupKey == summary.groupKey && 
                        (sbn.notification.flags and android.app.Notification.FLAG_GROUP_SUMMARY) == 0
                    }
                    val matchedChildren = children.filter { child ->
                        matchesContact(child, contactName)
                    }
                    summaryChildMatches.addAll(matchedChildren)
                }

                // Combine and deduplicate matches by key
                val allMatches = (directChildMatches + summaryChildMatches).distinctBy { it.key }

                if (allMatches.size > 1) {
                    val reason = "Aborted: Multiple active notifications (${allMatches.size}) match the contact name '$contactName'. Aborting to prevent cross-fire misrouting."
                    PersonaLogger.d(PersonaLogTags.REPLY, "Execution failure: reason=$reason")
                    return ReplyExecutionResult.Aborted
                }

                liveSbn = allMatches.firstOrNull()
            }

            if (liveSbn == null) {
                val reason = "Aborted: notification no longer active in status bar for $contactName"
                PersonaLogger.d(PersonaLogTags.REPLY, "Execution failure: reason=$reason")
                return ReplyExecutionResult.Aborted
            }

            val freshAction = liveSbn.notification?.actions?.firstOrNull { act ->
                act.remoteInputs?.any { it.resultKey != null } == true
            }
            if (freshAction != null) {
                NotificationActionCache.put(liveSbn.key, freshAction)
                freshAction
            } else {
                NotificationActionCache.get(notificationKey)
            }
        } else {
            NotificationActionCache.get(notificationKey)
        }

        if (action == null) {
            val reason = "Notification action not found in cache (likely expired or dismissed)"
            PersonaLogger.d(PersonaLogTags.REPLY, "Execution failure: reason=$reason")
            return ReplyExecutionResult.Failed(reason)
        }

        // 2. Extract the RemoteInput actions
        val remoteInputs = action.remoteInputs
        if (remoteInputs.isNullOrEmpty()) {
            val reason = "No RemoteInputs found on notification action"
            PersonaLogger.d(PersonaLogTags.REPLY, "Execution failure: reason=$reason")
            return ReplyExecutionResult.Failed(reason)
        }

        val remoteInput = remoteInputs.firstOrNull { it.resultKey != null }
        if (remoteInput == null) {
            val reason = "No valid RemoteInput with a resultKey found"
            PersonaLogger.d(PersonaLogTags.REPLY, "Execution failure: reason=$reason")
            return ReplyExecutionResult.Failed(reason)
        }

        try {
            // 3. Create Intent and insert replyText using resultKey
            val resultsBundle = Bundle().apply {
                putCharSequence(remoteInput.resultKey, replyText)
            }

            val fillInIntent = Intent()
            RemoteInput.addResultsToIntent(remoteInputs, fillInIntent, resultsBundle)

            // 4. Fire the direct reply intent
            action.actionIntent.send(context, 0, fillInIntent)
            PersonaLogger.d(PersonaLogTags.REPLY, "Execution success")
            return ReplyExecutionResult.Success
        } catch (e: Exception) {
            val reason = "PendingIntent sending failed: ${e.message}"
            PersonaLogger.d(PersonaLogTags.REPLY, "Execution failure: reason=$reason")
            return ReplyExecutionResult.Failed(reason)
        }
    }

    private fun matchesContact(sbn: android.service.notification.StatusBarNotification?, contactName: String): Boolean {
        val extras = sbn?.notification?.extras ?: return false
        val title = extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString()
        val convTitle = extras.getCharSequence("android.conversationTitle")?.toString()
        val hiddenConvTitle = extras.getCharSequence("android.hiddenConversationTitle")?.toString()
        return title == contactName || convTitle == contactName || hiddenConvTitle == contactName
    }
}
