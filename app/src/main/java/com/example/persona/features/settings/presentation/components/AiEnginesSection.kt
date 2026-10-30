package com.example.persona.features.settings.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
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
import androidx.compose.ui.unit.sp
import com.example.persona.R
import com.example.persona.features.settings.domain.repository.ModelProviders
import com.example.persona.features.settings.presentation.ProviderConfigState
import com.example.persona.features.settings.presentation.SettingsUiState
import com.example.persona.features.settings.presentation.VerificationState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AiEnginesSection(
    state: SettingsUiState,
    onMoveProviderUp: (String) -> Unit,
    onMoveProviderDown: (String) -> Unit,
    onSelectProvider: (String) -> Unit,
    onAddApiKey: (String, String) -> Unit,
    onRemoveApiKey: (String, String) -> Unit,
    onTestApiKey: (String, String) -> Unit,
    onAddProviderModel: (String, String) -> Unit,
    onRemoveProviderModel: (String, String) -> Unit,
    onMoveModelUp: (String, String) -> Unit,
    onMoveModelDown: (String, String) -> Unit,
    modifier: Modifier = Modifier,
    onSaveCloudflareAccountId: (String) -> Unit = {}
) {
    val selectedProvider = state.selectedProviderForEdit
    val config = state.providerConfigs[selectedProvider] ?: ProviderConfigState(provider = selectedProvider)

    var newKeyInput by remember(selectedProvider) { mutableStateOf("") }
    var newModelInput by remember(selectedProvider) { mutableStateOf("") }
    val visibleKeys = remember { mutableStateMapOf<String, Boolean>() }
    var showGuideDialogForProvider by remember { mutableStateOf<String?>(null) }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // ── 1. Provider Priority Chain ─────────────────────────────────────────
        PreferenceSection(
            title = stringResource(R.string.settings_ai_priority_title),
            subtitle = stringResource(R.string.settings_ai_priority_subtitle)
        ) {
            state.providerOrder.forEachIndexed { index, provider ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (provider == selectedProvider)
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                        else
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectProvider(provider) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "${index + 1}",
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = provider.uppercase(),
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                val keyCount = state.providerConfigs[provider]?.keys?.size ?: 0
                                Text(
                                    text = stringResource(R.string.settings_ai_keys_count, keyCount),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { showGuideDialogForProvider = provider },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Default.Info,
                                    contentDescription = stringResource(R.string.settings_ai_provider_guide_btn),
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            IconButton(
                                onClick = { onMoveProviderUp(provider) },
                                enabled = index > 0,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.ArrowUpward, contentDescription = "Move Up", modifier = Modifier.size(18.dp))
                            }
                            IconButton(
                                onClick = { onMoveProviderDown(provider) },
                                enabled = index < state.providerOrder.size - 1,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.ArrowDownward, contentDescription = "Move Down", modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }

        // ── 2. Provider Selector Chips ─────────────────────────────────────────
        PreferenceSection(
            title = stringResource(R.string.settings_ai_manage_provider_title),
            subtitle = stringResource(R.string.settings_ai_manage_provider_subtitle)
        ) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ModelProviders.ALL.forEach { provider ->
                    FilterChip(
                        selected = provider == selectedProvider,
                        onClick = { onSelectProvider(provider) },
                        label = { Text(provider.uppercase()) },
                        trailingIcon = if (provider == selectedProvider) {
                            {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = stringResource(R.string.settings_ai_provider_guide_btn),
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clickable { showGuideDialogForProvider = provider }
                                }
                            }
                        } else null
                    )
                }
            }
        }

        // ── 3. API Keys Management ─────────────────────────────────────────────
        PreferenceSection(
            title = stringResource(R.string.settings_ai_keys_for_provider, selectedProvider.uppercase()),
            subtitle = stringResource(R.string.settings_ai_keys_subtitle)
        ) {
            // Guide bar with Info button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.settings_ai_guide_title, selectedProvider.uppercase()),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                OutlinedButton(
                    onClick = { showGuideDialogForProvider = selectedProvider },
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                ) {
                    Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.settings_ai_provider_guide_btn), fontSize = 11.sp)
                }
            }

            // Dedicated Cloudflare Account ID Field
            if (selectedProvider == ModelProviders.CLOUDFLARE) {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = stringResource(R.string.settings_ai_cloudflare_account_id_title),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Text(
                            text = stringResource(R.string.settings_ai_cloudflare_account_id_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        var accountIdInput by remember(state.cloudflareAccountId) {
                            mutableStateOf(state.cloudflareAccountId)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = accountIdInput,
                                onValueChange = {
                                    accountIdInput = it
                                    onSaveCloudflareAccountId(it)
                                },
                                label = { Text(stringResource(R.string.settings_ai_cloudflare_account_id_title)) },
                                placeholder = { Text("e.g. 9c2036eda99b642789288ba8cf9cd4c5") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = { onSaveCloudflareAccountId(accountIdInput) },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(stringResource(R.string.common_save))
                            }
                        }
                    }
                }
            }

            if (config.keys.isEmpty()) {
                Text(
                    text = stringResource(R.string.settings_ai_no_keys),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            } else {
                config.keys.forEachIndexed { keyIndex, apiKey ->
                    val isVisible = visibleKeys[apiKey] ?: false
                    val verificationState = config.verificationStates[apiKey] ?: VerificationState.Idle

                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.settings_ai_key_label, keyIndex + 1),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = if (isVisible) apiKey else apiKey.take(6) + "••••••••••••" + apiKey.takeLast(4),
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = { visibleKeys[apiKey] = !isVisible },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = "Toggle Visibility",
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { onRemoveApiKey(selectedProvider, apiKey) },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete Key",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Status display
                                when (verificationState) {
                                    is VerificationState.Idle -> {
                                        Text(
                                            text = stringResource(R.string.settings_ai_status_untested),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    is VerificationState.Checking -> {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = stringResource(R.string.settings_ai_status_checking),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                    is VerificationState.Success -> {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = verificationState.message,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color(0xFF2E7D32),
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                    is VerificationState.Error -> {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = verificationState.error,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.error,
                                                maxLines = 2
                                            )
                                        }
                                    }
                                }

                                OutlinedButton(
                                    onClick = { onTestApiKey(selectedProvider, apiKey) },
                                    enabled = verificationState !is VerificationState.Checking,
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                ) {
                                    Text(stringResource(R.string.settings_ai_ping_btn), fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }

            // Add new key input row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = newKeyInput,
                    onValueChange = { newKeyInput = it },
                    label = { Text(stringResource(R.string.settings_ai_add_key_label)) },
                    placeholder = { Text("sk-...") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        if (newKeyInput.isNotBlank()) {
                            onAddApiKey(selectedProvider, newKeyInput)
                            newKeyInput = ""
                        }
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.common_add))
                }
            }
        }

        // ── 4. Provider Models Management ──────────────────────────────────────
        PreferenceSection(
            title = stringResource(R.string.settings_ai_models_title, selectedProvider.uppercase()),
            subtitle = stringResource(R.string.settings_ai_models_subtitle)
        ) {
            config.models.forEachIndexed { modelIdx, modelName ->
                Card(
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = modelName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )

                        Row {
                            IconButton(
                                onClick = { onMoveModelUp(selectedProvider, modelName) },
                                enabled = modelIdx > 0,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.ArrowUpward, contentDescription = "Move Model Up", modifier = Modifier.size(16.dp))
                            }
                            IconButton(
                                onClick = { onMoveModelDown(selectedProvider, modelName) },
                                enabled = modelIdx < config.models.size - 1,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.ArrowDownward, contentDescription = "Move Model Down", modifier = Modifier.size(16.dp))
                            }
                            IconButton(
                                onClick = { onRemoveProviderModel(selectedProvider, modelName) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete Model", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }

            // Add new model input row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = newModelInput,
                    onValueChange = { newModelInput = it },
                    label = { Text(stringResource(R.string.settings_ai_add_model_label)) },
                    placeholder = { Text("e.g. qwen3.8-flash / deepseek-v4.1-flash") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        if (newModelInput.isNotBlank()) {
                            onAddProviderModel(selectedProvider, newModelInput)
                            newModelInput = ""
                        }
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Model")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.common_add))
                }
            }
        }
    }

    // Provider How to Get API Key Dialog
    showGuideDialogForProvider?.let { provider ->
        ProviderKeyGuideDialog(
            provider = provider,
            onDismissRequest = { showGuideDialogForProvider = null }
        )
    }
}
