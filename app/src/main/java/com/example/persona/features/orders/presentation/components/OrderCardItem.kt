package com.example.persona.features.orders.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Tune
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

/**
 * Egyptian Arabic commercial status labels for the courier manifest.
 * Kept here (next to the manifest) as the single place that maps raw
 * [OrderStatus] TEXT values to human-readable dispatch language.
 */
private fun courierStatusLabel(status: String): String {
    val statusDisplay = when (OrderStatus.fromString(status)) {
        OrderStatus.PENDING -> "طلب جديد"
        OrderStatus.IN_PREPARATION -> "قيد التجهيز"
        OrderStatus.SHIPPING_IN_PROGRESS -> "جاري التوصيل"
        OrderStatus.SHIPPED -> "تم الشحن / التسليم"
        OrderStatus.CANCELLED -> "ملغي"
        OrderStatus.RETURNED -> "مرتجع"
    }
    return statusDisplay
}

/**
 * Formats order information into a clean Egyptian commercial courier manifest
 * ready for 1-tap copy to delivery services (Bosta, Aramex, WhatsApp courier chats).
 * Terminal aesthetic: plain typography only — no emojis, no casual decoration.
 */
fun formatCourierManifest(order: OrderEntity): String = buildString {
    appendLine("بيانات الشحن والتوصيل")
    appendLine("━━━━━━━━━━━━━━━━")
    appendLine("اسم العميل: ${order.customerNameFromLlm?.takeIf { it.isNotBlank() } ?: order.contactName}")
    appendLine("الهاتف: ${order.contactName}")
    appendLine("العنوان: ${order.deliveryAddress?.takeIf { it.isNotBlank() } ?: "غير محدد"}")
    appendLine("تفاصيل الطلب: ${order.orderDetails?.takeIf { it.isNotBlank() } ?: "غير محدد"}")
    val orderNotes = order.customNotes?.takeIf { it.isNotBlank() }
    if (orderNotes != null) {
        appendLine("ملاحظات العميل: $orderNotes")
    }
    val prepNotes = order.merchantNote?.takeIf { it.isNotBlank() }
    if (prepNotes != null) {
        appendLine("ملاحظة التجهيز: $prepNotes")
    }
    append("الحالة: ${courierStatusLabel(order.status)}")
}

@Composable
fun OrderCard(
    order: OrderEntity,
    onStatusChanged: (Long, OrderStatus) -> Unit,
    onEditNote: () -> Unit,
    modifier: Modifier = Modifier,
    onAdvanceStatus: ((OrderEntity) -> Unit)? = null,
    onCopyCourierManifest: ((OrderEntity) -> Unit)? = null,
    onOpenChat: ((String) -> Unit)? = null
) {
    val orderStatus = OrderStatus.fromString(order.status)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            // Header: avatar + name/contact + timestamp … status badge + chat shortcut
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = order.contactName.take(2).uppercase(),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    val displayName = order.customerNameFromLlm?.takeIf { it.isNotBlank() } ?: order.contactName
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!order.customerNameFromLlm.isNullOrBlank() && order.customerNameFromLlm != order.contactName) {
                        Text(
                            text = order.contactName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = formatOrderTimestamp(order.timestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }
                StatusBadge(status = order.status)
                if (onOpenChat != null) {
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(
                        onClick = { onOpenChat(order.contactName) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Chat,
                            contentDescription = "Open Chat",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Order items details
            if (!order.orderDetails.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "ITEMS",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = order.orderDetails,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Delivery address
            if (!order.deliveryAddress.isNullOrBlank()) {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier
                            .size(16.dp)
                            .padding(top = 2.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = order.deliveryAddress,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Merchant fulfillment note (injected into bot context)
            if (!order.merchantNote.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "AI Note: \"${order.merchantNote}\"",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Custom notes from customer
            if (!order.customNotes.isNullOrBlank()) {
                Text(
                    text = "Customer Notes: ${order.customNotes}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Action row: tactile state advance, courier copy, note, overflow menu
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Primary state advancement button (Kanban transition)
                when (orderStatus) {
                    OrderStatus.PENDING -> {
                        Button(
                            onClick = { onAdvanceStatus?.invoke(order) },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = OrderBlue),
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Start Prep",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                    OrderStatus.IN_PREPARATION, OrderStatus.SHIPPING_IN_PROGRESS -> {
                        Button(
                            onClick = { onAdvanceStatus?.invoke(order) },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = OrderGreen),
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocalShipping,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Ship Order",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                    OrderStatus.SHIPPED -> {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = OrderGreen.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, OrderGreen.copy(alpha = 0.4f)),
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.DoneAll,
                                        contentDescription = null,
                                        tint = OrderGreen,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Fulfilled",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = OrderGreen
                                    )
                                }
                            }
                        }
                    }
                    OrderStatus.CANCELLED -> {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = OrderRed.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, OrderRed.copy(alpha = 0.35f)),
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Text(
                                    text = "Cancelled",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = OrderRed
                                )
                            }
                        }
                    }
                    OrderStatus.RETURNED -> {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = OrderBrown.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, OrderBrown.copy(alpha = 0.4f)),
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Text(
                                    text = "Returned",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = OrderBrown
                                )
                            }
                        }
                    }
                }

                // Courier 1-tap copy manifest button
                OutlinedButton(
                    onClick = { onCopyCourierManifest?.invoke(order) },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.height(38.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy Manifest",
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Courier",
                        style = MaterialTheme.typography.labelMedium
                    )
                }

                // Note edit button
                OutlinedButton(
                    onClick = onEditNote,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.height(38.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Note",
                        style = MaterialTheme.typography.labelMedium
                    )
                    if (!order.merchantNote.isNullOrBlank()) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Note saved",
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }

                // Full status selector dropdown
                OrderStatusMenuButton(
                    currentStatus = order.status,
                    onSelected = { onStatusChanged(order.id, it) }
                )
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
fun OrderStatusMenuButton(
    currentStatus: String,
    onSelected: (OrderStatus) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier.size(38.dp)
        ) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "Change Status",
                modifier = Modifier.size(18.dp)
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
