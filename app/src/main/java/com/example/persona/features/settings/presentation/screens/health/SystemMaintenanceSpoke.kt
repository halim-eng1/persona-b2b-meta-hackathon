package com.example.persona.features.settings.presentation.screens.health

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.persona.features.settings.presentation.HealthActions
import com.example.persona.features.settings.presentation.SettingsUiState
import com.example.persona.features.settings.presentation.model.SettingKey

/**
 * System Maintenance & Health Recovery Spoke.
 *
 * Provides actionable, self-healing system recovery tools, including
 * interactive battery optimization exemption, OEM auto-start routing
 * (specialized for Transsion / Infinix XOS), and lock-in-recents guidance.
 */
@Composable
fun SystemMaintenanceSpoke(
    state: SettingsUiState,
    actions: HealthActions,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    focus: SettingKey? = null
) {
    HealthDataScreen(
        state = state,
        actions = actions,
        onBack = onBack,
        modifier = modifier,
        focus = focus
    )
}

/**
 * Requests Battery Optimization Exemption via native Android dialog.
 */
fun requestBatteryOptimizationExemption(context: Context) {
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    val packageName = context.packageName
    if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:$packageName")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            val fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(fallback)
            } catch (_: Exception) {
                context.startActivity(Intent(Settings.ACTION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            }
        }
    }
}

/**
 * OEM Auto-Start & Background Routing safe intent resolver.
 * Includes specialized Transsion / Infinix XOS Phone Manager paths and generic OEM fallbacks.
 */
fun openAutoStartSettings(context: Context) {
    val intents = listOf(
        // Transsion / Infinix / Tecno Phone Manager
        Intent().setComponent(ComponentName("com.transsion.phonemanager", "com.transsion.phonemanager.view.AutoStartListActivity")),
        Intent().setComponent(ComponentName("com.transsion.phonemanager", "com.transsion.phonemanager.view.PowerConsumptionActivity")),
        // Transsion Power / Optimizer fallback
        Intent().setComponent(ComponentName("com.transsion.powersaveschedule", "com.transsion.powersaveschedule.PowerSaveScheduleActivity")),
        // Xiaomi / MIUI
        Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")),
        // Huawei / Honor
        Intent().setComponent(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")),
        // Oppo / Realme
        Intent().setComponent(ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")),
        // Standard App Details Settings (Universal Fallback)
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
    )

    for (intent in intents) {
        try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            return
        } catch (_: Exception) {
            // Try next fallback
        }
    }
}

