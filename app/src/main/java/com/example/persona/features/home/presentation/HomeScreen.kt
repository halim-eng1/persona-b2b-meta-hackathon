package com.example.persona.features.home.presentation

import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Build
import android.service.notification.NotificationListenerService
import android.widget.Toast
import com.example.persona.features.auto_reply.data.service.WhatsAppNotificationListener
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.persona.R
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

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshSystemHealth()
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
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(imageVector = Icons.Default.Settings, contentDescription = stringResource(R.string.nav_settings))
                }
            }

            // ── Section 1: System Health Diagnostic Banner ────────────────────
            SystemHealthCard(
                health = state.systemHealth,
                onOpenSettings = { viewModel.openNotificationSettings(context) },
                onRestartService = {
                    if (!viewModel.isNotificationPermissionGranted()) {
                        viewModel.openNotificationSettings(context)
                    } else {
                        try {
                            val componentName = ComponentName(
                                context,
                                WhatsAppNotificationListener::class.java
                            )
                            val pm = context.packageManager
                            pm.setComponentEnabledSetting(
                                componentName,
                                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                                PackageManager.DONT_KILL_APP
                            )
                            pm.setComponentEnabledSetting(
                                componentName,
                                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                                PackageManager.DONT_KILL_APP
                            )
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                NotificationListenerService.requestRebind(componentName)
                            }
                            viewModel.restartService()
                            viewModel.refreshSystemHealth()
                            Toast.makeText(
                                context,
                                "تمت إعادة تشغيل خدمة الإشعارات بنجاح",
                                Toast.LENGTH_SHORT
                            ).show()
                        } catch (e: Exception) {
                            Toast.makeText(
                                context,
                                "تعذر إعادة تشغيل الخدمة: ${e.message}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
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
                text = stringResource(R.string.daily_metrics_title),
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

            // ── Section 7: Recent Activity ────────────────────────────────────
            if (state.recentActivity.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.home_recent_activity_title),
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
                            text = stringResource(R.string.home_view_all_audit_log),
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

    }
}
