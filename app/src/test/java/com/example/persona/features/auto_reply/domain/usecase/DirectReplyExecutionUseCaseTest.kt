package com.example.persona.features.auto_reply.domain.usecase

import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.os.Bundle
import android.service.notification.StatusBarNotification
import com.example.persona.features.auto_reply.data.service.NotificationActionCache
import com.example.persona.features.auto_reply.data.service.WhatsAppNotificationListener
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.lang.ref.WeakReference

class DirectReplyExecutionUseCaseTest {

    private lateinit var context: Context
    private lateinit var useCase: DirectReplyExecutionUseCase

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        useCase = DirectReplyExecutionUseCase(context)
        NotificationActionCache.clear()
        WhatsAppNotificationListener.instance = null
    }

    private fun createMockAction(remoteInput: RemoteInput, pendingIntent: PendingIntent): Notification.Action {
        val action = mockk<Notification.Action>()
        every { action.remoteInputs } returns arrayOf(remoteInput)
        try {
            val field = Notification.Action::class.java.getDeclaredField("actionIntent")
            field.isAccessible = true
            field.set(action, pendingIntent)
        } catch (_: Exception) {}
        return action
    }

    @Test
    fun `returns Aborted when action not found in live notifications or cache`() = runBlocking {
        val result = useCase.execute(
            notificationKey = "non_existent_key",
            replyText = "Hello",
            contactName = "Test Contact",
            sourceClass = "BUSINESS"
        )

        assertEquals(ReplyExecutionResult.Aborted, result)
    }

    @Test
    fun `picks latest child with RemoteInput when multiple notifications match instead of aborting`() = runBlocking {
        val listenerService = mockk<WhatsAppNotificationListener>(relaxed = true)
        WhatsAppNotificationListener.instance = WeakReference(listenerService)

        val remoteInput = mockk<RemoteInput> {
            every { resultKey } returns "key_text_reply"
        }
        val pendingIntent = mockk<PendingIntent>(relaxed = true)

        val olderAction = createMockAction(remoteInput, pendingIntent)
        val newerAction = createMockAction(remoteInput, pendingIntent)

        val mockExtras = mockk<Bundle> {
            every { getCharSequence(Notification.EXTRA_TITLE) } returns "Ahmed"
            every { getCharSequence("android.conversationTitle") } returns null
            every { getCharSequence("android.hiddenConversationTitle") } returns null
        }

        val olderNotification = mockk<Notification> {
            flags = 0
            actions = arrayOf(olderAction)
            extras = mockExtras
        }
        val newerNotification = mockk<Notification> {
            flags = 0
            actions = arrayOf(newerAction)
            extras = mockExtras
        }

        val olderSbn = mockk<StatusBarNotification> {
            every { key } returns "sbn_older_1"
            every { postTime } returns 1000L
            every { packageName } returns "com.whatsapp.w4b"
            every { isGroup } returns false
            every { notification } returns olderNotification
        }

        val newerSbn = mockk<StatusBarNotification> {
            every { key } returns "sbn_newer_2"
            every { postTime } returns 2000L
            every { packageName } returns "com.whatsapp.w4b"
            every { isGroup } returns false
            every { notification } returns newerNotification
        }

        every { listenerService.activeNotifications } returns arrayOf(olderSbn, newerSbn)

        val result = useCase.execute(
            notificationKey = null,
            replyText = "مرحباً يا أحمد",
            contactName = "Ahmed",
            sourceClass = "BUSINESS"
        )

        // Multiple notifications present: does NOT abort! Executes reply on the latest child notification
        assertEquals(ReplyExecutionResult.Success, result)
    }

    @Test
    fun `falls back to NotificationActionCache when status bar has no active match`() = runBlocking {
        val remoteInput = mockk<RemoteInput> {
            every { resultKey } returns "key_text_reply"
        }
        val pendingIntent = mockk<PendingIntent>(relaxed = true)
        val cachedAction = createMockAction(remoteInput, pendingIntent)

        NotificationActionCache.put("cached_key_1", cachedAction, "BUSINESS", "Mahmoud")

        val result = useCase.execute(
            notificationKey = "cached_key_1",
            replyText = "تم تأكيد طلبك",
            contactName = "Mahmoud",
            sourceClass = "BUSINESS"
        )

        assertEquals(ReplyExecutionResult.Success, result)
    }
}
