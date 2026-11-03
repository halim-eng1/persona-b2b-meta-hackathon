package com.example.persona.features.settings.presentation.screens.health

import android.Manifest
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.persona.R
import com.example.persona.features.settings.presentation.HealthActions
import com.example.persona.features.settings.presentation.SettingsUiState
import com.example.persona.features.settings.presentation.components.SettingsGroup
import com.example.persona.features.settings.presentation.components.settingFocus
import com.example.persona.features.settings.presentation.model.SettingKey

/**
 * Interactive System Health & Permissions Section.
 * Replaces passive read-only cards with interactive self-healing action controls.
 */
@Composable
fun SystemHealthSection(
    state: SettingsUiState,
    actions: HealthActions,
    onOpenRecentsTutorial: () -> Unit,
    modifier: Modifier = Modifier,
    contactsLauncher: ManagedActivityResultLauncher<String, Boolean>? = null
) {
    val context = LocalContext.current
    val fallbackContactsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        actions.recheckPermissions()
    }
    val launcher = contactsLauncher ?: fallbackContactsLauncher

    SettingsGroup(
        title = R.string.settings_perm_check_title,
        modifier = modifier
    ) {
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

            // 1. WhatsApp Notification Access
            HealthItemRow(
                title = stringResource(R.string.settings_perm_notification_title),
                isOk = state.isNotificationListenerGranted,
                okText = stringResource(R.string.settings_perm_notification_active),
                missingText = stringResource(R.string.settings_perm_notification_disabled),
                onRequestPermission = {
                    actions.openNotificationListenerSettings(context)
                }
            )

            // 2. Battery Optimization Exemption
            HealthItemRow(
                title = stringResource(R.string.settings_perm_battery_title),
                isOk = state.isBatteryOptimizationIgnored,
                okText = stringResource(R.string.settings_perm_battery_ok),
                missingText = stringResource(R.string.settings_perm_battery_missing),
                onRequestPermission = {
                    actions.requestBatteryOptimizationExemption(context)
                }
            )

            // 3. Voice Notes & Storage Access
            HealthItemRow(
                title = stringResource(R.string.settings_perm_storage_title),
                isOk = state.isStorageAccessGranted,
                okText = stringResource(R.string.settings_perm_storage_active),
                missingText = stringResource(R.string.settings_perm_storage_missing),
                onRequestPermission = {
                    actions.requestStoragePermission(context)
                }
            )

            // 4. Contacts Access
            HealthItemRow(
                title = stringResource(R.string.settings_perm_contacts_title),
                isOk = state.isContactsPermissionGranted,
                okText = stringResource(R.string.settings_perm_contacts_active),
                missingText = stringResource(R.string.settings_perm_contacts_missing),
                onRequestPermission = {
                    launcher.launch(Manifest.permission.READ_CONTACTS)
                }
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Proactive Guidance Banner: Lock in Recents
            RecentsLockGuidanceBanner(
                onOpenTutorial = onOpenRecentsTutorial
            )

            Spacer(modifier = Modifier.height(14.dp))

            OutlinedButton(
                onClick = actions::recheckPermissions,
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
}

