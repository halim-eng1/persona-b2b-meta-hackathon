package com.example.persona.features.settings.presentation.model

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.example.persona.R
import com.example.persona.features.settings.presentation.SettingsSpoke

@Immutable
data class SettingEntry(
    val key: SettingKey,
    @StringRes val title: Int,
    @StringRes val keywords: Int,
    val spoke: SettingsSpoke,
    val provider: String? = null
)

val SettingsSearchIndex: List<SettingEntry> = listOf(
    SettingEntry(SettingKey.BotName, R.string.settings_bot_name_label, R.string.kw_bot_name, SettingsSpoke.Voice),
    SettingEntry(SettingKey.StoreName, R.string.settings_store_name_label, R.string.kw_store_name, SettingsSpoke.Voice),
    SettingEntry(SettingKey.Tone, R.string.settings_persona_tone_title, R.string.kw_tone, SettingsSpoke.Voice),
    SettingEntry(SettingKey.ReplyDelay, R.string.settings_reply_delay_label, R.string.kw_delay, SettingsSpoke.Voice),
    SettingEntry(SettingKey.Cooldown, R.string.settings_cooldown_title, R.string.kw_cooldown, SettingsSpoke.Voice),
    SettingEntry(SettingKey.Sensitivity, R.string.settings_sensitivity_title, R.string.kw_sensitivity, SettingsSpoke.Voice),

    SettingEntry(SettingKey.Greeting, R.string.settings_greeting_template_label, R.string.kw_greeting, SettingsSpoke.Messages),
    SettingEntry(SettingKey.Farewell, R.string.settings_farewell_template_label, R.string.kw_farewell, SettingsSpoke.Messages),
    SettingEntry(SettingKey.OutOfHours, R.string.settings_hours_off_message_label, R.string.kw_out_of_hours, SettingsSpoke.Messages),
    SettingEntry(SettingKey.HumanHolding, R.string.settings_routing_hitl_message_label, R.string.kw_human_holding, SettingsSpoke.Messages),
    SettingEntry(SettingKey.MediaFallback, R.string.settings_routing_media_fallback_label, R.string.kw_media_fallback, SettingsSpoke.Messages),
    SettingEntry(SettingKey.BlankReply, R.string.settings_routing_blank_reply_label, R.string.kw_blank_reply, SettingsSpoke.Messages),

    SettingEntry(SettingKey.BusinessHours, R.string.settings_hours_enable_title, R.string.kw_business_hours, SettingsSpoke.Hours),
    SettingEntry(SettingKey.WorkingDays, R.string.settings_hours_official_days, R.string.kw_days, SettingsSpoke.Hours),
    SettingEntry(SettingKey.WorkingTimes, R.string.settings_hours_range_label, R.string.kw_working_times, SettingsSpoke.Hours),
    SettingEntry(SettingKey.IgnoreGroups, R.string.settings_hours_ignore_groups_title, R.string.kw_ignore_groups, SettingsSpoke.Hours),
    SettingEntry(SettingKey.Blocklist, R.string.settings_blocklist_title, R.string.kw_blocklist, SettingsSpoke.Hours),

    SettingEntry(SettingKey.ListenMain, R.string.settings_routing_main_app_title, R.string.kw_listen_main, SettingsSpoke.WhatsApp),
    SettingEntry(SettingKey.ListenBusiness, R.string.settings_routing_w4b_title, R.string.kw_listen_business, SettingsSpoke.WhatsApp),
    SettingEntry(SettingKey.ListenClone, R.string.settings_routing_cloned_title, R.string.kw_listen_clone, SettingsSpoke.WhatsApp),
    SettingEntry(SettingKey.StorageAccess, R.string.settings_routing_storage_title, R.string.kw_storage_access, SettingsSpoke.WhatsApp),
    SettingEntry(SettingKey.HumanTakeover, R.string.settings_routing_pause_on_human_title, R.string.kw_human_takeover, SettingsSpoke.WhatsApp),

    SettingEntry(SettingKey.ProviderChain, R.string.settings_ai_priority_title, R.string.kw_provider_chain, SettingsSpoke.AiEngine),
    SettingEntry(SettingKey.ApiKeys, R.string.settings_ai_keys_title, R.string.kw_api_keys, SettingsSpoke.AiEngine),
    SettingEntry(SettingKey.Models, R.string.settings_ai_models_summary_sheet, R.string.kw_models, SettingsSpoke.AiEngine),

    SettingEntry(SettingKey.Permissions, R.string.settings_perm_check_title, R.string.kw_permissions, SettingsSpoke.Health),
    SettingEntry(SettingKey.Backup, R.string.settings_backup_title, R.string.kw_backup, SettingsSpoke.Health),
    SettingEntry(SettingKey.Cache, R.string.settings_cache_clean_title, R.string.kw_cache, SettingsSpoke.Health),
    SettingEntry(SettingKey.Language, R.string.settings_language_title, R.string.kw_language, SettingsSpoke.Voice),
    SettingEntry(SettingKey.Theme, R.string.settings_theme_title, R.string.kw_theme, SettingsSpoke.Voice),
)

fun String.searchKey(): String =
    lowercase()
        .filterNot { it.code in 0x064B..0x065F || it.code == 0x0670 || it.code == 0x0640 }
        .map { ch ->
            when (ch) {
                'أ', 'إ', 'آ' -> 'ا'
                'ى' -> 'ي'
                'ة' -> 'ه'
                else -> ch
            }
        }
        .joinToString("")
