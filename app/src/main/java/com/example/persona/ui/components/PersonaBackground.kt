package com.example.persona.ui.components

import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * Standardizes the subtle screen background vertical gradient across root screens,
 * adapting cleanly between Light and Dark mode tokens without background leaks.
 * In Light Mode, uses Slate 200 -> Slate 100 to maintain legible hierarchy and depth under direct sunlight glare.
 */
@Composable
fun Modifier.personaScreenBackground(
    isLight: Boolean = MaterialTheme.colorScheme.background.luminance() > 0.5f,
    topColor: Color = if (isLight) Color(0xFFE2E8F0) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
    bottomColor: Color = if (isLight) Color(0xFFF1F5F9) else MaterialTheme.colorScheme.background
): Modifier {
    return this.background(
        Brush.verticalGradient(
            colors = listOf(topColor, bottomColor)
        )
    )
}
