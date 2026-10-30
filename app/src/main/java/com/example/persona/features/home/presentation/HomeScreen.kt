package com.example.persona.features.home.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.persona.features.home.presentation.components.*

@Composable
fun HomeScreen(
    onNavigateToImport: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToAuditLog: () -> Unit,
    onNavigateToChat: (contactName: String, sourceClass: String) -> Unit,
    onNavigateToOrders: () -> Unit = {},
    onNavigateToKnowledge: () -> Unit = onNavigateToImport,
    onNavigateToContacts: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var showEmergencyDialog by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshHealth()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        MaterialTheme.colorScheme.background
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // ── Top Bar / Header ──────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "لوحة التحكم",
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "Persona B2B — مركز القيادة والمتابعة",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = onNavigateToSettings,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(imageVector = Icons.Default.Settings, contentDescription = "الإعدادات")
                }
            }

            // ── Section 1: System Health Diagnostic Banner ────────────────────
            SystemHealthCard(
                health = state.systemHealth,
                onOpenSettings = { viewModel.openNotificationSettings(context) },
                onRestartService = { viewModel.restartService() }
            )

            // ── Section 2: Human Takeover (HITL) Alert Center ─────────────────
            UrgentAttentionBanner(
                pendingCount = state.pendingAttentionCount,
                waitingCustomers = state.waitingCustomers,
                onCustomerClick = onNavigateToChat
            )

            // ── Section 3: Master Toggle ──────────────────────────────────────
            AutoReplyMasterToggleCard(
                isEnabled = state.isAutoReplyEnabled,
                onToggle = { viewModel.setAutoReplyEnabled(it) }
            )

            // ── Section 4: Daily B2B Operational Metrics Flight Deck ──────────
            Text(
                text = "مؤشرات التشغيل اليومية (DAILY B2B METRICS)",
                style = MaterialTheme.typography.labelSmall.copy(
                    letterSpacing = 0.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            B2BOperationalMetricsHub(
                todayOrders = state.todayOrdersCount,
                pendingRevenue = state.todayPendingRevenue,
                waitingCount = state.waitingCustomersCount,
                todayReplies = state.todayRepliesCount,
                totalReplies = state.totalRepliesSent
            )

            // ── Section 5: Quick Action Deck ──────────────────────────────────
            QuickActionsSection(
                onNavigateToKnowledge = onNavigateToKnowledge,
                onNavigateToImport = onNavigateToImport,
                onNavigateToOrders = onNavigateToOrders,
                onEmergencyStop = { showEmergencyDialog = true },
                onNavigateToContacts = onNavigateToContacts
            )

            // ── Section 6: Knowledge Base Empty Prompt ────────────────────────
            if (state.knowledgeChunkCount == 0) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "كتالوج وقاعدة بيانات المتجر فارغة",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "أضف منتجاتك أو استورد محادثات الواتساب القديمة لتدريب المساعد على الأسعار وسياسات الشحن.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = onNavigateToKnowledge,
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.heightIn(min = 48.dp),
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = "إضافة معرفة ومنتجات للمتجر", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // ── Section 7: Recent Activity ────────────────────────────────────
            if (state.recentActivity.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "آخر العمليات والردود (RECENT ACTIVITY)",
                        style = MaterialTheme.typography.labelSmall.copy(
                            letterSpacing = 0.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(
                        onClick = onNavigateToAuditLog,
                        modifier = Modifier.heightIn(min = 48.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "عرض الكل في سجل التدقيق",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(4.dp)) {
                        state.recentActivity.forEachIndexed { index, item ->
                            RecentActivityRow(
                                item = item,
                                onClick = { onNavigateToChat(item.contactName, item.sourceClass) }
                            )
                            if (index < state.recentActivity.lastIndex) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(1.dp)
                                        .padding(horizontal = 16.dp)
                                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        // ── Emergency Stop Confirmation Dialog ────────────────────────────────
        if (showEmergencyDialog) {
            EmergencyStopDialog(
                onConfirm = {
                    showEmergencyDialog = false
                    viewModel.emergencyKillSwitch()
                },
                onDismiss = { showEmergencyDialog = false }
            )
        }
    }
}
