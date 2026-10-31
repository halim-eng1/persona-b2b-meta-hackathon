package com.example.persona.features.chats.presentation.detail.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.persona.R
import com.example.persona.features.chats.presentation.detail.ActiveOrderSummary
import com.example.persona.features.orders.domain.EgyptianOrderUtils
import com.example.persona.ui.components.AvatarShape
import com.example.persona.ui.components.PersonaAvatar
import com.example.persona.ui.theme.chatColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactQuickProfileSheet(
    contactName: String,
    sourceClass: String,
    phoneNumber: String?,
    activeOrder: ActiveOrderSummary?,
    onDismissRequest: () -> Unit,
    onOpenWhatsApp: () -> Unit,
    modifier: Modifier = Modifier
) {
    val chatColors = MaterialTheme.chatColors

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // ── 1. Contact Identity Row ──────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                PersonaAvatar(
                    name = contactName,
                    size = 52.dp,
                    shape = AvatarShape.ROUNDED
                )
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = EgyptianOrderUtils.formatContactDisplayName(contactName),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        val localizedSource = when (sourceClass.uppercase()) {
                            "BUSINESS" -> stringResource(R.string.chat_badge_business)
                            "MAIN_APP" -> stringResource(R.string.chat_badge_main)
                            else -> sourceClass
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = when (sourceClass.uppercase()) {
                                "BUSINESS" -> chatColors.badgeBusiness
                                "MAIN_APP" -> chatColors.badgeMainApp
                                else -> chatColors.badgeOther
                            }
                        ) {
                            Text(
                                text = localizedSource,
                                color = chatColors.onBadge,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    val phone = phoneNumber?.takeIf { it.isNotBlank() } ?: stringResource(R.string.chat_profile_no_phone)
                    Text(
                        text = phone,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ── 2. Dedicated Active Orders Section ───────────────────────────────
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.LocalShipping,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = stringResource(R.string.chat_sheet_active_orders),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                if (activeOrder != null) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 2.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Status & Total Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                val statusLabel = when (activeOrder.status.uppercase()) {
                                    "PENDING" -> stringResource(R.string.order_status_pending)
                                    "CONFIRMED", "IN_PREPARATION" -> stringResource(R.string.order_status_in_prep)
                                    "SHIPPED" -> stringResource(R.string.order_status_shipped)
                                    "DELIVERED" -> stringResource(R.string.order_status_delivered)
                                    "CANCELLED" -> stringResource(R.string.order_status_cancelled)
                                    else -> activeOrder.status
                                }

                                val (statusBg, statusFg) = when (activeOrder.status.uppercase()) {
                                    "PENDING" -> Pair(Color(0xFFFEF3C7), Color(0xFF92400E))
                                    "CONFIRMED", "IN_PREPARATION" -> Pair(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
                                    "SHIPPED" -> Pair(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
                                    "DELIVERED" -> Pair(Color(0xFFD1FAE5), Color(0xFF065F46))
                                    "CANCELLED" -> Pair(MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
                                    else -> Pair(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
                                }

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = statusBg
                                ) {
                                    Text(
                                        text = statusLabel,
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = statusFg,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }

                                Text(
                                    text = stringResource(R.string.chat_order_cod_amount, activeOrder.totalAmount.toInt()),
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            // Items / Details
                            if (activeOrder.orderDetails.isNotBlank()) {
                                Text(
                                    text = activeOrder.orderDetails,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            // Delivery Address (if provided)
                            if (!activeOrder.deliveryAddress.isNullOrBlank()) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.LocationOn,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = stringResource(R.string.chat_sheet_delivery_address, activeOrder.deliveryAddress),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            // Creation Timestamp
                            if (activeOrder.createdAt > 0L) {
                                val timeFormat = SimpleDateFormat("yyyy/MM/dd • hh:mm a", Locale.getDefault())
                                val formattedDate = timeFormat.format(Date(activeOrder.createdAt))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AccessTime,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = stringResource(R.string.chat_sheet_order_time, formattedDate),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = stringResource(R.string.chat_sheet_no_active_orders),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }
            }

            // ── 3. Action Buttons ───────────────────────────────────────────────
            Button(
                onClick = onOpenWhatsApp,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(R.string.chat_open_whatsapp))
            }
        }
    }
}
