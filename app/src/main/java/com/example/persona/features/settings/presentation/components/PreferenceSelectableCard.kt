package com.example.persona.features.settings.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.persona.ui.components.preferences.PreferenceSelectableCard as CanonicalPreferenceSelectableCard

/**
 * Settings feature bridge delegating to design system [CanonicalPreferenceSelectableCard].
 */
@Composable
fun PreferenceSelectableCard(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    previewContent: String? = null,
    badgeText: String? = if (isSelected) "Active" else null
) = CanonicalPreferenceSelectableCard(
    title = title,
    subtitle = subtitle,
    isSelected = isSelected,
    onSelect = onSelect,
    modifier = modifier,
    previewContent = previewContent,
    badgeText = badgeText
)
