package com.example.persona.features.onboarding.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AutoStartHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun launchAutoStartSettings(): Boolean {
        val intents = listOf(
            // Xiaomi MIUI
            Intent().setComponent(ComponentName("com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity")),
            // Oppo ColorOS
            Intent().setComponent(ComponentName("com.coloros.safecenter",
                "com.coloros.safecenter.permission.startup.StartupAppListActivity")),
            // Vivo FuntouchOS
            Intent().setComponent(ComponentName("com.vivo.permissionmanager",
                "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")),
            // Huawei EMUI
            Intent().setComponent(ComponentName("com.huawei.systemmanager",
                "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")),
            // OnePlus OxygenOS
            Intent().setComponent(ComponentName("com.oneplus.security",
                "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity")),
            // Samsung Device Care
            Intent().setComponent(ComponentName("com.samsung.android.lool",
                "com.samsung.android.sm.battery.ui.BatteryActivity")),
            // Realme (variant of ColorOS)
            Intent().setComponent(ComponentName("com.coloros.safecenter",
                "com.coloros.safecenter.startupapp.StartupAppListActivity")),
            // Infinix / Tecno (HiOS / XOS)
            Intent().setComponent(ComponentName("com.transsion.phonemanager",
                "com.itel.autobootmanager.activity.AutoBootMgrActivity")),
            // Honor (post-Huawei split)
            Intent().setComponent(ComponentName("com.hihonor.systemmanager",
                "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity"))
        )

        for (intent in intents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (context.packageManager.resolveActivity(intent, 0) != null) {
                    context.startActivity(intent)
                    return true
                }
            } catch (_: Exception) { /* Intent not available on this specific ROM version */ }
        }
        return false
    }

    fun getManufacturerName(): String = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
}
