package com.example.persona.features.auto_reply.data.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.persona.R
import com.example.persona.features.auto_reply.domain.model.WhatsAppNotification
import com.example.persona.features.chat_import.data.embedding.OnnxModelManager
import com.example.persona.features.chat_import.data.embedding.HuggingFaceTokenizer
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

@AndroidEntryPoint
class WhatsAppNotificationListener : NotificationListenerService() {

    @Inject lateinit var messageDispatcher: IncomingMessageDispatcher
    @Inject lateinit var modelManager: OnnxModelManager
    @Inject lateinit var tokenizer: HuggingFaceTokenizer

    private val serviceScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + CoroutineName("NLS-AutoReply")
    )

    companion object {
        private val WHATSAPP_PACKAGES = setOf(
            "com.whatsapp",
            "com.whatsapp.w4b"
        )
        private const val EXTRA_CONVERSATION_TITLE = "android.hiddenConversationTitle"
        
        const val CHANNEL_ID = "persona_auto_reply_channel"
        const val NOTIFICATION_ID = 1001
        private const val DEDUP_TTL_MS = 30_000L  // 30 seconds
    }

    private val recentKeys = ConcurrentHashMap<String, Long>(64)

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        // 1. Filter: only WhatsApp packages
        if (sbn.packageName !in WHATSAPP_PACKAGES) return

        // 2. Filter: only messaging-style notifications
        val extras = sbn.notification?.extras ?: return
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: return

        // 3. Skip group summary notifications
        if (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return

        // 4. ConcurrentHashMap-based dedup with TTL
        val dedupeKey = "${sbn.key}_${text.hashCode()}"
        val now = System.currentTimeMillis()
        recentKeys.entries.removeAll { now - it.value > DEDUP_TTL_MS }
        if (recentKeys.putIfAbsent(dedupeKey, now) != null) return // Already processed

        // 5. Extract the RemoteInput reply action and cache it
        val replyAction = sbn.notification.actions?.firstOrNull { action ->
            action.remoteInputs?.any { it.resultKey != null } == true
        }

        // Cache the reply action for later DirectReply execution
        if (replyAction != null) {
            NotificationActionCache.put(sbn.key, replyAction)
        }

        // 6. Build domain model and dispatch
        val whatsappNotification = WhatsAppNotification(
            title = title,
            text = text,
            postTime = sbn.postTime,
            notificationKey = sbn.key
        )

        messageDispatcher.dispatch(whatsappNotification)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (sbn.packageName in WHATSAPP_PACKAGES) {
            NotificationActionCache.remove(sbn.key)
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        createNotificationChannel()
        
        val notification = buildSilentNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(
                NOTIFICATION_ID,
                notification
            )
        }

        // Lazy Initialization: pre-warm ONNX model and tokenizer in a background coroutine to prevent ANR
        serviceScope.launch(Dispatchers.IO) {
            try {
                tokenizer.initialize()
                modelManager.loadModel()
                Log.i("NLS", "ONNX model and tokenizer pre-warmed in background successfully")
            } catch (e: Exception) {
                Log.e("NLS", "Failed to lazy initialize ONNX model/tokenizer in background", e)
            }
        }

        // Start listening to the messages flow
        startListeningForMessages()
    }

    override fun onListenerDisconnected() {
        serviceScope.cancel()
        // Request rebind so the OS restores the NLS on next opportunity
        requestRebind(ComponentName(this, WhatsAppNotificationListener::class.java))
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startListeningForMessages() {
        serviceScope.launch {
            messageDispatcher.notifications.collect { notification ->
                Log.d("NLS", "Received notification: ${notification.notificationKey} - ${notification.title}: ${notification.text}")
            }
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Auto-Reply Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Keeps PersonaChat auto-reply running"
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_SECRET
        }
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    private fun buildSilentNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("PersonaChat Active")
            .setContentText("Auto-reply is monitoring messages")
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }
}
