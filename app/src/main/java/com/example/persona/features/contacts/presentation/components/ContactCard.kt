package com.example.persona.features.contacts.presentation.components

import com.example.persona.R
import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.persona.core.database.entity.ContactEntity
import com.example.persona.core.database.entity.ContactTag
import com.example.persona.features.chats.presentation.detail.WhatsAppLauncher
import com.example.persona.features.orders.domain.EgyptianOrderUtils
import com.example.persona.ui.components.extractMonogram
import com.example.persona.ui.theme.chatColors
import com.example.persona.ui.theme.statusColors
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ContactCard(
    contact: ContactEntity,
    onEditClicked: () -> Unit,
    onNavigateToChat: ((contactName: String) -> Unit)? = null,
    onDeleteClicked: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val statusColors = MaterialTheme.statusColors
    val chatColors = MaterialTheme.chatColors
    val tagEnum = runCatching { ContactTag.valueOf(contact.tag) }.getOrDefault(ContactTag.REGULAR)

    val (badgeBgColor, badgeTextColor) = when (tagEnum) {
        ContactTag.VIP -> statusColors.pendingContainer to statusColors.pending
        ContactTag.DEBTOR -> statusColors.warningContainer to statusColors.warning
        ContactTag.PROBLEMATIC -> statusColors.cancelledContainer to statusColors.cancelled
        ContactTag.BLOCKED -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
        ContactTag.REGULAR -> statusColors.inPreparationContainer to statusColors.inPreparation
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .combinedClickable(
                enabled = onNavigateToChat != null || onDeleteClicked != null,
                onClick = { onNavigateToChat?.invoke(contact.contactName) },
                onLongClick = onDeleteClicked
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header: Avatar, Name & Tag Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = extractMonogram(contact.contactName, 1),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = EgyptianOrderUtils.formatContactDisplayName(contact.contactName),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (contact.phoneNumber.isNotBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = EgyptianOrderUtils.formatDisplayPhone(contact.phoneNumber),
                            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.outline)
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = badgeBgColor,
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Text(
                        text = stringResource(tagEnum.labelResId),
                        color = badgeTextColor,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        ),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Stats row: Orders count, Total spent & Last active
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.contacts_total_orders_stat, contact.totalOrders),
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium)
                )
                Text(
                    text = stringResource(R.string.contacts_total_spent_stat, contact.totalSpent.toInt()),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                )
                Text(
                    text = formatLastActive(contact.lastActiveTimestamp),
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.outline,
                        fontSize = 11.sp
                    )
                )
            }

            // Notes preview if present
            if (contact.notes.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.contacts_notes_label, contact.notes),
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }

            // Action Buttons: 1-Tap WhatsApp, Edit Tag & Notes, Chat
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Edit notes & tag button
                OutlinedButton(
                    onClick = onEditClicked,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = stringResource(R.string.common_edit),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.common_edit), style = MaterialTheme.typography.labelSmall)
                }

                Spacer(modifier = Modifier.width(8.dp))

                // 1-Tap WhatsApp Deep link button
                Button(
                    onClick = {
                        WhatsAppLauncher.openChat(
                            context = context,
                            phoneNumber = contact.phoneNumber,
                            contactName = contact.contactName,
                            preferBusiness = true
                        )
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = chatColors.badgeBusiness, // WhatsApp Green
                        contentColor = chatColors.onBadge
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Chat,
                        contentDescription = stringResource(R.string.contacts_action_whatsapp_desc),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.contacts_action_whatsapp),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }
}

@Composable
private fun formatLastActive(timestampMs: Long): String {
    if (timestampMs <= 0) return stringResource(R.string.time_relative_undefined)
    val parsed = runCatching {
        val dt = Instant.fromEpochMilliseconds(timestampMs).toLocalDateTime(TimeZone.currentSystemDefault())
        val now = Instant.fromEpochMilliseconds(System.currentTimeMillis()).toLocalDateTime(TimeZone.currentSystemDefault())
        val days = now.date.toEpochDays() - dt.date.toEpochDays()
        Triple(days, dt, true)
    }.getOrNull() ?: return ""

    val (days, dt) = parsed
    return when {
        days == 0 -> stringResource(R.string.time_relative_today_at, dt.hour, dt.minute)
        days == 1 -> stringResource(R.string.time_relative_yesterday)
        days in 2..6 -> stringResource(R.string.time_relative_days_ago, days)
        else -> "%02d/%02d/%04d".format(dt.dayOfMonth, dt.monthNumber, dt.year)
    }
}
