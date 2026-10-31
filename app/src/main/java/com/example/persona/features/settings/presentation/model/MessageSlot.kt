package com.example.persona.features.settings.presentation.model

import androidx.annotation.StringRes
import androidx.compose.runtime.staticCompositionLocalOf
import com.example.persona.R
import com.example.persona.core.data.repository.UserPreferencesRepository
import com.example.persona.features.settings.presentation.MessageActions
import com.example.persona.features.settings.presentation.SettingsUiState

enum class MessageSlot(
    @StringRes val title: Int,
    @StringRes val whenSent: Int,
    val read: (SettingsUiState) -> String,
    val write: (MessageActions, String) -> Unit,
    val defaultValue: String? = null,
) {
    Greeting(
        title = R.string.settings_greeting_template_label,
        whenSent = R.string.msg_when_greeting,
        read = { it.greetingTemplate },
        write = { a, v -> a.setGreeting(v) }
    ),
    Farewell(
        title = R.string.settings_farewell_template_label,
        whenSent = R.string.msg_when_farewell,
        read = { it.farewellTemplate },
        write = { a, v -> a.setFarewell(v) }
    ),
    OutOfHours(
        title = R.string.settings_hours_off_message_label,
        whenSent = R.string.msg_when_closed,
        read = { it.businessHoursSchedule.outOfHoursMessage },
        write = { a, v -> a.setOutOfHours(v) }
    ),
    HumanHolding(
        title = R.string.settings_routing_hitl_message_label,
        whenSent = R.string.msg_when_human,
        read = { it.hitlHoldingMessage },
        write = { a, v -> a.setHitlHolding(v) },
        defaultValue = UserPreferencesRepository.DEFAULT_HITL_HOLDING_MESSAGE
    ),
    MediaFallback(
        title = R.string.settings_routing_media_fallback_label,
        whenSent = R.string.msg_when_media,
        read = { it.mediaFallbackMessage },
        write = { a, v -> a.setMediaFallback(v) },
        defaultValue = UserPreferencesRepository.DEFAULT_MEDIA_FALLBACK_MESSAGE
    ),
    BlankReply(
        title = R.string.settings_routing_blank_reply_label,
        whenSent = R.string.msg_when_blank,
        read = { it.blankReplyFallbackTemplate },
        write = { a, v -> a.setBlankReply(v) },
        defaultValue = UserPreferencesRepository.DEFAULT_BLANK_REPLY_FALLBACK_TEMPLATE
    )
}

fun interface MessageEditorLauncher {
    fun open(slot: MessageSlot)
}

val LocalMessageEditor = staticCompositionLocalOf<MessageEditorLauncher> {
    error("MessageEditorLauncher not provided")
}
