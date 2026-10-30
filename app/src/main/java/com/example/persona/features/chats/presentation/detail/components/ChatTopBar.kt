package com.example.persona.features.chats.presentation.detail.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.persona.core.data.repository.UserPreferencesRepository
import com.example.persona.features.chats.presentation.detail.ReplyCapability
import com.example.persona.ui.theme.chatColors

@Composable
fun CompactChatTopBar(
    contactName: String,
    sourceClass: String,
    replyCapability: ReplyCapability,
    isAiPaused: Boolean,
    botName: String = UserPreferencesRepository.DEFAULT_BOT_NAME,
    isAiGenerating: Boolean = false,
    needsHumanAttention: Boolean = false,
    onNavigateBack: () -> Unit,
    onToggleAi: () -> Unit,
    onOpenWhatsApp: (() -> Unit)? = null,
    onResolveAttention: (() -> Unit)? = null,
    onExportChat: () -> Unit = {},
    onClearChat: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val chatColors = MaterialTheme.chatColors
    Surface(
        modifier = modifier
            .fillMaxWidth()
            // Grows with the user's font scale: the two-line name/status column exceeds
            // 56dp at large system font sizes, so 56dp is a floor, not a fixed height.
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
            IconButton(
                onClick = onNavigateBack,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = chatColors.onSurface
                )
            }

            // Circular avatar (32.dp)
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(chatColors.surfaceAlt),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = contactName.take(1).uppercase(),
                    color = chatColors.onSurface,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Contact info column - clicking header provides direct access to export chat
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(
                        role = Role.Button,
                        onClickLabel = "Export Chat (تصدير المحادثة)",
                        onClick = onExportChat
                    )
                    .padding(vertical = 4.dp, horizontal = 4.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = contactName,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = chatColors.onSurface
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = when (sourceClass.uppercase()) {
                            "BUSINESS" -> chatColors.badgeBusiness
                            "MAIN_APP" -> chatColors.badgeMainApp
                            else -> chatColors.badgeOther
                        }
                    ) {
                        Text(
                            text = sourceClass.take(4),
                            color = chatColors.onBadge,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }

                // Subtitle. The bot name is rendered as its own Text segment so a mixed
                // Arabic/English name cannot invert the surrounding punctuation under
                // the LTR root layout (BiDi first-strong rule).
                val subtitleColor = when {
                    isAiGenerating -> chatColors.statusGenerating
                    isAiPaused -> chatColors.statusPaused
                    replyCapability == ReplyCapability.READY_VIA_NOTIFICATION -> chatColors.statusReady
                    else -> chatColors.statusIdle
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    when {
                        isAiGenerating -> {
                            // The thinking bubble in the stream says "…is generating reply…";
                            // the top bar only carries who, so the sentence isn't said twice.
                            Text(
                                text = botName,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 11.sp,
                                    textDirection = TextDirection.Content
                                ),
                                color = subtitleColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        replyCapability == ReplyCapability.READY_VIA_NOTIFICATION -> {
                            Text(
                                text = "● Ready • ",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                color = subtitleColor
                            )
                            Text(
                                text = botName,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 11.sp,
                                    textDirection = TextDirection.Content
                                ),
                                color = subtitleColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        isAiPaused -> {
                            Text(
                                text = "AI Paused",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                color = subtitleColor
                            )
                        }
                        else -> {
                            Text(
                                text = "○ Awaiting notification",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                color = subtitleColor
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            var menuExpanded by remember { mutableStateOf(false) }

            // Right row: AI Auto-Reply switch with clean label ("AI") & More options menu
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(end = 4.dp)
            ) {
                Text(
                    text = "AI",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = chatColors.onSurface
                    )
                )
                // Breathing room between label and switch: the label used to get its
                // gap from the 0.75f switch scale, which no longer applies.
                Spacer(modifier = Modifier.width(6.dp))
                Switch(
                    checked = !isAiPaused,
                    onCheckedChange = { onToggleAi() },
                    modifier = Modifier.semantics {
                        contentDescription = "AI auto-reply"
                        stateDescription = if (isAiPaused) "Paused" else "Active"
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = chatColors.onSurface,
                        checkedTrackColor = chatColors.switchTrackChecked,
                        uncheckedThumbColor = chatColors.statusIdle,
                        uncheckedTrackColor = chatColors.surfaceAlt
                    )
                )

                Spacer(modifier = Modifier.width(2.dp))

                // 1-Tap WhatsApp button
                if (onOpenWhatsApp != null) {
                    IconButton(
                        onClick = onOpenWhatsApp,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Chat,
                            contentDescription = "فتح المحادثة في واتساب",
                            tint = Color(0xFF25D366),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(2.dp))
                }

                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "خيارات إضافية (More options)",
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
                                        text = "حل التنبيه (Resolve Attention)",
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
                                    text = "Export Chat (تصدير المحادثة)",
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
                                    text = "مسح المحادثة (Clear Chat)",
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
