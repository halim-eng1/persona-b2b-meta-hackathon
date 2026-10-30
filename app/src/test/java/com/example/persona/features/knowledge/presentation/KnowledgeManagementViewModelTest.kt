package com.example.persona.features.knowledge.presentation

import com.example.persona.core.data.repository.UserPreferencesRepository
import com.example.persona.core.database.dao.FailedQueryDao
import com.example.persona.core.database.entity.BusinessKnowledgeEntity
import com.example.persona.core.database.entity.FailedQueryEntity
import com.example.persona.features.auto_reply.data.repository.SemanticRetrievalRepository
import com.example.persona.features.auto_reply.domain.gatekeeper.InboundIntent
import com.example.persona.features.auto_reply.domain.model.RetrievalCandidate
import com.example.persona.features.auto_reply.domain.repository.ReplyGenerationRepository
import com.example.persona.features.chat_import.data.embedding.EmbeddingEngine
import com.example.persona.features.knowledge.data.repository.KnowledgeRepository
import com.example.persona.features.knowledge.domain.model.KnowledgeEntry
import com.example.persona.features.knowledge.domain.usecase.AddManualKnowledgeUseCase
import com.example.persona.features.knowledge.domain.usecase.DeleteKnowledgeEntryUseCase
import com.example.persona.features.knowledge.domain.usecase.UpdateKnowledgeEntryUseCase
import com.example.persona.features.knowledge.presentation.model.SimulatorMediaType
import com.example.persona.features.knowledge.presentation.model.SimulatorSender
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class KnowledgeManagementViewModelTest {

    private val repository = mockk<KnowledgeRepository>()
    private val failedQueryDao = mockk<FailedQueryDao>()
    private val addManualKnowledgeUseCase = mockk<AddManualKnowledgeUseCase>()
    private val deleteKnowledgeEntryUseCase = mockk<DeleteKnowledgeEntryUseCase>()
    private val userPreferencesRepository = mockk<UserPreferencesRepository>(relaxed = true)
    private val updateKnowledgeEntryUseCase = mockk<UpdateKnowledgeEntryUseCase>(relaxed = true)
    private val semanticRetrievalRepository = mockk<SemanticRetrievalRepository>(relaxed = true)
    private val embeddingEngine = mockk<EmbeddingEngine>(relaxed = true)

    private val testDispatcher = StandardTestDispatcher()

    private val mockEntitiesFlow = flowOf(
        listOf(
            BusinessKnowledgeEntity(
                chunkId = "chunk_1",
                chunkText = "Fact 1",
                sourceType = "MANUAL_ENTRY",
                timestamp = 1000L
            ),
            BusinessKnowledgeEntity(
                chunkId = "chunk_2",
                chunkText = "[منتج]: سويت شيرت | [السعر]: 350 ج.م",
                sourceType = "MANUAL_ENTRY",
                timestamp = 2000L
            )
        )
    )

    private val mockQueriesFlow = flowOf(
        listOf(
            FailedQueryEntity(
                id = 42L,
                contactDisplayName = "John Doe",
                queryText = "What is shipping price?",
                bestScore = 0.50f,
                timestamp = 2000L
            )
        )
    )

    private fun createViewModel() = KnowledgeManagementViewModel(
        repository = repository,
        failedQueryDao = failedQueryDao,
        addManualKnowledgeUseCase = addManualKnowledgeUseCase,
        deleteKnowledgeEntryUseCase = deleteKnowledgeEntryUseCase,
        userPreferencesRepository = userPreferencesRepository,
        updateKnowledgeEntryUseCase = updateKnowledgeEntryUseCase,
        semanticRetrievalRepository = semanticRetrievalRepository,
        embeddingEngine = embeddingEngine
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        coEvery { repository.observeAllChunks() } returns mockEntitiesFlow
        coEvery { failedQueryDao.observeUnreviewedQueries() } returns mockQueriesFlow
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun init_loadsDataAndUpdatesState() = runTest {
        val viewModel = createViewModel()

        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(2, state.entries.size)
        assertEquals("Fact 1", state.entries[0].chunkText)
        assertEquals(1, state.unansweredQueries.size)
        assertEquals("What is shipping price?", state.unansweredQueries[0].queryText)
    }

    @Test
    fun onInputQueryChanged_updatesState() = runTest {
        val viewModel = createViewModel()

        viewModel.onInputQueryChanged("New Query")

        assertEquals("New Query", viewModel.uiState.value.inputQuery)
    }

    @Test
    fun addManualEntry_onSuccess_resetsInput() = runTest {
        val viewModel = createViewModel()
        coEvery { addManualKnowledgeUseCase("Valid Fact", false) } returns true

        viewModel.onInputQueryChanged("Valid Fact")
        viewModel.addManualEntry()

        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("", viewModel.uiState.value.inputQuery)
        assertFalse(viewModel.uiState.value.isProcessing)
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun addManualEntry_onFailure_setsErrorMessage() = runTest {
        val viewModel = createViewModel()
        coEvery { addManualKnowledgeUseCase("Valid Fact", false) } returns false

        viewModel.onInputQueryChanged("Valid Fact")
        viewModel.addManualEntry()

        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("Valid Fact", viewModel.uiState.value.inputQuery)
        assertFalse(viewModel.uiState.value.isProcessing)
        assertNotNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun deleteEntry_callsUseCase() = runTest {
        val viewModel = createViewModel()
        coEvery { deleteKnowledgeEntryUseCase("chunk_1") } just Runs

        viewModel.deleteEntry("chunk_1")

        testDispatcher.scheduler.advanceUntilIdle()
        coVerify(exactly = 1) { deleteKnowledgeEntryUseCase("chunk_1") }
    }

    @Test
    fun onSaveEditedEntry_callsUpdateUseCase() = runTest {
        val viewModel = createViewModel()
        coEvery { updateKnowledgeEntryUseCase(any(), any(), any(), any(), any()) } returns true

        viewModel.onSaveEditedEntry("chunk_1", "Updated Fact Text")
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { updateKnowledgeEntryUseCase("chunk_1", "Updated Fact Text") }
        assertNull(viewModel.uiState.value.editingEntry)
    }

    @Test
    fun onSaveEditedEntry_preservesCoreDocumentSourceTypeAndHeaderContext() = runTest {
        val viewModel = createViewModel()
        val coreEntry = KnowledgeEntry(
            chunkId = "chunk_core_1",
            headerContext = "سياسة الشحن",
            chunkText = "شحن مجاني للطلبات فوق 500 جنيه",
            sourceType = "CORE_DOCUMENT",
            timestamp = 1000L
        )
        viewModel.onEditEntry(coreEntry)
        coEvery { updateKnowledgeEntryUseCase(any(), any(), any(), any(), any()) } returns true

        viewModel.onSaveEditedEntry("chunk_core_1", "شحن مجاني للطلبات فوق 600 جنيه")
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) {
            updateKnowledgeEntryUseCase(
                chunkId = "chunk_core_1",
                newText = "شحن مجاني للطلبات فوق 600 جنيه",
                sourceType = "CORE_DOCUMENT",
                headerContext = "سياسة الشحن"
            )
        }
        assertNull(viewModel.uiState.value.editingEntry)
    }

    @Test
    fun saveAnswerForFailedQuery_persistsStructuredQAAndMarksReviewed() = runTest {
        val viewModel = createViewModel()
        coEvery { addManualKnowledgeUseCase(any(), false) } returns true
        coEvery { failedQueryDao.markReviewed(42L) } just Runs

        viewModel.saveAnswerForFailedQuery(
            queryId = 42L,
            queryText = "بكام الشحن لأسيوط؟",
            answerText = "الشحن 60 جنيه",
            isTemporaryOffer = false
        )
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) {
            addManualKnowledgeUseCase("سؤال: بكام الشحن لأسيوط؟\nإجابة: الشحن 60 جنيه", false)
        }
        coVerify(exactly = 1) { failedQueryDao.markReviewed(42L) }
        assertNull(viewModel.uiState.value.answeringQuery)
    }

    @Test
    fun runSimulation_executesRetrievalAndUpdatesState() = runTest {
        val viewModel = createViewModel()
        val mockVector = FloatArray(384) { 0.1f }
        val mockCandidate = RetrievalCandidate(
            chunkId = "chunk_2",
            headerContext = "الكتالوج",
            chunkText = "[منتج]: سويت شيرت | [السعر]: 350 ج.م",
            similarityScore = 0.88f
        )

        coEvery { embeddingEngine.embed("بكام السويت شيرت؟") } returns mockVector
        coEvery {
            semanticRetrievalRepository.retrieveCandidates(
                queryEmbedding = mockVector,
                limit = 5,
                minimumSimilarityThreshold = any(),
                queryText = "بكام السويت شيرت؟"
            )
        } returns Pair(listOf(mockCandidate), 0.88f)

        viewModel.runSimulation("بكام السويت شيرت؟")
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isSimulating)
        assertEquals("[منتج]: سويت شيرت | [السعر]: 350 ج.م", state.simulatedResponse)
        assertEquals(1, state.simulationResults.size)
        assertEquals(0.88f, state.simulationBestScore, 0.001f)
    }

    @Test
    fun runSimulation_multiTurn_preservesChatHistoryAndExecutionTrace() = runTest {
        val viewModel = createViewModel()
        val mockVector = FloatArray(384) { 0.1f }
        val mockCandidate1 = RetrievalCandidate(
            chunkId = "chunk_2",
            headerContext = "الكتالوج",
            chunkText = "[منتج]: سويت شيرت | [السعر]: 350 ج.م",
            similarityScore = 0.90f
        )
        val mockCandidate2 = RetrievalCandidate(
            chunkId = "chunk_1",
            headerContext = "الشحن",
            chunkText = "الشحن للقاهرة 50 جنيه",
            similarityScore = 0.85f
        )

        coEvery { embeddingEngine.embed("بكام السويت شيرت؟") } returns mockVector
        coEvery { embeddingEngine.embed("ومصاريف الشحن كام؟") } returns mockVector
        coEvery {
            semanticRetrievalRepository.retrieveCandidates(
                queryEmbedding = mockVector,
                limit = 5,
                minimumSimilarityThreshold = any(),
                queryText = "بكام السويت شيرت؟"
            )
        } returns Pair(listOf(mockCandidate1), 0.90f)
        coEvery {
            semanticRetrievalRepository.retrieveCandidates(
                queryEmbedding = mockVector,
                limit = 5,
                minimumSimilarityThreshold = any(),
                queryText = "ومصاريف الشحن كام؟"
            )
        } returns Pair(listOf(mockCandidate2), 0.85f)

        // Turn 1
        viewModel.runSimulation("بكام السويت شيرت؟")
        testDispatcher.scheduler.advanceUntilIdle()

        var state = viewModel.uiState.value
        assertEquals(2, state.simulatorMessages.size)
        assertEquals(SimulatorSender.USER, state.simulatorMessages[0].sender)
        assertEquals("بكام السويت شيرت؟", state.simulatorMessages[0].text)
        assertEquals(SimulatorSender.BOT, state.simulatorMessages[1].sender)
        assertNotNull(state.simulatorMessages[1].trace)
        assertEquals(1, state.simulatorMessages[1].trace?.ragCandidates?.size)

        // Turn 2
        viewModel.runSimulation("ومصاريف الشحن كام؟")
        testDispatcher.scheduler.advanceUntilIdle()

        state = viewModel.uiState.value
        assertEquals(4, state.simulatorMessages.size)
        assertEquals(SimulatorSender.USER, state.simulatorMessages[2].sender)
        assertEquals("ومصاريف الشحن كام؟", state.simulatorMessages[2].text)
        assertEquals(SimulatorSender.BOT, state.simulatorMessages[3].sender)
        assertNotNull(state.simulatorMessages[3].trace)
        assertEquals("الشحن للقاهرة 50 جنيه", state.simulatorMessages[3].text)
    }

    @Test
    fun onClearSimulatorHistory_clearsMessagesAndSession() = runTest {
        val viewModel = createViewModel()
        val mockVector = FloatArray(384) { 0.1f }
        coEvery { embeddingEngine.embed(any()) } returns mockVector
        coEvery { semanticRetrievalRepository.retrieveCandidates(any(), any(), any(), any()) } returns Pair(emptyList(), 0f)

        viewModel.runSimulation("مرحبا")
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.simulatorMessages.isEmpty())

        viewModel.onClearSimulatorHistory()

        val state = viewModel.uiState.value
        assertTrue(state.simulatorMessages.isEmpty())
        assertNull(state.simulatedResponse)
        assertTrue(state.simulationResults.isEmpty())
        assertEquals(0f, state.simulationBestScore, 0.001f)
    }

    @Test
    fun simulatorMediaSelection_andExecution_updatesStateAndTrace() = runTest {
        val viewModel = createViewModel()

        viewModel.onSimulatorMediaSelected("content://media/image_123.jpg", SimulatorMediaType.IMAGE)
        assertEquals("content://media/image_123.jpg", viewModel.uiState.value.selectedMediaUri)
        assertEquals(SimulatorMediaType.IMAGE, viewModel.uiState.value.selectedMediaType)

        val mockVector = FloatArray(384) { 0.1f }
        coEvery { embeddingEngine.embed(any()) } returns mockVector
        coEvery { semanticRetrievalRepository.retrieveCandidates(any(), any(), any(), any()) } returns Pair(emptyList(), 0f)

        viewModel.runSimulation("عايز زي ده")
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.selectedMediaUri)
        assertNull(state.selectedMediaType)

        assertEquals(2, state.simulatorMessages.size)
        val userMsg = state.simulatorMessages[0]
        assertEquals("content://media/image_123.jpg", userMsg.mediaUri)
        assertEquals(SimulatorMediaType.IMAGE, userMsg.mediaType)

        val botMsg = state.simulatorMessages[1]
        assertNotNull(botMsg.trace)
        assertEquals(InboundIntent.CATALOG_INQUIRY, botMsg.trace?.intent)
        assertEquals("FastPath: Image Present", botMsg.trace?.intentSourceTier)
    }

    @Test
    fun filtering_worksBySearchQueryAndCategory() = runTest {
        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(2, viewModel.uiState.value.filteredEntries.size)

        viewModel.onSearchQueryChanged("سويت شيرت")
        assertEquals(1, viewModel.uiState.value.filteredEntries.size)
        assertEquals("chunk_2", viewModel.uiState.value.filteredEntries[0].chunkId)

        viewModel.onSearchQueryChanged("")
        viewModel.onFilterSelected("PRODUCT")
        assertEquals(1, viewModel.uiState.value.filteredEntries.size)
        assertEquals("chunk_2", viewModel.uiState.value.filteredEntries[0].chunkId)
    }

    @Test
    fun markQueryReviewed_callsDao() = runTest {
        val viewModel = createViewModel()
        coEvery { failedQueryDao.markReviewed(42L) } just Runs

        viewModel.markQueryReviewed(42L)

        testDispatcher.scheduler.advanceUntilIdle()
        coVerify(exactly = 1) { failedQueryDao.markReviewed(42L) }
    }

    @Test
    fun useQueryAsInput_updatesQueryText() = runTest {
        val viewModel = createViewModel()

        viewModel.useQueryAsInput("Query Text")

        assertEquals("Query Text", viewModel.uiState.value.inputQuery)
    }

    @Test
    fun deleteQuery_callsDao() = runTest {
        val viewModel = createViewModel()
        coEvery { failedQueryDao.deleteById(42L) } just Runs

        viewModel.deleteQuery(42L)

        testDispatcher.scheduler.advanceUntilIdle()
        coVerify(exactly = 1) { failedQueryDao.deleteById(42L) }
    }

    @Test
    fun clearAllKnowledge_clearsRepositoryTopicsAndState() = runTest {
        coEvery { repository.clearAllKnowledge() } just Runs
        coEvery { userPreferencesRepository.clearStoreKnowledgeTopics() } just Runs
        coEvery { userPreferencesRepository.clearStoreWebsite() } just Runs

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.clearAllKnowledge()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { repository.clearAllKnowledge() }
        coVerify(exactly = 1) { userPreferencesRepository.clearStoreKnowledgeTopics() }
        coVerify(exactly = 1) { userPreferencesRepository.clearStoreWebsite() }
        assertTrue(viewModel.uiState.value.entries.isEmpty())
        assertFalse(viewModel.uiState.value.isProcessing)
    }

    @Test
    fun runSimulation_whenQueryVeryLong_truncatesSanitizesAndDoesNotCrash() = runTest {
        val viewModel = createViewModel()
        val mockVector = FloatArray(384) { 0.1f }
        coEvery { embeddingEngine.embed(any()) } returns mockVector
        coEvery { semanticRetrievalRepository.retrieveCandidates(any(), any(), any(), any()) } returns Pair(emptyList(), 0f)

        val longQuery = "أ".repeat(4500)
        viewModel.runSimulation(longQuery)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isSimulating)
        assertEquals(2, state.simulatorMessages.size)
        // User message should be capped at 3000 characters
        assertEquals(3000, state.simulatorMessages[0].text.length)
        assertNotNull(state.simulatorMessages[1].trace)
    }

    @Test
    fun runSimulation_whenZeroCandidates_handlesGracefullyWithoutCrashing() = runTest {
        val viewModel = createViewModel()
        val mockVector = FloatArray(384) { 0.1f }
        coEvery { embeddingEngine.embed(any()) } returns mockVector
        coEvery { semanticRetrievalRepository.retrieveCandidates(any(), any(), any(), any()) } returns Pair(emptyList(), 0f)

        viewModel.runSimulation("سؤال غير موجود بالمرة في أي مكان")
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isSimulating)
        assertEquals(2, state.simulatorMessages.size)
        assertEquals("لم يتم العثور على رد مطابق في كتالوج المتجر.", state.simulatedResponse)
        assertTrue(state.simulationResults.isEmpty())
        assertEquals(0f, state.simulationBestScore, 0.001f)
    }

    @Test
    fun runSimulation_whenHitlActionReturned_handlesGracefullyWithHitlExplanation() = runTest {
        val mockReplyRepo = mockk<ReplyGenerationRepository>()
        val viewModel = KnowledgeManagementViewModel(
            repository = repository,
            failedQueryDao = failedQueryDao,
            addManualKnowledgeUseCase = addManualKnowledgeUseCase,
            deleteKnowledgeEntryUseCase = deleteKnowledgeEntryUseCase,
            userPreferencesRepository = userPreferencesRepository,
            updateKnowledgeEntryUseCase = updateKnowledgeEntryUseCase,
            semanticRetrievalRepository = semanticRetrievalRepository,
            embeddingEngine = embeddingEngine,
            replyGenerationRepository = mockReplyRepo
        )

        val mockVector = FloatArray(384) { 0.1f }
        coEvery { embeddingEngine.embed(any()) } returns mockVector
        coEvery { semanticRetrievalRepository.retrieveCandidates(any(), any(), any(), any()) } returns Pair(emptyList(), 0f)
        coEvery {
            mockReplyRepo.generateReply(
                notification = any(),
                candidates = any(),
                isOngoingConversation = any(),
                chatHistory = any(),
                activeOrders = any(),
                personalChunks = any(),
                maxTokens = any(),
                lastOrder = any(),
                orderStatusCounts = any(),
                inboundIntent = any()
            )
        } returns """{"action":"HITL","reason":"Requires human owner confirmation"}"""

        viewModel.runSimulation("ممكن خصم 80% عشان أنا صاحب المحل؟")
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isSimulating)
        assertEquals(2, state.simulatorMessages.size)
        val botMsg = state.simulatorMessages[1]
        assertEquals(SimulatorSender.BOT, botMsg.sender)
        assertTrue(botMsg.text.contains("للمراجعة البشرية"))
        assertEquals("HITL", botMsg.trace?.llmAction)
    }
}
