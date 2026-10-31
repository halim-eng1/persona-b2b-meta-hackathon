package com.example.persona.features.settings.presentation.components

import com.example.persona.R
import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.persona.core.data.repository.UserPreferencesRepository
import com.example.persona.features.settings.presentation.SettingsUiState

@Composable
fun AppRoutingSection(
    state: SettingsUiState,
    onListenMainAppChange: (Boolean) -> Unit,
    onListenBusinessAppChange: (Boolean) -> Unit,
    onListenCloneAppChange: (Boolean) -> Unit,
    onRequestStoragePermission: () -> Unit,
    onAutoPauseOnHitlChange: (Boolean) -> Unit,
    onHitlHoldingMessageChange: (String) -> Unit,
    onMediaFallbackMessageChange: (String) -> Unit,
    onBlankReplyFallbackChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // ── WhatsApp App Routing ──────────────────────────────────────────────
        PreferenceSection(
            title = stringResource(R.string.settings_routing_title),
            subtitle = stringResource(R.string.settings_routing_subtitle)
        ) {
            PreferenceSwitchRow(
                title = stringResource(R.string.settings_routing_main_app_title),
                subtitle = stringResource(R.string.settings_routing_main_app_desc),
                checked = state.listenMainApp,
                onCheckedChange = onListenMainAppChange
            )
            PreferenceSwitchRow(
                title = stringResource(R.string.settings_routing_w4b_title),
                subtitle = stringResource(R.string.settings_routing_w4b_desc),
                checked = state.listenBusinessApp,
                onCheckedChange = onListenBusinessAppChange
            )
            PreferenceSwitchRow(
                title = stringResource(R.string.settings_routing_cloned_title),
                subtitle = stringResource(R.string.settings_routing_cloned_desc),
                checked = state.listenCloneApp,
                onCheckedChange = onListenCloneAppChange
            )
        }

        // ── WhatsApp Media & Voice Notes Access ────────────────────────────────
        PreferenceSection(
            title = stringResource(R.string.settings_routing_storage_title),
            subtitle = "يتيح قراءة الصور والرسائل الصوتية الواردة ومعالجتها بنماذج الذكاء الاصطناعي محلياً."
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_routing_all_files_perm),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = if (state.isStorageAccessGranted)
                            stringResource(R.string.settings_routing_storage_active_desc)
                        else
                            stringResource(R.string.settings_routing_storage_inactive_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (state.isStorageAccessGranted)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.error
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = onRequestStoragePermission,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (state.isStorageAccessGranted)
                            MaterialTheme.colorScheme.secondaryContainer
                        else
                            MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(
                        text = if (state.isStorageAccessGranted) stringResource(R.string.settings_routing_perm_granted_btn) else stringResource(R.string.settings_routing_grant_perm_btn),
                        color = if (state.isStorageAccessGranted)
                            MaterialTheme.colorScheme.onSecondaryContainer
                        else
                            MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }

        // ── Human Takeover & Safety (HITL) ────────────────────────────────────
        PreferenceSection(
            title = stringResource(R.string.settings_routing_hitl_section_title),
            subtitle = stringResource(R.string.settings_routing_hitl_section_desc)
        ) {
            PreferenceSwitchRow(
                title = stringResource(R.string.settings_routing_pause_on_human_title),
                subtitle = stringResource(R.string.settings_routing_pause_on_human_desc),
                checked = state.isAutoPauseOnHitl,
                onCheckedChange = onAutoPauseOnHitlChange
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = state.hitlHoldingMessage,
                onValueChange = onHitlHoldingMessageChange,
                label = { Text(stringResource(R.string.settings_routing_hitl_message_label)) },
                placeholder = { Text(UserPreferencesRepository.DEFAULT_HITL_HOLDING_MESSAGE) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )
        }

        // ── System Fallback Messages ──────────────────────────────────────────
        PreferenceSection(
            title = stringResource(R.string.settings_routing_fallback_section_title),
            subtitle = stringResource(R.string.settings_routing_fallback_section_desc)
        ) {
            OutlinedTextField(
                value = state.mediaFallbackMessage,
                onValueChange = onMediaFallbackMessageChange,
                label = { Text(stringResource(R.string.settings_routing_media_fallback_label)) },
                placeholder = { Text(UserPreferencesRepository.DEFAULT_MEDIA_FALLBACK_MESSAGE) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = state.blankReplyFallbackTemplate,
                onValueChange = onBlankReplyFallbackChange,
                label = { Text(stringResource(R.string.settings_routing_blank_reply_label)) },
                placeholder = { Text(UserPreferencesRepository.DEFAULT_BLANK_REPLY_FALLBACK_TEMPLATE) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )
        }
    }
}
