package com.example.persona.core.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.os.bundleOf

/**
 * Helper to dispatch Android settings intents with target component highlighting.
 *
 * Directs merchants straight to the system's Notification Listener access page
 * with the Persona [WhatsAppNotificationListener] entry highlighted/pulsing,
 * minimizing friction during first-time onboarding and permission recovery.
 */
object PermissionHighlightHelper {

    const val NOTIFICATION_LISTENER_CLASS: String =
        "com.example.persona.features.auto_reply.data.service.WhatsAppNotificationListener"

    /**
     * Opens system Notification Listener settings with fragment argument flags
     * that instruct the Settings app on Android 9+ (API 28+) to highlight the Persona row.
     */
    fun openNotificationListenerSettingsWithHighlight(context: Context) {
        try {
            val componentName = ComponentName(context, NOTIFICATION_LISTENER_CLASS).flattenToString()
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                putExtra(":settings:fragment_args_key", componentName)
                putExtra(":settings:show_fragment_args", bundleOf(":settings:fragment_args_key" to componentName))
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            try {
                val fallbackIntent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(fallbackIntent)
            } catch (_: Exception) {
                context.startActivity(Intent(Settings.ACTION_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                })
            }
        }
    }

    /**
     * Requests battery optimization exemption for uninterrupted background WhatsApp message processing.
     */
    fun openBatteryOptimizationSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            try {
                val fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(fallback)
            } catch (_: Exception) {
                context.startActivity(Intent(Settings.ACTION_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                })
            }
        }
    }
}

/**
 * Top-level convenience alias for [PermissionHighlightHelper.openNotificationListenerSettingsWithHighlight].
 */
fun openNotificationListenerSettingsWithHighlight(context: Context) {
    PermissionHighlightHelper.openNotificationListenerSettingsWithHighlight(context)
}
