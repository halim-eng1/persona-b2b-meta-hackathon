package com.example.persona.features.settings.presentation.screens.hub

import androidx.annotation.StringRes
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
import androidx.compose.material3.Icon
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
        }
    )
}
