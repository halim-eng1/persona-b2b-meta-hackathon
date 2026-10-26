package com.example.persona.features.auto_reply.domain.usecase

import com.example.persona.core.data.repository.UserPreferencesRepository
import com.example.persona.features.auto_reply.data.service.ServiceController
import javax.inject.Inject

class ToggleAutoReplyUseCase @Inject constructor(
    private val serviceController: ServiceController,
    private val prefsRepository: UserPreferencesRepository
) {
    suspend operator fun invoke(enable: Boolean) {
        prefsRepository.setAutoReplyEnabled(enable)
        if (enable) {
            // Ensure the service component is enabled in PackageManager
            serviceController.enableAutoReply()
        }
        // When disabling auto-reply, we DO NOT call disableAutoReply() on the service component.
        // The NotificationListenerService remains running to capture incoming customer messages
        // for the Live Inbox and cache RemoteInput actions for manual merchant replies.
    }
}
