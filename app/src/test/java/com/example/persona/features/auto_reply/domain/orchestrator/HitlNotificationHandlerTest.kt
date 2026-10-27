package com.example.persona.features.auto_reply.domain.orchestrator

import android.app.NotificationManager
import android.content.Context
import com.example.persona.core.data.repository.UserPreferencesRepository
import com.example.persona.core.database.dao.ConversationMetadataDao
import com.example.persona.features.auto_reply.data.diagnostics.NotificationSourceClass
import com.example.persona.features.auto_reply.domain.model.ProcessedNotification
import com.example.persona.features.chat_import.domain.model.ConversationContext
import com.example.persona.features.chat_import.domain.model.IntentType
import com.example.persona.features.chat_import.domain.model.SentimentType
import com.example.persona.features.chat_import.domain.model.TopicType
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class HitlNotificationHandlerTest {

    private val context = mockk<Context>(relaxed = true)
    private val notificationManager = mockk<NotificationManager>(relaxed = true)
    private val conversationMetadataDao = mockk<ConversationMetadataDao>(relaxed = true)
    private val userPreferencesRepository = mockk<UserPreferencesRepository>(relaxed = true)

    private lateinit var handler: HitlNotificationHandler

    private val testNotification = ProcessedNotification(
        notificationKey = "notif_123",
        contactName = "عميل مميز",
        originalText = "عاوز اكلم خدمة العملاء فورا",
        normalizedText = "عاوز اكلم خدمة العملاء فورا",
        context = ConversationContext(
            intent = IntentType.REQUEST,
            topic = TopicType.GENERAL,
            sentiment = SentimentType.NEUTRAL,
            confidence = 1.0f
        ),
        postTime = 123456789L,
        sourceClass = NotificationSourceClass.BUSINESS
    )

    @Before
    fun setUp() {
        val baseHandler = HitlNotificationHandler(
            context = context,
            conversationMetadataDao = conversationMetadataDao,
            userPreferencesRepository = userPreferencesRepository
        )
        handler = spyk(baseHandler)
        every { handler.triggerHitlAlarm(any(), any(), any(), any()) } just Runs
    }

    @Test
    fun `when auto_pause_on_hitl is false, triggering HITL sets needs_human_attention true, posts alarm notification, but does NOT pause AI`() = runTest {
        coEvery { userPreferencesRepository.isAutoPauseOnHitlEnabledSync() } returns false

        handler.handleHitlEscalation(testNotification, score = 0.95f)

        // 1. Verify needs_human_attention is set to true
        coVerify(exactly = 1) {
            conversationMetadataDao.setNeedsHumanAttention("عميل مميز", "BUSINESS", true)
        }

        // 2. Verify setAiPaused is NEVER called
        coVerify(exactly = 0) {
            conversationMetadataDao.setAiPaused(any(), any(), any())
        }

        // 3. Verify alarm notification trigger is executed
        verify(exactly = 1) {
            handler.triggerHitlAlarm("عميل مميز", "عاوز اكلم خدمة العملاء فورا", 0.95f, "BUSINESS")
        }
    }

    @Test
    fun `when auto_pause_on_hitl is true, triggering HITL sets needs_human_attention true, sets is_ai_paused true, and posts alarm notification`() = runTest {
        coEvery { userPreferencesRepository.isAutoPauseOnHitlEnabledSync() } returns true

        handler.handleHitlEscalation(testNotification, score = 0.95f)

        // 1. Verify needs_human_attention is set to true
        coVerify(exactly = 1) {
            conversationMetadataDao.setNeedsHumanAttention("عميل مميز", "BUSINESS", true)
        }

        // 2. Verify setAiPaused is called with true
        coVerify(exactly = 1) {
            conversationMetadataDao.setAiPaused("عميل مميز", "BUSINESS", true)
        }

        // 3. Verify alarm notification trigger is executed
        verify(exactly = 1) {
            handler.triggerHitlAlarm("عميل مميز", "عاوز اكلم خدمة العملاء فورا", 0.95f, "BUSINESS")
        }
    }
}
