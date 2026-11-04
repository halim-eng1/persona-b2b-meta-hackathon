package com.example.persona.features.settings.presentation.screens.hub

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GroupOff
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.persona.R
import com.example.persona.features.settings.presentation.SettingsActions
import com.example.persona.features.settings.presentation.SettingsUiState

@Composable
fun QuickSwitchesRow(
    state: SettingsUiState,
    actions: SettingsActions,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        QuickChip(
            label = R.string.quick_hours,
            icon = Icons.Default.Schedule,
            checked = state.businessHoursSchedule.isEnabled,
            onChange = { actions.hours.setBusinessHoursEnabled(it) }
        )
        QuickChip(
            label = R.string.quick_groups,
            icon = Icons.Default.GroupOff,
            checked = state.ignoreWhatsAppGroups,
            onChange = { actions.hours.setIgnoreGroups(it) }
        )
        QuickChip(
            label = R.string.quick_handoff,
            icon = Icons.Default.PanTool,
            checked = state.isAutoPauseOnHitl,
            onChange = { actions.whatsApp.setAutoPauseOnHitl(it) }
        )
    }
}

/**
 * A filter chip whose selected state mirrors the feature's enabled state.
 * Unselected chips stay neutral (muted border and icon); selected chips use the
 * primary teal container so the active switches read as a single accent group.
 */
@Composable
private fun QuickChip(
    @StringRes label: Int,
    icon: ImageVector,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    FilterChip(
        selected = checked,
        onClick = { onChange(!checked) },
        label = { Text(stringResource(label)) },
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
        },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surface,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
            selectedLeadingIconColor = MaterialTheme.colorScheme.primary
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (checked) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            }
        )
    )
}
