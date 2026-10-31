package com.example.persona.features.settings.presentation.screens.health

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.persona.R
import com.example.persona.features.settings.presentation.HealthActions
import com.example.persona.features.settings.presentation.SettingsSpoke
import com.example.persona.features.settings.presentation.SettingsUiState
import com.example.persona.features.settings.presentation.components.HealthRing
import com.example.persona.features.settings.presentation.components.SettingsGroup
import com.example.persona.features.settings.presentation.components.SpokeScaffold
import com.example.persona.features.settings.presentation.components.settingFocus
import com.example.persona.features.settings.presentation.dialogs.ClearCacheDialog
import com.example.persona.features.settings.presentation.dialogs.ConfirmRestoreBackupDialog
import com.example.persona.features.settings.presentation.model.SettingKey
import com.example.persona.ui.theme.PersonaTheme

@Composable
fun HealthDataScreen(
    state: SettingsUiState,
    actions: HealthActions,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    focus: SettingKey? = null
) {
    var showRestoreBackupDialog by remember { mutableStateOf(false) }
    var showClearCacheDialog by remember { mutableStateOf(false) }

    val checks = remember(state) {
        listOf(
            state.isNotificationListenerGranted,
            state.isBatteryOptimizationIgnored,
            state.isAutoStartConfirmed,
            state.isStorageAccessGranted
        )
    }
    val passedChecks = checks.count { it }

    SpokeScaffold(
        spoke = SettingsSpoke.Health,
        title = R.string.spoke_health,
        onBack = onBack,
        focus = focus,
        modifier = modifier
    ) {
        // Health Progress Ring
        HealthRing(
            passedChecks = passedChecks,
            totalChecks = checks.size
        )

        // Permissions Health Check Group
        SettingsGroup(title = R.string.settings_perm_check_title) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .settingFocus(SettingKey.Permissions)
            ) {
                Text(
                    text = stringResource(R.string.settings_perm_check_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                HealthItemRow(
                    title = stringResource(R.string.settings_perm_notification_title),
                    isOk = state.isNotificationListenerGranted,
                    okText = stringResource(R.string.settings_perm_notification_active),
                    missingText = stringResource(R.string.settings_perm_notification_disabled)
                )

                HealthItemRow(
                    title = stringResource(R.string.settings_perm_battery_title),
                    isOk = state.isBatteryOptimizationIgnored,
                    okText = stringResource(R.string.settings_perm_battery_ok),
                    missingText = stringResource(R.string.settings_perm_battery_missing)
                )

                HealthItemRow(
                    title = stringResource(R.string.settings_perm_autostart_title),
                    isOk = state.isAutoStartConfirmed,
                    okText = stringResource(R.string.settings_perm_autostart_ok),
                    missingText = stringResource(R.string.settings_perm_notification_missing)
                )

                HealthItemRow(
                    title = stringResource(R.string.settings_perm_storage_title),
                    isOk = state.isStorageAccessGranted,
                    okText = stringResource(R.string.settings_perm_storage_active),
                    missingText = stringResource(R.string.settings_perm_storage_missing)
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedButton(
                    onClick = actions::refreshHealth,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.settings_perm_recheck_btn))
                }
            }
        }

        // Local Encrypted Backup & Restore Group
        SettingsGroup(title = R.string.settings_backup_title) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .settingFocus(SettingKey.Backup)
            ) {
                Text(
                    text = stringResource(R.string.settings_backup_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 14.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = actions::exportBackup,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.settings_backup_export_btn),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // FRANKENSTEIN 2: Explicit Confirmation Dialog before restoring
                    OutlinedButton(
                        onClick = { showRestoreBackupDialog = true },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Upload,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.settings_backup_restore_btn),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // Cache Cleaner Group
        SettingsGroup(title = R.string.settings_cache_clean_title) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .settingFocus(SettingKey.Cache)
            ) {
                Text(
                    text = stringResource(R.string.settings_cache_clean_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                OutlinedButton(
                    onClick = { showClearCacheDialog = true },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.CleaningServices,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.settings_cache_clean_btn))
                }
            }
        }
    }

    if (showRestoreBackupDialog) {
        ConfirmRestoreBackupDialog(
            onConfirm = { actions.restoreBackup() },
            onDismiss = { showRestoreBackupDialog = false }
        )
    }

    if (showClearCacheDialog) {
        ClearCacheDialog(
            onConfirm = { actions.clearCache() },
            onDismiss = { showClearCacheDialog = false }
        )
    }
}

@Composable
private fun HealthItemRow(
    title: String,
    isOk: Boolean,
    okText: String,
    missingText: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isOk)
            PersonaTheme.statusColors.successContainer.copy(alpha = 0.35f)
        else
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f),
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (isOk) okText else missingText,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isOk) PersonaTheme.statusColors.success else MaterialTheme.colorScheme.error
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = if (isOk) Icons.Default.CheckCircle else Icons.Default.Error,
                contentDescription = null,
                tint = if (isOk) PersonaTheme.statusColors.success else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
