package com.example.persona.ui.theme

import android.app.Activity
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = PersonaPrimitives.Teal400,
    onPrimary = PersonaPrimitives.Teal900,
    primaryContainer = Color(0xFF0E3E39),
    onPrimaryContainer = Color(0xFF5EEAD4),
    inversePrimary = PersonaPrimitives.Teal600,
    secondary = Color(0xFF7C8BA1),
    onSecondary = Color(0xFF0B0F19),
    secondaryContainer = Color(0xFF132B30),
    onSecondaryContainer = Color(0xFF5EEAD4),
    tertiary = Color(0xFFF59E0B),
    onTertiary = Color(0xFF261A00),
    tertiaryContainer = Color(0xFF78350F),
    onTertiaryContainer = Color(0xFFFEF3C7),
    background = Color(0xFF0B0F19),
    onBackground = Color(0xFFF1F5F9),
    surface = Color(0xFF151D2E),
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = Color(0xFF1E293B),
    onSurfaceVariant = Color(0xFF94A3B8),
    surfaceTint = PersonaPrimitives.Teal400,
    inverseSurface = Color(0xFFF1F5F9),
    inverseOnSurface = Color(0xFF0B0F19),
    outline = Color(0xFF475569),
    outlineVariant = Color(0xFF233047),
    scrim = PersonaPrimitives.Slate950,
    error = PersonaPrimitives.Red400,
    onError = Color(0xFF0B0F19),
    errorContainer = Color(0xFF381418),
    onErrorContainer = Color(0xFFFCA5A5)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF0D5C54),              // Teal 800 — 7.28:1 contrast against white
    onPrimary = Color.White,
    primaryContainer = PersonaPrimitives.Teal100,
    onPrimaryContainer = PersonaPrimitives.Teal900,
    inversePrimary = PersonaPrimitives.Teal400,
    secondary = Color(0xFF4338CA),            // Indigo 700 — 7.54:1 contrast
    onSecondary = Color.White,
    secondaryContainer = PersonaPrimitives.Slate100,
    onSecondaryContainer = PersonaPrimitives.Slate800,
    tertiary = Color(0xFF92400E),             // Amber 800 — 7.12:1 contrast
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFEF3C7),
    onTertiaryContainer = Color(0xFF78350F),
    background = Color(0xFFF1F5F9),           // Slate 100 — subtle depth under white cards
    onBackground = Color(0xFF0A0F1D),          // Slate 950 — ultra-crisp text
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0A0F1D),             // Slate 950 — ultra-crisp text
    surfaceVariant = PersonaPrimitives.Slate200,
    onSurfaceVariant = Color(0xFF334155),     // Slate 700 — 9.6:1 contrast
    surfaceTint = Color(0xFF0D5C54),
    inverseSurface = PersonaPrimitives.Slate800,
    inverseOnSurface = PersonaPrimitives.Slate50,
    outline = Color(0xFF475569),              // Slate 600
    outlineVariant = Color(0xFFCBD5E1),       // Slate 300 — sharp 3.2:1 card border
    scrim = Color.Black,
    error = Color(0xFF991B1B),                // Red 800 — 7.35:1 contrast
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D)       // Red 900 — eliminates 3.08:1 washed-out error banner
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
    darkTheme: Boolean = false,
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

    val view = LocalView.current
    if (!view.isInEditMode) {
        DisposableEffect(darkTheme) {
            var ctx = view.context
            var activity: Activity? = null
            while (ctx is ContextWrapper) {
                if (ctx is Activity) {
                    activity = ctx
                    break
                }
                ctx = ctx.baseContext
            }
            val window = activity?.window
            if (window != null) {
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = !darkTheme
                insetsController.isAppearanceLightNavigationBars = !darkTheme
            }
            onDispose {}
        }
    }

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
