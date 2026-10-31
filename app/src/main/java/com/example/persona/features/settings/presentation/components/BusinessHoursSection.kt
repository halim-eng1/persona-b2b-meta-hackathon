package com.example.persona.features.settings.presentation.components

import com.example.persona.R
import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.persona.features.settings.presentation.SettingsUiState

private val ALL_DAYS = listOf(
    "SATURDAY" to R.string.settings_hours_sat,
    "SUNDAY" to R.string.settings_hours_sun,
    "MONDAY" to R.string.settings_hours_mon,
    "TUESDAY" to R.string.settings_hours_tue,
    "WEDNESDAY" to R.string.settings_hours_wed,
    "THURSDAY" to R.string.settings_hours_thu,
    "FRIDAY" to R.string.settings_hours_fri
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BusinessHoursSection(
    state: SettingsUiState,
    onBusinessHoursEnabledChange: (Boolean) -> Unit,
    onWorkingDaysChange: (Set<String>) -> Unit,
    onWorkingHoursChange: (String, String) -> Unit,
    onOutOfHoursMessageChange: (String) -> Unit,
    onIgnoreGroupsChange: (Boolean) -> Unit,
    onAddBlacklistNumber: (String) -> Unit,
    onRemoveBlacklistNumber: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val schedule = state.businessHoursSchedule
    var startTimeInput by remember(schedule.startTime) { mutableStateOf(schedule.startTime) }
    var endTimeInput by remember(schedule.endTime) { mutableStateOf(schedule.endTime) }
    var newNumberInput by remember { mutableStateOf("") }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // ── Business Hours Schedule ───────────────────────────────────────────
        PreferenceSection(
            title = stringResource(R.string.settings_hours_title),
            subtitle = stringResource(R.string.settings_hours_desc)
        ) {
            PreferenceSwitchRow(
                title = stringResource(R.string.settings_hours_enable_title),
                subtitle = "عند التفعيل، سيرسل الروبوت رسالة خارج أوقات العمل بدلاً من محاولة البيع في غير أوقات الدوام.",
                checked = schedule.isEnabled,
                onCheckedChange = onBusinessHoursEnabledChange
            )

            if (schedule.isEnabled) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.settings_hours_official_days),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    ALL_DAYS.forEach { (code, nameRes) ->
                        val isSelected = schedule.workingDays.contains(code)
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                val updated = if (isSelected) {
                                    schedule.workingDays - code
                                } else {
                                    schedule.workingDays + code
                                }
                                onWorkingDaysChange(updated)
                            },
                            label = { Text(stringResource(nameRes)) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = startTimeInput,
                        onValueChange = {
                            startTimeInput = it
                            onWorkingHoursChange(it, endTimeInput)
                        },
                        label = { Text(stringResource(R.string.settings_hours_start_label)) },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = endTimeInput,
                        onValueChange = {
                            endTimeInput = it
                            onWorkingHoursChange(startTimeInput, it)
                        },
                        label = { Text(stringResource(R.string.settings_hours_end_label)) },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = schedule.outOfHoursMessage,
                    onValueChange = onOutOfHoursMessageChange,
                    label = { Text(stringResource(R.string.settings_hours_off_message_label)) },
                    placeholder = { Text(stringResource(R.string.settings_hours_off_message_placeholder)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
            }
        }

        // ── Privacy & Blacklist ───────────────────────────────────────────────
        PreferenceSection(
            title = stringResource(R.string.settings_hours_privacy_title),
            subtitle = stringResource(R.string.settings_hours_privacy_desc)
        ) {
            PreferenceSwitchRow(
                title = stringResource(R.string.settings_hours_ignore_groups_title),
                subtitle = stringResource(R.string.settings_hours_ignore_groups_desc),
                checked = state.ignoreWhatsAppGroups,
                onCheckedChange = onIgnoreGroupsChange
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.settings_hours_exempt_numbers),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )

            if (state.blacklistedNumbers.isEmpty()) {
                Text(
                    text = stringResource(R.string.settings_hours_no_blocked_numbers),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                state.blacklistedNumbers.forEach { number ->
                    Card(
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = number,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            IconButton(
                                onClick = { onRemoveBlacklistNumber(number) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Remove",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Input to add new number
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = newNumberInput,
                    onValueChange = { newNumberInput = it },
                    label = { Text(stringResource(R.string.settings_hours_add_blocked_label)) },
                    placeholder = { Text(stringResource(R.string.settings_hours_add_blocked_placeholder)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        if (newNumberInput.isNotBlank()) {
                            onAddBlacklistNumber(newNumberInput)
                            newNumberInput = ""
                        }
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.settings_hours_block_btn))
                }
            }
        }
    }
}
