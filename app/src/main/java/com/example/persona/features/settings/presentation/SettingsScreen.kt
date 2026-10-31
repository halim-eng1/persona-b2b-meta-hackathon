package com.example.persona.features.settings.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.persona.R
import com.example.persona.features.onboarding.data.AutoStartHelper
import com.example.persona.features.onboarding.presentation.PermissionIntentDispatcher
import com.example.persona.features.settings.presentation.components.AiEnginesSection
import com.example.persona.features.settings.presentation.components.AppRoutingSection
import com.example.persona.features.settings.presentation.components.BusinessHoursSection
import com.example.persona.features.settings.presentation.components.StoreIdentitySection
import com.example.persona.features.settings.presentation.components.SystemMaintenanceSection

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val intentDispatcher = remember(context) {
        PermissionIntentDispatcher(AutoStartHelper(context.applicationContext))
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshStoragePermission()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val scrollState = rememberScrollState()
    LaunchedEffect(state.selectedTab) {
        scrollState.scrollTo(0)
    }

    val surfaceVariantColor = MaterialTheme.colorScheme.surfaceVariant
    val backgroundColor = MaterialTheme.colorScheme.background
    val backgroundBrush = remember(surfaceVariantColor, backgroundColor) {
        Brush.verticalGradient(
            colors = listOf(
                surfaceVariantColor.copy(alpha = 0.25f),
                backgroundColor
            )
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundBrush)
    ) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            // ── Top Bar Header ────────────────────────────────────────────────
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        modifier = Modifier
                            .widthIn(max = 720.dp)
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.settings_title),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = stringResource(R.string.settings_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // ── Categorized Tabs ──────────────────────────────────────────────
            val tabs = listOf(
                stringResource(R.string.settings_tab_ai) to Icons.Default.Psychology,
                stringResource(R.string.settings_tab_identity) to Icons.Default.Store,
                stringResource(R.string.settings_tab_hours) to Icons.Default.Schedule,
                stringResource(R.string.settings_tab_routing) to Icons.Default.Chat,
                stringResource(R.string.settings_tab_maintenance) to Icons.Default.Build
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                ScrollableTabRow(
                    selectedTabIndex = state.selectedTab,
                    edgePadding = 16.dp,
                    containerColor = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .widthIn(max = 720.dp)
                        .fillMaxWidth()
                ) {
                    tabs.forEachIndexed { index, (label, icon) ->
                        Tab(
                            selected = state.selectedTab == index,
                            onClick = { viewModel.selectTab(index) },
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(label, fontSize = 13.sp, fontWeight = if (state.selectedTab == index) FontWeight.Bold else FontWeight.Normal)
                                }
                            }
                        )
                    }
                }
            }

            // ── Main Content Area ─────────────────────────────────────────────
            AnimatedVisibility(visible = !state.isLoading, enter = fadeIn(), exit = fadeOut()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .imePadding()
                        .navigationBarsPadding(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Column(
                        modifier = Modifier
                            .widthIn(max = 720.dp)
                            .fillMaxWidth()
                            .fillMaxHeight()
                            .verticalScroll(scrollState)
                            .padding(horizontal = 20.dp, vertical = 16.dp)
                    ) {
                    when (state.selectedTab) {
                        0 -> {
                            // Section 1: AI Engines, Keys & Models
                            AiEnginesSection(
                                state = state,
                                onMoveProviderUp = { viewModel.moveProviderUp(it) },
                                onMoveProviderDown = { viewModel.moveProviderDown(it) },
                                onSelectProvider = { viewModel.selectProviderForEdit(it) },
                                onAddApiKey = { provider, key -> viewModel.addApiKey(provider, key) },
                                onRemoveApiKey = { provider, key -> viewModel.removeApiKey(provider, key) },
                                onTestApiKey = { provider, key -> viewModel.testConnection(provider, key) },
                                onAddProviderModel = { provider, model -> viewModel.addProviderModel(provider, model) },
                                onRemoveProviderModel = { provider, model -> viewModel.removeProviderModel(provider, model) },
                                onMoveModelUp = { provider, model -> viewModel.moveModelUp(provider, model) },
                                onMoveModelDown = { provider, model -> viewModel.moveModelDown(provider, model) },
                                onSaveCloudflareAccountId = { viewModel.setCloudflareAccountId(it) }
                            )
                        }
                        1 -> {
                            // Section 2: Store Identity & Bot Persona
                            StoreIdentitySection(
                                state = state,
                                onLanguageChange = { viewModel.setAppLanguage(it) },
                                onThemeModeChange = { viewModel.setThemeMode(it) },
                                onBotNameChange = { viewModel.setBotName(it) },
                                onStoreNameChange = { viewModel.setStoreName(it) },
                                onPersonaToneChange = { viewModel.setPersonaTone(it) },
                                onThresholdChange = { viewModel.setThresholdLevel(it) },
                                onCooldownChange = { viewModel.setCooldownMins(it) },
                                onReplyDelayChange = { viewModel.setReplyDelayInput(it) },
                                onGreetingTemplateChange = { viewModel.setGreetingTemplate(it) },
                                onFarewellTemplateChange = { viewModel.setFarewellTemplate(it) }
                            )
                        }
                        2 -> {
                            // Section 3: Business Hours & Blacklist
                            BusinessHoursSection(
                                state = state,
                                onBusinessHoursEnabledChange = { viewModel.setBusinessHoursEnabled(it) },
                                onWorkingDaysChange = { viewModel.setWorkingDays(it) },
                                onWorkingHoursChange = { start, end -> viewModel.setWorkingHours(start, end) },
                                onOutOfHoursMessageChange = { viewModel.setOutOfHoursMessage(it) },
                                onIgnoreGroupsChange = { viewModel.setIgnoreWhatsAppGroups(it) },
                                onAddBlacklistNumber = { viewModel.addBlacklistedNumber(it) },
                                onRemoveBlacklistNumber = { viewModel.removeBlacklistedNumber(it) }
                            )
                        }
                        3 -> {
                            // Section 4: WhatsApp App Routing & HITL
                            AppRoutingSection(
                                state = state,
                                onListenMainAppChange = { viewModel.setListenMainApp(it) },
                                onListenBusinessAppChange = { viewModel.setListenBusinessApp(it) },
                                onListenCloneAppChange = { viewModel.setListenCloneApp(it) },
                                onRequestStoragePermission = { intentDispatcher.openAllFilesAccessSettings(context) },
                                onAutoPauseOnHitlChange = { viewModel.onAutoPauseOnHitlToggled(it) },
                                onHitlHoldingMessageChange = { viewModel.setHitlHoldingMessage(it) },
                                onMediaFallbackMessageChange = { viewModel.setMediaFallbackMessage(it) },
                                onBlankReplyFallbackChange = { viewModel.setBlankReplyFallbackTemplate(it) }
                            )
                        }
                        4 -> {
                            // Section 5: System Maintenance & Health
                            SystemMaintenanceSection(
                                state = state,
                                onRefreshHealth = { viewModel.refreshStoragePermission() },
                                onExportBackup = { viewModel.exportLocalBackup() },
                                onRestoreBackup = { viewModel.restoreLocalBackup() },
                                onClearCache = { viewModel.clearCache() }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(36.dp))
                }
            }
        }
    }
}
}
