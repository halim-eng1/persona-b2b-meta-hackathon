package com.example.persona.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Semantic color roles for domain order and operation statuses.
 * Adapts between light and dark appearances while preserving WCAG AA contrast.
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
    pending = PersonaPrimitives.Amber400,
    pendingContainer = PersonaPrimitives.Amber400.copy(alpha = 0.15f),
    inPreparation = PersonaPrimitives.Blue500,
    inPreparationContainer = PersonaPrimitives.Blue500.copy(alpha = 0.15f),
    shipping = PersonaPrimitives.Indigo400,
    shippingContainer = PersonaPrimitives.Indigo400.copy(alpha = 0.15f),
    completed = PersonaPrimitives.Emerald400,
    completedContainer = PersonaPrimitives.Emerald400.copy(alpha = 0.15f),
    cancelled = PersonaPrimitives.Red400,
    cancelledContainer = PersonaPrimitives.Red400.copy(alpha = 0.15f),
    returned = PersonaPrimitives.Brown400,
    returnedContainer = PersonaPrimitives.Brown400.copy(alpha = 0.15f),
    success = PersonaPrimitives.Emerald400,
    successContainer = PersonaPrimitives.Emerald400.copy(alpha = 0.15f),
    warning = PersonaPrimitives.Amber400,
    warningContainer = PersonaPrimitives.Amber400.copy(alpha = 0.15f)
)

fun lightStatusColors(): StatusColors = StatusColors(
    pending = Color(0xFFD97706),
    pendingContainer = Color(0xFFFEF3C7),
    inPreparation = Color(0xFF2563EB),
    inPreparationContainer = Color(0xFFDBEAFE),
    shipping = Color(0xFF4F46E5),
    shippingContainer = Color(0xFFE0E7FF),
    completed = Color(0xFF059669),
    completedContainer = Color(0xFFD1FAE5),
    cancelled = Color(0xFFDC2626),
    cancelledContainer = Color(0xFFFEE2E2),
    returned = Color(0xFF78350F),
    returnedContainer = Color(0xFFF3E8E2),
    success = Color(0xFF059669),
    successContainer = Color(0xFFD1FAE5),
    warning = Color(0xFFD97706),
    warningContainer = Color(0xFFFEF3C7)
)

val LocalStatusColors = staticCompositionLocalOf { darkStatusColors() }
