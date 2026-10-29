package com.example.persona.features.chats.presentation.detail

/**
 * One-shot UI and navigation side-effects dispatched from [ChatDetailViewModel] to Composables.
 */
sealed interface ChatDetailUiEvent {
    data class LaunchWhatsApp(val packageName: String, val messageText: String) : ChatDetailUiEvent
    data class ShareTranscript(val transcriptText: String, val contactName: String) : ChatDetailUiEvent
    data class ShowSnackbar(val message: String) : ChatDetailUiEvent
}
