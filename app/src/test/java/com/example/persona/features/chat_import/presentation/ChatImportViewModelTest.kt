package com.example.persona.features.chat_import.presentation

import android.net.Uri
import androidx.work.Data
import androidx.work.WorkInfo
import com.example.persona.core.common.AppException
import com.example.persona.core.common.Resource
import com.example.persona.features.chat_import.data.repository.ChatImportRepository
import com.example.persona.features.chat_import.data.worker.ChatImportWorker
import com.example.persona.features.chat_import.domain.usecase.StartChatImportUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatImportViewModelTest {

    private val startImportUseCase = mockk<StartChatImportUseCase>()
    private val repository = mockk<ChatImportRepository>(relaxed = true)
    private val testDispatcher = UnconfinedTestDispatcher()
    private val mockUri = mockk<Uri>()

    private lateinit var viewModel: ChatImportViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = ChatImportViewModel(startImportUseCase, repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun onFilePicked_updatesSelectedFileUri() {
        viewModel.onFilePicked(mockUri)

        assertEquals(mockUri, viewModel.uiState.value.selectedFileUri)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun cancelImport_triggersRepositoryCancelAndUpdatesState() {
        viewModel.cancelImport()

        coVerify(exactly = 1) { repository.cancelImport() }
        assertFalse(viewModel.uiState.value.isImporting)
        assertTrue(viewModel.uiState.value.isCancelled)
    }

    @Test
    fun startImport_whenUseCaseReturnsError_updatesStateWithError() = runTest {
        viewModel.onFilePicked(mockUri)
        coEvery { startImportUseCase(mockUri, "User") } returns Resource.Error(AppException.ContactLimitReached(1))

        viewModel.startImport("User")

        assertFalse(viewModel.uiState.value.isImporting)
        assertEquals("Contact limit reached: 1", viewModel.uiState.value.error)
    }

    @Test
    fun startImport_whenUseCaseReturnsSuccess_observesWorkInfoUpdates() = runTest {
        viewModel.onFilePicked(mockUri)

        val workInfo = mockk<WorkInfo>()
        every { workInfo.state } returns WorkInfo.State.RUNNING
        every { workInfo.progress } returns Data.Builder()
            .putString(ChatImportWorker.KEY_PHASE, "EMBEDDING")
            .putInt(ChatImportWorker.KEY_CURRENT_BATCH, 2)
            .putInt(ChatImportWorker.KEY_TOTAL_BATCHES, 5)
            .putInt(ChatImportWorker.KEY_MESSAGES_PROCESSED, 400)
            .putInt(ChatImportWorker.KEY_TOTAL_MESSAGES, 1000)
            .build()

        coEvery { startImportUseCase(mockUri, "User") } returns Resource.Success(flowOf(workInfo))

        viewModel.startImport("User")

        val state = viewModel.uiState.value
        assertTrue(state.isImporting)
        assertEquals("EMBEDDING", state.currentPhase)
        assertEquals(2, state.currentBatch)
        assertEquals(5, state.totalBatches)
        assertEquals(400, state.messagesProcessed)
        assertEquals(1000, state.totalMessages)
        assertEquals(0.4f, state.progressFraction, 0.001f)
    }
}
