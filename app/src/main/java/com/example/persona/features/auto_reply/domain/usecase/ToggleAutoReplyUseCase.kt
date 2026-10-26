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
            serviceController.enableAutoReply()
        } else {
            serviceController.disableAutoReply()
        }
    }
}
