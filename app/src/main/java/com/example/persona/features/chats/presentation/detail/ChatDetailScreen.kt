package com.example.persona.features.chats.presentation.detail

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.persona.features.chats.presentation.detail.components.AiThinkingIndicatorBubble
import com.example.persona.features.chats.presentation.detail.components.ChatComposerBar
import com.example.persona.features.chats.presentation.detail.components.ClearChatDialog
import com.example.persona.features.chats.presentation.detail.components.CompactChatTopBar
import com.example.persona.features.chats.presentation.detail.components.DateHeaderChip
import com.example.persona.features.chats.presentation.detail.components.ExportChatDialog
import com.example.persona.features.chats.presentation.detail.components.MessageBubble
import com.example.persona.features.chats.presentation.detail.components.ScrollToBottomButton
import com.example.persona.ui.theme.chatColors
import kotlinx.coroutines.CoroutineScope
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
    val density = LocalDensity.current
    val imeBottom = WindowInsets.ime.getBottom(density)
    val chatColors = MaterialTheme.chatColors
    val snackbarHostState = remember { SnackbarHostState() }

    // Track whether initial scroll to bottom has completed for the active contact
    var isInitialScrolled by remember(state.contactName) { mutableStateOf(false) }
    var previousMessageCount by remember(state.contactName) { mutableIntStateOf(0) }
    var unreadCountBelow by remember(state.contactName) { mutableIntStateOf(0) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showClearChatDialog by remember { mutableStateOf(false) }

    if (showClearChatDialog) {
        ClearChatDialog(
            onDismissRequest = { showClearChatDialog = false },
            onConfirmClear = { range ->
                showClearChatDialog = false
                viewModel.clearChat(range)
            }
        )
    }

    if (showExportDialog) {
        ExportChatDialog(
            onDismissRequest = { showExportDialog = false },
            onConfirmExport = { range ->
                showExportDialog = false
                viewModel.exportChat(range) { exportText ->
                    try {
                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "سجل محادثة ${state.contactName}")
                            putExtra(Intent.EXTRA_TEXT, exportText)
                        }
                        context.startActivity(Intent.createChooser(sendIntent, "مشاركة سجل المحادثة"))
                    } catch (e: Exception) {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("تعذر فتح قائمة المشاركة")
                        }
                    }
                }
            }
        )
    }

    // Derived state: is the user currently viewing the bottom of the message stream?
    val isAtBottom by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val visibleItems = layoutInfo.visibleItemsInfo
            if (visibleItems.isEmpty()) {
                true
            } else {
                !listState.canScrollForward || (visibleItems.last().index >= layoutInfo.totalItemsCount - 1)
            }
        }
    }

    // Reset unread counter whenever the user returns to the bottom
    LaunchedEffect(isAtBottom) {
        if (isAtBottom) {
            unreadCountBelow = 0
        }
    }

    // Master Scroll Orchestrator:
    // 1. Initial Load: Instantly positions directly at the bottom without top-message flicker.
    // 2. Incoming Messages: If the user was viewing the bottom, smooth auto-scroll reveals the message.
    // 3. History Reading: If the user scrolled up, keeps reading position stable and increments unread badge.
    LaunchedEffect(state.messages.size, state.isAiGenerating, imeBottom) {
        val totalItems = state.messages.size + if (state.isAiGenerating) 1 else 0
        if (totalItems <= 0) return@LaunchedEffect

        if (!isInitialScrolled) {
            // First load: immediate jump to bottom with zero visual jitter
            listState.scrollToItem(totalItems - 1)
            isInitialScrolled = true
            previousMessageCount = state.messages.size
        } else {
            val currentCount = state.messages.size
            val newMessagesCount = currentCount - previousMessageCount

            if (newMessagesCount > 0) {
                val layoutInfo = listState.layoutInfo
                val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
                val wasAtBottom = (lastVisibleIndex >= previousMessageCount - 1) || !listState.canScrollForward
                val isOutgoingFromMe = state.messages.lastOrNull()?.isFromUser == true

                if (wasAtBottom || isOutgoingFromMe) {
                    listState.animateScrollToItem(totalItems - 1)
                    unreadCountBelow = 0
                } else {
                    unreadCountBelow += newMessagesCount
                }
                previousMessageCount = currentCount
            } else if (newMessagesCount < 0) {
                previousMessageCount = currentCount
            } else {
                // AI generating state or keyboard size changed while at the bottom
                if (isAtBottom || !listState.canScrollForward) {
                    listState.animateScrollToItem(totalItems - 1)
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(chatColors.background)
            .statusBarsPadding()
            .windowInsetsPadding(
                WindowInsets.navigationBars.union(WindowInsets.ime).only(WindowInsetsSides.Bottom)
            )
    ) {
        // Compact TopBar (56.dp floor; grows with font scale)
        CompactChatTopBar(
            contactName = state.contactName,
            sourceClass = state.sourceClass,
            replyCapability = state.replyCapability,
            isAiPaused = state.isAiPaused,
            botName = state.botName,
            isAiGenerating = state.isAiGenerating,
            onNavigateBack = onNavigateBack,
            onToggleAi = { viewModel.toggleAiPaused() },
            onExportChat = { showExportDialog = true },
            onClearChat = { showClearChatDialog = true }
        )

        // In-chat banner when AI is paused. Interactive: carries a button role, a tap
        // label, and a state description so TalkBack announces affordance + state, and
        // a 48dp minimum touch target (the content row is shorter than that).
        AnimatedVisibility(visible = state.isAiPaused) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .semantics { stateDescription = "AI auto-reply is paused" }
                    .clickable(
                        role = Role.Button,
                        onClickLabel = "Resume AI auto-reply"
                    ) { viewModel.toggleAiPaused() },
                color = chatColors.surfaceAlt
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PauseCircle,
                        contentDescription = null,
                        tint = chatColors.statusPaused,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "AI Auto-Reply paused for this chat — Tap to Resume",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Medium,
                            color = chatColors.onSurface
                        )
                    )
                }
            }
        }

        // Messages Stream. The snackbar overlays the bottom of the list instead of taking
        // layout space in the Column, so feedback never compresses the stream or shifts it.
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(if (isInitialScrolled || state.messages.isEmpty()) 1f else 0f),
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
                        botName = state.botName,
                        onRetry = { viewModel.retryMessage(msg.id, msg.text) },
                        onOpenWhatsApp = {
                            val pkg = state.fallbackTargetPackage
                                ?: if (state.sourceClass.uppercase() == "BUSINESS") "com.whatsapp.w4b" else "com.whatsapp"
                            openWhatsAppFallback(context, snackbarHostState, coroutineScope, pkg, msg.text)
                        }
                    )
                }

                if (state.isAiGenerating) {
                    item(key = "ai_thinking_indicator") {
                        // Align to the outgoing (AI) side, matching the green bubble stream
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            AiThinkingIndicatorBubble(
                                botName = state.botName,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            // WhatsApp-style Scroll to Bottom Floating Action Button with Unread Counter
            androidx.compose.animation.AnimatedVisibility(
                visible = !isAtBottom && isInitialScrolled,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut(),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 12.dp)
            ) {
                ScrollToBottomButton(
                    unreadCount = unreadCountBelow,
                    onClick = {
                        coroutineScope.launch {
                            val totalItems = state.messages.size + if (state.isAiGenerating) 1 else 0
                            if (totalItems > 0) {
                                listState.animateScrollToItem(totalItems - 1)
                            }
                            unreadCountBelow = 0
                        }
                    }
                )
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }

        // Composer Bar (Root layout already handles imePadding)
        ChatComposerBar(
            inputText = state.inputText,
            isSending = state.isSending,
            onInputTextChanged = { viewModel.onInputTextChanged(it) },
            onSendMessage = { viewModel.sendMessage() }
        )
    }
}

