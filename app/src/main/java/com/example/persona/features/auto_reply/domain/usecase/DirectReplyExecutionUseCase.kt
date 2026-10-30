package com.example.persona.features.auto_reply.domain.usecase

import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.notification.StatusBarNotification
import com.example.persona.core.logging.PersonaLogger
import com.example.persona.core.logging.PersonaLogTags
import com.example.persona.features.auto_reply.data.service.NotificationActionCache
import com.example.persona.features.auto_reply.data.service.WhatsAppNotificationListener
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
     * Executes the direct reply action for a cached or active notification.
     *
     * Tries:
     * 1. Exact match on live notificationKey in active notifications.
     * 2. Live contactName + sourceClass matching in active notifications (picking the latest child carrying RemoteInput).
     * 3. Fallback to cached Notification.Action (from dual-indexed NotificationActionCache).
     *
     * Catches [PendingIntent.CanceledException] gracefully if the notification has been invalidated by the OS.
     */
    suspend fun execute(
        notificationKey: String?,
        replyText: String,
        contactName: String,
        sourceClass: String = "BUSINESS"
    ): ReplyExecutionResult {
        PersonaLogger.d(
            PersonaLogTags.REPLY,
            "Reply execution started for $contactName ($sourceClass), length=${replyText.length}"
        )

        val listenerService = WhatsAppNotificationListener.instance?.get()
        var action: Notification.Action? = null

        if (listenerService != null) {
            val activeNotifications = listenerService.activeNotifications ?: emptyArray()

            // 1. Try exact notificationKey matching in live status bar carrying RemoteInput
            var liveSbn = if (!notificationKey.isNullOrBlank()) {
                activeNotifications.firstOrNull { sbn ->
                    sbn.key == notificationKey &&
                    sbn.notification?.actions?.any { act ->
                        act.remoteInputs?.any { r -> r.resultKey != null } == true
                    } == true
                }
            } else {
                null
            }

            // 2. Fallback to contact name and source package matching in live status bar
            if (liveSbn == null) {
                val directChildMatches = activeNotifications.filter { sbn ->
                    (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY) == 0 &&
                    matchesContact(sbn, contactName) &&
                    matchesSourceClass(sbn, sourceClass)
                }

                val groupSummaries = activeNotifications.filter { sbn ->
                    sbn.isGroup && (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0
                }

                val summaryChildMatches = mutableListOf<StatusBarNotification>()
                for (summary in groupSummaries) {
                    val children = activeNotifications.filter { sbn ->
                        sbn.groupKey == summary.groupKey &&
                        (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY) == 0 &&
                        matchesContact(sbn, contactName) &&
                        matchesSourceClass(sbn, sourceClass)
                    }
                    summaryChildMatches.addAll(children)
                }

                val allMatches = (directChildMatches + summaryChildMatches).distinctBy { it.key }
                // Smart Active Notification Selection: Pick latest active notification carrying a RemoteInput
                val matchWithRemoteInput = allMatches
                    .filter { sbn ->
                        sbn.notification?.actions?.any { act ->
                            act.remoteInputs?.any { r -> r.resultKey != null } == true
                        } == true
                    }
                    .maxByOrNull { it.postTime }

                liveSbn = matchWithRemoteInput ?: allMatches.maxByOrNull { it.postTime }
            }

            if (liveSbn != null) {
                val freshAction = liveSbn.notification?.actions?.firstOrNull { act ->
                    act.remoteInputs?.any { it.resultKey != null } == true
                }
                if (freshAction != null) {
                    NotificationActionCache.put(liveSbn.key, freshAction, sourceClass, contactName)
                    action = freshAction
                }
            }
        }

        // 3. Status bar recovery: fallback to cached Notification.Action if live notification is gone
        if (action == null) {
            action = if (!notificationKey.isNullOrBlank()) {
                NotificationActionCache.get(notificationKey)
            } else {
                null
            }
        }
        if (action == null) {
            action = NotificationActionCache.get(sourceClass, contactName)
        }

        if (action == null) {
            val reason = "Notification action not found in active status bar or cache for $contactName ($sourceClass)"
            PersonaLogger.d(PersonaLogTags.REPLY, "Execution aborted: reason=$reason")
            return ReplyExecutionResult.Aborted
        }

        // 4. Extract RemoteInput
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
            val resultsBundle = Bundle().apply {
                putCharSequence(remoteInput.resultKey, replyText)
            }

            val fillInIntent = Intent()
            RemoteInput.addResultsToIntent(remoteInputs, fillInIntent, resultsBundle)

            action.actionIntent.send(context, 0, fillInIntent)
            PersonaLogger.d(PersonaLogTags.REPLY, "Execution success")
            return ReplyExecutionResult.Success
        } catch (e: PendingIntent.CanceledException) {
            val reason = "PendingIntent canceled (notification expired or invalidated): ${e.message}"
            PersonaLogger.d(PersonaLogTags.REPLY, "Execution failure: reason=$reason")
            return ReplyExecutionResult.Failed(reason)
        } catch (e: Exception) {
            val reason = "PendingIntent sending failed: ${e.message}"
            PersonaLogger.d(PersonaLogTags.REPLY, "Execution failure: reason=$reason")
            return ReplyExecutionResult.Failed(reason)
        }
    }

    private fun matchesContact(sbn: StatusBarNotification?, contactName: String): Boolean {
        val extras = sbn?.notification?.extras ?: return false
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val convTitle = extras.getCharSequence("android.conversationTitle")?.toString()
        val hiddenConvTitle = extras.getCharSequence("android.hiddenConversationTitle")?.toString()
        return title.equals(contactName, ignoreCase = true) ||
            convTitle.equals(contactName, ignoreCase = true) ||
            hiddenConvTitle.equals(contactName, ignoreCase = true)
    }

    private fun matchesSourceClass(sbn: StatusBarNotification, sourceClass: String): Boolean {
        return when (sourceClass.uppercase()) {
            "MAIN_APP" -> sbn.packageName == "com.whatsapp"
            "BUSINESS" -> sbn.packageName == "com.whatsapp.w4b"
            else -> true
        }
    }
}
