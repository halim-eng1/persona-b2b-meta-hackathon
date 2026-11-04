package com.example.persona.features.home.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.persona.R
import com.example.persona.features.audit_log.domain.model.AuditLogItem
import com.example.persona.features.home.presentation.SystemHealthStatus
import com.example.persona.features.home.presentation.WaitingCustomer
import com.example.persona.ui.components.AvatarShape
import com.example.persona.ui.components.PersonaAvatar
import com.example.persona.ui.components.PersonaStatusBadge
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Unified Hero Copilot Card for the Home Dashboard.
 *
 * Eliminates the legacy "Double-Mint" banner stacking by fusing the active listener health status
 * and the master auto-reply toggle switch into a single 24.dp Bento card.
 *
 * In normal active operation, displays a calm Bento surface with a primary teal bot icon,
 * clear Arabic status description, and end-aligned toggle switch.
 *
 * If permissions are missing or the background service is killed, gracefully degrades into
 * an actionable warning/error container with an immediate 1-tap repair button.
 */
@Composable
fun HomeHeroCopilotCard(
    isEnabled: Boolean,
    health: SystemHealthStatus,
    onToggle: (Boolean) -> Unit,
    onOpenSettings: () -> Unit,
    onRestartService: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val isDark = scheme.surface.luminance() < 0.5f

    val isDegraded = health == SystemHealthStatus.PERMISSION_MISSING || health == SystemHealthStatus.SERVICE_KILLED

    val containerColor = when {
        isDegraded -> scheme.errorContainer
        else -> if (isDark) scheme.surfaceVariant else scheme.surfaceContainerHigh
    }

    val contentColor = when {
        isDegraded -> scheme.onErrorContainer
        else -> scheme.onSurface
    }

    val title = when (health) {
        SystemHealthStatus.PERMISSION_MISSING -> stringResource(R.string.home_health_perm_missing_title)
        SystemHealthStatus.SERVICE_KILLED -> stringResource(R.string.home_health_service_killed_title)
        else -> stringResource(R.string.home_toggle_auto_reply_title)
    }

    val message = when (health) {
        SystemHealthStatus.PERMISSION_MISSING -> stringResource(R.string.home_health_perm_missing_desc)
        SystemHealthStatus.SERVICE_KILLED -> stringResource(R.string.home_health_service_killed_desc)
        SystemHealthStatus.PAUSED -> stringResource(R.string.home_toggle_auto_reply_paused)
        SystemHealthStatus.ACTIVE -> if (isEnabled) {
            stringResource(R.string.home_health_active_desc)
        } else {
            stringResource(R.string.home_toggle_auto_reply_paused)
        }
    }

    val icon = when (health) {
        SystemHealthStatus.PERMISSION_MISSING -> Icons.Default.Warning
        SystemHealthStatus.SERVICE_KILLED -> Icons.Default.Error
        else -> if (isEnabled) Icons.Default.SmartToy else Icons.Default.PauseCircle
    }

    val iconTint = when {
        isDegraded -> scheme.error
        isEnabled -> scheme.primary
        else -> scheme.onSurfaceVariant
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .then(
                if (!isDegraded) {
                    Modifier.toggleable(
                        value = isEnabled,
                        role = Role.Switch,
                        onValueChange = onToggle
                    )
                } else {
                    Modifier
                }
            ),
        shape = RoundedCornerShape(24.dp),
        color = containerColor,
        border = if (isDark && !isDegraded) BorderStroke(1.dp, scheme.outlineVariant) else null
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(28.dp)
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = contentColor
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isDegraded) contentColor.copy(alpha = 0.9f) else scheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (!isDegraded) {
                    Switch(
                        checked = isEnabled,
                        onCheckedChange = null,
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = scheme.primary
                        )
                    )
                }
            }

            if (isDegraded) {
                val buttonText = if (health == SystemHealthStatus.PERMISSION_MISSING) {
                    stringResource(R.string.home_health_perm_missing_btn)
                } else {
                    stringResource(R.string.home_health_service_killed_btn)
                }
                val buttonAction = if (health == SystemHealthStatus.PERMISSION_MISSING) {
                    onOpenSettings
                } else {
                    onRestartService
                }

                Button(
                    onClick = buttonAction,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = scheme.error),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                ) {
                    Text(
                        text = buttonText,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = scheme.onError
                    )
                }
            }
        }
    }
}

@Deprecated(
    message = "Use HomeHeroCopilotCard which unifies system health and master toggle into a single Bento card.",
    replaceWith = ReplaceWith("HomeHeroCopilotCard(isEnabled, health, onToggle, onOpenSettings, onRestartService, modifier)")
)
@Composable
fun SystemHealthCard(
    health: SystemHealthStatus,
    onOpenSettings: () -> Unit,
    onRestartService: () -> Unit,
    modifier: Modifier = Modifier
) {
    HomeHeroCopilotCard(
        isEnabled = health == SystemHealthStatus.ACTIVE,
        health = health,
        onToggle = {},
        onOpenSettings = onOpenSettings,
        onRestartService = onRestartService,
        modifier = modifier
    )
}

