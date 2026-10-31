package com.example.persona.features.settings.presentation.components

import com.example.persona.R
import androidx.compose.ui.res.stringResource

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.persona.features.settings.presentation.SettingsUiState
import com.example.persona.ui.theme.PersonaTheme

@Composable
fun SystemMaintenanceSection(
    state: SettingsUiState,
    onRefreshHealth: () -> Unit,
    onExportBackup: () -> Unit,
    onRestoreBackup: () -> Unit,
    onClearCache: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Feedback message banner
        AnimatedVisibility(visible = state.maintenanceMessage != null) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = state.maintenanceMessage.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // ── Permissions Health Check ──────────────────────────────────────────
        PreferenceSection(
            title = stringResource(R.string.settings_perm_check_title),
            subtitle = stringResource(R.string.settings_perm_check_desc)
        ) {
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

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = onRefreshHealth,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.settings_perm_recheck_btn))
            }
        }

        // ── Encrypted Local Backup & Restore ──────────────────────────────────
        PreferenceSection(
            title = stringResource(R.string.settings_backup_title),
            subtitle = stringResource(R.string.settings_backup_desc)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onExportBackup,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.settings_backup_export_btn), fontSize = 12.sp)
                }

                OutlinedButton(
                    onClick = onRestoreBackup,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.settings_backup_restore_btn), fontSize = 12.sp)
                }
            }
        }

        // ── Cache Cleaner ─────────────────────────────────────────────────────
        PreferenceSection(
            title = stringResource(R.string.settings_cache_clean_title),
            subtitle = stringResource(R.string.settings_cache_clean_desc)
        ) {
            OutlinedButton(
                onClick = onClearCache,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.CleaningServices, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.settings_cache_clean_btn))
            }
        }
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
        shape = RoundedCornerShape(10.dp),
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
