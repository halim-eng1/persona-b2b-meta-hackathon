package com.example.persona.features.orders.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.persona.ui.components.PersonaAvatar
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

private val ORDER_MONTHS = mapOf(
    1 to "Jan", 2 to "Feb", 3 to "Mar", 4 to "Apr", 5 to "May", 6 to "Jun",
    7 to "Jul", 8 to "Aug", 9 to "Sep", 10 to "Oct", 11 to "Nov", 12 to "Dec"
)

internal fun formatOrderTimestamp(timestamp: Long): String {
    val dt = Instant.fromEpochMilliseconds(timestamp)
        .toLocalDateTime(TimeZone.currentSystemDefault())
    val month = ORDER_MONTHS[dt.monthNumber] ?: "%02d".format(java.util.Locale.US, dt.monthNumber)
    return "%d %s — %02d:%02d".format(
        java.util.Locale.US, dt.dayOfMonth, month, dt.hour, dt.minute
    )
}

@Composable
fun OrderCardHeader(
    contactName: String,
    customerName: String?,
    timestamp: Long,
    status: String,
    modifier: Modifier = Modifier,
    onOpenChat: (() -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PersonaAvatar(
            name = contactName,
            size = 40.dp,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            val displayName = customerName?.takeIf { it.isNotBlank() } ?: contactName
            Text(
                text = displayName,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!customerName.isNullOrBlank() && customerName != contactName) {
                Text(
                    text = contactName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = formatOrderTimestamp(timestamp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
        }
        OrderStatusBadge(status = status)
        if (onOpenChat != null) {
            Spacer(modifier = Modifier.width(4.dp))
            IconButton(
                onClick = onOpenChat,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Chat,
                    contentDescription = "Open Chat",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
