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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.persona.R
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

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // ── Row 1: Secondary actions (Note, Waybill, Edit) + Overflow Menu (⋮) ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Note edit button
            OutlinedButton(
                onClick = onEditNote,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp),
                contentPadding = PaddingValues(horizontal = 6.dp)
            ) {
                Text(
                    text = stringResource(R.string.order_action_note),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                )
                if (hasMerchantNote) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = stringResource(R.string.order_action_note_recorded),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            // Courier 1-tap copy manifest button
            OutlinedButton(
                onClick = { onCopyCourierManifest?.invoke() },
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp),
                contentPadding = PaddingValues(horizontal = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = stringResource(R.string.order_action_waybill_desc),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.order_action_waybill),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                )
            }

            // Edit order button (for manual corrections)
            if (onEditOrder != null) {
                OutlinedButton(
                    onClick = onEditOrder,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = stringResource(R.string.order_action_edit_desc),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.order_action_edit),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                }
            }

            // Full status selector dropdown (⋮)
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

        // ── Row 2: Primary status progression button spanning FULL WIDTH ──
        when (orderStatus) {
            OrderStatus.PENDING -> {
                Button(
                    onClick = { onAdvanceStatus?.invoke() },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = OrderBlue),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.order_status_start_prep),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
            OrderStatus.IN_PREPARATION -> {
                Button(
                    onClick = { onAdvanceStatus?.invoke() },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = OrderIndigo),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.LocalShipping,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.order_status_handover_courier),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
            OrderStatus.SHIPPING_IN_PROGRESS -> {
                Button(
                    onClick = { onAdvanceStatus?.invoke() },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = OrderGreen),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DoneAll,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.order_status_mark_delivered),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
            OrderStatus.SHIPPED -> {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = OrderGreen.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, OrderGreen.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.DoneAll,
                                contentDescription = null,
                                tint = OrderGreen,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.order_status_delivered_success),
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
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
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Text(
                            text = stringResource(R.string.order_status_cancelled_badge),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
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
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Text(
                            text = stringResource(R.string.order_status_returned_badge),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = OrderBrown
                        )
                    }
                }
            }
        }
    }
}
