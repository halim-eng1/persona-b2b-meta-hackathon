package com.example.persona.features.settings.presentation.screens.ai

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.example.persona.features.settings.presentation.sheets.FallbackOrderSheet

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

    SpokeScaffold(
        spoke = SettingsSpoke.AiEngine,
        title = R.string.spoke_ai,
        onBack = onBack,
        focus = focus,
        modifier = modifier
    ) {
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
                        Text(
                            text = provider.uppercase(),
                            fontWeight = FontWeight.Bold
                        )
                    },
                    supportingContent = {
                        val subText = buildString {
                            append(
                                if (keyCount == 0) {
                                    stringResource(R.string.settings_ai_no_keys_warning)
                                } else {
                                    stringResource(R.string.settings_ai_keys_count, keyCount)
                                }
                            )
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
        FallbackOrderSheet(
            order = state.providerOrder,
            keyCounts = keyCounts,
            onMoveUp = actions::moveProviderUp,
            onMoveDown = actions::moveProviderDown,
            onDismiss = { showFallbackSheet = false }
        )
    }
}
