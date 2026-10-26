package com.example.persona.features.auto_reply.domain.orchestrator

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.persona.R
import com.example.persona.core.database.dao.ConversationMetadataDao
import com.example.persona.features.auto_reply.domain.model.ProcessedNotification
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles system notification channels, alarm vibration, and Human-In-The-Loop notifications.
 */
@Singleton
class HitlNotificationHandler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val conversationMetadataDao: ConversationMetadataDao
) {
    companion object {
        private const val TAG = "HitlNotificationHandler"
        const val ALARM_CHANNEL_ID = "hitl_alarm_channel"
    }

    init {
        createAlarmNotificationChannel()
    }

    fun createAlarmNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Failed Query Alarm"
            val descriptionText = "Alerts when a customer query requires human manual takeover"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(ALARM_CHANNEL_ID, name, importance).apply {
                description = descriptionText
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500)
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    suspend fun handleHitlEscalation(
        processed: ProcessedNotification,
        score: Float
    ) {
        val sourceClassStr = processed.sourceClass.name
        conversationMetadataDao.setNeedsHumanAttention(processed.contactName, sourceClassStr, true)
        conversationMetadataDao.setAiPaused(processed.contactName, sourceClassStr, true)
        triggerHitlAlarm(processed.contactName, processed.originalText, score, sourceClassStr)
    }

    fun triggerHitlAlarm(
        contactName: String,
        queryText: String,
        score: Float,
        sourceClass: String = "BUSINESS"
    ) {
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val vibrationPattern = longArrayOf(0, 500, 200, 500)

            val title = context.getString(R.string.hitl_alarm_title)
            val body = context.getString(R.string.hitl_alarm_body)

            val intent = Intent(context, com.example.persona.MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("open_chat_contact_name", contactName)
                putExtra("open_chat_source_class", sourceClass)
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                (contactName + sourceClass).hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
            )

            val notification = NotificationCompat.Builder(context, ALARM_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText("$contactName: $queryText")
                .setStyle(NotificationCompat.BigTextStyle().bigText("$body\n\nالعميل: $contactName\nالرسالة: $queryText"))
                .setVibrate(vibrationPattern)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()

            notificationManager.notify(1002, notification)
            Log.i(TAG, "HITL alarm notification posted for $contactName (score: $score)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to trigger HITL alarm notification", e)
        }
    }

    fun notifyMerchantHandover(
        contactName: String,
        sourceClass: String,
        requestCodeSuffix: String = "1003"
    ) {
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val vibrationPattern = longArrayOf(0, 500, 200, 500)
            val intent = Intent(context, com.example.persona.MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("open_chat_contact_name", contactName)
                putExtra("open_chat_source_class", sourceClass)
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                (contactName + sourceClass + requestCodeSuffix).hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
            )
            val notification = NotificationCompat.Builder(context, ALARM_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("طلب تحويل لبشري")
                .setContentText("العميل $contactName يطلب التحدث مع موظف بشري الآن!")
                .setStyle(NotificationCompat.BigTextStyle().bigText("العميل $contactName يطلب التحدث مع موظف بشري الآن!"))
                .setVibrate(vibrationPattern)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()
            notificationManager.notify(1003, notification)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to post hand-off notification to merchant", e)
        }
    }
}
