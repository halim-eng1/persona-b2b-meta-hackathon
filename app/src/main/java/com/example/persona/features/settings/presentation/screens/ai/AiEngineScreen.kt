package com.example.persona.features.settings.presentation.screens.ai

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.persona.R
import com.example.persona.features.settings.domain.repository.ModelProviders
import com.example.persona.features.settings.presentation.AiActions
import com.example.persona.features.settings.presentation.SettingsSpoke
import com.example.persona.features.settings.presentation.SettingsUiState
import com.example.persona.features.settings.presentation.components.ProviderMonogram
import com.example.persona.features.settings.presentation.components.SettingsGroup
import com.example.persona.features.settings.presentation.components.SpokeScaffold
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
    val metaKeyCount = keyCounts[ModelProviders.META] ?: 0

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
        } else if (metaKeyCount > 0) {
            // Meta AI Active Banner: Reassuring message indicating full Text/Vision/Audio coverage & on-device decision fallback
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                tonalElevation = 1.dp
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = stringResource(R.string.settings_ai_meta_active_banner),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        // Meta AI Prominent Card (BYOK for Judges)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ProviderMonogram(provider = ModelProviders.META, size = 40.dp)
                        Column {
                            Text(
                                text = stringResource(R.string.settings_ai_meta_card_title),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = stringResource(R.string.settings_ai_meta_card_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (metaKeyCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = if (metaKeyCount > 0) {
                                stringResource(R.string.settings_ai_meta_active_badge)
                            } else {
                                stringResource(R.string.settings_ai_key_required_badge)
                            },
                            color = if (metaKeyCount > 0) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                var metaInputKey by remember { mutableStateOf("") }
                var isMetaKeyVisible by remember { mutableStateOf(false) }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = metaInputKey,
                        onValueChange = { metaInputKey = it },
                        modifier = Modifier.weight(1f),
                        placeholder = {
                            Text(
                                text = if (metaKeyCount > 0) "••••••••••••••••" else stringResource(R.string.settings_ai_meta_key_hint),
                                style = MaterialTheme.typography.bodySmall
                            )
                        },
                        visualTransformation = if (isMetaKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { isMetaKeyVisible = !isMetaKeyVisible }) {
                                Icon(
                                    imageVector = if (isMetaKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall
                    )

                    Button(
                        onClick = {
                            if (metaInputKey.isNotBlank()) {
                                actions.addApiKey(ModelProviders.META, metaInputKey.trim())
                                metaInputKey = ""
                            }
                        },
                        enabled = metaInputKey.isNotBlank()
                    ) {
                        Text(stringResource(R.string.settings_ai_meta_save_key))
                    }
                }
            }
        }

        // Fallback Chain Strip
        // Hidden: the active provider cascade is still enforced by the runtime, but the
        // reorder UI is intentionally kept out of the screen to reduce configuration noise.
        /*
        ProviderChainStrip(
            providerOrder = state.providerOrder,
            keyCounts = keyCounts,
            onOpenReorder = { showFallbackSheet = true },
            modifier = Modifier.settingFocus(SettingKey.ProviderChain)
        )
        */

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
