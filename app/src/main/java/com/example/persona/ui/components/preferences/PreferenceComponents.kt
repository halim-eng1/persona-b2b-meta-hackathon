package com.example.persona.ui.components.preferences

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.persona.features.settings.presentation.components.PreferenceSection as FeaturePreferenceSection
import com.example.persona.features.settings.presentation.components.PreferenceSelectableCard as FeaturePreferenceSelectableCard
import com.example.persona.features.settings.presentation.components.PreferenceSwitchRow as FeaturePreferenceSwitchRow

@Composable
fun PreferenceSection(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit
) = FeaturePreferenceSection(
    title = title,
    modifier = modifier,
    subtitle = subtitle,
    content = content
)

@Composable
fun PreferenceSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) = FeaturePreferenceSwitchRow(
    title = title,
    subtitle = subtitle,
    checked = checked,
    onCheckedChange = onCheckedChange,
    modifier = modifier,
    enabled = enabled
)

@Composable
fun PreferenceSelectableCard(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    previewContent: String? = null,
    badgeText: String? = if (isSelected) "Active" else null
) = FeaturePreferenceSelectableCard(
    title = title,
    subtitle = subtitle,
    isSelected = isSelected,
    onSelect = onSelect,
    modifier = modifier,
    previewContent = previewContent,
    badgeText = badgeText
)
