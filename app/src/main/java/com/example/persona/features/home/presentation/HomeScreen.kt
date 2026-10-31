package com.example.persona.features.home.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.persona.R
import com.example.persona.features.home.presentation.components.*
import com.example.persona.ui.components.PersonaSectionHeader
import com.example.persona.ui.components.personaScreenBackground
import com.example.persona.ui.theme.spacing

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
    val snackbarHostState = remember { SnackbarHostState() }
    val spacing = MaterialTheme.spacing

    LaunchedEffect(viewModel) {
        viewModel.uiEvents.collect { event ->
            when (event) {
                is HomeUiEvent.ShowSnackbar -> {
                    snackbarHostState.showSnackbar(event.message)
                }
            }
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshSystemHealth()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .personaScreenBackground()
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = spacing.xl, vertical = spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.lg)
        ) {
            // ── Top Bar / Header ──────────────────────────────────────────────
            item(key = "header") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.home_dashboard_title),
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = stringResource(R.string.home_dashboard_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = onNavigateToSettings,
                        modifier = Modifier.size(spacing.touchTargetMin)
                    ) {
                        Icon(imageVector = Icons.Default.Settings, contentDescription = stringResource(R.string.nav_settings))
                    }
                }
            }

            // ── Section 1: System Health Diagnostic Banner ────────────────────
            item(key = "system_health") {
                SystemHealthCard(
                    health = state.systemHealth,
                    onOpenSettings = { viewModel.openNotificationSettings(context) },
                    onRestartService = { viewModel.restartNotificationListenerService(context) }
                )
            }

            // ── Section 2: Human Takeover (HITL) Alert Center ─────────────────
            if (state.pendingAttentionCount > 0) {
                item(key = "urgent_attention") {
                    UrgentAttentionBanner(
                        pendingCount = state.pendingAttentionCount,
                        waitingCustomers = state.waitingCustomers,
                        onCustomerClick = onNavigateToChat
                    )
                }
            }

            // ── Section 3: Master Toggle ──────────────────────────────────────
            item(key = "master_toggle") {
                AutoReplyMasterToggleCard(
                    isEnabled = state.isAutoReplyEnabled,
                    onToggle = { viewModel.setAutoReplyEnabled(it) }
                )
            }

            // ── Section 4: Daily B2B Operational Metrics Flight Deck ──────────
            item(key = "metrics_header") {
                PersonaSectionHeader(
                    title = stringResource(R.string.daily_metrics_title)
                )
            }

            item(key = "metrics_hub") {
                B2BOperationalMetricsHub(
                    todayOrders = state.todayOrdersCount,
                    pendingRevenue = state.todayPendingRevenue,
                    waitingCount = state.waitingCustomersCount,
                    todayReplies = state.todayRepliesCount,
                    totalReplies = state.totalRepliesSent
                )
            }

            // ── Section 5: Quick Action Deck ──────────────────────────────────
            item(key = "quick_actions") {
                QuickActionsSection(
                    onNavigateToKnowledge = onNavigateToKnowledge,
                    onNavigateToImport = onNavigateToImport,
                    onNavigateToOrders = onNavigateToOrders,
                    onNavigateToContacts = onNavigateToContacts
                )
            }

            // ── Section 6: Knowledge Base Empty Prompt ────────────────────────
            if (state.knowledgeChunkCount == 0) {
                item(key = "empty_knowledge") {
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
                                text = stringResource(R.string.home_empty_knowledge_title),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = stringResource(R.string.home_empty_knowledge_desc),
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
                                Text(text = stringResource(R.string.home_btn_add_knowledge), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // ── Section 7: Recent Activity ────────────────────────────────────
            if (state.recentActivity.isNotEmpty()) {
                item(key = "recent_activity_header") {
                    PersonaSectionHeader(
                        title = stringResource(R.string.home_recent_activity_title),
                        action = {
                            TextButton(
                                onClick = onNavigateToAuditLog,
                                modifier = Modifier.heightIn(min = 48.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.home_view_all_audit_log),
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        }
                    )
                }

                item(key = "recent_activity_card") {
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
            }

            item(key = "bottom_spacer") {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = spacing.lg)
        )
    }
}
