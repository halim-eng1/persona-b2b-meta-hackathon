package com.example.persona.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = PersonaPrimitives.Teal400,
    onPrimary = PersonaPrimitives.Teal900,
    primaryContainer = PersonaPrimitives.Teal800,
    onPrimaryContainer = PersonaPrimitives.Teal100,
    inversePrimary = PersonaPrimitives.Teal600,
    secondary = PersonaPrimitives.Indigo400,
    onSecondary = PersonaPrimitives.Slate900,
    secondaryContainer = PersonaPrimitives.Slate700,
    onSecondaryContainer = PersonaPrimitives.Slate100,
    tertiary = PersonaPrimitives.Amber400,
    onTertiary = PersonaPrimitives.Slate900,
    tertiaryContainer = Color(0xFF78350F),
    onTertiaryContainer = Color(0xFFFEF3C7),
    background = PersonaPrimitives.Slate900,
    onBackground = PersonaPrimitives.Slate50,
    surface = PersonaPrimitives.Slate800,
    onSurface = PersonaPrimitives.Slate50,
    surfaceVariant = PersonaPrimitives.Slate700,
    onSurfaceVariant = PersonaPrimitives.Slate400,
    surfaceTint = PersonaPrimitives.Teal400,
    inverseSurface = PersonaPrimitives.Slate50,
    inverseOnSurface = PersonaPrimitives.Slate900,
    outline = PersonaPrimitives.Slate500,
    outlineVariant = PersonaPrimitives.Slate700,
    scrim = PersonaPrimitives.Slate950,
    error = PersonaPrimitives.Red400,
    onError = PersonaPrimitives.Slate900,
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = PersonaPrimitives.Red400
)

private val LightColorScheme = lightColorScheme(
    primary = PersonaPrimitives.Teal600,
    onPrimary = Color.White,
    primaryContainer = PersonaPrimitives.Teal100,
    onPrimaryContainer = PersonaPrimitives.Teal900,
    inversePrimary = PersonaPrimitives.Teal400,
    secondary = PersonaPrimitives.Indigo500,
    onSecondary = Color.White,
    secondaryContainer = PersonaPrimitives.Slate100,
    onSecondaryContainer = PersonaPrimitives.Slate800,
    tertiary = PersonaPrimitives.Amber500,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFEF3C7),
    onTertiaryContainer = Color(0xFF78350F),
    background = PersonaPrimitives.Slate50,
    onBackground = PersonaPrimitives.Slate900,
    surface = Color.White,
    onSurface = PersonaPrimitives.Slate900,
    surfaceVariant = PersonaPrimitives.Slate200,
    onSurfaceVariant = PersonaPrimitives.Slate600,
    surfaceTint = PersonaPrimitives.Teal600,
    inverseSurface = PersonaPrimitives.Slate800,
    inverseOnSurface = PersonaPrimitives.Slate50,
    outline = PersonaPrimitives.Slate400,
    outlineVariant = PersonaPrimitives.Slate200,
    scrim = Color.Black,
    error = PersonaPrimitives.Red500,
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = PersonaPrimitives.Red500
)

private val LocalChatColors = staticCompositionLocalOf { darkChatColors() }

/** Chat-feature color roles; resolve with the current appearance (dark/light). */
val MaterialTheme.chatColors: ChatColors
    @Composable get() = LocalChatColors.current

/** Semantic status color roles; resolve with the current appearance (dark/light). */
val MaterialTheme.statusColors: StatusColors
    @Composable get() = LocalStatusColors.current

/** Systematic spacing tokens for consistent padding, margins, and gaps. */
val MaterialTheme.spacing: PersonaSpacing
    @Composable get() = LocalPersonaSpacing.current

/** Unified access to Persona design tokens. */
object PersonaTheme {
    val spacing: PersonaSpacing
        @Composable get() = LocalPersonaSpacing.current

    val statusColors: StatusColors
        @Composable get() = LocalStatusColors.current

    val chatColors: ChatColors
        @Composable get() = LocalChatColors.current
}

@Composable
fun PersonaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    language: String = "ar",
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    val statusColors = if (darkTheme) darkStatusColors() else lightStatusColors()
    val chatColors = if (darkTheme) darkChatColors() else lightChatColors()
    val typography = remember(language) { getPersonaTypography(language) }

    CompositionLocalProvider(
        LocalPersonaSpacing provides PersonaSpacing(),
        LocalStatusColors provides statusColors,
        LocalChatColors provides chatColors
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            shapes = PersonaShapes,
            content = content
        )
    }
}
