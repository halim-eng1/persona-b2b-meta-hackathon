package com.example.persona.features.chats.presentation.detail

import androidx.lifecycle.SavedStateHandle
import com.example.persona.core.database.dao.ChatHistoryDao
import com.example.persona.core.database.dao.ConversationMetadataDao
import com.example.persona.core.database.entity.ChatHistoryEntity
import com.example.persona.core.database.entity.ConversationMetadataEntity
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
        every { chatHistoryDao.observeMessagesForContact("أحمد مصطفى", "BUSINESS") } returns flowOf(listOf(msg1, msg2))
        every { conversationMetadataDao.observeAllConversations() } returns flowOf(listOf(convMeta))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initialization marks messages and conversation as read`() = runTest(testDispatcher) {
        val viewModel = ChatDetailViewModel(
            chatHistoryDao = chatHistoryDao,
            conversationMetadataDao = conversationMetadataDao,
            sendManualReplyUseCase = sendManualReplyUseCase,
            savedStateHandle = savedStateHandle
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
            savedStateHandle = savedStateHandle
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
            savedStateHandle = savedStateHandle
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
            savedStateHandle = savedStateHandle
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
}
