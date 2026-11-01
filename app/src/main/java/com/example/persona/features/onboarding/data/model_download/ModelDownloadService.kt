package com.example.persona.features.onboarding.data.model_download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.persona.MainActivity
import com.example.persona.R
import com.example.persona.features.onboarding.domain.model_download.ModelDownloadState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ModelDownloadService : Service() {

    @Inject
    lateinit var modelDownloadManager: ModelDownloadManager

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private lateinit var notificationManager: NotificationManager

    companion object {
        private const val TAG = "ModelDownloadService"
        private const val NOTIFICATION_ID = 1004
        private const val CHANNEL_ID = "model_download_channel"

        fun start(context: Context) {
            val intent = Intent(context, ModelDownloadService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, ModelDownloadService::class.java)
            context.stopService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()

        val initialNotification = buildNotification("يتم الآن تجهيز المساعد الذكي لمتاجرك...", 0, 0, 0)
        
        try {
            val foregroundServiceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            }
            ServiceCompat.startForeground(this, NOTIFICATION_ID, initialNotification, foregroundServiceType)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground service: ${e.message}", e)
        }

        observeDownloadProgress()
    }

    private fun observeDownloadProgress() {
        serviceScope.launch {
            modelDownloadManager.state.collectLatest { state ->
                when (state) {
                    is ModelDownloadState.Downloading -> {
                        val percent = (state.progress.progressFraction * 100).toInt()
                        val downloadedMb = state.progress.downloadedBytes / (1024 * 1024)
                        val totalMb = state.progress.totalBytes / (1024 * 1024)
                        val content = "تم تحميل $downloadedMb من $totalMb ميجابايت ($percent%)"
                        notificationManager.notify(NOTIFICATION_ID, buildNotification(content, percent, downloadedMb, totalMb))
                    }
                    is ModelDownloadState.Verifying -> {
                        notificationManager.notify(
                            NOTIFICATION_ID,
                            buildNotification("جاري التأكد من سلامة ملفات المساعد...", 100, 127, 127, isIndeterminate = true)
                        )
                    }
                    is ModelDownloadState.Success,
                    is ModelDownloadState.Failed -> {
                        stopSelf()
                    }
                    else -> Unit
                }
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "تجهيز محرك الذكاء الاصطناعي",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "إشعارات تجهيز نماذج الذكاء الاصطناعي للعمل بدون إنترنت"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(
        contentText: String,
        progressPercent: Int,
        downloadedMb: Long = 0,
        totalMb: Long = 0,
        isIndeterminate: Boolean = false
    ): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val subText = if (totalMb > 0) "$downloadedMb / $totalMb MB" else null

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("تجهيز المساعد الذكي لمتاجرك")
            .setContentText(contentText)
            .apply {
                if (subText != null) {
                    setSubText(subText)
                }
            }
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, progressPercent, isIndeterminate)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope.cancel()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
}

