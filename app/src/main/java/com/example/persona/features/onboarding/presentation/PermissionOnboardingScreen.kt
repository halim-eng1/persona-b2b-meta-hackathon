package com.example.persona.features.onboarding.presentation

import com.example.persona.R
import androidx.compose.ui.res.stringResource

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.persona.features.onboarding.data.AutoStartHelper
import com.example.persona.features.onboarding.domain.model.OnboardingStep

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionOnboardingScreen(
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PermissionOnboardingViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val state by viewModel.permissionState.collectAsStateWithLifecycle()
    val wizardState by viewModel.wizardState.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()
    val intentDispatcher = remember(context) {
        PermissionIntentDispatcher(AutoStartHelper(context.applicationContext))
    }

    // Refresh permissions whenever returning to screen from system settings
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Post notification runtime permission launcher (Android 13+)
    val postNotificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        viewModel.refreshPermissions()
    }

    // Contacts runtime permission launcher
    val contactsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        viewModel.refreshPermissions()
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.onboarding_title),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                            MaterialTheme.colorScheme.background
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // ── Linear Wizard Progress ────────────────────────────────────
                val progress = if (wizardState.totalSteps > 0) {
                    ((wizardState.currentStepIndex + 1).toFloat() / wizardState.totalSteps.toFloat()).coerceIn(0f, 1f)
                } else 0f

                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.onboarding_progress_indicator),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = stringResource(R.string.onboarding_step_of_total, wizardState.currentStepIndex + 1, wizardState.totalSteps),
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.outlineVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = stringResource(R.string.onboarding_intro_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // ── Android 13+ Restricted Settings Warning Card ───────────────
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !state.isNotificationListenerGranted) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.onboarding_android13_title),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.onboarding_android13_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                lineHeight = 18.sp
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedButton(
                                onClick = { intentDispatcher.openAppDetailsForRestrictedSettings(context) },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(stringResource(R.string.onboarding_open_app_info_btn))
                            }
                        }
                    }
                }

                // ── Step 1: Notification Access (Always Required) ─────────────
                PermissionCard(
                    title = stringResource(R.string.onboarding_perm_notif_title),
                    description = stringResource(R.string.onboarding_perm_notif_desc),
                    isGranted = state.isNotificationListenerGranted,
                    isActiveStep = wizardState.currentStep is OnboardingStep.NotificationAccess,
                    onGrantClick = {
                        intentDispatcher.openNotificationListenerSettings(context)
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // ── Step 2: Ignore Battery Optimizations (Always Required) ────
                PermissionCard(
                    title = stringResource(R.string.onboarding_perm_battery_title),
                    description = stringResource(R.string.onboarding_perm_battery_desc),
                    isGranted = state.isBatteryOptimizationIgnored,
                    isActiveStep = wizardState.currentStep is OnboardingStep.BatteryOptimization,
                    onGrantClick = {
                        intentDispatcher.openBatteryOptimizationSettings(context)
                    }
                )

                // ── Step 3: POST_NOTIFICATIONS (Platform requires it on API 33+) ─
                if (state.isPostNotificationsRequired) {
                    Spacer(modifier = Modifier.height(16.dp))
                    PermissionCard(
                        title = stringResource(R.string.onboarding_perm_post_notif_title),
                        description = stringResource(R.string.onboarding_perm_post_notif_desc),
                        isGranted = state.isPostNotificationsGranted,
                        isActiveStep = wizardState.currentStep is OnboardingStep.PostNotifications,
                        onGrantClick = {
                            postNotificationLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        }
                    )
                }

                // ── Step 4: Contacts Access (Phone Resolution & CRM Linking) ──
                Spacer(modifier = Modifier.height(16.dp))
                PermissionCard(
                    title = stringResource(R.string.onboarding_perm_contacts_title),
                    description = stringResource(R.string.onboarding_perm_contacts_desc),
                    isGranted = state.isContactsPermissionGranted,
                    isActiveStep = false,
                    onGrantClick = {
                        contactsPermissionLauncher.launch(android.Manifest.permission.READ_CONTACTS)
                    }
                )

                // ── Step 4: AutoStart Configuration (Chinese / OEM ROMs) ───────
                if (state.isAutoStartEnabled != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (state.isAutoStartEnabled == true) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            }
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = stringResource(R.string.onboarding_autostart_title, viewModel.getManufacturerName()),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (state.isAutoStartEnabled == true) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Configured",
                                        tint = Color(0xFF4CAF50),
                                        modifier = Modifier.size(24.dp)
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = "Pending confirmation",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.onboarding_autostart_desc, viewModel.getManufacturerName()),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 18.sp
                            )
                            Spacer(modifier = Modifier.height(16.dp))

                            Button(
                                onClick = { viewModel.launchAutoStart() },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondary
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(stringResource(R.string.onboarding_open_autostart_btn))
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Checkbox(
                                    checked = state.isAutoStartEnabled == true,
                                    onCheckedChange = { isChecked ->
                                        viewModel.toggleAutoStartConfirmed(isChecked)
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.onboarding_autostart_checkbox),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(36.dp))

                // ── Bottom Action: Finish / Enable Auto-Reply ─────────────────
                Button(
                    onClick = {
                        viewModel.onAllPermissionsGranted()
                        onComplete()
                    },
                    enabled = wizardState.canProceed,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    contentPadding = PaddingValues(vertical = 16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 32.dp)
                ) {
                    Text(
                        text = if (wizardState.canProceed) stringResource(R.string.onboarding_start_engine_btn) else stringResource(R.string.onboarding_complete_perms_warning),
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionCard(
    title: String,
    description: String,
    isGranted: Boolean,
    isActiveStep: Boolean = false,
    onGrantClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isGranted -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                isActiveStep -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            }
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isGranted) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                if (isGranted) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Granted",
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.size(24.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Missing",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp
            )
            if (!isGranted) {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onGrantClick,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.onboarding_grant_now_btn))
                }
            }
        }
    }
}
