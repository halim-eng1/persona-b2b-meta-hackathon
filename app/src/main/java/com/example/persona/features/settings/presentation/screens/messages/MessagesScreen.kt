package com.example.persona.features.settings.presentation.screens.messages

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.example.persona.R
import com.example.persona.features.settings.presentation.MessageActions
import com.example.persona.features.settings.presentation.SettingsSpoke
import com.example.persona.features.settings.presentation.SettingsUiState
import com.example.persona.features.settings.presentation.components.SettingsGroup
import com.example.persona.features.settings.presentation.components.SpokeScaffold
import com.example.persona.features.settings.presentation.components.rows.NavRow
import com.example.persona.features.settings.presentation.components.settingFocus
import com.example.persona.features.settings.presentation.model.LocalMessageEditor
import com.example.persona.features.settings.presentation.model.MessageSlot
import com.example.persona.features.settings.presentation.model.SettingKey

@Composable
fun MessagesScreen(
    state: SettingsUiState,
    actions: MessageActions,
    focus: SettingKey?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val editor = LocalMessageEditor.current

    SpokeScaffold(
        spoke = SettingsSpoke.Messages,
        title = R.string.spoke_messages,
        onBack = onBack,
        focus = focus,
        modifier = modifier
    ) {
        // Conversation Start & End
        SettingsGroup(title = R.string.settings_templates_title) {
            NavRow(
                title = stringResource(R.string.settings_greeting_template_label),
                summary = state.greetingTemplate.lineSequence().firstOrNull().takeUnless { it.isNullOrBlank() }
                    ?: stringResource(R.string.msg_when_greeting),
                onClick = { editor.open(MessageSlot.Greeting) },
                modifier = Modifier.settingFocus(SettingKey.Greeting)
            )
            NavRow(
                title = stringResource(R.string.settings_farewell_template_label),
                summary = state.farewellTemplate.lineSequence().firstOrNull().takeUnless { it.isNullOrBlank() }
                    ?: stringResource(R.string.msg_when_farewell),
                onClick = { editor.open(MessageSlot.Farewell) },
                modifier = Modifier.settingFocus(SettingKey.Farewell)
            )
        }

        // Availability Group
        SettingsGroup(title = R.string.settings_hours_title) {
            NavRow(
                title = stringResource(R.string.settings_hours_off_message_label),
                summary = state.businessHoursSchedule.outOfHoursMessage.lineSequence().firstOrNull().takeUnless { it.isNullOrBlank() }
                    ?: stringResource(R.string.msg_when_closed),
                onClick = { editor.open(MessageSlot.OutOfHours) },
                modifier = Modifier.settingFocus(SettingKey.OutOfHours)
            )
        }

        // Handoff & Safety Group
        SettingsGroup(title = R.string.settings_routing_hitl_section_title) {
            NavRow(
                title = stringResource(R.string.settings_routing_hitl_message_label),
                summary = state.hitlHoldingMessage.lineSequence().firstOrNull().takeUnless { it.isNullOrBlank() }
                    ?: stringResource(R.string.msg_when_human),
                onClick = { editor.open(MessageSlot.HumanHolding) },
                modifier = Modifier.settingFocus(SettingKey.HumanHolding)
            )
        }

        // Fallbacks Group
        SettingsGroup(title = R.string.settings_routing_fallback_section_title) {
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
