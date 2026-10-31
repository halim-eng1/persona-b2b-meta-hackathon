package com.example.persona.features.settings.presentation.sheets

import android.content.Context
import androidx.annotation.StringRes
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
