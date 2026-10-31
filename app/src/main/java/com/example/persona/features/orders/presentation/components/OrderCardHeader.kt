package com.example.persona.features.orders.presentation.components

import androidx.compose.ui.res.stringResource
import com.example.persona.R

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.persona.features.orders.domain.EgyptianOrderUtils
import com.example.persona.ui.components.PersonaAvatar
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Composable
internal fun formatOrderTimestamp(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    if (diff < 60_000) return stringResource(R.string.orders_time_just_now)
    if (diff < 3600_000) return stringResource(R.string.orders_time_minutes_ago, diff / 60_000)
    if (diff < 86400_000) return stringResource(R.string.orders_time_hours_ago, diff / 3600_000)

    val dt = Instant.fromEpochMilliseconds(timestamp)
        .toLocalDateTime(TimeZone.currentSystemDefault())
    return "%02d/%02d — %02d:%02d".format(
        java.util.Locale.US, dt.dayOfMonth, dt.monthNumber, dt.hour, dt.minute
    )
}

@Composable
fun OrderCardHeader(
    contactName: String,
    customerName: String?,
    timestamp: Long,
    status: String,
    modifier: Modifier = Modifier,
    customerPhone: String = "",
    onOpenChat: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val phoneToDial = customerPhone.ifBlank {
        if (contactName.startsWith("01") || contactName.startsWith("+20") || contactName.startsWith("20")) {
            contactName
        } else ""
    }

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
                text = EgyptianOrderUtils.formatContactDisplayName(displayName),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!customerName.isNullOrBlank() && customerName != contactName) {
                Text(
                    text = EgyptianOrderUtils.formatContactDisplayName(contactName),
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

        // Quick phone call dialer button (WCAG >= 48dp)
        if (phoneToDial.isNotBlank()) {
            IconButton(
                onClick = { dialPhoneNumber(context, phoneToDial) },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Phone,
                    contentDescription = stringResource(R.string.orders_action_call_customer),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        OrderStatusBadge(status = status)

        if (onOpenChat != null) {
            IconButton(
                onClick = onOpenChat,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Chat,
                    contentDescription = stringResource(R.string.orders_action_open_chat),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

private fun dialPhoneNumber(context: Context, rawPhone: String) {
    try {
        val cleanPhone = EgyptianOrderUtils.extractEgyptianMobilePhone(rawPhone)
            ?: EgyptianOrderUtils.convertArabicDigitsToAscii(rawPhone).filter { it.isDigit() || it == '+' }
        if (cleanPhone.isBlank()) return
        val intent = Intent(Intent.ACTION_DIAL).apply {
            data = Uri.parse("tel:$cleanPhone")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Exception) {}
}
