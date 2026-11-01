package com.example.persona.features.settings.presentation

import androidx.compose.runtime.Stable
import com.example.persona.features.auto_reply.domain.model.PersonaTone
import com.example.persona.features.auto_reply.domain.model.StyleProfile
import com.example.persona.features.settings.domain.repository.ModelCapability

@Stable
class SettingsActions(val vm: SettingsViewModel) {
    val app = AppActions(vm)
    val voice = VoiceActions(vm)
    val messages = MessageActions(vm)
    val hours = HoursActions(vm)
    val whatsApp = WhatsAppActions(vm)
    val ai = AiActions(vm)
    val health = HealthActions(vm)
}

@Stable
class AppActions(private val vm: SettingsViewModel) {
    fun setAppLanguage(language: String) = vm.setAppLanguage(language)
    fun setThemeMode(mode: String) = vm.setThemeMode(mode)
}

@Stable
class VoiceActions(private val vm: SettingsViewModel) {
    fun setBotName(name: String) = vm.setBotName(name)
    fun setStoreName(name: String) = vm.setStoreName(name)
    fun setPersonaTone(tone: PersonaTone) = vm.setPersonaTone(tone)
    fun setStyleProfile(profile: StyleProfile) = vm.setStyleProfile(profile)
    fun setThresholdLevel(level: ThresholdLevel) = vm.setThresholdLevel(level)
    fun setCooldownMins(mins: Int) = vm.setCooldownMins(mins)
    fun setReplyDelayInput(input: String) = vm.setReplyDelayInput(input)
}

@Stable
class MessageActions(private val vm: SettingsViewModel) {
    fun setGreeting(text: String) = vm.setGreetingTemplate(text)
    fun setFarewell(text: String) = vm.setFarewellTemplate(text)
    fun setOutOfHours(text: String) = vm.setOutOfHoursMessage(text)
    fun setHitlHolding(text: String) = vm.setHitlHoldingMessage(text)
    fun setMediaFallback(text: String) = vm.setMediaFallbackMessage(text)
    fun setBlankReply(text: String) = vm.setBlankReplyFallbackTemplate(text)
}

@Stable
class HoursActions(private val vm: SettingsViewModel) {
    fun setBusinessHoursEnabled(enabled: Boolean) = vm.setBusinessHoursEnabled(enabled)
    fun setWorkingDays(days: Set<String>) = vm.setWorkingDays(days)
    fun setWorkingHours(start: String, end: String) = vm.setWorkingHours(start, end)
    fun setIgnoreGroups(ignore: Boolean) = vm.setIgnoreWhatsAppGroups(ignore)
    fun addBlockedNumber(number: String) = vm.addBlacklistedNumber(number.trim())
    fun removeBlockedNumber(number: String) = vm.removeBlacklistedNumber(number)
}

@Stable
class WhatsAppActions(private val vm: SettingsViewModel) {
    fun setListenMain(enabled: Boolean) = vm.setListenMainApp(enabled)
    fun setListenBusiness(enabled: Boolean) = vm.setListenBusinessApp(enabled)
    fun setListenClone(enabled: Boolean) = vm.setListenCloneApp(enabled)
    fun setAutoPauseOnHitl(enabled: Boolean) = vm.onAutoPauseOnHitlToggled(enabled)
}

@Stable
class AiActions(private val vm: SettingsViewModel) {
    fun selectProvider(id: String) = vm.selectProviderForEdit(id)
    fun selectCapabilityFilter(cap: ModelCapability) = vm.selectCapabilityFilter(cap)
    fun moveProvider(from: Int, to: Int) = vm.moveProvider(from, to)
    fun moveProviderUp(provider: String) = vm.moveProviderUp(provider)
    fun moveProviderDown(provider: String) = vm.moveProviderDown(provider)
    fun moveProviderForCapability(cap: ModelCapability, from: Int, to: Int) = vm.moveProviderForCapability(cap, from, to)
    fun moveProviderUpForCapability(cap: ModelCapability, provider: String) = vm.moveProviderUpForCapability(cap, provider)
    fun moveProviderDownForCapability(cap: ModelCapability, provider: String) = vm.moveProviderDownForCapability(cap, provider)
    fun addApiKey(provider: String, key: String) = vm.addApiKey(provider, key)
    fun addApiKeyAtIndex(provider: String, key: String, index: Int) = vm.addApiKeyAtIndex(provider, key, index)
    fun removeApiKey(provider: String, key: String) = vm.removeApiKey(provider, key)
    fun moveApiKeyUp(provider: String, key: String) = vm.moveApiKeyUp(provider, key)
    fun moveApiKeyDown(provider: String, key: String) = vm.moveApiKeyDown(provider, key)
    fun moveApiKey(provider: String, from: Int, to: Int) = vm.moveApiKey(provider, from, to)
    fun testConnection(provider: String, key: String) = vm.testConnection(provider, key)
    fun testAll(provider: String) = vm.testAllConnections(provider)
    fun setCloudflareAccountId(id: String) = vm.setCloudflareAccountId(id)
    fun addProviderModel(provider: String, model: String) = vm.addProviderModel(provider, model)
    fun addProviderModelWithCapabilities(provider: String, model: String, capabilities: Set<ModelCapability>) =
        vm.addProviderModelWithCapabilities(provider, model, capabilities)
    fun addProviderModelAtIndex(provider: String, model: String, index: Int) = vm.addProviderModelAtIndex(provider, model, index)
    fun removeProviderModel(provider: String, model: String) = vm.removeProviderModel(provider, model)
    fun moveModel(provider: String, from: Int, to: Int) = vm.moveModel(provider, from, to)
    fun moveModelUp(provider: String, model: String) = vm.moveModelUp(provider, model)
    fun moveModelDown(provider: String, model: String) = vm.moveModelDown(provider, model)
}

@Stable
class HealthActions(private val vm: SettingsViewModel) {
    fun refreshHealth() = vm.refreshStoragePermission()
    fun exportBackup() = vm.exportLocalBackup()
    fun restoreBackup() = vm.restoreLocalBackup()
    fun clearCache() = vm.clearCache()
    fun clearMaintenanceMessage() = vm.clearMaintenanceMessage()
}
