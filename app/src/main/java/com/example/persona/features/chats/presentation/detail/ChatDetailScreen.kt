package com.example.persona.features.chats.presentation.detail

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.ui.text.style.TextOverflow
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
            needsHumanAttention = state.needsHumanAttention,
            isGlobalAutoReplyEnabled = state.isGlobalAutoReplyEnabled,
            isAiOverrideEnabled = state.isAiOverrideEnabled,
            effectiveAiActive = state.effectiveAiActive,
            onNavigateBack = onNavigateBack,
            onToggleAi = { viewModel.toggleAiActive() },
            onOpenWhatsApp = {
                WhatsAppLauncher.openChat(
                    context = context,
                    phoneNumber = state.contactPhoneNumber,
                    contactName = state.contactName,
                    preferBusiness = (state.sourceClass.uppercase() == "BUSINESS")
                )
            },
            onResolveAttention = { viewModel.resolveAttention() },
            onExportChat = { showExportDialog = true },
            onClearChat = { showClearChatDialog = true }
        )

        // Resolve Attention Banner (when customer requested human or triggered HITL)
        AnimatedVisibility(visible = state.needsHumanAttention) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
                color = MaterialTheme.colorScheme.errorContainer
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "⚠️ تنبيه: العميل بحاجة لتدخل بشري",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        )
                    }
                    FilledTonalButton(
                        onClick = { viewModel.resolveAttention() },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text(
                            text = "حل التنبيه",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }
        }

        // In-chat banner when AI is paused or inactive
        AnimatedVisibility(visible = !state.effectiveAiActive && !state.needsHumanAttention) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .semantics { stateDescription = "AI auto-reply is paused" }
                    .clickable(
                        role = Role.Button,
                        onClickLabel = "Toggle AI auto-reply"
                    ) { viewModel.toggleAiActive() },
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
                        text = if (!state.isGlobalAutoReplyEnabled)
                            "الرد الآلي العام متوقف — اضغط لتفعيل استثناء لهذه المحادثة"
                        else
                            "الرد الآلي متوقف لهذه المحادثة — اضغط للاستئناف",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Medium,
                            color = chatColors.onSurface
                        )
                    )
                }
            }
        }

        // Sticky Active Order Banner
        AnimatedVisibility(visible = state.activeOrder != null) {
            state.activeOrder?.let { order ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
                    tonalElevation = 2.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocalShipping,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "طلب نشط: ${formatOrderStatusArabic(order.status)}",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                )
                                Text(
                                    text = "${order.totalAmount.toInt()} ج.م (COD)",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                )
                            }
                            if (order.orderDetails.isNotBlank()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = order.orderDetails,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }

        // Messages Stream
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
                // Windowing / Pagination header: Load older messages
                if (state.hasMoreMessages) {
                    item(key = "load_more_messages_btn") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            OutlinedButton(
                                onClick = { viewModel.loadMoreMessages() },
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                Text(
                                    text = "تحميل رسائل أقدم (${state.messages.size} من ${state.totalMessageCount})",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }

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
                            openWhatsAppFallback(
                                context = context,
                                snackbarHostState = snackbarHostState,
                                coroutineScope = coroutineScope,
                                phoneNumber = state.contactPhoneNumber,
                                contactName = state.contactName,
                                packageName = pkg,
                                text = msg.text
                            )
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

        // Removed per user directive. Do not restore quick reply suggestion chips.
        ChatComposerBar(
            inputText = state.inputText,
            isSending = state.isSending,
            onInputTextChanged = { viewModel.onInputTextChanged(it) },
            onSendMessage = { viewModel.sendMessage() }
        )
    }
}

private fun formatOrderStatusArabic(status: String): String = when (status.uppercase()) {
    "PENDING" -> "قيد الانتظار"
    "CONFIRMED", "IN_PREPARATION" -> "قيد التجهيز"
    "SHIPPED" -> "مع المندوب 🚚"
    "DELIVERED" -> "تم التسليم"
    "CANCELLED" -> "ملغي"
    else -> status
}

private fun openWhatsAppFallback(
    context: Context,
    snackbarHostState: SnackbarHostState,
    coroutineScope: CoroutineScope,
    phoneNumber: String?,
    contactName: String,
    packageName: String,
    text: String
) {
    coroutineScope.launch {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("WhatsApp Reply", text))
            coroutineScope.launch { snackbarHostState.showSnackbar("تم نسخ الرسالة إلى الحافظة") }
        } catch (_: Exception) {}

        val opened = WhatsAppLauncher.openChat(
            context = context,
            phoneNumber = phoneNumber,
            contactName = contactName,
            preferBusiness = (packageName == "com.whatsapp.w4b")
        )
        if (!opened) {
            coroutineScope.launch { snackbarHostState.showSnackbar("تعذر فتح تطبيق واتساب") }
        }
    }
}
