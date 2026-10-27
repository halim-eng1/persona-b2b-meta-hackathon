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
        every { userPreferencesRepository.botName } returns flowOf(UserPreferencesRepository.DEFAULT_BOT_NAME)
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
}
