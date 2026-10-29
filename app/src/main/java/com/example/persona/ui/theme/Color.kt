package com.example.persona.ui.theme

import androidx.compose.ui.graphics.Color

// ── 1. Primitive Palette (Invariant Raw Values) ───────────────────────────────
object PersonaPrimitives {
    // Slates (Neutral foundation)
    val Slate950 = Color(0xFF0A0F1D)
    val Slate900 = Color(0xFF0F172A)
    val Slate800 = Color(0xFF1E293B)
    val Slate700 = Color(0xFF334155)
    val Slate600 = Color(0xFF475569)
    val Slate500 = Color(0xFF64748B)
    val Slate400 = Color(0xFF94A3B8)
    val Slate300 = Color(0xFFCBD5E1)
    val Slate200 = Color(0xFFE2E8F0)
    val Slate100 = Color(0xFFF1F5F9)
    val Slate50  = Color(0xFFF8FAFC)

    // Brand Teals (Egyptian commerce identity)
    val Teal900 = Color(0xFF003732)
    val Teal800 = Color(0xFF0D5C54)
    val Teal700 = Color(0xFF0F766E)
    val Teal600 = Color(0xFF0D9488)
    val Teal500 = Color(0xFF14B8A6)
    val Teal400 = Color(0xFF2DD4BF)
    val Teal300 = Color(0xFF5EEAD4)
    val Teal100 = Color(0xFFCCFBF1)

    // Functional & Domain Accents
    val GreenWhatsApp = Color(0xFF25D366)
    val Emerald500    = Color(0xFF10B981)
    val Emerald400    = Color(0xFF34D399)
    val Amber500      = Color(0xFFF59E0B)
    val Amber400      = Color(0xFFFFB300)
    val Blue500       = Color(0xFF3B82F6)
    val Indigo500     = Color(0xFF6366F1)
    val Indigo400     = Color(0xFF818CF8)
    val Red500        = Color(0xFFEF4444)
    val Red400        = Color(0xFFF87171)
    val Brown400      = Color(0xFFA1887F)
}

// ── 2. Backwards-Compatible Semantic Color Tokens ─────────────────────────────
// Dark Palette
val PrimaryDark = PersonaPrimitives.Teal400       // Cyan/Teal
val SecondaryDark = PersonaPrimitives.Indigo400   // Indigo
val TertiaryDark = PersonaPrimitives.Amber500     // Warm Amber
val BackgroundDark = PersonaPrimitives.Slate900   // Near-black slate
val SurfaceDark = PersonaPrimitives.Slate800      // Dark Slate
val OnPrimaryDark = PersonaPrimitives.Teal900
val OnSecondaryDark = Color(0xFF1E1B4B)
val OnBackgroundDark = PersonaPrimitives.Slate50  // Off-white
val OnSurfaceDark = PersonaPrimitives.Slate50
val ErrorDark = PersonaPrimitives.Red400         // Soft red
val OnErrorDark = Color(0xFF7F1D1D)

// Light Palette
val PrimaryLight = PersonaPrimitives.Teal600      // Deep Teal
val SecondaryLight = PersonaPrimitives.Indigo500  // Muted Indigo
val TertiaryLight = Color(0xFFD97706)             // Amber
val BackgroundLight = PersonaPrimitives.Slate50   // Off-white
val SurfaceLight = Color(0xFFFFFFFF)
val OnPrimaryLight = Color(0xFFFFFFFF)
val OnSecondaryLight = Color(0xFFFFFFFF)
val OnBackgroundLight = PersonaPrimitives.Slate900 // Near-black
val OnSurfaceLight = PersonaPrimitives.Slate900
val ErrorLight = Color(0xFFDC2626)                // Red
val OnErrorLight = Color(0xFFFFFFFF)
