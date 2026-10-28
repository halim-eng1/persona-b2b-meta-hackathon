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
import com.example.persona.core.data.repository.RoutingPrefs
import com.example.persona.core.data.repository.UserPreferencesRepository
import com.example.persona.core.logging.PersonaLogger
import com.example.persona.core.logging.PersonaLogTags
import com.example.persona.core.debug.DebugFlags
import com.example.persona.features.auto_reply.data.diagnostics.NotificationSourceClass
import com.example.persona.features.auto_reply.data.diagnostics.NotificationSourceDiagnostics
import com.example.persona.features.auto_reply.domain.model.ExtraMessage
import com.example.persona.features.auto_reply.domain.model.WhatsAppNotification
import com.example.persona.features.auto_reply.domain.orchestrator.AutoReplyOrchestrator
import com.example.persona.features.chat_import.data.embedding.HuggingFaceTokenizer
import com.example.persona.features.chat_import.data.embedding.OnnxModelManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.combine
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

@AndroidEntryPoint
class WhatsAppNotificationListener : NotificationListenerService() {

    @Inject lateinit var messageDispatcher: IncomingMessageDispatcher
    @Inject lateinit var modelManager: OnnxModelManager
    @Inject lateinit var tokenizer: HuggingFaceTokenizer
    @Inject lateinit var orchestrator: AutoReplyOrchestrator
    @Inject lateinit var sourceDiagnostics: NotificationSourceDiagnostics
    @Inject lateinit var prefsRepository: UserPreferencesRepository
    @Inject lateinit var mediaExtractor: WhatsAppMediaExtractor

