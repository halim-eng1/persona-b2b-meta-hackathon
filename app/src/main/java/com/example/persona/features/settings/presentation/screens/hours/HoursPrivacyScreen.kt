package com.example.persona.features.settings.presentation.screens.hours

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import com.example.persona.R
import com.example.persona.features.settings.presentation.HoursActions
import com.example.persona.features.settings.presentation.SettingsSpoke
import com.example.persona.features.settings.presentation.SettingsUiState
import com.example.persona.features.settings.presentation.components.DayDial
import com.example.persona.features.settings.presentation.components.SettingsGroup
import com.example.persona.features.settings.presentation.components.SpokeScaffold
import com.example.persona.features.settings.presentation.components.rows.NavRow
import com.example.persona.features.settings.presentation.components.rows.SwitchRow
import com.example.persona.features.settings.presentation.components.settingFocus
import com.example.persona.features.settings.presentation.model.LocalMessageEditor
import com.example.persona.features.settings.presentation.model.MessageSlot
import com.example.persona.features.settings.presentation.model.SettingKey
import com.example.persona.features.settings.presentation.sheets.WorkingHoursSheet

@Composable
fun HoursPrivacyScreen(
    state: SettingsUiState,
    actions: HoursActions,
    focus: SettingKey?,
    onBack: () -> Unit,
    onOpenBlocklist: () -> Unit,
    modifier: Modifier = Modifier
) {
    val schedule = state.businessHoursSchedule
    var showWorkingHoursSheet by remember { mutableStateOf(false) }
    val editor = LocalMessageEditor.current

    SpokeScaffold(
        spoke = SettingsSpoke.Hours,
        title = R.string.spoke_hours,
        onBack = onBack,
        focus = focus,
        modifier = modifier
    ) {
        // Business Hours Group
        SettingsGroup(title = R.string.settings_hours_title) {
            SwitchRow(
                title = stringResource(R.string.settings_hours_enable_title),
                summary = stringResource(R.string.settings_hours_out_of_hours_desc),
                checked = schedule.isEnabled,
                onCheckedChange = actions::setBusinessHoursEnabled,
                modifier = Modifier.settingFocus(SettingKey.BusinessHours)
            )

            AnimatedVisibility(visible = schedule.isEnabled) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column(modifier = Modifier.settingFocus(SettingKey.WorkingDays)) {
                        Text(
                            text = stringResource(R.string.settings_hours_official_days),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        DayDial(
                            selectedDays = schedule.workingDays.toSet(),
                            onDaysChange = actions::setWorkingDays
                        )
                        if (schedule.workingDays.isEmpty()) {
                            Text(
                                text = stringResource(R.string.settings_hours_no_days_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }
                    }

                    // Range Card
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .settingFocus(SettingKey.WorkingTimes)
                            .clickable { showWorkingHoursSheet = true }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = stringResource(R.string.settings_hours_range_label),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${schedule.startTime.ifBlank { "09:00" }} – ${schedule.endTime.ifBlank { "22:00" }}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    NavRow(
                        title = stringResource(R.string.settings_hours_off_message_label),
                        summary = schedule.outOfHoursMessage.lineSequence().firstOrNull().takeUnless { it.isNullOrBlank() }
                            ?: stringResource(R.string.msg_when_closed),
                        onClick = { editor.open(MessageSlot.OutOfHours) },
                        modifier = Modifier.settingFocus(SettingKey.OutOfHours)
                    )
                }
            }
        }

        // Privacy & Blacklist Group
        SettingsGroup(title = R.string.settings_hours_privacy_title) {
            SwitchRow(
                title = stringResource(R.string.settings_hours_ignore_groups_title),
                summary = stringResource(R.string.settings_hours_ignore_groups_desc),
                checked = state.ignoreWhatsAppGroups,
                onCheckedChange = actions::setIgnoreGroups,
                modifier = Modifier.settingFocus(SettingKey.IgnoreGroups)
            )

            NavRow(
                title = stringResource(R.string.settings_blocklist_title),
                summary = stringResource(R.string.settings_blocklist_count, state.blacklistedNumbers.size),
                icon = Icons.Default.Block,
                onClick = onOpenBlocklist,
                modifier = Modifier.settingFocus(SettingKey.Blocklist)
            )
        }
    }

    if (showWorkingHoursSheet) {
        WorkingHoursSheet(
            start = schedule.startTime,
            end = schedule.endTime,
            onConfirm = actions::setWorkingHours,
            onDismiss = { showWorkingHoursSheet = false }
        )
    }
}
