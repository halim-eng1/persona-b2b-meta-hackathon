package com.example.persona.ui.components

import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Standardizes the subtle screen background vertical gradient across root screens,
 * adapting cleanly between Light and Dark mode tokens without background leaks.
 */
@Composable
fun Modifier.personaScreenBackground(
    topColor: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
    bottomColor: Color = MaterialTheme.colorScheme.background
): Modifier {
    return this.background(
        Brush.verticalGradient(
            colors = listOf(topColor, bottomColor)
        )
    )
}
