package com.example.persona.features.chats.presentation.detail.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.persona.R
import com.example.persona.core.data.repository.UserPreferencesRepository
import com.example.persona.features.chats.presentation.detail.ReplyCapability
import com.example.persona.features.orders.domain.EgyptianOrderUtils
import com.example.persona.ui.components.extractMonogram
import com.example.persona.ui.theme.chatColors

@Composable
fun CompactChatTopBar(
    contactName: String,
    @Suppress("UNUSED_PARAMETER") sourceClass: String = "",
    replyCapability: ReplyCapability,
    isAiPaused: Boolean,
    botName: String = UserPreferencesRepository.DEFAULT_BOT_NAME,
    isAiGenerating: Boolean = false,
    needsHumanAttention: Boolean = false,
    isGlobalAutoReplyEnabled: Boolean = true,
    isAiOverrideEnabled: Boolean = false,
    effectiveAiActive: Boolean = if (isGlobalAutoReplyEnabled) !isAiPaused else isAiOverrideEnabled,
    onNavigateBack: () -> Unit,
    onToggleAi: () -> Unit,
    @Suppress("UNUSED_PARAMETER") onOpenWhatsApp: (() -> Unit)? = null,
    onResolveAttention: (() -> Unit)? = null,
    onOpenContactProfile: (() -> Unit)? = null,
    onExportChat: () -> Unit = {},
    onClearChat: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val chatColors = MaterialTheme.chatColors
    var menuExpanded by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            // Grows with font scale: minimum 56dp floor
            .heightIn(min = 56.dp),
        color = chatColors.surface,
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ── Navigation Back Button ───────────────────────────────────────
            IconButton(
                onClick = onNavigateBack,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.common_back),
                    tint = chatColors.onSurface
                )
            }

            // ── Customer Avatar Monogram (32dp) ──────────────────────────────
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(chatColors.surfaceAlt),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = extractMonogram(contactName, 1),
                    color = chatColors.onSurface,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // ── Contact Info Column (Tapping opens Customer Profile, NEVER Export) ──
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .then(
                        if (onOpenContactProfile != null) {
                            Modifier.clickable(
                                role = Role.Button,
                                onClick = onOpenContactProfile
                            )
                        } else {
                            Modifier
                        }
                    )
                    .padding(vertical = 4.dp, horizontal = 4.dp),
                verticalArrangement = Arrangement.Center
            ) {
                // Customer Name (Full horizontal width without badge truncation)
                Text(
                    text = EgyptianOrderUtils.formatContactDisplayName(contactName),
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = chatColors.onSurface
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Subtitle: Clean status without technical background leaks
                val subtitleColor = when {
                    isAiGenerating -> chatColors.statusGenerating
                    !isGlobalAutoReplyEnabled && isAiOverrideEnabled -> chatColors.statusReady
                    !effectiveAiActive -> chatColors.statusPaused
                    else -> chatColors.statusReady
                }

                val statusText = when {
                    isAiGenerating -> stringResource(R.string.chat_status_generating_clean)
                    !isGlobalAutoReplyEnabled && isAiOverrideEnabled -> stringResource(R.string.chat_active_exception_badge)
                    !effectiveAiActive -> {
                        if (!isGlobalAutoReplyEnabled) {
                            stringResource(R.string.chat_status_ai_paused_global)
                        } else {
                            stringResource(R.string.chat_status_ai_paused)
                        }
                    }
                    replyCapability == ReplyCapability.READY_VIA_NOTIFICATION -> stringResource(R.string.chat_status_assistant_ready)
                    else -> stringResource(R.string.chat_status_awaiting)
                }

                Text(
                    text = if (botName.isNotBlank() && !isAiGenerating) "$statusText • $botName" else statusText,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        textDirection = TextDirection.Content
                    ),
                    color = subtitleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            // ── Right Actions: Compact Copilot Pill, Overflow ───────────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(end = 2.dp)
            ) {
                // 1. Sleek Tactile Copilot Status Pill (Vector Icon + Clean Text)
                val isOverride = !isGlobalAutoReplyEnabled && isAiOverrideEnabled
                val pillIcon = when {
                    isOverride -> Icons.Default.Bolt
                    effectiveAiActive -> Icons.Default.SmartToy
                    else -> Icons.Default.PauseCircle
                }
                val pillText = when {
                    isOverride -> stringResource(R.string.chat_pill_override)
                    effectiveAiActive -> stringResource(R.string.chat_pill_active)
                    else -> stringResource(R.string.chat_pill_paused)
                }
                val (pillBg, pillFg) = when {
                    isOverride -> Pair(
                        MaterialTheme.colorScheme.tertiaryContainer,
                        MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    effectiveAiActive -> Pair(
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    else -> Pair(
                        chatColors.surfaceAlt,
                        chatColors.statusIdle
                    )
                }

                val animatedBg by animateColorAsState(
                    targetValue = pillBg,
                    animationSpec = tween(250),
                    label = "pill_bg"
                )

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = animatedBg,
                    modifier = Modifier
                        .height(28.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(role = Role.Switch, onClick = onToggleAi)
                        .semantics {
                            contentDescription = "AI auto-reply toggle"
                            stateDescription = if (effectiveAiActive) "Active" else "Paused"
                        }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(horizontal = 8.dp)
                    ) {
                        Icon(
                            imageVector = pillIcon,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = pillFg
                        )
                        Text(
                            text = pillText,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            ),
                            color = pillFg
                        )
                    }
                }

                // 2. 3-Dot Overflow Menu (Sole home for Export and Clear)
                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.chat_more_options),
                            tint = chatColors.onSurface
                        )
                    }

                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        modifier = Modifier.background(chatColors.surface)
                    ) {
                        if (needsHumanAttention && onResolveAttention != null) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = stringResource(R.string.chat_resolve_attention),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    onResolveAttention()
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = stringResource(R.string.chat_export_chat),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = chatColors.onSurface
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = null,
                                    tint = chatColors.accent
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onExportChat()
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = stringResource(R.string.chat_clear_chat),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onClearChat()
                            }
                        )
                    }
                }
            }
        }
    }
}
