package com.example.persona.features.settings.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.persona.ui.components.preferences.PreferenceSwitchRow as CanonicalPreferenceSwitchRow

/**
 * Settings feature bridge delegating to design system [CanonicalPreferenceSwitchRow].
 */
@Composable
fun PreferenceSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) = CanonicalPreferenceSwitchRow(
    title = title,
    subtitle = subtitle,
    checked = checked,
    onCheckedChange = onCheckedChange,
    modifier = modifier,
    enabled = enabled
)
