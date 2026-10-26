package com.example.persona.features.chats.presentation.detail

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

@Composable
fun ChatDetailScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChatDetailViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Auto-scroll to bottom when messages list size changes
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.lastIndex)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .background(Color(0xFF0F172A))
    ) {
        // Compact TopBar (fixed height 56.dp)
        CompactChatTopBar(
            contactName = state.contactName,
            sourceClass = state.sourceClass,
            replyCapability = state.replyCapability,
            isAiPaused = state.isAiPaused,
            onNavigateBack = onNavigateBack,
            onToggleAi = { viewModel.toggleAiPaused() }
        )

        // In-chat banner when AI is paused
        AnimatedVisibility(visible = state.isAiPaused) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.toggleAiPaused() },
                color = Color(0xFF334155)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PauseCircle,
                        contentDescription = null,
                        tint = Color(0xFFF59E0B),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "AI Auto-Reply paused for this chat — Tap to Resume",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Medium,
                            color = Color.White
                        )
                    )
                }
            }
        }

        // Messages Stream
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(
                items = state.messages,
                key = { _, msg -> msg.id }
            ) { index, msg ->
                val prevMsg = state.messages.getOrNull(index - 1)
                val showDateHeader = prevMsg == null || prevMsg.dateHeader != msg.dateHeader

                if (showDateHeader) {
                    DateHeaderChip(dateText = msg.dateHeader)
                }

                MessageBubble(
                    message = msg,
                    onRetry = { viewModel.retryMessage(msg.id, msg.text) },
                    onOpenWhatsApp = {
                        val pkg = state.fallbackTargetPackage
                            ?: if (state.sourceClass.uppercase() == "BUSINESS") "com.whatsapp.w4b" else "com.whatsapp"
                        openWhatsAppFallback(context, pkg, msg.text)
                    }
                )
            }
        }

        // Composer Bar (NO extra imePadding here!)
        ChatComposerBar(
            inputText = state.inputText,
            isSending = state.isSending,
            onInputTextChanged = { viewModel.onInputTextChanged(it) },
            onSendMessage = {
                viewModel.sendMessage()
                coroutineScope.launch {
                    if (state.messages.isNotEmpty()) {
                        listState.animateScrollToItem(state.messages.lastIndex)
                    }
                }
            }
        )
    }
}

@Composable
private fun CompactChatTopBar(
    contactName: String,
    sourceClass: String,
    replyCapability: ReplyCapability,
    isAiPaused: Boolean,
    onNavigateBack: () -> Unit,
    onToggleAi: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
        color = Color(0xFF1E293B),
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onNavigateBack,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }

            // Circular avatar (32.dp)
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF334155)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = contactName.take(1).uppercase(),
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Contact info column
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = contactName,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color.White
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = when (sourceClass.uppercase()) {
                            "BUSINESS" -> Color(0xFF25D366)
                            "MAIN_APP" -> Color(0xFF3B82F6)
                            else -> Color(0xFF8B5CF6)
                        }
                    ) {
                        Text(
                            text = sourceClass.take(4),
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }

                // Subtitle
                Text(
                    text = if (replyCapability == ReplyCapability.READY_VIA_NOTIFICATION) {
                        "● متاح للرد"
                    } else {
                        "○ بانتظار إشعار"
                    },
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    color = if (replyCapability == ReplyCapability.READY_VIA_NOTIFICATION) {
                        Color(0xFF4ADE80)
                    } else {
                        Color(0xFF94A3B8)
                    }
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Right row: Compact AI Auto-Reply Switch (0.75f scale) with clean label ("AI")
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(end = 4.dp)
            ) {
                Text(
                    text = "AI",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                )
                Switch(
                    checked = !isAiPaused,
                    onCheckedChange = { onToggleAi() },
                    modifier = Modifier.scale(0.75f),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFF22C55E),
                        uncheckedThumbColor = Color(0xFF94A3B8),
                        uncheckedTrackColor = Color(0xFF334155)
                    )
                )
            }
        }
    }
}

