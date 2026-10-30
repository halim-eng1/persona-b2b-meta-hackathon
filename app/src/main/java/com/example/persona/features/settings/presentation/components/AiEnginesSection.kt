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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    modifier: Modifier = Modifier
) {
    val selectedProvider = state.selectedProviderForEdit
    val config = state.providerConfigs[selectedProvider] ?: ProviderConfigState(provider = selectedProvider)

    var newKeyInput by remember(selectedProvider) { mutableStateOf("") }
    var newModelInput by remember(selectedProvider) { mutableStateOf("") }
    val visibleKeys = remember { mutableStateMapOf<String, Boolean>() }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // ── 1. Provider Priority Chain ─────────────────────────────────────────
        PreferenceSection(
            title = "سلسلة أولويات مزودي الذكاء الاصطناعي (AI Provider Priority)",
            subtitle = "رتّب أولوية المزودين؛ في حال تعثر المزود الأول أو نفاد رصيده يتم الانتقال تلقائياً للمزود التالي."
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
                                    text = "$keyCount مفتاح API مسجل",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Row {
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
            title = "إدارة المفاتيح والنماذج للمزود المختار",
            subtitle = "اختر المزود لإضافة عدد غير محدود من المفاتيح والنماذج المدعومة."
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
                        label = { Text(provider.uppercase()) }
                    )
                }
            }
        }

        // ── 3. API Keys Management ─────────────────────────────────────────────
        PreferenceSection(
            title = "مفاتيح الـ API لمزود (${selectedProvider.uppercase()})",
            subtitle = "يمكنك إضافة مفاتيح متعددة مع فحص اتصال كل مفتاح بشكل مباشر."
        ) {
            if (config.keys.isEmpty()) {
                Text(
                    text = "لا توجد مفاتيح مسجلة لهذا المزود حتى الآن. سيتم استخدام المفاتيح الافتراضية إذا توفرت.",
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
                                        text = "مفتاح #${keyIndex + 1}",
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
                                            text = "لم يتم الفحص بعد",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    is VerificationState.Checking -> {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "جارٍ فحص الاتصال...",
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
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                ) {
                                    Text("فحص الاتصال (Ping)", fontSize = 12.sp)
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
                    label = { Text("إضافة مفتاح API جديد") },
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
                    Text("إضافة")
                }
            }
        }

        // ── 4. Provider Models Management ──────────────────────────────────────
        PreferenceSection(
            title = "نماذج الذكاء الاصطناعي لمزود (${selectedProvider.uppercase()})",
            subtitle = "يمكنك تعديل ترتيب النماذج، حذف النماذج غير المرغوبة، أو إضافة كود نموذج مخصص."
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
                    label = { Text("إضافة كود نموذج مخصص") },
                    placeholder = { Text("e.g. qwen-max-latest / gemini-2.5-flash") },
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
                    Text("إضافة")
                }
            }
        }
    }
}
