package com.example.persona.features.auto_reply.data.receiver

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import com.example.persona.core.data.repository.UserPreferencesRepository
import com.example.persona.features.auto_reply.data.service.WhatsAppNotificationListener
import com.example.persona.features.onboarding.data.PermissionChecker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var prefsRepository: UserPreferencesRepository
    @Inject lateinit var permissionChecker: PermissionChecker

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON" ||
            intent.action == "com.htc.intent.action.QUICKBOOT_POWERON"
        ) {
            val pendingResult = goAsync()
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            scope.launch {
                try {
                    // Check if auto-reply was enabled before reboot
                    val autoReplyEnabled = prefsRepository.isAutoReplyEnabledSync()

                    // Safety check: check if the user has revoked Notification Access
                    val hasNotificationAccess = permissionChecker.isNotificationListenerEnabled()

                    if (autoReplyEnabled && hasNotificationAccess) {
                        val component = ComponentName(context, WhatsAppNotificationListener::class.java)
                        context.packageManager.setComponentEnabledSetting(
                            component,
                            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                            PackageManager.DONT_KILL_APP
                        )
                        Log.i("BootReceiver", "WhatsAppNotificationListener restored on boot")
                    } else {
                        Log.i("BootReceiver", "Skipping component restore. autoReply=$autoReplyEnabled, access=$hasNotificationAccess")
                    }
                } catch (e: Exception) {
                    Log.e("BootReceiver", "Failed to restore service component on boot", e)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