@Deprecated(
    message = "Use HomeHeroCopilotCard which unifies system health and master toggle into a single Bento card.",
    replaceWith = ReplaceWith("HomeHeroCopilotCard(isEnabled, health, onToggle, onOpenSettings, onRestartService, modifier)")
)
@Composable
fun AutoReplyMasterToggleCard(
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    HomeHeroCopilotCard(
        isEnabled = isEnabled,
        health = if (isEnabled) SystemHealthStatus.ACTIVE else SystemHealthStatus.PAUSED,
        onToggle = onToggle,
        onOpenSettings = {},
        onRestartService = {},
        modifier = modifier
    )
}

@Composable
fun UrgentAttentionBanner(
    pendingCount: Int,
    waitingCustomers: List<WaitingCustomer>,
    onCustomerClick: (contactName: String, sourceClass: String) -> Unit,
    modifier: Modifier = Modifier,
    isAutoPauseEnabled: Boolean = false
) {
    if (pendingCount <= 0) return

    val scheme = MaterialTheme.colorScheme
    val isDark = scheme.surface.luminance() < 0.5f

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = scheme.errorContainer.copy(alpha = if (isDark) 0.5f else 0.35f),
        border = BorderStroke(1.dp, scheme.error.copy(alpha = if (isDark) 0.6f else 0.4f))
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = stringResource(R.string.home_attention_customers_waiting, pendingCount),
                    tint = scheme.error,
                    modifier = Modifier.size(24.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.home_attention_customers_waiting, pendingCount),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = scheme.error
                    )
                    val subtitleText = if (isAutoPauseEnabled) {
                        stringResource(R.string.home_attention_customers_waiting_desc_paused)
                    } else {
                        stringResource(R.string.home_attention_customers_waiting_desc_active)
                    }
                    Text(
                        text = subtitleText,
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant
                    )
                }
            }

            if (waitingCustomers.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.home_attention_click_to_open),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = scheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    waitingCustomers.forEach { customer ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = scheme.surface,
                            border = if (isDark) BorderStroke(1.dp, scheme.outlineVariant) else null,
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable(role = Role.Button) { onCustomerClick(customer.contactName, customer.sourceClass) }
                                .semantics {
                                    contentDescription = "${customer.contactName}, ${customer.lastMessage}"
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Chat,
                                    contentDescription = null,
                                    tint = scheme.error,
                                    modifier = Modifier.size(16.dp)
                                )
                                Column {
                                    Text(
                                        text = customer.contactName,
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = scheme.onSurface
                                    )
                                    if (customer.lastMessage.isNotBlank()) {
                                        Text(
                                            text = customer.lastMessage,
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            color = scheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun B2BOperationalMetricsHub(
    todayOrders: Int,
    knowledgeChunks: Int,
    waitingCount: Int,
    todayReplies: Int,
    totalReplies: Int,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val isWide = maxWidth >= 600.dp
        val isWaitingAlert = waitingCount > 0

        val waitingContainerColor = if (isWaitingAlert) MaterialTheme.colorScheme.tertiaryContainer else null
        val waitingContentColor = if (isWaitingAlert) MaterialTheme.colorScheme.onTertiaryContainer else null
        val waitingIconTint = if (isWaitingAlert) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.secondary

        if (isWide) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.AutoMirrored.Filled.ReceiptLong,
                    value = todayOrders.toString(),
                    label = stringResource(R.string.home_stat_today_orders),
                    iconTint = MaterialTheme.colorScheme.primary
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.AutoMirrored.Filled.MenuBook,
                    value = knowledgeChunks.toString(),
                    label = stringResource(R.string.home_stat_knowledge_chunks),
                    iconTint = MaterialTheme.colorScheme.tertiary
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.SupportAgent,
                    value = waitingCount.toString(),
                    label = stringResource(R.string.home_stat_waiting_customers),
                    iconTint = waitingIconTint,
                    containerColor = waitingContainerColor,
                    contentColor = waitingContentColor
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.SmartToy,
                    value = todayReplies.toString(),
                    label = stringResource(R.string.home_stat_bot_replies),
                    subLabel = stringResource(R.string.home_stat_total_prefix, totalReplies),
                    iconTint = MaterialTheme.colorScheme.primary
                )
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.AutoMirrored.Filled.ReceiptLong,
                        value = todayOrders.toString(),
                        label = stringResource(R.string.home_stat_today_orders),
                        iconTint = MaterialTheme.colorScheme.primary
                    )
                    StatCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.AutoMirrored.Filled.MenuBook,
                        value = knowledgeChunks.toString(),
                        label = stringResource(R.string.home_stat_knowledge_chunks),
                        iconTint = MaterialTheme.colorScheme.tertiary
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.SupportAgent,
                        value = waitingCount.toString(),
                        label = stringResource(R.string.home_stat_waiting_customers),
                        iconTint = waitingIconTint,
                        containerColor = waitingContainerColor,
                        contentColor = waitingContentColor
                    )
                    StatCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.SmartToy,
                        value = todayReplies.toString(),
                        label = stringResource(R.string.home_stat_bot_replies),
                        subLabel = stringResource(R.string.home_stat_total_prefix, totalReplies),
                        iconTint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
fun QuickActionsSection(
    onNavigateToPersona: () -> Unit,
    onNavigateToImport: () -> Unit,
    onNavigateToChannels: () -> Unit,
    onNavigateToContacts: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val isDark = scheme.surface.luminance() < 0.5f

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = if (isDark) scheme.surfaceVariant else scheme.surfaceContainerHigh,
        border = if (isDark) BorderStroke(1.dp, scheme.outlineVariant) else null
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = stringResource(R.string.quick_actions_title),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = scheme.onSurface
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QuickActionButton(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.RecordVoiceOver,
                    label = stringResource(R.string.home_action_persona),
                    onClick = onNavigateToPersona
                )
                QuickActionButton(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.CloudDownload,
                    label = stringResource(R.string.home_action_import),
                    onClick = onNavigateToImport
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QuickActionButton(
                    modifier = Modifier.weight(1f),
                    icon = Icons.AutoMirrored.Filled.Chat,
                    label = stringResource(R.string.home_action_channels),
                    onClick = onNavigateToChannels
                )
                QuickActionButton(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.People,
                    label = stringResource(R.string.home_action_contacts),
                    onClick = onNavigateToContacts
                )
            }
        }
    }
}

