package com.example.persona.features.chats.presentation.detail.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.persona.core.data.repository.UserPreferencesRepository
import com.example.persona.features.chats.presentation.detail.ChatMessageUiModel
import com.example.persona.ui.theme.chatColors

@Composable
fun DateHeaderChip(dateText: String) {
    val chatColors = MaterialTheme.chatColors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = chatColors.surface
        ) {
            Text(
                text = dateText,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = chatColors.statusIdle,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
fun MessageBubble(
    message: ChatMessageUiModel,
    botName: String = UserPreferencesRepository.DEFAULT_BOT_NAME,
    onRetry: () -> Unit,
    onOpenWhatsApp: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isOutbound = !message.isFromUser
    val alignment = if (isOutbound) Alignment.CenterEnd else Alignment.CenterStart
    val chatColors = MaterialTheme.chatColors
    val bubbleColor = if (isOutbound) {
        if (message.senderType == "MERCHANT_MANUAL") {
            chatColors.bubbleOutboundManual
        } else {
            chatColors.bubbleOutboundBot
        }
    } else {
        chatColors.bubbleInbound
    }

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = alignment
    ) {
        Column(
            horizontalAlignment = if (isOutbound) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp,
                    bottomStart = if (isOutbound) 16.dp else 4.dp,
                    bottomEnd = if (isOutbound) 4.dp else 16.dp
                ),
                color = bubbleColor,
                tonalElevation = 1.dp
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    // Sender badge (Bot Name vs Manual) — iconified, zero-emoji
                    if (isOutbound) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (message.senderType == "MERCHANT_MANUAL") {
                                    Icons.Default.Person
                                } else {
                                    Icons.Default.SmartToy
                                },
                                contentDescription = null,
                                tint = chatColors.onBubbleSubtle,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (message.senderType == "MERCHANT_MANUAL") "You (Manual)" else botName,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = chatColors.onBubbleSubtle
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                    }

                    val isVoice = message.isVoiceNote || message.text.contains("[رسالة صوتية من العميل]")
                    val cleanText = message.text
                        .replace("[رسالة صوتية من العميل]:", "")
                        .replace("[رسالة صوتية من العميل]", "")
                        .trim()

                    // Voice note transcribed indicator
                    if (isVoice) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = chatColors.mediaChip,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Mic,
                                    contentDescription = "تسجيل صوتي",
                                    tint = chatColors.accentText,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "تسجيل صوتي مفرغ",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = chatColors.neutral
                                )
                            }
                        }
                    } else if (message.hasMediaIndicator) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = chatColors.mediaChip,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Attachment,
                                    contentDescription = null,
                                    tint = chatColors.accentText,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Media attached — view in WhatsApp app",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = chatColors.neutral
                                )
                            }
                        }
                    }

                    // Message text. ContentOrRtl picks the paragraph direction from the
                    // first strong directional character, so mixed Arabic/English content
                    // (brand names, product names, specs) keeps correct word order and
                    // punctuation placement regardless of the LTR root layout.
                    Text(
                        text = cleanText,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            textDirection = TextDirection.ContentOrRtl
                        ),
                        color = chatColors.onBubble
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Timestamp and delivery status indicator
                    Row(
                        modifier = Modifier.align(Alignment.End),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = message.formattedTime,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = chatColors.onBubbleSubtle
                        )

                        if (isOutbound) {
                            Spacer(modifier = Modifier.width(4.dp))
                            when (message.deliveryStatus.uppercase()) {
                                "SENDING" -> {
                                    Icon(
                                        imageVector = Icons.Default.Schedule,
                                        contentDescription = "Sending",
                                        tint = chatColors.onBubbleSubtle,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                                "SENT" -> {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Sent",
                                        tint = chatColors.delivered,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                                "QUEUED" -> {
                                    Icon(
                                        imageVector = Icons.Default.HourglassBottom,
                                        contentDescription = "Queued",
                                        tint = chatColors.statusPaused,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                                "FAILED" -> {
                                    Icon(
                                        imageVector = Icons.Default.Error,
                                        contentDescription = "Failed",
                                        tint = chatColors.failed,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Failure actions: Retry & Open in WhatsApp
            if (isOutbound && message.deliveryStatus.uppercase() == "FAILED") {
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onRetry,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            tint = chatColors.failed,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "Retry", color = chatColors.failed, style = MaterialTheme.typography.labelSmall)
                    }

                    TextButton(
                        onClick = onOpenWhatsApp,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = null,
                            tint = chatColors.accentText,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "Open in WhatsApp", color = chatColors.accentText, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}
