package com.example.persona.features.settings.presentation.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.persona.ui.components.preferences.PreferenceSection as CanonicalPreferenceSection

/**
 * Settings feature bridge delegating to design system [CanonicalPreferenceSection].
 */
@Composable
fun PreferenceSection(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit
) = CanonicalPreferenceSection(
    title = title,
    modifier = modifier,
    subtitle = subtitle,
    content = content
)
