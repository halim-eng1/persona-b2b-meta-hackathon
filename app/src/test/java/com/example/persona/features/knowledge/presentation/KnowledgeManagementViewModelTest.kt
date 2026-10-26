package com.example.persona.features.knowledge.presentation

import com.example.persona.core.database.dao.FailedQueryDao
import com.example.persona.core.database.entity.BusinessKnowledgeEntity
import com.example.persona.core.database.entity.FailedQueryEntity
import com.example.persona.features.knowledge.data.repository.KnowledgeRepository
import com.example.persona.features.knowledge.domain.usecase.AddManualKnowledgeUseCase
import com.example.persona.features.knowledge.domain.usecase.DeleteKnowledgeEntryUseCase
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
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

    private val testDispatcher = StandardTestDispatcher()

    private val mockEntitiesFlow = flowOf(
        listOf(
            BusinessKnowledgeEntity(
                chunkId = "chunk_1",
                chunkText = "Fact 1",
                sourceType = "MANUAL_ENTRY",
                timestamp = 1000L
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
        val viewModel = KnowledgeManagementViewModel(
            repository,
            failedQueryDao,
            addManualKnowledgeUseCase,
            deleteKnowledgeEntryUseCase
        )

        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.entries.size)
        assertEquals("Fact 1", state.entries[0].chunkText)
        assertEquals(1, state.unansweredQueries.size)
        assertEquals("What is shipping price?", state.unansweredQueries[0].queryText)
    }

    @Test
    fun onInputQueryChanged_updatesState() = runTest {
        val viewModel = KnowledgeManagementViewModel(
            repository,
            failedQueryDao,
            addManualKnowledgeUseCase,
            deleteKnowledgeEntryUseCase
        )

        viewModel.onInputQueryChanged("New Query")

        assertEquals("New Query", viewModel.uiState.value.inputQuery)
    }

    @Test
    fun addManualEntry_onSuccess_resetsInput() = runTest {
        val viewModel = KnowledgeManagementViewModel(
            repository,
            failedQueryDao,
            addManualKnowledgeUseCase,
            deleteKnowledgeEntryUseCase
        )
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
        val viewModel = KnowledgeManagementViewModel(
            repository,
            failedQueryDao,
            addManualKnowledgeUseCase,
            deleteKnowledgeEntryUseCase
        )
        coEvery { addManualKnowledgeUseCase("Valid Fact", false) } returns false

        viewModel.onInputQueryChanged("Valid Fact")
        viewModel.addManualEntry()

        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("Valid Fact", viewModel.uiState.value.inputQuery)
        assertFalse(viewModel.uiState.value.isProcessing)
        assertEquals("Failed to generate embedding or save entry.", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun deleteEntry_callsUseCase() = runTest {
        val viewModel = KnowledgeManagementViewModel(
            repository,
            failedQueryDao,
            addManualKnowledgeUseCase,
            deleteKnowledgeEntryUseCase
        )
        coEvery { deleteKnowledgeEntryUseCase("chunk_1") } just Runs

        viewModel.deleteEntry("chunk_1")

        testDispatcher.scheduler.advanceUntilIdle()
        coVerify(exactly = 1) { deleteKnowledgeEntryUseCase("chunk_1") }
    }

    @Test
    fun markQueryReviewed_callsDao() = runTest {
        val viewModel = KnowledgeManagementViewModel(
            repository,
            failedQueryDao,
            addManualKnowledgeUseCase,
            deleteKnowledgeEntryUseCase
        )
        coEvery { failedQueryDao.markReviewed(42L) } just Runs

        viewModel.markQueryReviewed(42L)

        testDispatcher.scheduler.advanceUntilIdle()
        coVerify(exactly = 1) { failedQueryDao.markReviewed(42L) }
    }

    @Test
    fun useQueryAsInput_updatesQueryText() = runTest {
        val viewModel = KnowledgeManagementViewModel(
            repository,
            failedQueryDao,
            addManualKnowledgeUseCase,
            deleteKnowledgeEntryUseCase
        )

        viewModel.useQueryAsInput("Query Text")

        assertEquals("Query Text", viewModel.uiState.value.inputQuery)
    }

    @Test
    fun deleteQuery_callsDao() = runTest {
        val viewModel = KnowledgeManagementViewModel(
            repository,
            failedQueryDao,
            addManualKnowledgeUseCase,
            deleteKnowledgeEntryUseCase
        )
        coEvery { failedQueryDao.deleteById(42L) } just Runs

        viewModel.deleteQuery(42L)

        testDispatcher.scheduler.advanceUntilIdle()
        coVerify(exactly = 1) { failedQueryDao.deleteById(42L) }
    }
}
