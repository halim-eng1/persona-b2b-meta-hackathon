package com.example.persona.features.orders.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.persona.core.database.entity.OrderEntity
import com.example.persona.core.database.entity.OrderStatus
import com.example.persona.features.orders.domain.usecase.FormatCourierManifestUseCase

private val defaultManifestUseCase = FormatCourierManifestUseCase()

/**
 * Formats order information into a clean Egyptian commercial courier manifest
 * ready for 1-tap copy to delivery services (Bosta, Aramex, WhatsApp courier chats).
 * Retained as a public top-level function for UI and test compatibility.
 */
fun formatCourierManifest(order: OrderEntity): String = defaultManifestUseCase(order)

/**
 * Dedicated Merchant Order Card.
 *
 * Coordinates header, expandable item/address details, financial COD amounts,
 * and tactile fulfillment actions.
 */
@Composable
fun OrderCard(
    order: OrderEntity,
    onStatusChanged: (Long, OrderStatus) -> Unit,
    onEditNote: () -> Unit,
    modifier: Modifier = Modifier,
    onAdvanceStatus: ((OrderEntity) -> Unit)? = null,
    onCopyCourierManifest: ((OrderEntity) -> Unit)? = null,
    onOpenChat: ((String) -> Unit)? = null,
    onEditOrder: ((OrderEntity) -> Unit)? = null,
    onCancelOrder: ((OrderEntity) -> Unit)? = null,
    isSelected: Boolean = false,
    isSelectionMode: Boolean = false,
    onToggleSelect: ((Long) -> Unit)? = null
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (isSelectionMode && onToggleSelect != null) {
                    Modifier.clickable { onToggleSelect(order.id) }
                } else Modifier
            ),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = if (isSelected) {
            BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
        } else null,
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 3.dp else 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isSelectionMode && onToggleSelect != null) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onToggleSelect(order.id) },
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .size(48.dp)
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    OrderCardHeader(
                        contactName = order.contactName,
                        customerName = order.customerNameFromLlm,
                        customerPhone = order.customerPhone,
                        timestamp = order.timestamp,
                        status = order.status,
                        onOpenChat = onOpenChat?.let { { it(order.contactName) } }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Order items, address, merchant AI note, customer note, COD amounts
            OrderCardDetails(
                orderDetails = order.orderDetails,
                deliveryAddress = order.deliveryAddress,
                merchantNote = order.merchantNote,
                customNotes = order.customNotes,
                totalAmount = order.totalAmount,
                shippingFee = order.shippingFee,
                governorate = order.governorate,
                cancellationReason = order.cancellationReason
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Action row: tactile state advance, edit, courier copy, note, overflow menu
            OrderCardActions(
                status = order.status,
                hasMerchantNote = !order.merchantNote.isNullOrBlank(),
                onAdvanceStatus = onAdvanceStatus?.let { { it(order) } },
                onCopyCourierManifest = onCopyCourierManifest?.let { { it(order) } },
                onEditNote = onEditNote,
                onStatusSelected = { onStatusChanged(order.id, it) },
                onEditOrder = onEditOrder?.let { { it(order) } },
                onCancelOrder = onCancelOrder?.let { { it(order) } }
            )
        }
    }
}
