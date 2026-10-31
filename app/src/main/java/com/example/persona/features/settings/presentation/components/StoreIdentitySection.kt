package com.example.persona.features.settings.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.persona.R
import com.example.persona.features.auto_reply.domain.model.PersonaTone
import com.example.persona.features.settings.presentation.SettingsUiState
import com.example.persona.features.settings.presentation.ThresholdLevel

@Composable
fun StoreIdentitySection(
    state: SettingsUiState,
    onBotNameChange: (String) -> Unit,
    onStoreNameChange: (String) -> Unit,
    onPersonaToneChange: (PersonaTone) -> Unit,
    onThresholdChange: (ThresholdLevel) -> Unit,
    onCooldownChange: (Int) -> Unit,
    onReplyDelayChange: (String) -> Unit,
    onGreetingTemplateChange: (String) -> Unit,
    onFarewellTemplateChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    onLanguageChange: (String) -> Unit = {},
    onThemeModeChange: (String) -> Unit = {}
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // ── App Language Preference ───────────────────────────────────────────
        PreferenceSection(
            title = stringResource(R.string.settings_language_title),
            subtitle = stringResource(R.string.settings_language_subtitle)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PersonaSelectionCard(
                    title = stringResource(R.string.language_arabic),
                    subtitle = stringResource(R.string.language_arabic_sub),
                    isSelected = state.appLanguage == "ar",
                    onClick = { onLanguageChange("ar") },
                    modifier = Modifier.weight(1f)
                )
                PersonaSelectionCard(
                    title = stringResource(R.string.language_english),
                    subtitle = stringResource(R.string.language_english_sub),
                    isSelected = state.appLanguage == "en",
                    onClick = { onLanguageChange("en") },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // ── App Appearance / Theme Mode (Adaptive & Accessible) ───────────────
        PreferenceSection(
            title = stringResource(R.string.settings_theme_title),
            subtitle = stringResource(R.string.settings_theme_subtitle)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PersonaSelectionCard(
                    title = stringResource(R.string.settings_theme_dark),
                    subtitle = stringResource(R.string.settings_theme_dark_sub),
                    isSelected = state.themeMode == "dark",
                    onClick = { onThemeModeChange("dark") },
                    modifier = Modifier.weight(1f)
                )
                PersonaSelectionCard(
                    title = stringResource(R.string.settings_theme_light),
                    subtitle = stringResource(R.string.settings_theme_light_sub),
                    isSelected = state.themeMode == "light",
                    onClick = { onThemeModeChange("light") },
                    modifier = Modifier.weight(1f)
                )
                PersonaSelectionCard(
                    title = stringResource(R.string.settings_theme_system),
                    subtitle = stringResource(R.string.settings_theme_system_sub),
                    isSelected = state.themeMode == "system",
                    onClick = { onThemeModeChange("system") },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // ── Bot & Store Brand Identity ────────────────────────────────────────
        PreferenceSection(
            title = stringResource(R.string.settings_store_identity_title),
            subtitle = stringResource(R.string.settings_store_identity_subtitle)
        ) {
            OutlinedTextField(
                value = state.botName,
                onValueChange = onBotNameChange,
                label = { Text(stringResource(R.string.settings_bot_name_label)) },
                placeholder = { Text(stringResource(R.string.settings_bot_name_placeholder)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = state.storeName,
                onValueChange = onStoreNameChange,
                label = { Text(stringResource(R.string.settings_store_name_label)) },
                placeholder = { Text(stringResource(R.string.settings_store_name_placeholder)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // ── AI Persona Tone ───────────────────────────────────────────────────
        PreferenceSection(
            title = stringResource(R.string.settings_persona_tone_title),
            subtitle = stringResource(R.string.settings_persona_tone_subtitle)
        ) {
            PersonaTone.entries.forEach { tone ->
                PersonaToneCard(
                    tone = tone,
                    isSelected = state.personaTone == tone,
                    onSelect = { onPersonaToneChange(tone) }
                )
            }
        }

        // ── Response Templates ────────────────────────────────────────────────
        PreferenceSection(
            title = stringResource(R.string.settings_templates_title),
            subtitle = stringResource(R.string.settings_templates_subtitle)
        ) {
            OutlinedTextField(
                value = state.greetingTemplate,
                onValueChange = onGreetingTemplateChange,
                label = { Text(stringResource(R.string.settings_greeting_template_label)) },
                placeholder = { Text(stringResource(R.string.greeting_template_default)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )
            OutlinedTextField(
                value = state.farewellTemplate,
                onValueChange = onFarewellTemplateChange,
                label = { Text(stringResource(R.string.settings_farewell_template_label)) },
                placeholder = { Text(stringResource(R.string.farewell_template_default)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )
        }

        // ── Reply Delay & Cooldown ────────────────────────────────────────────
        PreferenceSection(
            title = stringResource(R.string.settings_timing_title),
            subtitle = stringResource(R.string.settings_timing_subtitle)
        ) {
            OutlinedTextField(
                value = state.replyDelayInput,
                onValueChange = onReplyDelayChange,
                label = { Text(stringResource(R.string.settings_reply_delay_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
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
                }
            )
        }

        // ── Match Sensitivity ─────────────────────────────────────────────────
        PreferenceSection(
            title = stringResource(R.string.settings_sensitivity_title),
            subtitle = stringResource(R.string.settings_sensitivity_subtitle)
        ) {
            ThresholdLevel.entries.forEach { level ->
                ThresholdOption(
                    level = level,
                    isSelected = state.thresholdLevel == level,
                    onSelect = { onThresholdChange(level) }
                )
            }
        }

        // ── Cooldown Duration ─────────────────────────────────────────────────
        PreferenceSection(
            title = stringResource(R.string.settings_cooldown_title),
            subtitle = stringResource(R.string.settings_cooldown_subtitle)
        ) {
            listOf(0, 1, 5, 10, 30).forEach { mins ->
                CooldownOption(
                    mins = mins,
                    isSelected = state.cooldownMins == mins,
                    onSelect = { onCooldownChange(mins) }
                )
            }
        }
    }
}

/**
 * Reusable, accessible selectable card option for settings preferences (Language, Theme Mode, etc.).
 * Includes semantic [Role.RadioButton] and respects dynamic font scaling.
 */
@Composable
fun PersonaSelectionCard(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .selectable(
                selected = isSelected,
                onClick = onClick,
                role = Role.RadioButton
            ),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        ),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = isSelected,
                onClick = null,
                colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Column(modifier = Modifier.weight(1f, fill = false)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// Backward-compatibility alias for any legacy callers
@Composable
fun LanguageOptionCard(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) = PersonaSelectionCard(title, subtitle, isSelected, onClick, modifier)
