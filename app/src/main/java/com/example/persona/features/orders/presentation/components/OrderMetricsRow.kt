package com.example.persona.features.orders.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import com.example.persona.features.orders.presentation.OrderFilter

val OrderAmber = Color(0xFFFFB300)
val OrderBlue = Color(0xFF3B82F6)
val OrderIndigo = Color(0xFF818CF8)
val OrderGreen = Color(0xFF34D399)
val OrderRed = Color(0xFFF87171)
val OrderBrown = Color(0xFFA1887F)

@Composable
fun OrderMetricsRow(
    pendingCount: Int,
    inPreparationCount: Int,
    completedCount: Int,
    modifier: Modifier = Modifier,
    selectedFilter: OrderFilter = OrderFilter.ALL,
    onFilterClick: (OrderFilter) -> Unit = {}
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        MetricChip(
            modifier = Modifier.weight(1f),
            count = pendingCount,
            label = "Pending",
            color = OrderAmber,
            isSelected = selectedFilter == OrderFilter.PENDING,
            onClick = { onFilterClick(OrderFilter.PENDING) }
        )
        MetricChip(
            modifier = Modifier.weight(1f),
            count = inPreparationCount,
            label = "In Prep",
            color = OrderBlue,
            isSelected = selectedFilter == OrderFilter.IN_PREPARATION,
            onClick = { onFilterClick(OrderFilter.IN_PREPARATION) }
        )
        MetricChip(
            modifier = Modifier.weight(1f),
            count = completedCount,
            label = "Completed",
            color = OrderGreen,
            isSelected = selectedFilter == OrderFilter.COMPLETED,
            onClick = { onFilterClick(OrderFilter.COMPLETED) }
        )
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
    val clickableModifier = if (onClick != null) {
        modifier.clickable(onClick = onClick)
    } else {
        modifier
    }

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
            BorderStroke(1.dp, color.copy(alpha = 0.7f))
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
