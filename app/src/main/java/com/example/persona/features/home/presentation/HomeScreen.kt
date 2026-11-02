package com.example.persona.features.home.presentation

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.persona.R
import com.example.persona.core.util.PermissionHighlightHelper
import com.example.persona.features.home.presentation.components.*
import com.example.persona.features.onboarding.data.PermissionChecker
import com.example.persona.ui.components.PersonaSectionHeader
import com.example.persona.ui.components.personaScreenBackground
import com.example.persona.ui.theme.spacing

@OptIn(ExperimentalMaterial3Api::class)
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
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    // ── Permission State & Guided Flow Integration ────────────────────────────
    val permissionChecker = remember { PermissionChecker(context.applicationContext) }
    var permissionCheckTrigger by remember { mutableLongStateOf(0L) }

    val isContactsGranted = remember(permissionCheckTrigger) {
        permissionChecker.isContactsPermissionGranted(context)
    }
    val isPostNotifGranted = remember(permissionCheckTrigger) {
        permissionChecker.isPostNotificationsGranted()
    }
    val isStep1Granted = isContactsGranted && isPostNotifGranted

    val isStep2Granted = remember(permissionCheckTrigger) {
        permissionChecker.isNotificationListenerEnabled()
    }

    val isStep3Granted = remember(permissionCheckTrigger) {
        permissionChecker.isBatteryOptimizationIgnored()
    }

    val areAllPermissionsGranted = isStep1Granted && isStep2Granted && isStep3Granted

    val currentRequiredStep = when {
        !isStep1Granted -> 1
        !isStep2Granted -> 2
        !isStep3Granted -> 3
        else -> 0
    }

    var showGuidedPermissionSheet by remember {
        mutableStateOf(!permissionChecker.areCriticalPermissionsGranted(context))
    }
    var bannerDismissed by remember { mutableStateOf(false) }

    val runtimePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        permissionCheckTrigger = System.currentTimeMillis()
        viewModel.refreshSystemHealth()
    }

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
        permissionCheckTrigger = System.currentTimeMillis()
        viewModel.refreshSystemHealth()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .personaScreenBackground()
    ) {
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier.fillMaxSize()
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

                // ── Section 0: Polite Guided Permission Banner ─────────────────────
                if (!areAllPermissionsGranted && !bannerDismissed) {
                    item(key = "guided_permission_banner") {
                        GuidedPermissionBanner(
                            currentStep = currentRequiredStep,
                            onOpenGuide = { showGuidedPermissionSheet = true },
                            onDismiss = { bannerDismissed = true }
                        )
                    }
                }

                // ── Section 1: Hero Copilot Card (Health & Master Toggle Unified) ──
                item(key = "hero_copilot") {
                    HomeHeroCopilotCard(
                        isEnabled = state.isAutoReplyEnabled,
                        health = state.systemHealth,
                        onToggle = { shouldEnable ->
                            if (shouldEnable && !isStep2Granted) {
                                // Intercept toggle when Notification Listener is not granted
                                showGuidedPermissionSheet = true
                            } else {
                                viewModel.setAutoReplyEnabled(shouldEnable)
                            }
                        },
                        onOpenSettings = {
                            if (!isStep2Granted) {
                                PermissionHighlightHelper.openNotificationListenerSettingsWithHighlight(context)
                            } else {
                                viewModel.openNotificationSettings(context)
                            }
                        },
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

                // ── Section 3: Daily B2B Operational Metrics Flight Deck ──────────
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

                // ── Section 4: Quick Action Deck ──────────────────────────────────
                item(key = "quick_actions") {
                    QuickActionsSection(
                        onNavigateToKnowledge = onNavigateToKnowledge,
                        onNavigateToImport = onNavigateToImport,
                        onNavigateToOrders = onNavigateToOrders,
                        onNavigateToContacts = onNavigateToContacts
                    )
                }

                // ── Section 5: Knowledge Base Empty Prompt (Task 3: Generalized) ───
                if (state.knowledgeChunkCount == 0) {
                    item(key = "empty_knowledge") {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                            border = if (isDark) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = stringResource(R.string.home_empty_knowledge_title),
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = stringResource(R.string.home_empty_knowledge_desc),
                                    style = MaterialTheme.typography.bodySmall.copy(lineHeight = 20.sp),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(
                                    onClick = onNavigateToImport,
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

                // ── Section 5.1: Temporary Dev & Testing Controls ───
                item(key = "temp_dev_controls") {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f),
                        border = if (isDark) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.home_dev_tools_title),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )

                            // 1. زر إرسال إشعار تجريبي لاختبار الصوت
                            Button(
                                onClick = { viewModel.sendTestNotification() },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 44.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.NotificationsActive,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.home_btn_test_notification),
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }

                            // 2. زر توجل لإعادة فتح شاشات التهيئة (Onboarding)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        viewModel.toggleAlwaysShowOnboarding(!state.isAlwaysShowOnboardingEnabled)
                                    }
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.home_toggle_always_onboarding_title),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = stringResource(R.string.home_toggle_always_onboarding_desc),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Switch(
                                    checked = state.isAlwaysShowOnboardingEnabled,
                                    onCheckedChange = { viewModel.toggleAlwaysShowOnboarding(it) }
                                )
                            }
                        }
                    }
                }

                // ── Section 6: Recent Activity ────────────────────────────────────
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
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                            color = if (isDark) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surfaceContainerHigh,
                            border = if (isDark) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null
                        ) {
                            Column(modifier = Modifier.padding(4.dp)) {
                                state.recentActivity.forEachIndexed { index, item ->
                                    RecentActivityRow(
                                        item = item,
                                        onClick = { onNavigateToChat(item.contactName, item.sourceClass) }
                                    )
                                    if (index < state.recentActivity.lastIndex) {
                                        HorizontalDivider(
                                            modifier = Modifier.padding(horizontal = 16.dp),
                                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
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
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = spacing.lg)
        )

        // ── Sequential Guided Permission Bottom Sheet ─────────────────────────
        if (showGuidedPermissionSheet) {
            ModalBottomSheet(
                onDismissRequest = { showGuidedPermissionSheet = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
            ) {
                GuidedPermissionSheetContent(
                    isStep1Granted = isStep1Granted,
                    isStep2Granted = isStep2Granted,
                    isStep3Granted = isStep3Granted,
                    areAllGranted = areAllPermissionsGranted,
                    onRequestStep1 = {
                        val permissions = buildList {
                            add(Manifest.permission.READ_CONTACTS)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                add(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }.toTypedArray()
                        runtimePermissionLauncher.launch(permissions)
                    },
                    onRequestStep2 = {
                        PermissionHighlightHelper.openNotificationListenerSettingsWithHighlight(context)
                    },
                    onRequestStep3 = {
                        PermissionHighlightHelper.openBatteryOptimizationSettings(context)
                    },
                    onDismiss = { showGuidedPermissionSheet = false }
                )
            }
        }
    }
}

/**
 * Polite, non-intrusive setup banner displayed on Home when critical permissions are missing.
 */
@Composable
private fun GuidedPermissionBanner(
    currentStep: Int,
    onOpenGuide: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val stepTitle = when (currentStep) {
        1 -> stringResource(R.string.home_permission_banner_step1_desc)
        2 -> stringResource(R.string.home_permission_banner_step2_desc)
        3 -> stringResource(R.string.home_permission_banner_step3_desc)
        else -> stringResource(R.string.home_permission_banner_step_complete_desc)
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = if (isDark) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, if (isDark) MaterialTheme.colorScheme.outlineVariant else MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.home_permission_banner_step_prefix, currentStep),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stepTitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Button(
                onClick = onOpenGuide,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.heightIn(min = 36.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text(text = stringResource(R.string.home_permission_banner_btn_continue), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
            }

            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.home_permission_banner_close_desc),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/**
 * 3-Step Sequential Guided Permission Bottom Sheet Content.
 */
@Composable
private fun GuidedPermissionSheetContent(
    isStep1Granted: Boolean,
    isStep2Granted: Boolean,
    isStep3Granted: Boolean,
    areAllGranted: Boolean,
    onRequestStep1: () -> Unit,
    onRequestStep2: () -> Unit,
    onRequestStep3: () -> Unit,
    onDismiss: () -> Unit
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = stringResource(R.string.home_permission_sheet_title),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                Icon(imageVector = Icons.Default.Close, contentDescription = stringResource(R.string.home_permission_banner_close_desc))
            }
        }

        Text(
            text = stringResource(R.string.home_permission_sheet_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Step 1: Runtime Permissions
        GuidedStepCard(
            stepNumber = 1,
            title = stringResource(R.string.home_permission_step1_title),
            description = stringResource(R.string.home_permission_step1_desc),
            icon = Icons.Default.Contacts,
            isCompleted = isStep1Granted,
            isActive = !isStep1Granted,
            buttonText = stringResource(R.string.home_permission_step1_btn),
            onAction = onRequestStep1,
            isDark = isDark
        )

        // Step 2: Notification Listener with Highlight
        GuidedStepCard(
            stepNumber = 2,
            title = stringResource(R.string.home_permission_step2_title),
            description = stringResource(R.string.home_permission_step2_desc),
            icon = Icons.Default.NotificationsActive,
            isCompleted = isStep2Granted,
            isActive = isStep1Granted && !isStep2Granted,
            buttonText = stringResource(R.string.home_permission_step2_btn),
            onAction = onRequestStep2,
            isDark = isDark
        )

        // Step 3: Battery Optimization Exemption
        GuidedStepCard(
            stepNumber = 3,
            title = stringResource(R.string.home_permission_step3_title),
            description = stringResource(R.string.home_permission_step3_desc),
            icon = Icons.Default.BatteryChargingFull,
            isCompleted = isStep3Granted,
            isActive = isStep2Granted && !isStep3Granted,
            buttonText = stringResource(R.string.home_permission_step3_btn),
            onAction = onRequestStep3,
            isDark = isDark
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Completion or Dismiss Action
        if (areAllGranted) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF10B981).copy(alpha = 0.15f),
                border = BorderStroke(1.dp, Color(0xFF10B981))
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF10B981),
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = stringResource(R.string.home_permission_all_granted_title),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text(text = stringResource(R.string.home_permission_btn_start_now), fontWeight = FontWeight.Bold)
            }
        } else {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.home_permission_btn_later),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun GuidedStepCard(
    stepNumber: Int,
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isCompleted: Boolean,
    isActive: Boolean,
    buttonText: String,
    onAction: () -> Unit,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    val emeraldGreen = Color(0xFF10B981)
    val cardColor = when {
        isCompleted -> if (isDark) Color(0xFF0F291E) else Color(0xFFECFDF5)
        isActive -> if (isDark) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surfaceContainerHigh
        else -> if (isDark) Color(0xFF161F2E).copy(alpha = 0.6f) else Color(0xFFF8FAFC)
    }

    val borderColor = when {
        isCompleted -> emeraldGreen.copy(alpha = 0.5f)
        isActive -> MaterialTheme.colorScheme.primary
        else -> if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0)
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = cardColor,
        border = BorderStroke(if (isActive) 1.5.dp else 1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Step Indicator Badge
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                isCompleted -> emeraldGreen
                                isActive -> MaterialTheme.colorScheme.primary
                                else -> if (isDark) Color(0xFF334155) else Color(0xFFCBD5E1)
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isCompleted) {
                        Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    } else {
                        Text(
                            text = "$stepNumber",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = if (isActive) Color.White else Color(0xFF64748B)
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (isCompleted) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = emeraldGreen.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = stringResource(R.string.home_permission_status_completed),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = emeraldGreen,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            if (isActive && !isCompleted) {
                Button(
                    onClick = onAction,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 44.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = buttonText, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(imageVector = Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}
