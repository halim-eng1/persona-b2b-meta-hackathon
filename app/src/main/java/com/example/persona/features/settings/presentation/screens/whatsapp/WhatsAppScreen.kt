package com.example.persona.features.settings.presentation.screens.whatsapp

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.persona.R
import com.example.persona.features.onboarding.data.AutoStartHelper
import com.example.persona.features.onboarding.presentation.PermissionIntentDispatcher
import com.example.persona.features.settings.presentation.SettingsSpoke
import com.example.persona.features.settings.presentation.SettingsUiState
import com.example.persona.features.settings.presentation.WhatsAppActions
import com.example.persona.features.settings.presentation.components.PermissionCard
import com.example.persona.features.settings.presentation.components.SettingsGroup
import com.example.persona.features.settings.presentation.components.SpokeScaffold
import com.example.persona.features.settings.presentation.components.rows.NavRow
import com.example.persona.features.settings.presentation.components.rows.SwitchRow
import com.example.persona.features.settings.presentation.components.settingFocus
import com.example.persona.features.settings.presentation.model.LocalMessageEditor
import com.example.persona.features.settings.presentation.model.MessageSlot
import com.example.persona.features.settings.presentation.model.SettingKey

@Composable
fun WhatsAppScreen(
    state: SettingsUiState,
    actions: WhatsAppActions,
    focus: SettingKey?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onRequestStoragePermission: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val editor = LocalMessageEditor.current
    val intentDispatcher = remember(context) {
        PermissionIntentDispatcher(AutoStartHelper(context.applicationContext))
    }
    val handleStoragePermission = onRequestStoragePermission ?: {
        intentDispatcher.openAllFilesAccessSettings(context)
    }

    SpokeScaffold(
        spoke = SettingsSpoke.WhatsApp,
        title = R.string.spoke_whatsapp,
        onBack = onBack,
        focus = focus,
        modifier = modifier
    ) {
        // WhatsApp Routing Group
        SettingsGroup(title = R.string.settings_routing_title) {
            SwitchRow(
                title = stringResource(R.string.settings_routing_main_app_title),
                summary = stringResource(R.string.settings_routing_main_app_desc),
                checked = state.listenMainApp,
                onCheckedChange = actions::setListenMain,
                modifier = Modifier.settingFocus(SettingKey.ListenMain)
            )
            SwitchRow(
                title = stringResource(R.string.settings_routing_w4b_title),
                summary = stringResource(R.string.settings_routing_w4b_desc),
                checked = state.listenBusinessApp,
                onCheckedChange = actions::setListenBusiness,
                modifier = Modifier.settingFocus(SettingKey.ListenBusiness)
            )
            SwitchRow(
                title = stringResource(R.string.settings_routing_cloned_title),
                summary = stringResource(R.string.settings_routing_cloned_desc),
                checked = state.listenCloneApp,
                onCheckedChange = actions::setListenClone,
                modifier = Modifier.settingFocus(SettingKey.ListenClone)
            )
        }

        // WhatsApp Media & Voice Notes Storage Access
        SettingsGroup(title = R.string.settings_routing_storage_title) {
            PermissionCard(
                title = stringResource(R.string.settings_routing_all_files_perm),
                isGranted = state.isStorageAccessGranted,
                grantedDescription = stringResource(R.string.settings_routing_storage_active_desc),
                missingDescription = stringResource(R.string.settings_routing_storage_inactive_desc),
                onRequestPermission = handleStoragePermission,
                modifier = Modifier.settingFocus(SettingKey.StorageAccess)
            )
        }

        // Human-in-the-Loop & Fallback Messages
        SettingsGroup(title = R.string.settings_routing_hitl_section_title) {
            SwitchRow(
                title = stringResource(R.string.settings_routing_pause_on_human_title),
                summary = stringResource(R.string.settings_routing_pause_on_human_desc),
                checked = state.isAutoPauseOnHitl,
                onCheckedChange = actions::setAutoPauseOnHitl,
                modifier = Modifier.settingFocus(SettingKey.HumanTakeover)
            )

            AnimatedVisibility(visible = state.isAutoPauseOnHitl) {
                NavRow(
                    title = stringResource(R.string.settings_routing_hitl_message_label),
                    summary = state.hitlHoldingMessage.lineSequence().firstOrNull().takeUnless { it.isNullOrBlank() }
                        ?: stringResource(R.string.msg_when_human),
                    onClick = { editor.open(MessageSlot.HumanHolding) },
                    modifier = Modifier.settingFocus(SettingKey.HumanHolding)
                )
            }

            NavRow(
                title = stringResource(R.string.settings_routing_media_fallback_label),
                summary = state.mediaFallbackMessage.lineSequence().firstOrNull().takeUnless { it.isNullOrBlank() }
                    ?: stringResource(R.string.msg_when_media),
                onClick = { editor.open(MessageSlot.MediaFallback) },
                modifier = Modifier.settingFocus(SettingKey.MediaFallback)
            )

            NavRow(
                title = stringResource(R.string.settings_routing_blank_reply_label),
                summary = state.blankReplyFallbackTemplate.lineSequence().firstOrNull().takeUnless { it.isNullOrBlank() }
                    ?: stringResource(R.string.msg_when_blank),
                onClick = { editor.open(MessageSlot.BlankReply) },
                modifier = Modifier.settingFocus(SettingKey.BlankReply)
            )
        }
    }
}
