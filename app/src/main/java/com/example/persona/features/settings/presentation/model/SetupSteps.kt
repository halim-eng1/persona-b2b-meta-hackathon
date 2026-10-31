package com.example.persona.features.settings.presentation.model

import androidx.annotation.StringRes
import com.example.persona.R
import com.example.persona.features.settings.presentation.SettingsSpoke
import com.example.persona.features.settings.presentation.SettingsUiState

enum class SetupStep(
    val spoke: SettingsSpoke,
    val focus: SettingKey,
    @StringRes val title: Int
) {
    AddKey(SettingsSpoke.AiEngine, SettingKey.ApiKeys, R.string.setup_add_key),
    NotificationAccess(SettingsSpoke.Health, SettingKey.Permissions, R.string.setup_notification_access),
    StorageAccess(SettingsSpoke.WhatsApp, SettingKey.StorageAccess, R.string.setup_storage_access),
    NameStore(SettingsSpoke.Voice, SettingKey.StoreName, R.string.setup_name_store);
}

fun nextBestActions(s: SettingsUiState): List<SetupStep> = buildList {
    if (s.providerConfigs.values.none { it.keys.isNotEmpty() }) add(SetupStep.AddKey)
    if (!s.isNotificationListenerGranted) add(SetupStep.NotificationAccess)
    if (!s.isStorageAccessGranted) add(SetupStep.StorageAccess)
    if (s.storeName.isBlank() || s.storeName == "My Store") add(SetupStep.NameStore)
}
