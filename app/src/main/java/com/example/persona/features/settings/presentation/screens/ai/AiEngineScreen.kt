package com.example.persona.features.settings.presentation.screens.ai

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.persona.R
import com.example.persona.features.settings.presentation.AiActions
import com.example.persona.features.settings.presentation.SettingsSpoke
import com.example.persona.features.settings.presentation.SettingsUiState
import com.example.persona.features.settings.presentation.components.ProviderChainStrip
import com.example.persona.features.settings.presentation.components.ProviderMonogram
import com.example.persona.features.settings.presentation.components.SettingsGroup
import com.example.persona.features.settings.presentation.components.SpokeScaffold
import com.example.persona.features.settings.presentation.components.settingFocus
import com.example.persona.features.settings.presentation.model.SettingKey
import com.example.persona.features.settings.presentation.sheets.ProviderPrioritySheet

@Composable
fun AiEngineScreen(
    state: SettingsUiState,
    actions: AiActions,
    onBack: () -> Unit,
    onOpenProvider: (String) -> Unit,
    modifier: Modifier = Modifier,
    focus: SettingKey? = null
) {
    var showFallbackSheet by remember { mutableStateOf(false) }
    val keyCounts = remember(state.providerConfigs) {
        state.providerConfigs.mapValues { it.value.keys.size }
    }

    val primaryProvider = state.providerOrder.firstOrNull()
    val primaryKeyCount = primaryProvider?.let { keyCounts[it.lowercase()] ?: 0 } ?: 0
    val isNoKeysConfigured = primaryKeyCount == 0

    SpokeScaffold(
        spoke = SettingsSpoke.AiEngine,
        title = R.string.spoke_ai,
        onBack = onBack,
        focus = focus,
        modifier = modifier
    ) {
        // Informative banner if active primary provider has no keys configured
        if (isNoKeysConfigured) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f),
                tonalElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.WarningAmber,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = stringResource(R.string.settings_ai_no_primary_key_banner),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }

        // Fallback Chain Strip
        ProviderChainStrip(
            providerOrder = state.providerOrder,
            keyCounts = keyCounts,
            onOpenReorder = { showFallbackSheet = true },
            modifier = Modifier.settingFocus(SettingKey.ProviderChain)
        )

        // Providers Section
        SettingsGroup(title = R.string.settings_ai_providers_title) {
            state.providerOrder.forEach { provider ->
                val config = state.providerConfigs[provider.lowercase()]
                val keyCount = config?.keys?.size ?: 0
                val primaryModel = config?.models?.firstOrNull()

                ListItem(
                    leadingContent = {
                        ProviderMonogram(provider = provider)
                    },
                    headlineContent = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = provider.uppercase(),
                                fontWeight = FontWeight.Bold
                            )
                            if (keyCount == 0) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f)
                                ) {
                                    Text(
                                        text = stringResource(R.string.settings_ai_key_required_badge),
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    },
                    supportingContent = {
                        val subText = buildString {
                            append(
                                if (keyCount == 0) {
                                    stringResource(R.string.settings_ai_keys_count, 0)
                                } else {
                                    stringResource(R.string.settings_ai_keys_count, keyCount)
                                }
                            )
                            if (keyCount == 0) {
                                append(" • ")
                                append(stringResource(R.string.settings_ai_key_required_badge))
                            }
                            if (!primaryModel.isNullOrBlank()) {
                                append(" • ")
                                append(primaryModel)
                            }
                        }
                        Text(
                            text = subText,
                            color = if (keyCount == 0)
                                MaterialTheme.colorScheme.error
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    trailingContent = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable { onOpenProvider(provider) }
                )
            }
        }
    }

    if (showFallbackSheet) {
        ProviderPrioritySheet(
            selectedCapability = state.selectedCapabilityFilter,
            onSelectCapability = actions::selectCapabilityFilter,
            order = state.currentFilteredProviderOrder,
            keyCounts = keyCounts,
            onMoveUp = actions::moveProviderUp,
            onMoveDown = actions::moveProviderDown,
            onMove = actions::moveProvider,
            onMoveForCapability = actions::moveProviderForCapability,
            onDismiss = { showFallbackSheet = false }
        )
    }
}