@Composable
private fun QuickActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary,
    containerColor: Color? = null
) {
    val scheme = MaterialTheme.colorScheme
    val isDark = scheme.surface.luminance() < 0.5f
    val bg = containerColor ?: if (isDark) scheme.surface.copy(alpha = 0.7f) else scheme.surface.copy(alpha = 0.85f)

    Surface(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(role = Role.Button, onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = bg,
        border = if (isDark) BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.4f)) else null
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = scheme.onSurface
            )
        }
    }
}

/**
 * Settings-grade Bento metric card for the Home dashboard.
 *
 * Implements M3 tonal container architecture (surfaceContainerHigh on Light, surfaceVariant on Dark),
 * unboxed 24dp direct-accented vector iconography, and dynamic tonal alerts.
 */
@Composable
fun StatCard(
    icon: ImageVector,
    value: String,
    label: String,
    iconTint: Color,
    modifier: Modifier = Modifier,
    subLabel: String? = null,
    containerColor: Color? = null,
    contentColor: Color? = null,
    onClick: (() -> Unit)? = null
) {
    val scheme = MaterialTheme.colorScheme
    val isDark = scheme.surface.luminance() < 0.5f
    val bg = containerColor ?: if (isDark) scheme.surfaceVariant else scheme.surfaceContainerHigh
    val content = contentColor ?: scheme.onSurface
    val subContent = contentColor?.copy(alpha = 0.8f) ?: scheme.onSurfaceVariant

    Surface(
        modifier = modifier
            .heightIn(min = 112.dp)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier),
        shape = RoundedCornerShape(24.dp),
        color = bg,
        border = if (isDark && containerColor == null) BorderStroke(1.dp, scheme.outlineVariant) else null
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Column {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = content,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = subContent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subLabel != null) {
                    Text(
                        text = subLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = subContent.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun RecentActivityRow(
    item: AuditLogItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val localDt = item.timestamp.toLocalDateTime(TimeZone.currentSystemDefault())
    val formattedTime = "%02d:%02d".format(localDt.hour, localDt.minute)

    val (badgeText, badgeBg, badgeContent) = when {
        item.similarityScore >= 0.85f -> Triple(
            stringResource(R.string.home_match_high),
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
            MaterialTheme.colorScheme.primary
        )
        item.similarityScore >= 0.70f -> Triple(
            stringResource(R.string.home_match_medium),
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
            MaterialTheme.colorScheme.secondary
        )
        else -> Triple(
            stringResource(R.string.home_match_low),
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .semantics(mergeDescendants = true) {
                role = Role.Button
            }
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PersonaAvatar(
            name = item.contactName,
            size = 36.dp,
            shape = AvatarShape.ROUNDED,
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = com.example.persona.features.orders.domain.EgyptianOrderUtils.formatContactDisplayName(item.contactName),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = formattedTime,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = item.incomingMessage,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(R.string.home_recent_reply_prefix, item.generatedReply),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        PersonaStatusBadge(
            label = badgeText,
            containerColor = badgeBg,
            contentColor = badgeContent,
            shape = RoundedCornerShape(8.dp)
        )
    }
}