    private val serviceScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + CoroutineName("NLS-AutoReply")
    )

    /**
     * Cached routing prefs. Updated lazily from the DataStore on each notification
     * using a lightweight in-memory cache — avoids a full suspend call on the hot path
     * while still picking up pref changes within a few seconds.
     */
    @Volatile
    private var cachedRoutingPrefs: RoutingPrefs = RoutingPrefs(
        listenMainApp = true,
        listenBusinessApp = true,
        listenCloneApp = false
    )

    companion object {
        @Volatile
        var instance: java.lang.ref.WeakReference<WhatsAppNotificationListener>? = null

        private val WHATSAPP_PACKAGES = setOf(
            "com.whatsapp",
            "com.whatsapp.w4b"
        )

        /**
         * Titles that indicate the notification is from the user's own outgoing
         * messages or from WhatsApp system messages. These must be ignored to
         * prevent the bot from replying to itself.
         */
        private val SELF_REPLY_TITLES = setOf(
            "You",
            "أنت",
            "WhatsApp",
            "WhatsApp Business"
        )

        const val CHANNEL_ID = "persona_auto_reply_channel"
        const val NOTIFICATION_ID = 1001
        private const val DEDUP_TTL_MS = 30_000L  // 30 seconds

        private val recentlyDispatchedMerchantReplies = ConcurrentHashMap<String, Long>(64)
        private const val MERCHANT_ECHO_TTL_MS = 60_000L

        fun registerMerchantReply(contactName: String, replyText: String) {
            val key = "${contactName.trim().lowercase()}_${replyText.trim().hashCode()}"
            recentlyDispatchedMerchantReplies[key] = System.currentTimeMillis()
        }

        fun isMerchantReplyEcho(contactName: String, text: String): Boolean {
            val now = System.currentTimeMillis()
            recentlyDispatchedMerchantReplies.entries.removeAll { now - it.value > MERCHANT_ECHO_TTL_MS }
            val key = "${contactName.trim().lowercase()}_${text.trim().hashCode()}"
            return recentlyDispatchedMerchantReplies.containsKey(key)
        }
    }

    private val recentKeys = ConcurrentHashMap<String, Long>(64)

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        // ── 1. Package filter & Ongoing filter ─────────────────────────────────
        if (sbn.packageName !in WHATSAPP_PACKAGES) return
        if (sbn.isOngoing) return

        // ── 1a. Diagnostics (non-blocking, fire-and-forget) ───────────────────
        if (DebugFlags.ENABLE_NOTIFICATION_DIAGNOSTICS) {
            sourceDiagnostics.captureAndLog(sbn)
        }

        // ── 2. UserHandle-based source classification ──────────────────────────
        val userIdentifier = reflectUserIdentifier(sbn)
        val sourceClass = NotificationSourceDiagnostics.classify(sbn.packageName, userIdentifier)

        // ── 3. Routing gate ────────────────────────────────────────────────────
        //     Check the cached routing prefs. The prefs are refreshed on a background
        //     coroutine so we never block the NLS thread.
        val prefs = cachedRoutingPrefs
        val shouldProcess = when (sourceClass) {
            NotificationSourceClass.MAIN_APP  -> prefs.listenMainApp
            NotificationSourceClass.BUSINESS  -> prefs.listenBusinessApp
            NotificationSourceClass.CLONE     -> prefs.listenCloneApp
            NotificationSourceClass.UNKNOWN   -> false
        }
        if (!shouldProcess) {
            PersonaLogger.d(
                PersonaLogTags.NOTIFICATION,
                "Skipped notification: sourceClass=$sourceClass key=${sbn.key}"
            )
            return
        }

        // ── 4. Messaging style filter ─────────────────────────────────────────
        val extras = sbn.notification?.extras ?: return
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: return

        // Skip group summary banners
        if (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return

        // ── 4a. Self-reply guard (case-insensitive) ───────────────────────────
        //     Ignore notifications from the user's own outgoing messages or
        //     WhatsApp system messages to prevent the bot from replying to itself.
        if (SELF_REPLY_TITLES.any { it.equals(title.trim(), ignoreCase = true) }) {
            PersonaLogger.d(
                PersonaLogTags.NOTIFICATION,
                "Skipped self-reply notification: title=$title key=${sbn.key}"
            )
            return
        }

        if (isMerchantReplyEcho(title, text)) {
            PersonaLogger.d(
                PersonaLogTags.NOTIFICATION,
                "Skipped merchant reply echo notification: title=$title key=${sbn.key}"
            )
            return
        }

        // ── 4b. RemoteInput reply action filter ───────────────────────────────
        //     Non-replyable system/sync/backup notifications (e.g., "Checking for new messages")
        //     must be dropped immediately at the source so they never enter
        //     IncomingMessageDispatcher or chat_history.
        val replyAction = sbn.notification.actions?.firstOrNull { action ->
            action.remoteInputs?.any { it.resultKey != null } == true
        }
        if (replyAction == null) {
            PersonaLogger.d(
                PersonaLogTags.NOTIFICATION,
                "Skipped non-replyable notification (no RemoteInput action): title=$title key=${sbn.key}"
            )
            return
        }

        PersonaLogger.d(
            PersonaLogTags.NOTIFICATION,
            "Notification accepted: source=$sourceClass title=$title key=${sbn.key}"
        )

        // ── 5. Dedup ──────────────────────────────────────────────────────────
        val dedupeKey = "${sbn.key}_${text.hashCode()}"
        val now = System.currentTimeMillis()
        recentKeys.entries.removeAll { now - it.value > DEDUP_TTL_MS }
        if (recentKeys.putIfAbsent(dedupeKey, now) != null) return

        // ── 6. Cache reply action ─────────────────────────────────────────────
        NotificationActionCache.put(sbn.key, replyAction, sourceClass.name, title)

        // ── 7. Extract extra messages ─────────────────────────────────────────
        val extraMessagesList = mutableListOf<ExtraMessage>()
        try {
            val parcelableMessages = extras.getParcelableArray(Notification.EXTRA_MESSAGES)
            if (parcelableMessages != null) {
                for (parcelable in parcelableMessages) {
                    if (parcelable is android.os.Bundle) {
                        val msgText = parcelable.getCharSequence("text")?.toString()
                        val msgTimestamp = parcelable.getLong("time")
                        var senderName: String? = null
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            val senderPerson = parcelable.getParcelable<android.app.Person>("sender_person")
                            senderName = senderPerson?.name?.toString()
                        }
                        if (senderName == null) {
                            senderName = parcelable.getCharSequence("sender")?.toString()
                        }
                        if (msgText != null) {
                            val isUser = senderName != null && (
                                senderName.equals("You", ignoreCase = true) ||
                                senderName.equals("أنت", ignoreCase = true)
                            )
                            extraMessagesList.add(ExtraMessage(text = msgText, timestamp = msgTimestamp, isUser = isUser))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("NLS", "Failed to extract EXTRA_MESSAGES for ${sbn.key}", e)
        }

        if (extraMessagesList.isNotEmpty() && extraMessagesList.last().isUser && text.equals(extraMessagesList.last().text, ignoreCase = true)) {
            PersonaLogger.d(
                PersonaLogTags.NOTIFICATION,
                "Skipped extra messages self-echo: title=$title key=${sbn.key}"
            )
            return
        }

        // ── 8. Extract media, transcribe voice notes if present, and dispatch ─
        serviceScope.launch(Dispatchers.IO) {
            var effectiveText = text
            var mediaFilePath: String? = null
            var isVoiceFailed = false

            try {
                val isVoice = mediaExtractor.hasAudioIndicator(sbn, text)

                // Phase 1: Direct Image Extraction from Notification Extras (only if NOT voice)
                if (!isVoice && mediaExtractor.hasNotificationImage(sbn, text)) {
                    val imageFile = mediaExtractor.extractImage(sbn, sourceClass, this@WhatsAppNotificationListener, currentText = text)
                    if (imageFile != null) {
                        mediaFilePath = imageFile.absolutePath
                    }
                }

                // Phase 2: Voice Note (.opus / .ogg) Interception & Whisper Transcription
                if (isVoice) {
                    // 1. Direct MessagingStyle Audio Extraction
                    val directAudioFile = mediaExtractor.extractAudioFromNotification(sbn)
                    val durationSeconds = mediaExtractor.extractDurationSeconds(text)
                    val audioFile = directAudioFile ?: mediaExtractor.captureLatestVoiceNote(
                        sourceClass = sourceClass,
                        postTime = sbn.postTime,
                        expectedDurationSeconds = durationSeconds
                    )

                    if (audioFile != null) {
                        val transcribed = mediaExtractor.transcribeVoiceNote(audioFile)
                        if (!transcribed.isNullOrBlank()) {
                            effectiveText = "[رسالة صوتية من العميل]: $transcribed"
                            mediaFilePath = null // CRITICAL: Transcribed voice is a pure text turn; never an image attachment!
                            Log.i("PersonaMedia", "Voice message converted to pure text turn: '$effectiveText'")
                        } else {
                            isVoiceFailed = true
                        }
                    } else {
                        isVoiceFailed = true
                    }
                }
            } catch (e: Exception) {
                Log.e("PersonaMedia", "Error during media extraction or transcription: ${e.message}", e)
                if (mediaExtractor.hasAudioIndicator(sbn, text)) {
                    isVoiceFailed = true
                }
            }

            val whatsappNotification = WhatsAppNotification(
                title = title,
                text = effectiveText,
                postTime = sbn.postTime,
                notificationKey = sbn.key,
                extraMessages = extraMessagesList,
                sourceClass = sourceClass,
                mediaFilePath = mediaFilePath,
                isVoiceTranscriptionFailed = isVoiceFailed
            )

            messageDispatcher.dispatch(whatsappNotification)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        // Retain cached actions in NotificationActionCache for 24h TTL to support manual reply recovery.
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = java.lang.ref.WeakReference(this)
        createNotificationChannel()

        val notification = buildSilentNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        // Pre-warm ONNX model + tokenizer in background
        serviceScope.launch(Dispatchers.IO) {
            try {
                tokenizer.initialize()
                modelManager.loadModel()
                Log.i("NLS", "ONNX model and tokenizer pre-warmed successfully")
            } catch (e: Exception) {
                Log.e("NLS", "Failed to pre-warm ONNX model/tokenizer", e)
            }
        }

        // Continuously refresh routing prefs in the background
        serviceScope.launch(Dispatchers.IO) {
            combine(
                prefsRepository.listenMainApp,
                prefsRepository.listenBusinessApp,
                prefsRepository.listenCloneApp
            ) { listenMain, listenBusiness, listenClone ->
                RoutingPrefs(
                    listenMainApp = listenMain,
                    listenBusinessApp = listenBusiness,
                    listenCloneApp = listenClone
                )
            }.collect { prefs ->
                cachedRoutingPrefs = prefs
            }
        }

        startListeningForMessages()
        orchestrator.start()
    }

    override fun onListenerDisconnected() {
        if (instance?.get() === this) instance = null
        serviceScope.cancel()
        requestRebind(ComponentName(this, WhatsAppNotificationListener::class.java))
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        if (instance?.get() === this) instance = null
        serviceScope.cancel()
        super.onDestroy()
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ──────────────────────────────────────────────────────────────────────────

    private fun startListeningForMessages() {
        serviceScope.launch {
            messageDispatcher.notifications.collect { notification ->
                Log.d("NLS", "Dispatched: key=${notification.notificationKey} source=${notification.sourceClass} title=${notification.title}")
            }
        }
    }

    /**
     * Refreshes the routing prefs cache from DataStore.
     * Called once on connect and whenever the routing prefs flows emit.
     */
    private suspend fun refreshRoutingPrefs() {
        try {
            cachedRoutingPrefs = prefsRepository.getRoutingPrefsSync()
        } catch (e: Exception) {
            Log.w("NLS", "Failed to refresh routing prefs: ${e.message}")
        }
    }

    /**
     * Reflectively reads [UserHandle.identifier] from the SBN's user field.
     * Returns 0 on any failure (treats as primary user — MAIN_APP path).
     */
    private fun reflectUserIdentifier(sbn: StatusBarNotification): Int {
        return try {
            val userHandle = sbn.user ?: return 0
            val raw = userHandle.javaClass.getMethod("getIdentifier").invoke(userHandle) ?: return 0
            (raw as? Int) ?: raw.toString().toIntOrNull() ?: 0
        } catch (_: Throwable) {
            0
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
            .setContentTitle("Persona B2B Active")
            .setContentText("المساعد الآلي يراقب الرسائل الواردة")
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }
}
