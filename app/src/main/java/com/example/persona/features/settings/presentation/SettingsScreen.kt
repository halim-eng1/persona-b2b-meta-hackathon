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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.persona.core.data.repository.UserPreferencesRepository
import com.example.persona.features.auto_reply.domain.model.PersonaTone
import com.example.persona.features.auto_reply.domain.model.StyleProfile
import com.example.persona.features.settings.presentation.components.CooldownOption
import com.example.persona.features.settings.presentation.components.PersonaToneCard
import com.example.persona.features.settings.presentation.components.PreferenceSection
import com.example.persona.features.settings.presentation.components.PreferenceSwitchRow
import com.example.persona.features.settings.presentation.components.ProfileOption
import com.example.persona.features.settings.presentation.components.ThresholdOption

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

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
                    PreferenceSection(
                        title = "BOT & BRAND IDENTITY",
                        subtitle = "Configure the persona name and store brand name used in AI conversations."
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

                    // ── App Routing ───────────────────────────────────────────
                    PreferenceSection(
                        title = "APP ROUTING",
                        subtitle = "Select which WhatsApp apps Persona should intercept and reply to."
                    ) {
                        PreferenceSwitchRow(
                            title = "Main WhatsApp App",
                            subtitle = "com.whatsapp (Standard)",
                            checked = state.listenMainApp,
                            onCheckedChange = { viewModel.setListenMainApp(it) }
                        )
                        PreferenceSwitchRow(
                            title = "WhatsApp Business",
                            subtitle = "com.whatsapp.w4b (Business)",
                            checked = state.listenBusinessApp,
                            onCheckedChange = { viewModel.setListenBusinessApp(it) }
                        )
                        PreferenceSwitchRow(
                            title = "Cloned / Dual SIM App",
                            subtitle = "Secondary profile clone instances",
                            checked = state.listenCloneApp,
                            onCheckedChange = { viewModel.setListenCloneApp(it) }
                        )
                    }

                    // ── WhatsApp Media & Voice Notes Storage Access ────────────
                    PreferenceSection(
                        title = "WHATSAPP MEDIA & VOICE ACCESS",
                        subtitle = "Allows Persona to read incoming WhatsApp images and voice notes directly."
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "All Files Access (WhatsApp Media)",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = if (state.isStorageAccessGranted)
                                        "مفعل ✓ - يمكن قراءة صور ورسائل الواتساب الصوتية ومعالجتها بالذكاء الاصطناعي"
                                    else
                                        "غير مفعل - يلزم منح الإذن لقراءة وسائط الواتساب والرسائل الصوتية",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (state.isStorageAccessGranted)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.error
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = { viewModel.requestStoragePermission(context) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (state.isStorageAccessGranted)
                                        MaterialTheme.colorScheme.secondaryContainer
                                    else
                                        MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Text(
                                    text = if (state.isStorageAccessGranted) "مفعل ✓" else "منح الإذن",
                                    color = if (state.isStorageAccessGranted)
                                        MaterialTheme.colorScheme.onSecondaryContainer
                                    else
                                        MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                    }

                    // ── Human Takeover & Safety ───────────────────────────────
                    PreferenceSection(
                        title = "HUMAN TAKEOVER & SAFETY",
                        subtitle = "Configure automated escalation and customer hand-off behavior."
                    ) {
                        PreferenceSwitchRow(
                            title = "Auto-pause AI on Hand-off",
                            subtitle = "Automatically freeze bot replies for a contact when escalated to human customer service (HITL).",
                            checked = state.isAutoPauseOnHitl,
                            onCheckedChange = { viewModel.onAutoPauseOnHitlToggled(it) }
                        )
                    }

                    // ── Interception Templates ────────────────────────────────
                    PreferenceSection(
                        title = "RESPONSE TEMPLATES",
                        subtitle = "Customize templates for short-circuit replies (Greetings and Farewell / Closing intent)."
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

                    // ── System Fallback & Holding Messages ────────────────────
                    PreferenceSection(
                        title = "SYSTEM FALLBACK & HOLDING MESSAGES",
                        subtitle = "Customize automated messages for support hand-offs (HITL), media failures, and blank AI fallbacks."
                    ) {
                        OutlinedTextField(
                            value = state.hitlHoldingMessage,
                            onValueChange = { viewModel.setHitlHoldingMessage(it) },
                            label = { Text("Human Support Holding Message (HITL)") },
                            placeholder = { Text(UserPreferencesRepository.DEFAULT_HITL_HOLDING_MESSAGE) },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2
                        )
                        OutlinedTextField(
                            value = state.mediaFallbackMessage,
                            onValueChange = { viewModel.setMediaFallbackMessage(it) },
                            label = { Text("Media Fallback Message") },
                            placeholder = { Text(UserPreferencesRepository.DEFAULT_MEDIA_FALLBACK_MESSAGE) },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2
                        )
                        OutlinedTextField(
                            value = state.blankReplyFallbackTemplate,
                            onValueChange = { viewModel.setBlankReplyFallbackTemplate(it) },
                            label = { Text("Blank Reply Fallback Template (%s = Store Name)") },
                            placeholder = { Text(UserPreferencesRepository.DEFAULT_BLANK_REPLY_FALLBACK_TEMPLATE) },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2
                        )
                    }

                    // ── AI Persona Tone ─────────────────────────────────────
                    PreferenceSection(
                        title = "AI PERSONA TONE",
                        subtitle = "Choose how the AI communicates with your customers."
                    ) {
                        PersonaTone.entries.forEach { tone ->
                            PersonaToneCard(
                                tone = tone,
                                isSelected = state.personaTone == tone,
                                onSelect = { viewModel.setPersonaTone(tone) }
                            )
                        }
                    }

                    // ── Similarity Threshold ──────────────────────────────────
                    PreferenceSection(
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
                    PreferenceSection(
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
                    PreferenceSection(
                        title = "REPLY PRECISION DELAY",
                        subtitle = "Configure a delay before auto-reply to simulate natural response time."
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

                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}
