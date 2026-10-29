package com.example.persona.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Systematic 8-point spacing tokens for consistent padding, margins, and gaps.
 */
@Immutable
data class PersonaSpacing(
    val xxs: Dp = 2.dp,
    val xs: Dp = 4.dp,
    val s: Dp = 8.dp,
    val md: Dp = 12.dp,
    val lg: Dp = 16.dp,
    val xl: Dp = 20.dp,
    val xxl: Dp = 24.dp,
    val xxxl: Dp = 32.dp,
    val screenHorizontal: Dp = 16.dp,
    val screenVertical: Dp = 16.dp,
    val cardPadding: Dp = 16.dp,
    val touchTargetMin: Dp = 48.dp
)

val LocalPersonaSpacing = staticCompositionLocalOf { PersonaSpacing() }
