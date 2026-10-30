package com.example.persona.features.chats.presentation.detail

import androidx.lifecycle.SavedStateHandle
import com.example.persona.core.database.dao.ChatHistoryDao
import com.example.persona.core.database.dao.ConversationMetadataDao
import com.example.persona.core.database.entity.ChatHistoryEntity
import com.example.persona.core.database.entity.ConversationMetadataEntity
import com.example.persona.core.data.repository.UserPreferencesRepository
import com.example.persona.features.auto_reply.domain.orchestrator.AutoReplyOrchestrator
import com.example.persona.features.chats.domain.usecase.ManualReplyResult
import com.example.persona.features.chats.domain.usecase.SendManualReplyUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatDetailViewModelTest {

    private val chatHistoryDao = mockk<ChatHistoryDao>(relaxed = true)
    private val conversationMetadataDao = mockk<ConversationMetadataDao>(relaxed = true)
    private val sendManualReplyUseCase = mockk<SendManualReplyUseCase>(relaxed = true)
    private val userPreferencesRepository = mockk<UserPreferencesRepository>(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()

    private val savedStateHandle = SavedStateHandle(
        mapOf(
            "contactName" to "أحمد مصطفى",
            "sourceClass" to "BUSINESS"
        )
    )

    private val msg1 = ChatHistoryEntity(
        id = 1L,
        contactName = "أحمد مصطفى",
        messageText = "السلام عليكم",
        isFromUser = true,
        timestamp = 1000L,
        sourceClass = "BUSINESS",
        senderType = "CUSTOMER",
        deliveryStatus = "SENT"
    )

    private val msg2 = ChatHistoryEntity(
        id = 2L,
        contactName = "أحمد مصطفى",
        messageText = "وعليكم السلام يا فندم! 📷 صورة المنتج",
        isFromUser = false,
        timestamp = 2000L,
        sourceClass = "BUSINESS",
        senderType = "AI_AGENT",
        deliveryStatus = "SENT"
    )

    private val convMeta = ConversationMetadataEntity(
        contactName = "أحمد مصطفى",
        sourceClass = "BUSINESS",
        lastMessageText = "وعليكم السلام يا فندم! 📷 صورة المنتج",
        lastMessageSenderType = "AI_AGENT",
        lastMessageTimestamp = 2000L,
        unreadCount = 0,
        needsHumanAttention = false,
        isAiPaused = false
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        AutoReplyOrchestrator.clearActiveProcessingContacts()
        every { chatHistoryDao.observeMessagesForContact("أحمد مصطفى", "BUSINESS") } returns flowOf(listOf(msg1, msg2))
        every { conversationMetadataDao.observeAllConversations() } returns flowOf(listOf(convMeta))
        every { conversationMetadataDao.observeConversation("أحمد مصطفى", "BUSINESS") } returns flowOf(convMeta)
        coEvery { conversationMetadataDao.getConversation("أحمد مصطفى", "BUSINESS") } returns convMeta
        every { userPreferencesRepository.botName } returns flowOf(UserPreferencesRepository.DEFAULT_BOT_NAME)
        every { userPreferencesRepository.isAutoReplyEnabled } returns flowOf(true)
        coEvery { userPreferencesRepository.isAutoReplyEnabledSync() } returns true
    }

    @After
    fun tearDown() {
        AutoReplyOrchestrator.clearActiveProcessingContacts()
        Dispatchers.resetMain()
    }

    @Test
    fun `initialization marks messages and conversation as read`() = runTest(testDispatcher) {
        ChatDetailViewModel(
            chatHistoryDao = chatHistoryDao,
            conversationMetadataDao = conversationMetadataDao,
            sendManualReplyUseCase = sendManualReplyUseCase,
            savedStateHandle = savedStateHandle,
            userPreferencesRepository = userPreferencesRepository
        )
        advanceUntilIdle()

        coVerify(exactly = 1) { chatHistoryDao.markMessagesAsRead("أحمد مصطفى", "BUSINESS") }
        coVerify(exactly = 1) { conversationMetadataDao.markConversationRead("أحمد مصطفى", "BUSINESS") }
    }

    @Test
    fun `uiState emits messages with media indicators and delivery status`() = runTest(testDispatcher) {
        val viewModel = ChatDetailViewModel(
            chatHistoryDao = chatHistoryDao,
            conversationMetadataDao = conversationMetadataDao,
            sendManualReplyUseCase = sendManualReplyUseCase,
            savedStateHandle = savedStateHandle,
            userPreferencesRepository = userPreferencesRepository
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(2, state.messages.size)
        assertEquals("أحمد مصطفى", state.contactName)
        assertEquals("BUSINESS", state.sourceClass)
        assertFalse(state.isAiPaused)

        // First message: customer text, no media
        assertFalse(state.messages[0].hasMediaIndicator)
        assertTrue(state.messages[0].isFromUser)

        // Second message: bot response with camera emoji -> hasMediaIndicator = true
        assertTrue(state.messages[1].hasMediaIndicator)
        assertFalse(state.messages[1].isFromUser)
        assertEquals("AI_AGENT", state.messages[1].senderType)
    }

    @Test
    fun `toggleAiPaused toggles state in ConversationMetadataDao`() = runTest(testDispatcher) {
        val viewModel = ChatDetailViewModel(
            chatHistoryDao = chatHistoryDao,
            conversationMetadataDao = conversationMetadataDao,
            sendManualReplyUseCase = sendManualReplyUseCase,
            savedStateHandle = savedStateHandle,
            userPreferencesRepository = userPreferencesRepository
        )
        advanceUntilIdle()

        viewModel.toggleAiPaused()
        advanceUntilIdle()

        // Since initial isAiPaused = false, toggling should set it to true
        coVerify(exactly = 1) { conversationMetadataDao.setAiPaused("أحمد مصطفى", "BUSINESS", true) }
    }

    @Test
    fun `toggleAiActive when global is OFF toggles isAiOverrideEnabled in ConversationMetadataDao`() = runTest(testDispatcher) {
        every { userPreferencesRepository.isAutoReplyEnabled } returns flowOf(false)
        coEvery { userPreferencesRepository.isAutoReplyEnabledSync() } returns false

        val viewModel = ChatDetailViewModel(
            chatHistoryDao = chatHistoryDao,
            conversationMetadataDao = conversationMetadataDao,
            sendManualReplyUseCase = sendManualReplyUseCase,
            savedStateHandle = savedStateHandle,
            userPreferencesRepository = userPreferencesRepository
        )
        advanceUntilIdle()

        viewModel.toggleAiActive()
        advanceUntilIdle()

        // Since initial isAiOverrideEnabled = false, toggling should set it to true
        coVerify(exactly = 1) { conversationMetadataDao.setAiOverrideEnabled("أحمد مصطفى", "BUSINESS", true) }
    }

    @Test
    fun `sendMessage clears input and dispatches to SendManualReplyUseCase`() = runTest(testDispatcher) {
        coEvery {
            sendManualReplyUseCase("أحمد مصطفى", "BUSINESS", "سعر المنتج 150 جنيه")
        } returns ManualReplyResult.Success(3L)

        val viewModel = ChatDetailViewModel(
            chatHistoryDao = chatHistoryDao,
            conversationMetadataDao = conversationMetadataDao,
            sendManualReplyUseCase = sendManualReplyUseCase,
            savedStateHandle = savedStateHandle,
            userPreferencesRepository = userPreferencesRepository
        )
        advanceUntilIdle()

        viewModel.onInputTextChanged("سعر المنتج 150 جنيه")
        viewModel.sendMessage()
        advanceUntilIdle()

        assertEquals("", viewModel.inputText.value)
        coVerify(exactly = 1) {
            sendManualReplyUseCase("أحمد مصطفى", "BUSINESS", "سعر المنتج 150 جنيه")
        }
    }

    @Test
    fun `retryMessage delegates to SendManualReplyUseCase with retryMessageId`() = runTest(testDispatcher) {
        coEvery {
            sendManualReplyUseCase(
                contactName = "أحمد مصطفى",
                sourceClass = "BUSINESS",
                replyText = "رسالة تجريبية",
                retryMessageId = 42L
            )
        } returns ManualReplyResult.Success(42L)

        val viewModel = ChatDetailViewModel(
            chatHistoryDao = chatHistoryDao,
            conversationMetadataDao = conversationMetadataDao,
            sendManualReplyUseCase = sendManualReplyUseCase,
            savedStateHandle = savedStateHandle,
            userPreferencesRepository = userPreferencesRepository
        )
        advanceUntilIdle()

        viewModel.retryMessage(42L, "رسالة تجريبية")
        advanceUntilIdle()

        coVerify(exactly = 1) {
            sendManualReplyUseCase(
                contactName = "أحمد مصطفى",
                sourceClass = "BUSINESS",
                replyText = "رسالة تجريبية",
                retryMessageId = 42L
            )
        }
    }

    @Test
    fun `uiState reflects active AI generating state from orchestrator`() = runTest(testDispatcher) {
        val viewModel = ChatDetailViewModel(
            chatHistoryDao = chatHistoryDao,
            conversationMetadataDao = conversationMetadataDao,
            sendManualReplyUseCase = sendManualReplyUseCase,
            savedStateHandle = savedStateHandle,
            userPreferencesRepository = userPreferencesRepository
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isAiGenerating)

        AutoReplyOrchestrator.setContactProcessing("أحمد مصطفى", true)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isAiGenerating)

        AutoReplyOrchestrator.setContactProcessing("أحمد مصطفى", false)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isAiGenerating)
    }

    @Test
    fun `calculateSinceTimestamp returns expected boundaries for each range`() {
        val viewModel = ChatDetailViewModel(
            chatHistoryDao = chatHistoryDao,
            conversationMetadataDao = conversationMetadataDao,
            sendManualReplyUseCase = sendManualReplyUseCase,
            savedStateHandle = savedStateHandle,
            userPreferencesRepository = userPreferencesRepository
        )

        val fixedTimeMs = 1730048400000L // arbitrary fixed timestamp

        // ALL_TIME -> 0L
        assertEquals(0L, viewModel.calculateSinceTimestamp(ExportTimeRange.ALL_TIME, fixedTimeMs))

        // LAST_TWO_HOURS -> fixedTimeMs - 7_200_000L
        assertEquals(
            fixedTimeMs - (2 * 60 * 60 * 1000L),
            viewModel.calculateSinceTimestamp(ExportTimeRange.LAST_TWO_HOURS, fixedTimeMs)
        )

        // TODAY -> midnight of that day
        val cal = java.util.Calendar.getInstance().apply {
            timeInMillis = fixedTimeMs
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        assertEquals(
            cal.timeInMillis,
            viewModel.calculateSinceTimestamp(ExportTimeRange.TODAY, fixedTimeMs)
        )
    }

    @Test
    fun `buildExportTranscript produces properly formatted transcript with header, sender labels and dividers`() {
        val viewModel = ChatDetailViewModel(
            chatHistoryDao = chatHistoryDao,
            conversationMetadataDao = conversationMetadataDao,
            sendManualReplyUseCase = sendManualReplyUseCase,
            savedStateHandle = savedStateHandle,
            userPreferencesRepository = userPreferencesRepository
        )

        val msg3 = ChatHistoryEntity(
            id = 3L,
            contactName = "أحمد مصطفى",
            messageText = "تم يا فندم شرفتنا.",
            isFromUser = false,
            timestamp = 3000L,
            sourceClass = "BUSINESS",
            senderType = "MERCHANT_MANUAL",
            deliveryStatus = "SENT"
        )

        val transcript = viewModel.buildExportTranscript(
            contactName = "أحمد مصطفى",
            sourceClass = "BUSINESS",
            storeName = "متجر التجربة",
            botName = "مساعد المبيعات",
            range = ExportTimeRange.ALL_TIME,
            exportTimestampMs = 1730048400000L,
            messages = listOf(msg1, msg2, msg3)
        )

        assertTrue(transcript.contains("سجل محادثة: أحمد مصطفى (BUSINESS)"))
        assertTrue(transcript.contains("المتجر: متجر التجربة"))
        assertTrue(transcript.contains("تاريخ التصدير: "))
        assertTrue(transcript.contains("نطاق التصدير: All Time (كامل المحادثة)"))
        assertTrue(transcript.contains(ChatDetailViewModel.TRANSCRIPT_DIVIDER))

        // Customer message
        assertTrue(transcript.contains("العميل: السلام عليكم"))
        // AI bot message
        assertTrue(transcript.contains("مساعد المبيعات (AI): وعليكم السلام يا فندم! 📷 صورة المنتج"))
        // Manual merchant message
        assertTrue(transcript.contains("التاجر (Manual): تم يا فندم شرفتنا."))
    }

    @Test
    fun `exportChat queries messages from dao and invokes onExportReady callback`() = runTest(testDispatcher) {
        val msg3 = ChatHistoryEntity(
            id = 3L,
            contactName = "أحمد مصطفى",
            messageText = "تم يا فندم شرفتنا.",
            isFromUser = false,
            timestamp = 3000L,
            sourceClass = "BUSINESS",
            senderType = "MERCHANT_MANUAL",
            deliveryStatus = "SENT"
        )

        coEvery {
            chatHistoryDao.getMessagesSince("أحمد مصطفى", "BUSINESS", 0L)
        } returns listOf(msg1, msg2, msg3)

        coEvery { userPreferencesRepository.getStoreNameSync() } returns "متجر التجربة"
        coEvery { userPreferencesRepository.getBotNameSync() } returns "مساعد المبيعات"

        val viewModel = ChatDetailViewModel(
            chatHistoryDao = chatHistoryDao,
            conversationMetadataDao = conversationMetadataDao,
            sendManualReplyUseCase = sendManualReplyUseCase,
            savedStateHandle = savedStateHandle,
            userPreferencesRepository = userPreferencesRepository
        )
        advanceUntilIdle()

        var receivedExport: String? = null
        viewModel.exportChat(ExportTimeRange.ALL_TIME) { text ->
            receivedExport = text
        }
        advanceUntilIdle()

        coVerify(exactly = 1) {
            chatHistoryDao.getMessagesSince("أحمد مصطفى", "BUSINESS", 0L)
        }
        assertTrue(receivedExport != null)
        assertTrue(receivedExport!!.contains("سجل محادثة: أحمد مصطفى (BUSINESS)"))
        assertTrue(receivedExport!!.contains("المتجر: متجر التجربة"))
        assertTrue(receivedExport!!.contains("العميل: السلام عليكم"))
        assertTrue(receivedExport!!.contains("مساعد المبيعات (AI): وعليكم السلام يا فندم! 📷 صورة المنتج"))
        assertTrue(receivedExport!!.contains("التاجر (Manual): تم يا فندم شرفتنا."))
    }

    @Test
    fun `clearChat with ALL_TIME calls deleteMessagesSince with timestamp 0`() = runTest(testDispatcher) {
        val viewModel = ChatDetailViewModel(
            chatHistoryDao = chatHistoryDao,
            conversationMetadataDao = conversationMetadataDao,
            sendManualReplyUseCase = sendManualReplyUseCase,
            savedStateHandle = savedStateHandle,
            userPreferencesRepository = userPreferencesRepository
        )
        advanceUntilIdle()

        viewModel.clearChat(ClearChatTimeRange.ALL_TIME)
        advanceUntilIdle()

        coVerify(exactly = 1) {
            chatHistoryDao.deleteMessagesSince("أحمد مصطفى", "BUSINESS", 0L)
        }
    }

    @Test
    fun `clearChat with LAST_HOUR calls deleteMessagesSince with timestamp 1 hour ago`() = runTest(testDispatcher) {
        val viewModel = ChatDetailViewModel(
            chatHistoryDao = chatHistoryDao,
            conversationMetadataDao = conversationMetadataDao,
            sendManualReplyUseCase = sendManualReplyUseCase,
            savedStateHandle = savedStateHandle,
            userPreferencesRepository = userPreferencesRepository
        )
        advanceUntilIdle()

        val fixedNow = 1_700_000_000_000L
        viewModel.clearChat(ClearChatTimeRange.LAST_HOUR, fixedNow)
        advanceUntilIdle()

        val expectedSince = fixedNow - (1 * 60 * 60 * 1000L)
        coVerify(exactly = 1) {
            chatHistoryDao.deleteMessagesSince("أحمد مصطفى", "BUSINESS", expectedSince)
        }
    }

    @Test
    fun `clearChat with LAST_TWO_HOURS calls deleteMessagesSince with timestamp 2 hours ago`() = runTest(testDispatcher) {
        val viewModel = ChatDetailViewModel(
            chatHistoryDao = chatHistoryDao,
            conversationMetadataDao = conversationMetadataDao,
            sendManualReplyUseCase = sendManualReplyUseCase,
            savedStateHandle = savedStateHandle,
            userPreferencesRepository = userPreferencesRepository
        )
        advanceUntilIdle()

        val fixedNow = 1_700_000_000_000L
        viewModel.clearChat(ClearChatTimeRange.LAST_TWO_HOURS, fixedNow)
        advanceUntilIdle()

        val expectedSince = fixedNow - (2 * 60 * 60 * 1000L)
        coVerify(exactly = 1) {
            chatHistoryDao.deleteMessagesSince("أحمد مصطفى", "BUSINESS", expectedSince)
        }
    }

    @Test
    fun `clearChat with TODAY calls deleteMessagesSince with start of day timestamp`() = runTest(testDispatcher) {
        val viewModel = ChatDetailViewModel(
            chatHistoryDao = chatHistoryDao,
            conversationMetadataDao = conversationMetadataDao,
            sendManualReplyUseCase = sendManualReplyUseCase,
            savedStateHandle = savedStateHandle,
            userPreferencesRepository = userPreferencesRepository
        )
        advanceUntilIdle()

        val fixedNow = 1_700_000_000_000L
        viewModel.clearChat(ClearChatTimeRange.TODAY, fixedNow)
        advanceUntilIdle()

        val expectedSince = viewModel.calculateClearSinceTimestamp(ClearChatTimeRange.TODAY, fixedNow)
        coVerify(exactly = 1) {
            chatHistoryDao.deleteMessagesSince("أحمد مصطفى", "BUSINESS", expectedSince)
        }
    }

    @Test
    fun `clearChat when no messages remain deletes conversation metadata row`() = runTest(testDispatcher) {
        val viewModel = ChatDetailViewModel(
            chatHistoryDao = chatHistoryDao,
            conversationMetadataDao = conversationMetadataDao,
            sendManualReplyUseCase = sendManualReplyUseCase,
            savedStateHandle = savedStateHandle,
            userPreferencesRepository = userPreferencesRepository
        )
        advanceUntilIdle()

        coEvery { chatHistoryDao.getLatestMessageForSource("أحمد مصطفى", "BUSINESS") } returns null

        viewModel.clearChat(ClearChatTimeRange.ALL_TIME)
        advanceUntilIdle()

        coVerify(exactly = 1) {
            chatHistoryDao.deleteMessagesSince("أحمد مصطفى", "BUSINESS", 0L)
            conversationMetadataDao.deleteConversation("أحمد مصطفى", "BUSINESS")
        }
    }

    @Test
    fun `clearChat when earlier messages remain updates conversation metadata with latest remaining message`() = runTest(testDispatcher) {
        val viewModel = ChatDetailViewModel(
            chatHistoryDao = chatHistoryDao,
            conversationMetadataDao = conversationMetadataDao,
            sendManualReplyUseCase = sendManualReplyUseCase,
            savedStateHandle = savedStateHandle,
            userPreferencesRepository = userPreferencesRepository
        )
        advanceUntilIdle()

        val remainingMessage = ChatHistoryEntity(
            id = 42L,
            contactName = "أحمد مصطفى",
            sourceClass = "BUSINESS",
            messageText = "رسالة سابقة متبقية",
            isFromUser = true,
            timestamp = 1_699_000_000_000L,
            senderType = "CUSTOMER"
        )
        coEvery { chatHistoryDao.getLatestMessageForSource("أحمد مصطفى", "BUSINESS") } returns remainingMessage

        val fixedNow = 1_700_000_000_000L
        viewModel.clearChat(ClearChatTimeRange.LAST_HOUR, fixedNow)
        advanceUntilIdle()

        coVerify(exactly = 1) {
            conversationMetadataDao.updateLastMessage(
                contactName = "أحمد مصطفى",
                sourceClass = "BUSINESS",
                lastMessageText = "رسالة سابقة متبقية",
                lastMessageTimestamp = 1_699_000_000_000L,
                lastMessageSenderType = "CUSTOMER"
            )
        }
        coVerify(exactly = 0) {
            conversationMetadataDao.deleteConversation(any(), any())
        }
    }

    @Test
    fun `resolveAttention directly clears needsHumanAttention flag`() = runTest(testDispatcher) {
        val viewModel = ChatDetailViewModel(
            chatHistoryDao = chatHistoryDao,
            conversationMetadataDao = conversationMetadataDao,
            sendManualReplyUseCase = sendManualReplyUseCase,
            savedStateHandle = savedStateHandle,
            userPreferencesRepository = userPreferencesRepository
        )
        advanceUntilIdle()

        viewModel.resolveAttention()
        advanceUntilIdle()

        coVerify(exactly = 1) {
            conversationMetadataDao.setNeedsHumanAttention("أحمد مصطفى", "BUSINESS", false)
        }
    }

    @Test
    fun `voice note message strips raw prompt tag and marks isVoiceNote true`() = runTest(testDispatcher) {
        val voiceMsg = ChatHistoryEntity(
            id = 10L,
            contactName = "أحمد مصطفى",
            sourceClass = "BUSINESS",
            messageText = "[رسالة صوتية من العميل]: عايز أعرف تفاصيل المقاس واللون",
            isFromUser = true,
            timestamp = 5000L,
            senderType = "CUSTOMER"
        )
        every { chatHistoryDao.observeMessagesForContact("أحمد مصطفى", "BUSINESS") } returns flowOf(listOf(voiceMsg))

        val viewModel = ChatDetailViewModel(
            chatHistoryDao = chatHistoryDao,
            conversationMetadataDao = conversationMetadataDao,
            sendManualReplyUseCase = sendManualReplyUseCase,
            savedStateHandle = savedStateHandle,
            userPreferencesRepository = userPreferencesRepository
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.messages.size)
        assertEquals("عايز أعرف تفاصيل المقاس واللون", state.messages[0].text)
        assertTrue(state.messages[0].isVoiceNote)
    }

    @Test
    fun `message windowing limits initial display to 50 and loadMoreMessages expands limit`() = runTest(testDispatcher) {
        val manyMessages = (1..60).map { i ->
            ChatHistoryEntity(
                id = i.toLong(),
                contactName = "أحمد مصطفى",
                sourceClass = "BUSINESS",
                messageText = "رسالة رقم $i",
                isFromUser = i % 2 == 0,
                timestamp = i * 1000L,
                senderType = "CUSTOMER"
            )
        }
        every { chatHistoryDao.observeMessagesForContact("أحمد مصطفى", "BUSINESS") } returns flowOf(manyMessages)

        val viewModel = ChatDetailViewModel(
            chatHistoryDao = chatHistoryDao,
            conversationMetadataDao = conversationMetadataDao,
            sendManualReplyUseCase = sendManualReplyUseCase,
            savedStateHandle = savedStateHandle,
            userPreferencesRepository = userPreferencesRepository
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        advanceUntilIdle()

        val stateBefore = viewModel.uiState.value
        assertEquals(50, stateBefore.messages.size)
        assertTrue(stateBefore.hasMoreMessages)
        assertEquals(60, stateBefore.totalMessageCount)
        assertEquals("رسالة رقم 60", stateBefore.messages.last().text)

        viewModel.loadMoreMessages()
        advanceUntilIdle()

        val stateAfter = viewModel.uiState.value
        assertEquals(60, stateAfter.messages.size)
        assertFalse(stateAfter.hasMoreMessages)
    }

    @Test
    fun `active order is mapped and exposed in uiState`() = runTest(testDispatcher) {
        val orderDao = mockk<com.example.persona.core.database.dao.OrderDao>(relaxed = true)
        val activeOrder = com.example.persona.core.database.entity.OrderEntity(
            id = 501L,
            contactName = "أحمد مصطفى",
            status = "IN_PREPARATION",
            orderDetails = "2x قميص كلاسيك مقاس L",
            totalAmount = 650.0,
            deliveryAddress = "المعادي، القاهرة"
        )
        every { orderDao.observeAllOrders() } returns flowOf(listOf(activeOrder))

        val viewModel = ChatDetailViewModel(
            chatHistoryDao = chatHistoryDao,
            conversationMetadataDao = conversationMetadataDao,
            sendManualReplyUseCase = sendManualReplyUseCase,
            savedStateHandle = savedStateHandle,
            userPreferencesRepository = userPreferencesRepository,
            orderDao = orderDao
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        advanceUntilIdle()

        val state = viewModel.uiState.value
        val summary = state.activeOrder
        org.junit.Assert.assertNotNull(summary)
        assertEquals(501L, summary?.id)
        assertEquals("IN_PREPARATION", summary?.status)
        assertEquals("2x قميص كلاسيك مقاس L", summary?.orderDetails)
        assertEquals(650.0, summary?.totalAmount ?: 0.0, 0.01)
    }
}
