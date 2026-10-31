package com.example.persona.features.orders.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.persona.ui.components.PersonaOrderStatusBadge
import com.example.persona.ui.components.PersonaStatusBadge

/** Color-coded status badge surfaces. */
enum class StatusBadgeStyle(
    val container: Color,
    val content: Color,
    val label: String,
    @androidx.annotation.StringRes val labelResId: Int
) {
    PENDING(OrderAmber.copy(alpha = 0.15f), OrderAmber, "طلب جديد", com.example.persona.R.string.order_status_pending),
    IN_PREPARATION(OrderBlue.copy(alpha = 0.15f), OrderBlue, "قيد التجهيز", com.example.persona.R.string.order_status_in_prep),
    SHIPPING_IN_PROGRESS(OrderIndigo.copy(alpha = 0.15f), OrderIndigo, "جاري التوصيل", com.example.persona.R.string.order_status_shipping),
    SHIPPED(OrderGreen.copy(alpha = 0.15f), OrderGreen, "تم التسليم", com.example.persona.R.string.order_status_delivered_card),
    CANCELLED(OrderRed.copy(alpha = 0.15f), OrderRed, "ملغي", com.example.persona.R.string.order_status_cancelled),
    RETURNED(OrderBrown.copy(alpha = 0.15f), OrderBrown, "مرتجع", com.example.persona.R.string.order_status_returned_badge);

    companion object {
        fun from(statusName: String): StatusBadgeStyle =
            entries.firstOrNull { it.name == statusName } ?: PENDING
    }
}

/**
 * Reusable status badge for order items integrating with [PersonaOrderStatusBadge]
 * and [PersonaStatusBadge].
 */
@Composable
fun OrderStatusBadge(
    status: String,
    modifier: Modifier = Modifier
) {
    PersonaOrderStatusBadge(statusName = status, modifier = modifier)
}

/**
 * Backward compatibility alias for [OrderStatusBadge].
 */
@Composable
fun StatusBadge(
    status: String,
    modifier: Modifier = Modifier
) {
    OrderStatusBadge(status = status, modifier = modifier)
}
