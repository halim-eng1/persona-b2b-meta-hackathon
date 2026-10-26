package com.example.persona.features.auto_reply.data.service

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Controls enabling and disabling of the NotificationListenerService component.
 * Enabling the component tells the OS to bind the listener, which triggers
 * onListenerConnected.
 */
@Singleton
class ServiceController @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun enableAutoReply() {
        val component = ComponentName(context, WhatsAppNotificationListener::class.java)
        context.packageManager.setComponentEnabledSetting(
            component,
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP
        )
    }

    fun disableAutoReply() {
        val component = ComponentName(context, WhatsAppNotificationListener::class.java)
        context.packageManager.setComponentEnabledSetting(
            component,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        )
    }

    fun isAutoReplyEnabled(): Boolean {
        val component = ComponentName(context, WhatsAppNotificationListener::class.java)
        val state = context.packageManager.getComponentEnabledSetting(component)
        return state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED ||
                state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
    }
}
