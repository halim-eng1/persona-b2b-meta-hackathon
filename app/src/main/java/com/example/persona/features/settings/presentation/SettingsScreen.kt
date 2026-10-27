package com.example.persona.features.settings.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.persona.features.auto_reply.domain.model.StyleProfile
import com.example.persona.features.settings.presentation.components.CooldownOption
import com.example.persona.features.settings.presentation.components.ProfileOption
import com.example.persona.features.settings.presentation.components.ThresholdOption

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                        MaterialTheme.colorScheme.background
                    )
                )
            )
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ── Header ────────────────────────────────────────────────────────
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }

            AnimatedVisibility(visible = !state.isLoading, enter = fadeIn(), exit = fadeOut()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // ── Bot & Brand Identity ──────────────────────────────────
                    SettingsSection(
                        title = "BOT & BRAND IDENTITY",
                        subtitle = "Configure the persona name and store brand name used in AI conversations."
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            OutlinedTextField(
                                value = state.botName,
                                onValueChange = { viewModel.setBotName(it) },
                                label = { Text("Bot Representative Name") },
                                placeholder = { Text("e.g. سارة / مساعد المبيعات") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = state.storeName,
                                onValueChange = { viewModel.setStoreName(it) },
                                label = { Text("Store / Brand Name") },
                                placeholder = { Text("e.g. متجرنا / متجر الأناقة") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    // ── App Routing ───────────────────────────────────────────
                    SettingsSection(
                        title = "APP ROUTING",
                        subtitle = "Select which WhatsApp apps Persona should intercept and reply to."
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Main WhatsApp App", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                                    Text("com.whatsapp (Standard)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(checked = state.listenMainApp, onCheckedChange = { viewModel.setListenMainApp(it) })
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("WhatsApp Business", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                                    Text("com.whatsapp.w4b (Business)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(checked = state.listenBusinessApp, onCheckedChange = { viewModel.setListenBusinessApp(it) })
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Cloned / Dual SIM App", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                                    Text("Secondary profile clone instances", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(checked = state.listenCloneApp, onCheckedChange = { viewModel.setListenCloneApp(it) })
                            }
                        }
                    }

                    // ── Human Takeover & Safety ───────────────────────────────
                    SettingsSection(
                        title = "HUMAN TAKEOVER & SAFETY",
                        subtitle = "Configure automated escalation and customer hand-off behavior."
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Auto-pause AI on Hand-off",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                    Text(
                                        text = "Automatically freeze bot replies for a contact when escalated to human customer service (HITL).",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = state.isAutoPauseOnHitl,
                                    onCheckedChange = { viewModel.onAutoPauseOnHitlToggled(it) }
                                )
                            }
                        }
                    }

                    // ── Interception Templates ────────────────────────────────
                    SettingsSection(
                        title = "RESPONSE TEMPLATES",
                        subtitle = "Customize templates for short-circuit replies (Greetings and Farewell / Closing intent)."
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            OutlinedTextField(
                                value = state.greetingTemplate,
                                onValueChange = { viewModel.setGreetingTemplate(it) },
                                label = { Text("Greeting Template") },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 2
                            )
                            OutlinedTextField(
                                value = state.farewellTemplate,
                                onValueChange = { viewModel.setFarewellTemplate(it) },
                                label = { Text("Farewell / Closing Template") },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 2
                            )
                        }
                    }

                    // ── Reply Style ─────────────────────────────────────────
                    SettingsSection(title = "REPLY STYLE") {
                        StyleProfile.entries.forEach { profile ->
                            ProfileOption(
                                profile = profile,
                                isSelected = state.styleProfile == profile,
                                onSelect = { viewModel.setStyleProfile(profile) }
                            )
                        }
                    }

                    // ── Similarity Threshold ──────────────────────────────────
                    SettingsSection(
                        title = "MATCH SENSITIVITY",
                        subtitle = "Controls how closely incoming messages must match your training data."
                    ) {
                        ThresholdLevel.entries.forEach { level ->
                            ThresholdOption(
                                level = level,
                                isSelected = state.thresholdLevel == level,
                                onSelect = { viewModel.setThresholdLevel(level) }
                            )
                        }
                    }

                    // ── Cooldown Duration ─────────────────────────────────────
                    SettingsSection(
                        title = "COOLDOWN DURATION",
                        subtitle = "Minimum wait time between auto-replies to the same contact."
                    ) {
                        listOf(0, 1, 5, 10, 30).forEach { mins ->
                            CooldownOption(
                                mins = mins,
                                isSelected = state.cooldownMins == mins,
                                onSelect = { viewModel.setCooldownMins(mins) }
                            )
                        }
                    }

                    // ── Reply Precision Delay ─────────────────────────────────
                    SettingsSection(
                        title = "REPLY PRECISION DELAY",
                        subtitle = "Configure a delay before auto-reply to simulate natural response time."
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = state.replyDelayInput,
                                onValueChange = { viewModel.setReplyDelayInput(it) },
                                label = { Text("Reply Delay (Seconds)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                isError = state.replyDelayError != null,
                                supportingText = {
                                    if (state.replyDelayError != null) {
                                        Text(
                                            text = state.replyDelayError!!,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    subtitle: String? = null,
    content: @Composable () -> Unit
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall.copy(
                letterSpacing = 1.2.sp,
                fontWeight = FontWeight.SemiBold
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (subtitle != null) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(4.dp)) {
                content()
            }
        }
    }
}