private fun openWhatsAppFallback(
    context: Context,
    snackbarHostState: SnackbarHostState,
    coroutineScope: CoroutineScope,
    packageName: String,
    text: String
) {
    // Feedback is fire-and-forget: showSnackbar suspends until the snackbar is dismissed
    // (~4s at Short duration), so awaiting it before startActivity would delay the
    // WhatsApp launch by that long. Child coroutines keep the snackbar visible without
    // blocking the launch. There are no suspension points before startActivity, so the
    // screen scope being cancelled (e.g. the user navigating back) can no longer
    // interrupt the launch sequence either.
    fun report(message: String) {
        coroutineScope.launch { snackbarHostState.showSnackbar(message) }
    }

    coroutineScope.launch {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("WhatsApp Reply", text))
            report("Message copied to clipboard")
        } catch (_: Exception) {
            // Some OEMs restrict background clipboard access — report the actual failure
            // instead of blaming WhatsApp. Opening the chat is still useful.
            report("Could not copy the reply to the clipboard")
        }

        try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
                ?: context.packageManager.getLaunchIntentForPackage("com.whatsapp")
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } else {
                report("WhatsApp app not found on device")
            }
        } catch (_: ActivityNotFoundException) {
            report("Could not open WhatsApp")
        } catch (_: Exception) {
            report("Could not open WhatsApp")
        }
    }
}
