package com.example.persona.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Semantic color roles for domain order and operation statuses.
 * Adapts between light and dark appearances while preserving WCAG AA contrast (>= 4.5:1).
 */
@Immutable
data class StatusColors(
    val pending: Color,
    val pendingContainer: Color,
    val inPreparation: Color,
    val inPreparationContainer: Color,
    val shipping: Color,
    val shippingContainer: Color,
    val completed: Color,
    val completedContainer: Color,
    val cancelled: Color,
    val cancelledContainer: Color,
    val returned: Color,
    val returnedContainer: Color,
    val success: Color,
    val successContainer: Color,
    val warning: Color,
    val warningContainer: Color
)

fun darkStatusColors(): StatusColors = StatusColors(
    pending = Color(0xFFFBBF24), // Amber 400
    pendingContainer = Color(0xFF78350F).copy(alpha = 0.35f),
    inPreparation = Color(0xFF60A5FA), // Blue 400
    inPreparationContainer = Color(0xFF1E3A8A).copy(alpha = 0.35f),
    shipping = Color(0xFF818CF8), // Indigo 400
    shippingContainer = Color(0xFF312E81).copy(alpha = 0.35f),
    completed = Color(0xFF34D399), // Emerald 400
    completedContainer = Color(0xFF064E3B).copy(alpha = 0.35f),
    cancelled = Color(0xFFF87171), // Red 400
    cancelledContainer = Color(0xFF7F1D1D).copy(alpha = 0.35f),
    returned = Color(0xFFA1887F), // Brown 400
    returnedContainer = Color(0xFF3E2723).copy(alpha = 0.35f),
    success = Color(0xFF34D399),
    successContainer = Color(0xFF064E3B).copy(alpha = 0.35f),
    warning = Color(0xFFFBBF24),
    warningContainer = Color(0xFF78350F).copy(alpha = 0.35f)
)

fun lightStatusColors(): StatusColors = StatusColors(
    pending = Color(0xFFB45309), // Amber 700 - Contrast against #FEF3C7 is >= 4.5:1
    pendingContainer = Color(0xFFFEF3C7),
    inPreparation = Color(0xFF1D4ED8), // Blue 700 - Contrast against #DBEAFE is >= 4.5:1
    inPreparationContainer = Color(0xFFDBEAFE),
    shipping = Color(0xFF4338CA), // Indigo 700 - Contrast against #E0E7FF is >= 4.5:1
    shippingContainer = Color(0xFFE0E7FF),
    completed = Color(0xFF047857), // Emerald 700 - Contrast against #D1FAE5 is >= 4.5:1
    completedContainer = Color(0xFFD1FAE5),
    cancelled = Color(0xFFB91C1C), // Red 700 - Contrast against #FEE2E2 is >= 4.5:1
    cancelledContainer = Color(0xFFFEE2E2),
    returned = Color(0xFF78350F), // Amber 900 - Contrast against #F3E8E2 is >= 4.5:1
    returnedContainer = Color(0xFFF3E8E2),
    success = Color(0xFF047857),
    successContainer = Color(0xFFD1FAE5),
    warning = Color(0xFFB45309),
    warningContainer = Color(0xFFFEF3C7)
)

val LocalStatusColors = staticCompositionLocalOf { darkStatusColors() }
