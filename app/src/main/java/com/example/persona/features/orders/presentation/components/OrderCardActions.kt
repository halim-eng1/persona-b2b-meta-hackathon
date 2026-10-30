package com.example.persona.features.orders.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.persona.core.database.entity.OrderStatus

@Composable
fun OrderCardActions(
    status: String,
    hasMerchantNote: Boolean,
    onAdvanceStatus: (() -> Unit)?,
    onCopyCourierManifest: (() -> Unit)?,
    onEditNote: () -> Unit,
    onStatusSelected: (OrderStatus) -> Unit,
    modifier: Modifier = Modifier,
    onEditOrder: (() -> Unit)? = null,
    onCancelOrder: (() -> Unit)? = null
) {
    val orderStatus = OrderStatus.fromString(status)

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Primary state advancement button (Kanban progression)
        when (orderStatus) {
            OrderStatus.PENDING -> {
                Button(
                    onClick = { onAdvanceStatus?.invoke() },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = OrderBlue),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "بدء التجهيز",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
            OrderStatus.IN_PREPARATION -> {
                Button(
                    onClick = { onAdvanceStatus?.invoke() },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = OrderIndigo),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.LocalShipping,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "تسليم للمندوب",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
            OrderStatus.SHIPPING_IN_PROGRESS -> {
                Button(
                    onClick = { onAdvanceStatus?.invoke() },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = OrderGreen),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DoneAll,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "تم التسليم",
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
                        .heightIn(min = 48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.DoneAll,
                                contentDescription = null,
                                tint = OrderGreen,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "تم التسليم بنجاح",
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
                        .heightIn(min = 48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Text(
                            text = "طلب ملغي",
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
                        .heightIn(min = 48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Text(
                            text = "شحنة مرتجعة",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = OrderBrown
                        )
                    }
                }
            }
        }

        // Edit order button (for manual corrections)
        if (onEditOrder != null) {
            OutlinedButton(
                onClick = onEditOrder,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.heightIn(min = 48.dp),
                contentPadding = PaddingValues(horizontal = 10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "تعديل الطلب",
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "تعديل",
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }

        // Courier 1-tap copy manifest button
        OutlinedButton(
            onClick = { onCopyCourierManifest?.invoke() },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.heightIn(min = 48.dp),
            contentPadding = PaddingValues(horizontal = 10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.ContentCopy,
                contentDescription = "بوليصة الشحن",
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "بوليصة",
                style = MaterialTheme.typography.labelMedium
            )
        }

        // Note edit button
        OutlinedButton(
            onClick = onEditNote,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.heightIn(min = 48.dp),
            contentPadding = PaddingValues(horizontal = 10.dp)
        ) {
            Text(
                text = "ملاحظة",
                style = MaterialTheme.typography.labelMedium
            )
            if (hasMerchantNote) {
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "ملاحظة مسجلة",
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        // Full status selector dropdown
        OrderStatusMenuButton(
            currentStatus = status,
            onSelected = { selected ->
                if (selected == OrderStatus.CANCELLED && onCancelOrder != null) {
                    onCancelOrder()
                } else {
                    onStatusSelected(selected)
                }
            }
        )
    }
}
