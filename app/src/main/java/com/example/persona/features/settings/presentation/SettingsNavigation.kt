package com.example.persona.features.settings.presentation

import androidx.annotation.Keep
import androidx.annotation.StringRes
import com.example.persona.R
import com.example.persona.features.settings.presentation.model.SettingKey
import kotlinx.serialization.Serializable

@Keep
@Serializable
sealed interface SettingsRoute {
    @Keep
    @Serializable
    data object Hub : SettingsRoute

    @Keep
    @Serializable
    data class Spoke(val spoke: SettingsSpoke, val focus: SettingKey? = null) : SettingsRoute

    @Keep
    @Serializable
    data class Provider(val id: String, val focus: SettingKey? = null) : SettingsRoute

    @Keep
    @Serializable
    data object Blocklist : SettingsRoute
}

@Keep
@Serializable
enum class SettingsSpoke(@StringRes val titleRes: Int) {
    Voice(R.string.spoke_voice),
    Messages(R.string.spoke_messages),
    Hours(R.string.spoke_hours),
    WhatsApp(R.string.spoke_whatsapp),
    AiEngine(R.string.spoke_ai),
    Health(R.string.spoke_health);
}
