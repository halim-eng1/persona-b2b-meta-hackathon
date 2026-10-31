package com.example.persona.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.persona.core.database.entity.OrderStatus
import com.example.persona.ui.theme.PersonaTheme

/**
 * Standardized status pill badge for displaying semantic status labels.
 * Features a subtle 0.5dp translucent border stroke so status chips appear
 * as crisp, illuminated terminal elements under dark and light appearances.
 */
@Composable
fun PersonaStatusBadge(
    label: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(8.dp),
    style: TextStyle = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
    border: BorderStroke? = BorderStroke(0.5.dp, contentColor.copy(alpha = 0.35f))
) {
    Surface(
        modifier = modifier,
        shape = shape,
        color = containerColor,
        border = border
    ) {
        Text(
            text = label,
            style = style,
            color = contentColor,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

/**
 * Order-specific status pill resolving colors from [PersonaTheme.statusColors].
 * Employs authentic Egyptian commercial Arabic labels.
 */
@Composable
fun PersonaOrderStatusBadge(
    status: OrderStatus,
    modifier: Modifier = Modifier
) {
    val statusColors = PersonaTheme.statusColors
    val (container, content, labelRes) = when (status) {
        OrderStatus.PENDING -> Triple(statusColors.pendingContainer, statusColors.pending, com.example.persona.R.string.order_status_pending)
        OrderStatus.IN_PREPARATION -> Triple(statusColors.inPreparationContainer, statusColors.inPreparation, com.example.persona.R.string.order_status_in_prep)
        OrderStatus.SHIPPING_IN_PROGRESS -> Triple(statusColors.shippingContainer, statusColors.shipping, com.example.persona.R.string.order_status_shipping)
        OrderStatus.SHIPPED -> Triple(statusColors.completedContainer, statusColors.completed, com.example.persona.R.string.order_status_delivered_card)
        OrderStatus.CANCELLED -> Triple(statusColors.cancelledContainer, statusColors.cancelled, com.example.persona.R.string.order_status_cancelled)
        OrderStatus.RETURNED -> Triple(statusColors.returnedContainer, statusColors.returned, com.example.persona.R.string.order_status_returned_badge)
    }

    PersonaStatusBadge(
        label = androidx.compose.ui.res.stringResource(labelRes),
        containerColor = container,
        contentColor = content,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp)
    )
}

/**
 * String-based overload for [PersonaOrderStatusBadge].
 */
@Composable
fun PersonaOrderStatusBadge(
    statusName: String,
    modifier: Modifier = Modifier
) {
    val orderStatus = OrderStatus.fromString(statusName)
    PersonaOrderStatusBadge(status = orderStatus, modifier = modifier)
}

/**
 * Formats badge count consistently (0 -> null, 1..99 -> "N", 100+ -> "99+").
 */
fun formatBadgeCount(count: Int): String? = when {
    count <= 0 -> null
    count > 99 -> "99+"
    else -> count.toString()
}

/**
 * Standardized unread counter badge.
 */
@Composable
fun PersonaUnreadBadge(
    count: Int,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary
) {
    val formatted = formatBadgeCount(count) ?: return
    Badge(
        containerColor = containerColor,
        contentColor = contentColor,
        modifier = modifier.semantics {
            contentDescription = "$count رسائل غير مقروءة"
        }
    ) {
        Text(
            text = formatted,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
        )
    }
}
