package com.example.persona.features.settings.presentation.screens.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyOff
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.persona.R
import com.example.persona.features.settings.presentation.AiActions
import com.example.persona.features.settings.presentation.ProviderConfigState
import com.example.persona.features.settings.presentation.SettingsUiState
import com.example.persona.features.settings.presentation.VerificationState
import com.example.persona.features.settings.presentation.components.DebouncedTextField
import com.example.persona.features.settings.presentation.components.KeyRow
import com.example.persona.features.settings.presentation.components.LocalSettingFocus
import com.example.persona.features.settings.presentation.components.LocalUndoHost
import com.example.persona.features.settings.presentation.components.SettingFocusState
import com.example.persona.features.settings.presentation.components.SettingsGroup
import com.example.persona.features.settings.presentation.components.rememberReorderableColumnState
import com.example.persona.features.settings.presentation.components.reorderableColumnHandle
import com.example.persona.features.settings.presentation.components.reorderableColumnItem
import com.example.persona.features.settings.presentation.components.rows.NavRow
import com.example.persona.features.settings.presentation.components.settingFocus
import com.example.persona.features.settings.presentation.model.SettingKey
import com.example.persona.features.settings.presentation.sheets.AddApiKeySheet
import com.example.persona.features.settings.presentation.sheets.ModelsSheet
import com.example.persona.features.settings.presentation.sheets.ProviderGuideSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderDetailScreen(
    state: SettingsUiState,
    providerId: String,
    actions: AiActions,
    focus: SettingKey?,
    modifier: Modifier = Modifier,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val undo = LocalUndoHost.current
    val config = state.providerConfigs[providerId.lowercase()] ?: ProviderConfigState(provider = providerId)

    var showAddKeySheet by remember { mutableStateOf(false) }
    var showModelsSheet by remember { mutableStateOf(false) }
    var showGuideSheet by remember { mutableStateOf(false) }

    // SECURITY: Strictly volatile in memory, never rememberSaveable
    var revealedKeys by remember { mutableStateOf(emptySet<String>()) }
    val haptics = LocalHapticFeedback.current
    val reorderKeysState = rememberReorderableColumnState { from, to ->
        actions.moveApiKey(providerId, from, to)
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    CompositionLocalProvider(
        LocalSettingFocus provides remember(focus) { SettingFocusState(focus) }
    ) {
        Scaffold(
            modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            containerColor = MaterialTheme.colorScheme.surface,
            topBar = {
                LargeTopAppBar(
                    title = {
                        Column {
                            Text(providerId.uppercase())
                            Text(
                                text = stringResource(R.string.settings_ai_keys_count, config.keys.size),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = stringResource(R.string.common_back)
                            )
                        }
                    },
                    actions = {
                        if (config.keys.isNotEmpty()) {
                            IconButton(onClick = { actions.testAll(providerId) }) {
                                Icon(
                                    imageVector = Icons.Default.NetworkCheck,
                                    contentDescription = stringResource(R.string.settings_ai_test_all),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        IconButton(onClick = { showGuideSheet = true }) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = stringResource(R.string.guide_dialog_title),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    scrollBehavior = scrollBehavior
                )
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentAlignment = Alignment.TopCenter
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 720.dp)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .imePadding()
                        .navigationBarsPadding()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // Cloudflare Account ID (if applicable)
                    if (providerId.equals("cloudflare", ignoreCase = true)) {
                        SettingsGroup(title = R.string.settings_ai_cloudflare_account_id_title) {
                            Column(
                                modifier = Modifier
                                    .padding(16.dp)
                                    .settingFocus(SettingKey.CloudflareId)
                            ) {
                                Text(
                                    text = stringResource(R.string.settings_ai_cloudflare_account_id_subtitle),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                DebouncedTextField(
                                    value = state.cloudflareAccountId,
                                    onValueChange = actions::setCloudflareAccountId,
                                    label = stringResource(R.string.settings_ai_cloudflare_account_id_title),
                                    placeholder = "e.g. 0123456789abcdef0123456789abcdef",
                                    supportingText = { Text(stringResource(R.string.settings_ai_cloudflare_hint)) }
                                )
                            }
                        }
                    }

                    // API Keys Pool
                    SettingsGroup(title = R.string.settings_ai_keys_title) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .settingFocus(SettingKey.ApiKeys)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(R.string.settings_ai_keys_for_provider, providerId.uppercase()),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Button(
                                    onClick = { showAddKeySheet = true },
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Add,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(stringResource(R.string.common_add))
                                }
                            }

                            if (config.keys.isEmpty()) {
                                Card(
                                    shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(20.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.KeyOff,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(36.dp)
                                        )
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Text(
                                            text = stringResource(R.string.settings_ai_no_keys),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.height(14.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                            OutlinedButton(onClick = { showGuideSheet = true }) {
                                                Text(stringResource(R.string.settings_ai_provider_guide_btn))
                                            }
                                            Button(onClick = { showAddKeySheet = true }) {
                                                Text(stringResource(R.string.settings_ai_add_key_label))
                                            }
                                        }
                                    }
                                }
                            } else {
                                val canReorder = config.keys.size > 1
                                config.keys.forEachIndexed { index, apiKey ->
                                    val status = config.verificationStates[apiKey] ?: VerificationState.Idle
                                    val revealed = apiKey in revealedKeys

                                    KeyRow(
                                        index = index,
                                        apiKey = apiKey,
                                        status = status,
                                        revealed = revealed,
                                        canReorder = canReorder,
                                        onMoveUp = if (index > 0) { { actions.moveApiKeyUp(providerId, apiKey) } } else null,
                                        onMoveDown = if (index < config.keys.size - 1) { { actions.moveApiKeyDown(providerId, apiKey) } } else null,
                                        dragHandleModifier = Modifier.reorderableColumnHandle(
                                            state = reorderKeysState,
                                            key = apiKey,
                                            index = index,
                                            totalItems = config.keys.size,
                                            haptics = haptics
                                        ),
                                        onToggleReveal = {
                                            revealedKeys = if (revealed) revealedKeys - apiKey else revealedKeys + apiKey
                                        },
                                        onTest = { actions.testConnection(providerId, apiKey) },
                                        onDelete = {
                                            actions.removeApiKey(providerId, apiKey)
                                            // FRANKENSTEIN 2: Index-preserving key undo
                                            undo.offer(
                                                message = context.getString(R.string.settings_ai_key_deleted),
                                                actionLabel = context.getString(R.string.common_undo)
                                            ) {
                                                actions.addApiKeyAtIndex(providerId, apiKey, index)
                                            }
                                        },
                                        modifier = if (canReorder) {
                                            Modifier.reorderableColumnItem(
                                                state = reorderKeysState,
                                                key = apiKey,
                                                index = index,
                                                totalItems = config.keys.size,
                                                haptics = haptics
                                            )
                                        } else {
                                            Modifier
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Models & Priority Group
                    SettingsGroup(title = R.string.settings_ai_models_title) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .settingFocus(SettingKey.Models)
                        ) {
                            val primaryModel = config.models.firstOrNull().orEmpty()
                            val summary = if (config.models.isEmpty()) {
                                stringResource(R.string.settings_ai_models_summary_empty)
                            } else {
                                stringResource(R.string.settings_ai_models_summary, config.models.size, primaryModel)
                            }

                            NavRow(
                                title = stringResource(R.string.settings_ai_models_summary_sheet),
                                summary = summary,
                                onClick = { showModelsSheet = true }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddKeySheet) {
        AddApiKeySheet(
            provider = providerId,
            onAdd = { key, testImmediately ->
                actions.addApiKey(providerId, key)
                if (testImmediately) {
                    actions.testConnection(providerId, key)
                }
            },
            onDismiss = { showAddKeySheet = false }
        )
    }

    if (showModelsSheet) {
        ModelsSheet(
            provider = providerId,
            models = config.models,
            modelConfigs = config.modelConfigs,
            capabilityModelConfigs = config.capabilityModelConfigs,
            actions = actions,
            onDismiss = { showModelsSheet = false }
        )
    }

    if (showGuideSheet) {
        ProviderGuideSheet(
            provider = providerId,
            onDismiss = { showGuideSheet = false }
        )
    }
}
