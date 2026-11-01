package com.example.persona.features.settings.presentation.model

import androidx.annotation.Keep
import kotlinx.serialization.Serializable

@Keep
@Serializable
enum class SettingKey {
    BotName,
    StoreName,
    Tone,
    ReplyDelay,
    Cooldown,
    Sensitivity,
    Greeting,
    Farewell,
    OutOfHours,
    HumanHolding,
    MediaFallback,
    BlankReply,
    BusinessHours,
    WorkingDays,
    WorkingTimes,
    IgnoreGroups,
    Blocklist,
    ListenMain,
    ListenBusiness,
    ListenClone,
    StorageAccess,
    HumanTakeover,
    ProviderChain,
    ApiKeys,
    Models,
    CloudflareId,
    Permissions,
    Backup,
    Cache,
    Language,
    Theme,
}
