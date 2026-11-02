package com.example.persona.features.settings.presentation.screens.ai

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.filled.Info
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
import com.example.persona.features.settings.domain.repository.ModelProviders
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

    val hasAnyKeyConfigured = keyCounts.values.any { it > 0 }
    val openRouterKeyCount = keyCounts[ModelProviders.OPENROUTER] ?: 0

    SpokeScaffold(
        spoke = SettingsSpoke.AiEngine,
        title = R.string.spoke_ai,
        onBack = onBack,
        focus = focus,
        modifier = modifier
    ) {
        if (!hasAnyKeyConfigured) {
            // Calm tonal banner if no keys at all are configured
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = 1.dp
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(R.string.settings_ai_no_primary_key_banner),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else if (openRouterKeyCount == 0) {
            // Smart Dependency Banner: If OpenRouter has no key configured, guide user on dependencies
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                tonalElevation = 1.dp
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = stringResource(R.string.settings_ai_openrouter_dependency_banner),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
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
            val allSupportedProviders = remember(state.providerOrder) {
                val ordered = state.providerOrder.filter { it in ModelProviders.ALL }
                val remaining = ModelProviders.ALL.filterNot { it in ordered }
                ordered + remaining
            }
            allSupportedProviders.forEach { provider ->
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
                                    color = MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Text(
                                        text = stringResource(R.string.settings_ai_key_required_badge),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        softWrap = false,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    },
                    supportingContent = {
                        val subText = buildString {
                            if (keyCount == 0) {
                                append(stringResource(R.string.settings_ai_no_key_registered))
                                append(" • ")
                                append(stringResource(R.string.settings_ai_key_required_badge))
                            } else {
                                append(stringResource(R.string.settings_ai_keys_count, keyCount))
                            }
                            if (!primaryModel.isNullOrBlank()) {
                                append(" • ")
                                append(primaryModel)
                            }
                        }
                        Text(
                            text = subText,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
            onOpenProvider = { provider ->
                showFallbackSheet = false
                onOpenProvider(provider)
            },
            onDismiss = { showFallbackSheet = false }
        )
    }
}
