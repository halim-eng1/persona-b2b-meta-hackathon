package com.example.persona.features.orders.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
    modifier: Modifier = Modifier
) {
    val orderStatus = OrderStatus.fromString(status)

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Primary state advancement button (Kanban transition)
        when (orderStatus) {
            OrderStatus.PENDING -> {
                Button(
                    onClick = { onAdvanceStatus?.invoke() },
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
                    onClick = { onAdvanceStatus?.invoke() },
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
            onClick = { onCopyCourierManifest?.invoke() },
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
            if (hasMerchantNote) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Note saved",
                    modifier = Modifier.size(12.dp)
                )
            }
        }

        // Full status selector dropdown
        OrderStatusMenuButton(
            currentStatus = status,
            onSelected = onStatusSelected
        )
    }
}