@Composable
private fun ChatComposerBar(
    inputText: String,
    isSending: Boolean,
    onInputTextChanged: (String) -> Unit,
    onSendMessage: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color(0xFF1E293B)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = onInputTextChanged,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
                placeholder = { Text("Type a manual reply...", color = Color(0xFF94A3B8)) },
                maxLines = 4,
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedContainerColor = Color(0xFF0F172A),
                    unfocusedContainerColor = Color(0xFF0F172A),
                    focusedBorderColor = Color(0xFF3B82F6),
                    unfocusedBorderColor = Color(0xFF334155)
                )
            )

            IconButton(
                onClick = onSendMessage,
                enabled = inputText.isNotBlank() && !isSending,
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        color = if (inputText.isNotBlank()) Color(0xFF25D366) else Color(0xFF334155),
                        shape = CircleShape
                    )
            ) {
                if (isSending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = if (inputText.isNotBlank()) Color.White else Color(0xFF94A3B8)
                    )
                }
            }
        }
    }
}

@Composable
private fun DateHeaderChip(dateText: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF1E293B)
        ) {
            Text(
                text = dateText,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = Color(0xFF94A3B8),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun MessageBubble(
    message: ChatMessageUiModel,
    onRetry: () -> Unit,
    onOpenWhatsApp: () -> Unit
) {
    val isOutbound = !message.isFromUser
    val alignment = if (isOutbound) Alignment.CenterEnd else Alignment.CenterStart
    val bubbleColor = if (isOutbound) {
        if (message.senderType == "MERCHANT_MANUAL") {
            Color(0xFF025144)
        } else {
            Color(0xFF005C4B)
        }
    } else {
        Color(0xFF202C33)
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
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
                    // Badge for Outbound sender (🤖 Auto vs 👤 Manual)
                    if (isOutbound) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (message.senderType == "MERCHANT_MANUAL") "👤 You (Manual)" else "🤖 AI Auto",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = Color(0xFF8696A0)
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                    }

                    // Media placeholder indicator if inbound message contains media
                    if (message.hasMediaIndicator) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF111B21),
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
                                    tint = Color(0xFF25D366),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Media attached — view in WhatsApp app",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF8696A0)
                                )
                            }
                        }
                    }

                    // Message text
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White
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
                            color = Color(0xFF8696A0)
                        )

                        if (isOutbound) {
                            Spacer(modifier = Modifier.width(4.dp))
                            when (message.deliveryStatus.uppercase()) {
                                "SENDING" -> {
                                    Icon(
                                        imageVector = Icons.Default.Schedule,
                                        contentDescription = "Sending",
                                        tint = Color(0xFF8696A0),
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                                "SENT" -> {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Sent",
                                        tint = Color(0xFF53BDEB),
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                                "QUEUED" -> {
                                    Icon(
                                        imageVector = Icons.Default.HourglassBottom,
                                        contentDescription = "Queued",
                                        tint = Color(0xFFF59E0B),
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                                "FAILED" -> {
                                    Icon(
                                        imageVector = Icons.Default.Error,
                                        contentDescription = "Failed",
                                        tint = Color(0xFFEF4444),
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
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "Retry", color = Color(0xFFEF4444), style = MaterialTheme.typography.labelSmall)
                    }

                    TextButton(
                        onClick = onOpenWhatsApp,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.OpenInNew,
                            contentDescription = null,
                            tint = Color(0xFF25D366),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "Open in WhatsApp", color = Color(0xFF25D366), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

private fun openWhatsAppFallback(context: Context, packageName: String, text: String) {
    try {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("WhatsApp Reply", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "Message copied to clipboard", Toast.LENGTH_SHORT).show()

        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            ?: context.packageManager.getLaunchIntentForPackage("com.whatsapp")
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } else {
            Toast.makeText(context, "WhatsApp app not found on device", Toast.LENGTH_LONG).show()
        }
    } catch (e: Exception) {
        Toast.makeText(context, "Could not open WhatsApp: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}