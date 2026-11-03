package com.example.persona.features.settings.presentation.screens.voice

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.persona.R
import com.example.persona.features.settings.domain.model.AccountMode
import com.example.persona.features.settings.presentation.SettingsSpoke
import com.example.persona.features.settings.presentation.SettingsUiState
import com.example.persona.features.settings.presentation.ThresholdLevel
import com.example.persona.features.settings.presentation.VoiceActions
import com.example.persona.core.data.repository.UserPreferencesRepository
import com.example.persona.features.settings.presentation.components.DraftSaveTextField
import com.example.persona.features.settings.presentation.components.SettingsGroup
import com.example.persona.features.settings.presentation.components.SpokeScaffold
import com.example.persona.features.settings.presentation.components.rows.NavRow
import com.example.persona.features.settings.presentation.components.rows.SegmentedRow
import com.example.persona.features.settings.presentation.components.settingFocus
import com.example.persona.features.settings.presentation.model.SettingKey
import com.example.persona.features.settings.presentation.sheets.TonePickerSheet

@Composable
fun VoiceBehaviorScreen(
    state: SettingsUiState,
    actions: VoiceActions,
    focus: SettingKey?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showTonePicker by remember { mutableStateOf(false) }

    SpokeScaffold(
        spoke = SettingsSpoke.Voice,
        title = R.string.spoke_voice,
        onBack = onBack,
        focus = focus,
        modifier = modifier
    ) {
        // ── 1. Usage Mode & Identity ───────────────────────────────────────────
        val isPersonalMode = state.accountMode == AccountMode.PERSONAL

        SettingsGroup(
            title = if (isPersonalMode) R.string.settings_personal_identity_title else R.string.settings_store_identity_title
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // Usage Mode Selector
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .settingFocus(SettingKey.AccountMode),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = stringResource(R.string.settings_account_mode_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    SegmentedRow(
                        title = stringResource(R.string.settings_mode_business),
                        isSelected = state.accountMode == AccountMode.BUSINESS,
                        onSelect = { actions.setAccountMode(AccountMode.BUSINESS) }
                    )
                    SegmentedRow(
                        title = stringResource(R.string.settings_mode_personal),
                        isSelected = state.accountMode == AccountMode.PERSONAL,
                        onSelect = { actions.setAccountMode(AccountMode.PERSONAL) }
                    )
                }

                // Bot / Assistant Name Field
                DraftSaveTextField(
                    value = if (isPersonalMode && state.botName == UserPreferencesRepository.DEFAULT_BOT_NAME) {
                        UserPreferencesRepository.DEFAULT_PERSONAL_ASSISTANT_NAME
                    } else {
                        state.botName
                    },
                    onSave = actions::setBotName,
                    label = stringResource(
                        if (isPersonalMode) R.string.settings_assistant_name_label else R.string.settings_bot_name_label
                    ),
                    placeholder = stringResource(
                        if (isPersonalMode) R.string.settings_assistant_name_placeholder else R.string.settings_bot_name_placeholder
                    ),
                    fallbackDefault = if (isPersonalMode) UserPreferencesRepository.DEFAULT_PERSONAL_ASSISTANT_NAME else UserPreferencesRepository.DEFAULT_BOT_NAME,
                    modifier = Modifier.settingFocus(SettingKey.BotName)
                )

                // Store / Owner Name Field
                DraftSaveTextField(
                    value = if (isPersonalMode && state.storeName == UserPreferencesRepository.DEFAULT_STORE_NAME) {
                        UserPreferencesRepository.DEFAULT_PERSONAL_OWNER_NAME
                    } else {
                        state.storeName
                    },
                    onSave = actions::setStoreName,
                    label = stringResource(
                        if (isPersonalMode) R.string.settings_owner_name_label else R.string.settings_store_name_label
                    ),
                    placeholder = stringResource(
                        if (isPersonalMode) R.string.settings_owner_name_placeholder else R.string.settings_store_name_placeholder
                    ),
                    fallbackDefault = if (isPersonalMode) UserPreferencesRepository.DEFAULT_PERSONAL_OWNER_NAME else UserPreferencesRepository.DEFAULT_STORE_NAME,
                    modifier = Modifier.settingFocus(SettingKey.StoreName)
                )
            }
        }

        // ── 2. Persona Tone ───────────────────────────────────────────────────
        SettingsGroup(title = R.string.settings_persona_tone_title) {
            val isArabic = state.appLanguage.equals("ar", ignoreCase = true)
            NavRow(
                title = if (isArabic) state.personaTone.titleArabic else state.personaTone.subtitle,
                summary = if (isArabic) state.personaTone.subtitle else state.personaTone.titleArabic,
                icon = Icons.Default.RecordVoiceOver,
                onClick = { showTonePicker = true },
                modifier = Modifier.settingFocus(SettingKey.Tone)
            )
        }

        // ── 3. Response Timing & Pacing (Frankenstein Stepper + Presets) ────────
        SettingsGroup(title = R.string.settings_timing_title) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Stepper + Input Field
                Column(modifier = Modifier.settingFocus(SettingKey.ReplyDelay)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilledIconButton(
                            onClick = {
                                val current = state.replyDelayInput.toIntOrNull() ?: state.replyDelaySeconds
                                val next = (current - 1).coerceAtLeast(1)
                                actions.setReplyDelayInput(next.toString())
                            },
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Remove,
                                contentDescription = stringResource(R.string.settings_reply_delay_stepper_decrease)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        OutlinedTextField(
                            value = state.replyDelayInput,
                            onValueChange = actions::setReplyDelayInput,
                            label = { Text(stringResource(R.string.settings_reply_delay_label)) },
                            singleLine = true,
                            isError = state.replyDelayError != null,
                            supportingText = {
                                if (state.replyDelayError != null) {
                                    Text(
                                        text = state.replyDelayError,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                } else {
                                    Text(stringResource(R.string.settings_reply_delay_hint))
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        FilledIconButton(
                            onClick = {
                                val current = state.replyDelayInput.toIntOrNull() ?: state.replyDelaySeconds
                                val next = current + 1
                                actions.setReplyDelayInput(next.toString())
                            },
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = stringResource(R.string.settings_reply_delay_stepper_increase)
                            )
                        }
                    }

                    // Preset chips: [ 1s ], [ 2s ], [ 5s ], [ 10s ]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(1, 2, 5, 10).forEach { sec ->
                            AssistChip(
                                onClick = { actions.setReplyDelayInput(sec.toString()) },
                                label = { Text("${sec}s") },
                                shape = RoundedCornerShape(10.dp)
                            )
                        }
                    }
                }
            }
        }

        // ── 4. Match Sensitivity ──────────────────────────────────────────────
        SettingsGroup(title = R.string.settings_sensitivity_title) {
            ThresholdLevel.entries.forEach { level ->
                SegmentedRow(
                    title = stringResource(level.labelResId),
                    summary = stringResource(level.descResId),
                    isSelected = state.thresholdLevel == level,
                    onSelect = { actions.setThresholdLevel(level) },
                    modifier = Modifier.settingFocus(SettingKey.Sensitivity)
                )
            }
        }

        // ── 5. Cooldown Duration ──────────────────────────────────────────────
        SettingsGroup(title = R.string.settings_cooldown_title) {
            listOf(0, 1, 5, 10, 30).forEach { mins ->
                val label = when (mins) {
                    0 -> stringResource(R.string.settings_cooldown_none)
                    1 -> stringResource(R.string.settings_cooldown_one_min)
                    else -> stringResource(R.string.settings_cooldown_mins, mins)
                }
                SegmentedRow(
                    title = label,
                    isSelected = state.cooldownMins == mins,
                    onSelect = { actions.setCooldownMins(mins) },
                    modifier = Modifier.settingFocus(SettingKey.Cooldown)
                )
            }
        }
    }

    if (showTonePicker) {
        TonePickerSheet(
            selectedTone = state.personaTone,
            onSelectTone = actions::setPersonaTone,
            onDismiss = { showTonePicker = false },
            appLanguage = state.appLanguage
        )
    }
}
