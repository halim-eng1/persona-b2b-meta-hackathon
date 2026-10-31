package com.example.persona.features.settings.presentation.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.persona.R
import com.example.persona.features.settings.domain.repository.ModelProviders

data class ProviderGuideDetails(
    val provider: String,
    val title: String,
    val platformName: String,
    val portalUrl: String,
    val keyOrIdLabel: String,
    @StringRes val stepResIds: List<Int>,
    @StringRes val tipsResId: Int
) {
    fun getAiPrompt(context: Context): String {
        return context.getString(
            R.string.guide_ai_prompt_template,
            platformName,
            portalUrl,
            keyOrIdLabel
        )
    }
}

object ProviderGuideRegistry {
    fun getGuide(provider: String): ProviderGuideDetails {
        return when (ModelProviders.normalize(provider)) {
            ModelProviders.QWEN -> ProviderGuideDetails(
                provider = ModelProviders.QWEN,
                title = "Qwen",
                platformName = "Qwen Cloud",
                portalUrl = "https://home.qwencloud.com/",
                keyOrIdLabel = "API Key",
                stepResIds = listOf(
                    R.string.guide_qwen_step_1,
                    R.string.guide_qwen_step_2,
                    R.string.guide_qwen_step_3,
                    R.string.guide_qwen_step_4
                ),
                tipsResId = R.string.guide_qwen_tips
            )
            ModelProviders.GEMINI -> ProviderGuideDetails(
                provider = ModelProviders.GEMINI,
                title = "Google AI Studio (Gemini)",
                platformName = "Google AI Studio",
                portalUrl = "https://aistudio.google.com/app/apikey",
                keyOrIdLabel = "API Key",
                stepResIds = listOf(
                    R.string.guide_gemini_step_1,
                    R.string.guide_gemini_step_2,
                    R.string.guide_gemini_step_3,
                    R.string.guide_gemini_step_4
                ),
                tipsResId = R.string.guide_gemini_tips
            )
            ModelProviders.GROQ -> ProviderGuideDetails(
                provider = ModelProviders.GROQ,
                title = "GroqCloud Console",
                platformName = "GroqCloud",
                portalUrl = "https://console.groq.com/keys",
                keyOrIdLabel = "API Key",
                stepResIds = listOf(
                    R.string.guide_groq_step_1,
                    R.string.guide_groq_step_2,
                    R.string.guide_groq_step_3,
                    R.string.guide_groq_step_4
                ),
                tipsResId = R.string.guide_groq_tips
            )
            ModelProviders.NVIDIA -> ProviderGuideDetails(
                provider = ModelProviders.NVIDIA,
                title = "NVIDIA NIM / Build",
                platformName = "NVIDIA Build",
                portalUrl = "https://build.nvidia.com",
                keyOrIdLabel = "API Key",
                stepResIds = listOf(
                    R.string.guide_nvidia_step_1,
                    R.string.guide_nvidia_step_2,
                    R.string.guide_nvidia_step_3,
                    R.string.guide_nvidia_step_4
                ),
                tipsResId = R.string.guide_nvidia_tips
            )
            ModelProviders.OPENROUTER -> ProviderGuideDetails(
                provider = ModelProviders.OPENROUTER,
                title = "OpenRouter Unified Gateway",
                platformName = "OpenRouter",
                portalUrl = "https://openrouter.ai/keys",
                keyOrIdLabel = "API Key",
                stepResIds = listOf(
                    R.string.guide_openrouter_step_1,
                    R.string.guide_openrouter_step_2,
                    R.string.guide_openrouter_step_3,
                    R.string.guide_openrouter_step_4
                ),
                tipsResId = R.string.guide_openrouter_tips
            )
            ModelProviders.CLOUDFLARE -> ProviderGuideDetails(
                provider = ModelProviders.CLOUDFLARE,
                title = "Cloudflare Workers AI",
                platformName = "Cloudflare Dashboard",
                portalUrl = "https://dash.cloudflare.com",
                keyOrIdLabel = "Account ID & API Token",
                stepResIds = listOf(
                    R.string.guide_cloudflare_step_1,
                    R.string.guide_cloudflare_step_2,
                    R.string.guide_cloudflare_step_3,
                    R.string.guide_cloudflare_step_4
                ),
                tipsResId = R.string.guide_cloudflare_tips
            )
            ModelProviders.LIQUID -> ProviderGuideDetails(
                provider = ModelProviders.LIQUID,
                title = "Liquid AI LFM System",
                platformName = "Liquid AI",
                portalUrl = "https://www.liquid.ai",
                keyOrIdLabel = "API Key",
                stepResIds = listOf(
                    R.string.guide_liquid_step_1,
                    R.string.guide_liquid_step_2,
                    R.string.guide_liquid_step_3,
                    R.string.guide_liquid_step_4
                ),
                tipsResId = R.string.guide_liquid_tips
            )
            ModelProviders.NARA -> ProviderGuideDetails(
                provider = ModelProviders.NARA,
                title = "Nara Router AI",
                platformName = "Nara Router",
                portalUrl = "https://router.bynara.id",
                keyOrIdLabel = "API Key",
                stepResIds = listOf(
                    R.string.guide_nara_step_1,
                    R.string.guide_nara_step_2,
                    R.string.guide_nara_step_3,
                    R.string.guide_nara_step_4
                ),
                tipsResId = R.string.guide_nara_tips
            )
            else -> ProviderGuideDetails(
                provider = provider,
                title = provider.uppercase(),
                platformName = provider,
                portalUrl = "https://google.com",
                keyOrIdLabel = "API Key",
                stepResIds = listOf(
                    R.string.guide_default_step_1,
                    R.string.guide_default_step_2,
                    R.string.guide_default_step_3
                ),
                tipsResId = R.string.guide_default_tips
            )
        }
    }
}

@Composable
fun ProviderKeyGuideDialog(
    provider: String,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val guide = ProviderGuideRegistry.getGuide(provider)

    AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Key,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.guide_dialog_title),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = guide.title,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                IconButton(onClick = onDismissRequest) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.guide_close_desc),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Official Portal Link Card
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.guide_portal_link_label),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = guide.portalUrl,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(
                            onClick = {
                                runCatching { uriHandler.openUri(guide.portalUrl) }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.OpenInNew,
                                contentDescription = stringResource(R.string.guide_open_portal_desc),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // Step by step instructions
                Text(
                    text = stringResource(R.string.guide_steps_header),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    guide.stepResIds.forEachIndexed { index, stepResId ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "${index + 1}",
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = stringResource(stepResId),
                                style = MaterialTheme.typography.bodySmall,
                                lineHeight = 18.sp,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // Tips Card
                Card(
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(guide.tipsResId),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                // Prominent AI Guide Prompt Copy Button
                Button(
                    onClick = {
                        val aiPrompt = guide.getAiPrompt(context)
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("AI Guide Prompt", aiPrompt)
                        clipboard.setPrimaryClip(clip)

                        val notificationText = context.getString(R.string.guide_notification_copy_text)
                        Toast.makeText(context, notificationText, Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.guide_copy_prompt_btn),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        confirmButton = {
            OutlinedButton(
                onClick = onDismissRequest,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(stringResource(R.string.common_close))
            }
        }
    )
}
