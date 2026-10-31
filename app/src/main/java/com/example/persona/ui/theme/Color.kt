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
// Dark Palette (INVARIANT - Dark Terminal Mode)
val PrimaryDark = PersonaPrimitives.Teal400       // Cyan/Teal
val SecondaryDark = Color(0xFF7C8BA1)             // Sleek Slate-Indigo
val TertiaryDark = Color(0xFFF59E0B)              // Warm Terminal Gold
val BackgroundDark = Color(0xFF0B0F19)            // Obsidian Terminal canvas
val SurfaceDark = Color(0xFF151D2E)               // Elevated Terminal Slate
val OnPrimaryDark = PersonaPrimitives.Teal900
val OnSecondaryDark = Color(0xFF0B0F19)
val OnBackgroundDark = Color(0xFFF1F5F9)          // Soft off-white
val OnSurfaceDark = Color(0xFFF1F5F9)
val ErrorDark = PersonaPrimitives.Red400          // Soft red
val OnErrorDark = Color(0xFF381418)

// Light Palette (Outdoor High-Contrast WCAG AAA)
val PrimaryLight = Color(0xFF0D5C54)              // Teal 800 - 7.28:1 contrast against white
val SecondaryLight = Color(0xFF4338CA)            // Indigo 700 - 7.54:1 contrast
val TertiaryLight = Color(0xFF92400E)             // Amber 800 - 7.12:1 contrast
val BackgroundLight = Color(0xFFF1F5F9)          // Slate 100
val SurfaceLight = Color(0xFFFFFFFF)
val OnPrimaryLight = Color(0xFFFFFFFF)
val OnSecondaryLight = Color(0xFFFFFFFF)
val OnBackgroundLight = Color(0xFF0A0F1D)         // Slate 950
val OnSurfaceLight = Color(0xFF0A0F1D)            // Slate 950
val ErrorLight = Color(0xFF991B1B)                // Red 800 - 7.35:1 contrast
val OnErrorLight = Color(0xFFFFFFFF)
