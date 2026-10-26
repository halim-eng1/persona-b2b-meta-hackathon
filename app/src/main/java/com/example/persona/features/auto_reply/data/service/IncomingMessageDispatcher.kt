package com.example.persona.features.auto_reply.data.service

import com.example.persona.features.auto_reply.domain.model.WhatsAppNotification
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bridge between the NotificationListenerService and the auto-reply pipeline.
 * Uses SharedFlow to decouple the Android service from the processing logic.
 */
@Singleton
class IncomingMessageDispatcher @Inject constructor() {
    private val _notifications = MutableSharedFlow<WhatsAppNotification>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val notifications: SharedFlow<WhatsAppNotification> = _notifications.asSharedFlow()

    fun dispatch(notification: WhatsAppNotification) {
        _notifications.tryEmit(notification)
    }
}
