package com.example.persona.features.orders.presentation.components

import com.example.persona.R
import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.persona.features.orders.domain.EgyptianOrderUtils
import com.example.persona.features.orders.presentation.OrderFilter
import com.example.persona.ui.theme.PersonaTheme

// WCAG 2.1 AA compliant status colors (>= 4.5:1 contrast against light background)
val OrderAmber = Color(0xFFB45309)
val OrderBlue = Color(0xFF1D4ED8)
val OrderIndigo = Color(0xFF4338CA)
val OrderGreen = Color(0xFF047857)
val OrderRed = Color(0xFFB91C1C)
val OrderBrown = Color(0xFF78350F)

@Composable
fun OrderMetricsRow(
    pendingCount: Int,
    inPreparationCount: Int,
    completedCount: Int,
    modifier: Modifier = Modifier,
    shippingCount: Int = 0,
    totalCodAmount: Double = 0.0,
    selectedFilter: OrderFilter = OrderFilter.ALL,
    onFilterClick: (OrderFilter) -> Unit = {}
) {
    val statusColors = PersonaTheme.statusColors

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        MetricChip(
            count = pendingCount,
            label = stringResource(R.string.order_status_new),
            color = statusColors.pending,
            isSelected = selectedFilter == OrderFilter.PENDING,
            onClick = { onFilterClick(OrderFilter.PENDING) }
        )
        MetricChip(
            count = inPreparationCount,
            label = stringResource(R.string.order_status_in_prep),
            color = statusColors.inPreparation,
            isSelected = selectedFilter == OrderFilter.IN_PREPARATION,
            onClick = { onFilterClick(OrderFilter.IN_PREPARATION) }
        )
        if (shippingCount > 0) {
            MetricChip(
                count = shippingCount,
                label = stringResource(R.string.order_status_shipping),
                color = statusColors.shipping,
                isSelected = selectedFilter == OrderFilter.SHIPPING_IN_PROGRESS,
                onClick = { onFilterClick(OrderFilter.SHIPPING_IN_PROGRESS) }
            )
        }
        MetricChip(
            count = completedCount,
            label = stringResource(R.string.order_status_delivered_card),
            color = statusColors.completed,
            isSelected = selectedFilter == OrderFilter.COMPLETED,
            onClick = { onFilterClick(OrderFilter.COMPLETED) }
        )
        if (totalCodAmount > 0.0) {
            val formatted = EgyptianOrderUtils.formatMoney(totalCodAmount)
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(statusColors.completed)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "$formatted ج.م",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = statusColors.completed
                        )
                        Text(
                            text = stringResource(R.string.orders_metrics_displayed_cod),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MetricChip(
    count: Int,
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val clickableModifier = (if (onClick != null) {
        modifier.clickable(onClick = onClick)
    } else {
        modifier
    }).heightIn(min = 48.dp)

    Card(
        modifier = clickableModifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = if (isSelected) {
            BorderStroke(1.5.dp, color.copy(alpha = 0.8f))
        } else {
            null
        },
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 2.dp else 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(color)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
