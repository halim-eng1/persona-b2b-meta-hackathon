package com.example.persona.features.onboarding.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AutoStartHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun launchAutoStartSettings(): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase()

        val intentMap = mapOf(
            "xiaomi" to listOf(
                Intent().setComponent(ComponentName("com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity")),
                Intent("miui.intent.action.OP_AUTO_START").addCategory(Intent.CATEGORY_DEFAULT)
            ),
            "redmi" to listOf(
                Intent().setComponent(ComponentName("com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity"))
            ),
            "oppo" to listOf(
                Intent().setComponent(ComponentName("com.coloros.safecenter",
                    "com.coloros.safecenter.permission.startup.StartupAppListActivity")),
                Intent().setComponent(ComponentName("com.coloros.safecenter",
                    "com.coloros.safecenter.startupapp.StartupAppListActivity")),
                Intent().setComponent(ComponentName("com.oppo.safe",
                    "com.oppo.safe.permission.startup.StartupAppListActivity"))
            ),
            "realme" to listOf(
                Intent().setComponent(ComponentName("com.coloros.safecenter",
                    "com.coloros.safecenter.startupapp.StartupAppListActivity")),
                Intent().setComponent(ComponentName("com.coloros.safecenter",
                    "com.coloros.safecenter.permission.startup.StartupAppListActivity"))
            ),
            "vivo" to listOf(
                Intent().setComponent(ComponentName("com.iqoo.secure",
                    "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity")),
                Intent().setComponent(ComponentName("com.vivo.permissionmanager",
                    "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")),
                Intent().setComponent(ComponentName("com.iqoo.secure",
                    "com.iqoo.secure.MainActivity"))
            ),
            "transsion" to listOf(
                Intent().setComponent(ComponentName("com.transsion.phonemaster",
                    "com.transsion.phonemaster.autostart.AutoStartActivity")),
                Intent().setComponent(ComponentName("com.transsion.phonemaster",
                    "com.transsion.phonemaster.MainActivity")),
                Intent().setComponent(ComponentName("com.transsion.phonemanager",
                    "com.itel.autobootmanager.activity.AutoBootMgrActivity"))
            ),
            "infinix" to listOf(
                Intent().setComponent(ComponentName("com.transsion.phonemaster",
                    "com.transsion.phonemaster.autostart.AutoStartActivity")),
                Intent().setComponent(ComponentName("com.transsion.phonemaster",
                    "com.transsion.phonemaster.MainActivity")),
                Intent().setComponent(ComponentName("com.transsion.phonemanager",
                    "com.itel.autobootmanager.activity.AutoBootMgrActivity"))
            ),
            "tecno" to listOf(
                Intent().setComponent(ComponentName("com.transsion.phonemaster",
                    "com.transsion.phonemaster.autostart.AutoStartActivity")),
                Intent().setComponent(ComponentName("com.transsion.phonemaster",
                    "com.transsion.phonemaster.MainActivity")),
                Intent().setComponent(ComponentName("com.transsion.phonemanager",
                    "com.itel.autobootmanager.activity.AutoBootMgrActivity"))
            ),
            "itel" to listOf(
                Intent().setComponent(ComponentName("com.itel.autostart",
                    "com.itel.autostart.AutoStartActivity")),
                Intent().setComponent(ComponentName("com.transsion.phonemaster",
                    "com.transsion.phonemaster.autostart.AutoStartActivity")),
                Intent().setComponent(ComponentName("com.transsion.phonemanager",
                    "com.itel.autobootmanager.activity.AutoBootMgrActivity"))
            ),
            "samsung" to listOf(
                Intent().setComponent(ComponentName("com.samsung.android.lool",
                    "com.samsung.android.sm.battery.ui.BatteryActivity")),
                Intent().setComponent(ComponentName("com.samsung.android.lool",
                    "com.samsung.android.sm.ui.battery.BatteryActivity"))
            ),
            "huawei" to listOf(
                Intent().setComponent(ComponentName("com.huawei.systemmanager",
                    "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")),
                Intent().setComponent(ComponentName("com.huawei.systemmanager",
                    "com.huawei.systemmanager.optimize.process.ProtectActivity"))
            ),
            "honor" to listOf(
                Intent().setComponent(ComponentName("com.hihonor.systemmanager",
                    "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity")),
                Intent().setComponent(ComponentName("com.huawei.systemmanager",
                    "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"))
            ),
            "oneplus" to listOf(
                Intent().setComponent(ComponentName("com.oneplus.security",
                    "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"))
            )
        )

        // Prioritize matched manufacturer intents first
        val prioritizedIntents = mutableListOf<Intent>()
        intentMap.entries.firstOrNull { manufacturer.contains(it.key) }?.value?.let {
            prioritizedIntents.addAll(it)
        }

        // Add remaining manufacturer intents as fallbacks
        intentMap.values.flatten().forEach { intent ->
            if (!prioritizedIntents.contains(intent)) {
                prioritizedIntents.add(intent)
            }
        }

        for (intent in prioritizedIntents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (context.packageManager.resolveActivity(intent, 0) != null) {
                    context.startActivity(intent)
                    return true
                }
            } catch (_: Exception) {
                // ROM version mismatch, try next candidate
            }
        }

        // Ultimate fallback: open App Details screen so the user can configure battery/permissions
        return try {
            val fallbackIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(fallbackIntent)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun getManufacturerName(): String = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
}
