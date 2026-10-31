package com.example.persona.features.orders.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.persona.ui.components.PersonaOrderStatusBadge
import com.example.persona.ui.components.PersonaStatusBadge

/** Color-coded status badge metadata. Resolves dynamically via [PersonaOrderStatusBadge]. */
enum class StatusBadgeStyle(
    val label: String,
    @androidx.annotation.StringRes val labelResId: Int
) {
    PENDING("طلب جديد", com.example.persona.R.string.order_status_pending),
    IN_PREPARATION("قيد التجهيز", com.example.persona.R.string.order_status_in_prep),
    SHIPPING_IN_PROGRESS("جاري التوصيل", com.example.persona.R.string.order_status_shipping),
    SHIPPED("تم التسليم", com.example.persona.R.string.order_status_delivered_card),
    CANCELLED("ملغي", com.example.persona.R.string.order_status_cancelled),
    RETURNED("مرتجع", com.example.persona.R.string.order_status_returned_badge);

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
