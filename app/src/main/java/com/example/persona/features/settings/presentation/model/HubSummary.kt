package com.example.persona.features.settings.presentation.model

import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.persona.R
import com.example.persona.features.settings.presentation.SettingsSpoke
import com.example.persona.features.settings.presentation.SettingsUiState

enum class Attention { None, Warning, Critical }

@Immutable
data class HubCard(
    val spoke: SettingsSpoke,
    @StringRes val title: Int,
    val icon: ImageVector,
    val summary: String,
    val attention: Attention = Attention.None
)

fun buildHubCards(s: SettingsUiState, r: Resources): List<HubCard> {
    val keyCount = s.providerConfigs.values.sumOf { it.keys.size }
    val checks = listOf(
        s.isNotificationListenerGranted,
        s.isBatteryOptimizationIgnored,
        s.isAutoStartConfirmed,
        s.isStorageAccessGranted,
        s.isContactsPermissionGranted
    )
    val listening = listOf(s.listenMainApp, s.listenBusinessApp, s.listenCloneApp).count { it }

    return listOf(
        HubCard(
            spoke = SettingsSpoke.Voice,
            title = R.string.spoke_voice,
            icon = Icons.Default.RecordVoiceOver,
            summary = r.getString(R.string.hub_voice_summary, s.personaTone.titleArabic, s.replyDelaySeconds)
        ),
        HubCard(
            spoke = SettingsSpoke.Messages,
            title = R.string.spoke_messages,
            icon = Icons.Default.Forum,
            summary = r.getString(R.string.hub_messages_summary, MessageSlot.entries.size)
        ),
        HubCard(
            spoke = SettingsSpoke.Hours,
            title = R.string.spoke_hours,
            icon = Icons.Default.Schedule,
            summary = if (s.businessHoursSchedule.isEnabled) {
                r.getString(R.string.hub_hours_on, s.businessHoursSchedule.workingDays.size)
            } else {
                r.getString(R.string.hub_hours_off)
            }
        ),
        HubCard(
            spoke = SettingsSpoke.WhatsApp,
            title = R.string.spoke_whatsapp,
            icon = Icons.AutoMirrored.Filled.Chat,
            summary = r.getString(R.string.hub_whatsapp_summary, listening),
            attention = if (listening == 0) Attention.Warning else Attention.None
        ),
        HubCard(
            spoke = SettingsSpoke.AiEngine,
            title = R.string.spoke_ai,
            icon = Icons.Default.Psychology,
            summary = r.getString(R.string.hub_ai_summary, s.providerOrder.firstOrNull().orEmpty().uppercase(), keyCount),
            attention = if (keyCount == 0) Attention.Warning else Attention.None
        ),
        HubCard(
            spoke = SettingsSpoke.Health,
            title = R.string.spoke_health,
            icon = Icons.Default.Build,
            summary = r.getString(R.string.hub_health_summary, checks.count { it }, checks.size),
            attention = when {
                !s.isNotificationListenerGranted -> Attention.Critical
                checks.any { !it } -> Attention.Warning
                else -> Attention.None
            }
        )
    )
}
