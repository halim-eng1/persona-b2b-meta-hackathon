package com.example.persona.features.chats.presentation.list

import com.example.persona.core.database.dao.ConversationMetadataDao
import com.example.persona.core.database.entity.ConversationMetadataEntity
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatsListViewModelTest {

    private val conversationMetadataDao = mockk<ConversationMetadataDao>()
    private val testDispatcher = StandardTestDispatcher()

    private val conv1 = ConversationMetadataEntity(
        contactName = "عميل 1",
        sourceClass = "BUSINESS",
        lastMessageText = "السعر كام؟",
        lastMessageSenderType = "CUSTOMER",
        lastMessageTimestamp = 1000L,
        unreadCount = 2,
        needsHumanAttention = false,
        isAiPaused = false
    )

    private val conv2 = ConversationMetadataEntity(
        contactName = "عميل 2",
        sourceClass = "MAIN_APP",
        lastMessageText = "السعر 250 جنيه",
        lastMessageSenderType = "AI_AGENT",
        lastMessageTimestamp = 2000L,
        unreadCount = 0,
        needsHumanAttention = true,
        isAiPaused = false
    )

    private val conv3 = ConversationMetadataEntity(
        contactName = "عميل 3",
        sourceClass = "CLONE",
        lastMessageText = "تم إرسال الشحنة",
        lastMessageSenderType = "MERCHANT_MANUAL",
        lastMessageTimestamp = 3000L,
        unreadCount = 0,
        needsHumanAttention = false,
        isAiPaused = true
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { conversationMetadataDao.observeAllConversations() } returns flowOf(listOf(conv1, conv2, conv3))
        every { conversationMetadataDao.observeTotalUnreadCount() } returns flowOf(2)
        every { conversationMetadataDao.observeTotalAttentionCount() } returns flowOf(1)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state emits all conversations with correct sender prefixes`() = runTest(testDispatcher) {
        val viewModel = ChatsListViewModel(conversationMetadataDao)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(3, state.conversations.size)
        assertEquals(2, state.totalUnreadCount)
        assertEquals(1, state.attentionCount)

        // Customer message has no prefix
        assertEquals("السعر كام؟", state.conversations[0].lastMessageSnippet)
        // AI message has 🤖: prefix
        assertEquals("🤖: السعر 250 جنيه", state.conversations[1].lastMessageSnippet)
        // Merchant manual message has 👤 You: prefix
        assertEquals("👤 You: تم إرسال الشحنة", state.conversations[2].lastMessageSnippet)
    }

    @Test
    fun `filtering by BUSINESS returns only business conversations`() = runTest(testDispatcher) {
        val viewModel = ChatsListViewModel(conversationMetadataDao)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        advanceUntilIdle()

        viewModel.onFilterSelected(ChatFilter.BUSINESS)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.conversations.size)
        assertEquals("عميل 1", state.conversations[0].contactName)
        assertEquals("Business", state.conversations[0].sourceLabel)
    }

    @Test
    fun `filtering by NEEDS_ATTENTION returns unread or flagged chats`() = runTest(testDispatcher) {
        val viewModel = ChatsListViewModel(conversationMetadataDao)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        advanceUntilIdle()

        viewModel.onFilterSelected(ChatFilter.NEEDS_ATTENTION)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        // conv1 has unreadCount = 2, conv2 has needsHumanAttention = true
        assertEquals(2, state.conversations.size)
        assertTrue(state.conversations.any { it.contactName == "عميل 1" })
        assertTrue(state.conversations.any { it.contactName == "عميل 2" })
    }

    @Test
    fun `search query filters conversations by contact name and message text`() = runTest(testDispatcher) {
        val viewModel = ChatsListViewModel(conversationMetadataDao)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        advanceUntilIdle()

        viewModel.onSearchQueryChanged("عميل 3")
        advanceUntilIdle()

        val stateByName = viewModel.uiState.value
        assertEquals(1, stateByName.conversations.size)
        assertEquals("عميل 3", stateByName.conversations[0].contactName)

        viewModel.onSearchQueryChanged("250")
        advanceUntilIdle()

        val stateByText = viewModel.uiState.value
        assertEquals(1, stateByText.conversations.size)
        assertEquals("عميل 2", stateByText.conversations[0].contactName)
    }
}
