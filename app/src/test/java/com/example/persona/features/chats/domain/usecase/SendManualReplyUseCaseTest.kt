package com.example.persona.features.chats.domain.usecase

import com.example.persona.core.database.dao.ChatHistoryDao
import com.example.persona.core.database.dao.ConversationMetadataDao
import com.example.persona.core.database.entity.ChatHistoryEntity
import com.example.persona.features.auto_reply.domain.orchestrator.AutoReplyOrchestrator
import com.example.persona.features.auto_reply.domain.usecase.DirectReplyExecutionUseCase
import com.example.persona.features.auto_reply.domain.usecase.ReplyExecutionResult
import com.example.persona.features.chat_import.data.embedding.EmbeddingEngine
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SendManualReplyUseCaseTest {

    private val chatHistoryDao = mockk<ChatHistoryDao>(relaxed = true)
    private val conversationMetadataDao = mockk<ConversationMetadataDao>(relaxed = true)
    private val directReplyExecutionUseCase = mockk<DirectReplyExecutionUseCase>()
    private val embeddingEngine = mockk<EmbeddingEngine>(relaxed = true)

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var useCase: SendManualReplyUseCase

    @Before
    fun setUp() {
        useCase = SendManualReplyUseCase(
            chatHistoryDao = chatHistoryDao,
            conversationMetadataDao = conversationMetadataDao,
            directReplyExecutionUseCase = directReplyExecutionUseCase,
            embeddingEngine = embeddingEngine,
            scope = testScope
        )
    }

    @Test
    fun `successful manual reply inserts optimistic turn, pauses AI, updates status to SENT and clears attention`() = runTest(testDispatcher) {
        val messageSlot = slot<ChatHistoryEntity>()
        coEvery { chatHistoryDao.insert(capture(messageSlot)) } returns 42L
        coEvery {
            directReplyExecutionUseCase.execute(
                notificationKey = null,
                replyText = "تمام تم التجهيز",
                contactName = "أحمد",
                sourceClass = "BUSINESS"
            )
        } returns ReplyExecutionResult.Success

        val result = useCase.invoke(
            contactName = "أحمد",
            sourceClass = "BUSINESS",
            replyText = " تمام تم التجهيز "
        )

        assertTrue(result is ManualReplyResult.Success)
        assertEquals(42L, (result as ManualReplyResult.Success).messageId)

        // Verify optimistic message properties
        val captured = messageSlot.captured
        assertEquals("أحمد", captured.contactName)
        assertEquals("تمام تم التجهيز", captured.messageText)
        assertEquals("BUSINESS", captured.sourceClass)
        assertEquals("MERCHANT_MANUAL", captured.senderType)
        assertEquals("SENDING", captured.deliveryStatus)
        assertFalse(captured.isFromUser)
        assertTrue(captured.isRead)

        // Verify AI auto-pause guard
        coVerify(exactly = 1) {
            conversationMetadataDao.setAiPaused("أحمد", "BUSINESS", true)
        }

        // Verify metadata update
        coVerify(exactly = 1) {
            conversationMetadataDao.recordMessageTurn(
                contactName = "أحمد",
                sourceClass = "BUSINESS",
                messageText = "تمام تم التجهيز",
                senderType = "MERCHANT_MANUAL",
                timestamp = any(),
                notificationKey = null,
                incrementUnread = false,
                isAiPaused = true
            )
        }

        // Verify status updated to SENT and attention cleared
        coVerify(exactly = 1) { chatHistoryDao.updateDeliveryStatus(42L, "SENT") }
        coVerify(exactly = 1) { conversationMetadataDao.setNeedsHumanAttention("أحمد", "BUSINESS", false) }
    }

    @Test
    fun `aborted reply maps to FAILED delivery status for immediate user feedback`() = runTest(testDispatcher) {
        coEvery { chatHistoryDao.insert(any()) } returns 99L
        coEvery {
            directReplyExecutionUseCase.execute(any(), any(), any(), any())
        } returns ReplyExecutionResult.Aborted

        val result = useCase.invoke(
            contactName = "محمد",
            sourceClass = "MAIN_APP",
            replyText = "أهلاً بك"
        )

        assertTrue(result is ManualReplyResult.Failed)
        assertEquals(99L, (result as ManualReplyResult.Failed).messageId)

        // Aborted turns must be updated to FAILED in Room so merchant can retry or open WhatsApp
        coVerify(exactly = 1) { chatHistoryDao.updateDeliveryStatus(99L, "FAILED") }
    }

    @Test
    fun `retrying failed reply updates existing row instead of inserting duplicate`() = runTest(testDispatcher) {
        coEvery {
            directReplyExecutionUseCase.execute(any(), any(), any(), any())
        } returns ReplyExecutionResult.Success

        val result = useCase.invoke(
            contactName = "محمد",
            sourceClass = "MAIN_APP",
            replyText = "أهلاً بك مجدداً",
            retryMessageId = 88L
        )

        assertTrue(result is ManualReplyResult.Success)
        assertEquals(88L, (result as ManualReplyResult.Success).messageId)

        // Verify existing message set to SENDING then SENT, and NO new row inserted
        coVerify(exactly = 1) { chatHistoryDao.updateDeliveryStatus(88L, "SENDING") }
        coVerify(exactly = 1) { chatHistoryDao.updateDeliveryStatus(88L, "SENT") }
        coVerify(exactly = 0) { chatHistoryDao.insert(any()) }
    }

    @Test
    fun `failed direct reply updates status to FAILED and returns target package for fallback intent`() = runTest(testDispatcher) {
        coEvery { chatHistoryDao.insert(any()) } returns 101L
        coEvery {
            directReplyExecutionUseCase.execute(any(), any(), any(), any())
        } returns ReplyExecutionResult.Failed("PendingIntent exception")

        val result = useCase.invoke(
            contactName = "سارة",
            sourceClass = "BUSINESS",
            replyText = "متاح مقاس 38"
        )

        assertTrue(result is ManualReplyResult.Failed)
        val failedResult = result as ManualReplyResult.Failed
        assertEquals(101L, failedResult.messageId)
        assertEquals("com.whatsapp.w4b", failedResult.targetPackage)
        assertEquals("PendingIntent exception", failedResult.reason)

        coVerify(exactly = 1) { chatHistoryDao.updateDeliveryStatus(101L, "FAILED") }
    }

    @Test
    fun `manual reply cancels in-flight turn in AutoReplyOrchestrator`() = runTest(testDispatcher) {
        val orchestrator = mockk<AutoReplyOrchestrator>(relaxed = true)
        val testUseCase = SendManualReplyUseCase(
            chatHistoryDao = chatHistoryDao,
            conversationMetadataDao = conversationMetadataDao,
            directReplyExecutionUseCase = directReplyExecutionUseCase,
            embeddingEngine = embeddingEngine,
            scope = testScope,
            autoReplyOrchestrator = orchestrator
        )

        coEvery { chatHistoryDao.insert(any()) } returns 55L
        coEvery { directReplyExecutionUseCase.execute(any(), any(), any(), any()) } returns ReplyExecutionResult.Success

        testUseCase.invoke(
            contactName = "كريم",
            sourceClass = "BUSINESS",
            replyText = "أهلاً كريم"
        )

        verify(exactly = 1) { orchestrator.cancelInFlightTurn("كريم") }
    }
}
