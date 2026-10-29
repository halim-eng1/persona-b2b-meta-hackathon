package com.example.persona.features.orders.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
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
 * Coordinates header, item/address details, and tactile fulfillment actions.
 */
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
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header: avatar + name/contact + timestamp … status badge + chat shortcut
            OrderCardHeader(
                contactName = order.contactName,
                customerName = order.customerNameFromLlm,
                timestamp = order.timestamp,
                status = order.status,
                onOpenChat = onOpenChat?.let { { it(order.contactName) } }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Order items, address, merchant AI note, customer note
            OrderCardDetails(
                orderDetails = order.orderDetails,
                deliveryAddress = order.deliveryAddress,
                merchantNote = order.merchantNote,
                customNotes = order.customNotes
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Action row: tactile state advance, courier copy, note, overflow menu
            OrderCardActions(
                status = order.status,
                hasMerchantNote = !order.merchantNote.isNullOrBlank(),
                onAdvanceStatus = onAdvanceStatus?.let { { it(order) } },
                onCopyCourierManifest = onCopyCourierManifest?.let { { it(order) } },
                onEditNote = onEditNote,
                onStatusSelected = { onStatusChanged(order.id, it) }
            )
        }
    }
}
