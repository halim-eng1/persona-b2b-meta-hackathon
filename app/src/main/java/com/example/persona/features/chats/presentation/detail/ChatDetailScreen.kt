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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.persona.features.chats.presentation.detail.components.ChatComposerBar
import com.example.persona.features.chats.presentation.detail.components.CompactChatTopBar
import com.example.persona.features.chats.presentation.detail.components.DateHeaderChip
import com.example.persona.features.chats.presentation.detail.components.MessageBubble
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

        // Composer Bar (Root layout already handles imePadding)
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