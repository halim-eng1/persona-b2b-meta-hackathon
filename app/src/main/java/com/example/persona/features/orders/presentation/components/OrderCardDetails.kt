package com.example.persona.features.orders.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.persona.features.orders.domain.EgyptianOrderUtils

@Composable
fun OrderCardDetails(
    orderDetails: String?,
    deliveryAddress: String?,
    merchantNote: String?,
    customNotes: String?,
    modifier: Modifier = Modifier,
    totalAmount: Double = 0.0,
    shippingFee: Double = 0.0,
    governorate: String? = null,
    cancellationReason: String? = null
) {
    var isExpanded by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        // Financial & COD Balance Badge
        val codTotal = totalAmount + shippingFee
        if (codTotal > 0.0) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = OrderGreen.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, OrderGreen.copy(alpha = 0.35f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Payments,
                        contentDescription = null,
                        tint = OrderGreen,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        val formattedTotal = EgyptianOrderUtils.formatMoney(codTotal)
                        Text(
                            text = "المطلوب تحصيله (COD): $formattedTotal ج.م",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = OrderGreen
                        )
                        if (shippingFee > 0.0) {
                            val formattedShipping = EgyptianOrderUtils.formatMoney(shippingFee)
                            Text(
                                text = "يشمل مصاريف الشحن: $formattedShipping ج.م",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Order items details with Expand / Collapse
        if (!orderDetails.isNullOrBlank()) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "محتويات الشحنة والأصناف",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clickable { isExpanded = !isExpanded }
                                .padding(4.dp)
                        ) {
                            Text(
                                text = if (isExpanded) "عرض أقل" else "عرض المزيد",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Icon(
                                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = orderDetails,
                        style = MaterialTheme.typography.bodySmall.copy(
                            textDirection = TextDirection.ContentOrRtl
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = if (isExpanded) Int.MAX_VALUE else 4,
                        overflow = if (isExpanded) TextOverflow.Clip else TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Delivery address & governorate
        if (!deliveryAddress.isNullOrBlank() || !governorate.isNullOrBlank()) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier
                        .size(18.dp)
                        .padding(top = 2.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Column {
                    val fullAddress = buildString {
                        if (!deliveryAddress.isNullOrBlank()) append(deliveryAddress)
                        if (!governorate.isNullOrBlank()) {
                            if (isNotEmpty()) append(" — ")
                            append(governorate)
                        }
                    }
                    Text(
                        text = fullAddress,
                        style = MaterialTheme.typography.bodySmall.copy(
                            textDirection = TextDirection.ContentOrRtl
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Merchant fulfillment note (injected into bot context)
        if (!merchantNote.isNullOrBlank()) {
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
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "ملاحظة التجهيز للمساعد الآلي: \"$merchantNote\"",
                        style = MaterialTheme.typography.labelSmall.copy(
                            textDirection = TextDirection.ContentOrRtl
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Custom notes from customer
        if (!customNotes.isNullOrBlank()) {
            Text(
                text = "ملاحظات العميل: $customNotes",
                style = MaterialTheme.typography.labelSmall.copy(
                    textDirection = TextDirection.ContentOrRtl
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Cancellation reason if order is cancelled
        if (!cancellationReason.isNullOrBlank()) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "سبب الإلغاء: $cancellationReason",
                        style = MaterialTheme.typography.labelSmall.copy(
                            textDirection = TextDirection.ContentOrRtl,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
