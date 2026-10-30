package com.example.persona.features.auto_reply.domain.orchestrator

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.persona.MainActivity
import com.example.persona.R
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles audible and vibration-enabled high-priority Android notifications
 * when API keys are evicted or LLM providers trip circuit breakers.
 */
@Singleton
class SystemAlertNotificationHandler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "SystemAlertNotificationHandler"
        const val ALERT_CHANNEL_ID = "persona_system_provider_alerts_channel"
        private val notificationIdGenerator = AtomicInteger(2000)
    }

    init {
        createAlertNotificationChannel()
    }

    fun createAlertNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "تنبيهات النظام ومفاتيح التشغيل (System & Key Alerts)"
            val descriptionText = "تنبيهات فورية عند نفاد رصيد المفاتيح أو عزل مزودي الذكاء الاصطناعي"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(ALERT_CHANNEL_ID, name, importance).apply {
                description = descriptionText
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 250, 500)
                val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                setSound(soundUri, null)
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun notifyKeyEvicted(provider: String, evictedKeyMasked: String, reason: String) {
        try {
            val title = "⚠️ استبعاد مفتاح $provider"
            val body = "تم استبعاد المفتاح $evictedKeyMasked بسبب: $reason، والتحويل للمفتاح التالي في الحوض."
            dispatchNotification(title, body)
            Log.w(TAG, "Key evicted alert dispatched: provider=$provider, key=$evictedKeyMasked, reason=$reason")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to dispatch key evicted alert", e)
        }
    }

    fun notifyProviderTripped(provider: String, cooldownMinutes: Int = 5, reason: String) {
        try {
            val title = "🚨 عزل مزود الذكاء الاصطناعي: $provider"
            val body = "تم عزل مزود $provider لمدة $cooldownMinutes دقائق بسبب: $reason. تم التحويل الفوري لمزود بديل في 0ms."
            dispatchNotification(title, body)
            Log.e(TAG, "Provider tripped alert dispatched: provider=$provider, cooldown=${cooldownMinutes}m, reason=$reason")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to dispatch provider tripped alert", e)
        }
    }

    private fun dispatchNotification(title: String, body: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationIdGenerator.incrementAndGet(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val vibrationPattern = longArrayOf(0, 500, 250, 500)

        val notification = NotificationCompat.Builder(context, ALERT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setSound(soundUri)
            .setVibrate(vibrationPattern)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(notificationIdGenerator.get(), notification)
    }
}
