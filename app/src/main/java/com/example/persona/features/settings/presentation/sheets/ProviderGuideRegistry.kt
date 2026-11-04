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
            ModelProviders.META -> ProviderGuideDetails(
                provider = ModelProviders.META,
                title = "Meta AI Model API",
                platformName = "Meta AI",
                portalUrl = "https://api.meta.ai",
                keyOrIdLabel = "API Key",
                stepResIds = listOf(
                    R.string.guide_default_step_1,
                    R.string.guide_default_step_2,
                    R.string.guide_default_step_3
                ),
                tipsResId = R.string.guide_default_tips
            )
            else -> ProviderGuideDetails(
                provider = provider,
                title = "Meta AI Model API",
                platformName = "Meta AI",
                portalUrl = "https://api.meta.ai",
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
