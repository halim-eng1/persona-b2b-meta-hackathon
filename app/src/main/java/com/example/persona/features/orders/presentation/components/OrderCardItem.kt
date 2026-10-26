package com.example.persona.features.orders.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.persona.core.database.entity.OrderEntity
import com.example.persona.core.database.entity.OrderStatus
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** Color-coded status badge surfaces. */
enum class StatusBadgeStyle(val container: Color, val content: Color, val label: String) {
    PENDING(OrderAmber.copy(alpha = 0.15f), OrderAmber, "Pending"),
    IN_PREPARATION(OrderBlue.copy(alpha = 0.15f), OrderBlue, "Preparing"),
    SHIPPING_IN_PROGRESS(OrderIndigo.copy(alpha = 0.15f), OrderIndigo, "Shipping"),
    SHIPPED(OrderGreen.copy(alpha = 0.15f), OrderGreen, "Shipped"),
    CANCELLED(OrderRed.copy(alpha = 0.15f), OrderRed, "Cancelled"),
    RETURNED(OrderBrown.copy(alpha = 0.15f), OrderBrown, "Returned");

    companion object {
        fun from(statusName: String): StatusBadgeStyle =
            entries.firstOrNull { it.name == statusName } ?: PENDING
    }
}

private val ORDER_MONTHS = mapOf(
    1 to "Jan", 2 to "Feb", 3 to "Mar", 4 to "Apr", 5 to "May", 6 to "Jun",
    7 to "Jul", 8 to "Aug", 9 to "Sep", 10 to "Oct", 11 to "Nov", 12 to "Dec"
)

private fun formatOrderTimestamp(timestamp: Long): String {
    val dt = Instant.fromEpochMilliseconds(timestamp)
        .toLocalDateTime(TimeZone.currentSystemDefault())
    val month = ORDER_MONTHS[dt.monthNumber] ?: "%02d".format(java.util.Locale.US, dt.monthNumber)
    return "%d %s — %02d:%02d".format(
        java.util.Locale.US, dt.dayOfMonth, month, dt.hour, dt.minute
    )
}

@Composable
fun OrderCard(
    order: OrderEntity,
    onStatusChanged: (Long, OrderStatus) -> Unit,
    onEditNote: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            // Header: avatar + name + timestamp … status badge
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = order.contactName.take(2).uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = order.customerNameFromLlm ?: order.contactName,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = formatOrderTimestamp(order.timestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                StatusBadge(status = order.status)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Order details summary
            if (!order.orderDetails.isNullOrBlank()) {
                Text(
                    text = order.orderDetails,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (!order.deliveryAddress.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = order.deliveryAddress,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Quick actions: status dropdown + note editor
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusDropdownButton(
                    currentStatus = order.status,
                    onSelected = { onStatusChanged(order.id, it) }
                )
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedButton(
                    onClick = onEditNote,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (order.merchantNote.isNullOrBlank()) "Note" else "Note ✓",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}

@Composable
fun StatusBadge(status: String, modifier: Modifier = Modifier) {
    val style = StatusBadgeStyle.from(status)
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = style.container
    ) {
        Text(
            text = style.label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = style.content,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun StatusDropdownButton(
    currentStatus: String,
    onSelected: (OrderStatus) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = StatusBadgeStyle.from(currentStatus).label,
                style = MaterialTheme.typography.labelMedium
            )
            Icon(
                imageVector = Icons.Default.ExpandMore,
                contentDescription = "Change status",
                modifier = Modifier.size(16.dp)
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            OrderStatus.entries.forEach { candidate ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = StatusBadgeStyle.from(candidate.name).label,
                            fontWeight = if (candidate.name == currentStatus) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    onClick = {
                        expanded = false
                        if (candidate.name != currentStatus) onSelected(candidate)
                    }
                )
            }
        }
    }
}
