package com.example.persona.features.home.presentation

import com.example.persona.core.data.repository.UserPreferencesRepository
import com.example.persona.core.database.dao.BusinessKnowledgeDao
import com.example.persona.core.database.dao.ConversationMetadataDao
import com.example.persona.core.database.entity.ConversationMetadataEntity
import com.example.persona.features.auto_reply.domain.model.AuditLogItem
import com.example.persona.features.auto_reply.domain.repository.AuditLogRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Instant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val userPreferencesRepository = mockk<UserPreferencesRepository>(relaxed = true)
    private val auditLogRepository = mockk<AuditLogRepository>()
    private val businessKnowledgeDao = mockk<BusinessKnowledgeDao>()
    private val conversationMetadataDao = mockk<ConversationMetadataDao>(relaxed = true)

    private val isAutoReplyFlow = MutableStateFlow(true)
    private val replyCountFlow = MutableStateFlow(42)
    private val chunkCountFlow = MutableStateFlow(15)
    private val recentLogsFlow = MutableStateFlow<List<AuditLogItem>>(emptyList())

    private lateinit var viewModel: HomeViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        every { userPreferencesRepository.isAutoReplyEnabled } returns isAutoReplyFlow
        every { auditLogRepository.observeReplyCount() } returns replyCountFlow
        every { auditLogRepository.observeLogs(limit = 3) } returns recentLogsFlow
        every { businessKnowledgeDao.observeChunkCount() } returns chunkCountFlow

        viewModel = HomeViewModel(
            userPreferencesRepository = userPreferencesRepository,
            auditLogRepository = auditLogRepository,
            businessKnowledgeDao = businessKnowledgeDao,
            conversationMetadataDao = conversationMetadataDao
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `uiState combines all sources and sets isLoading to false`() = runTest(testDispatcher) {
        val collectJob = launch { viewModel.uiState.collect() }
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertTrue(state.isAutoReplyEnabled)
        assertEquals(42, state.totalRepliesSent)
        assertEquals(15, state.knowledgeChunkCount)
        assertTrue(state.recentActivity.isEmpty())

        // Never calls the heavy observeAllConversations
        verify(exactly = 0) { conversationMetadataDao.observeAllConversations() }

        collectJob.cancel()
    }

    @Test
    fun `recentActivity resolves sourceClass via targeted contact lookup`() = runTest(testDispatcher) {
        val log1 = AuditLogItem(
            id = 1L,
            contactName = "01012345678",
            incomingMessage = "Hi",
            generatedReply = "Hello",
            similarityScore = 0.9f,
            wasAutoSent = true,
            timestamp = Instant.fromEpochMilliseconds(1000L),
            sourceClass = "BUSINESS"
        )
        val log2 = AuditLogItem(
            id = 2L,
            contactName = "01198765432",
            incomingMessage = "Price?",
            generatedReply = "100 EGP",
            similarityScore = 0.85f,
            wasAutoSent = true,
            timestamp = Instant.fromEpochMilliseconds(2000L),
            sourceClass = "BUSINESS"
        )

        coEvery { conversationMetadataDao.getLatestConversationForContact("01012345678") } returns mockk {
            every { sourceClass } returns "MAIN_APP"
        }
        coEvery { conversationMetadataDao.getLatestConversationForContact("01198765432") } returns null

        val collectJob = launch { viewModel.uiState.collect() }
        advanceUntilIdle()

        recentLogsFlow.value = listOf(log1, log2)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(2, state.recentActivity.size)
        assertEquals("MAIN_APP", state.recentActivity[0].sourceClass)
        assertEquals("BUSINESS", state.recentActivity[1].sourceClass)

        coVerify(exactly = 1) { conversationMetadataDao.getLatestConversationForContact("01012345678") }
        coVerify(exactly = 1) { conversationMetadataDao.getLatestConversationForContact("01198765432") }
        verify(exactly = 0) { conversationMetadataDao.observeAllConversations() }

        collectJob.cancel()
    }

    @Test
    fun `setAutoReplyEnabled delegates to userPreferencesRepository`() = runTest(testDispatcher) {
        viewModel.setAutoReplyEnabled(false)
        advanceUntilIdle()

        coVerify(exactly = 1) { userPreferencesRepository.setAutoReplyEnabled(false) }
    }
}
