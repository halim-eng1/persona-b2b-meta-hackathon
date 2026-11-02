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
        viewModel.onFilePicked(mockUri, "knowledge_faq.md")

        assertEquals(mockUri, viewModel.uiState.value.selectedFileUri)
        assertEquals("knowledge_faq.md", viewModel.uiState.value.selectedFileName)
        assertNull(viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isOwnerNameMissing)
    }

    @Test
    fun onUnsupportedFilePicked_setsErrorAndClearsSelection() {
        viewModel.onFilePicked(mockUri, "invalid.pdf")

        viewModel.onUnsupportedFilePicked()

        assertNull(viewModel.uiState.value.selectedFileUri)
        assertNull(viewModel.uiState.value.selectedFileName)
        assertEquals(
            "نوع الملف غير مدعوم. اختر ملف بنسق .txt أو .md (Unsupported file type. Please pick a .txt or .md file.)",
            viewModel.uiState.value.error
        )
        assertFalse(viewModel.uiState.value.isOwnerNameMissing)
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
        coEvery { startImportUseCase(mockUri) } returns Resource.Error(AppException.ContactLimitReached(1))

        viewModel.startImport()

        assertFalse(viewModel.uiState.value.isImporting)
        assertEquals("Contact limit reached: 1", viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isOwnerNameMissing)
    }

    @Test
    fun startImport_whenUseCaseReturnsOwnerNameNotConfigured_updatesStateWithErrorAndFlag() = runTest {
        viewModel.onFilePicked(mockUri)
        coEvery { startImportUseCase(mockUri) } returns Resource.Error(AppException.OwnerNameNotConfigured)

        viewModel.startImport()

        assertFalse(viewModel.uiState.value.isImporting)
        assertEquals("Owner name not configured. Please set your WhatsApp display name in Settings.", viewModel.uiState.value.error)
        assertTrue(viewModel.uiState.value.isOwnerNameMissing)
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

        coEvery { startImportUseCase(mockUri) } returns Resource.Success(flowOf(workInfo))

        viewModel.startImport()

        val state = viewModel.uiState.value
        assertTrue(state.isImporting)
        assertEquals("EMBEDDING", state.currentPhase)
        assertEquals(2, state.currentBatch)
        assertEquals(5, state.totalBatches)
        assertEquals(400, state.messagesProcessed)
        assertEquals(1000, state.totalMessages)
        assertEquals(0.4f, state.progressFraction, 0.001f)
        assertFalse(state.isOwnerNameMissing)
    }

    @Test
    fun onDirectInputTextChanged_updatesDirectInputText() {
        viewModel.onDirectInputTextChanged("نص المعرفة التجريبي")

        assertEquals("نص المعرفة التجريبي", viewModel.uiState.value.directInputText)
        assertNull(viewModel.uiState.value.directIndexError)
        assertNull(viewModel.uiState.value.directIndexSuccessMessage)
    }

    @Test
    fun ingestDirectText_whenTextIsBlank_setsError() {
        viewModel.onDirectInputTextChanged("   ")
        viewModel.ingestDirectText()

        assertTrue(viewModel.uiState.value.directIndexError?.contains("blank", ignoreCase = true) == true ||
            viewModel.uiState.value.directIndexError?.contains("أولاً") == true)
        assertFalse(viewModel.uiState.value.isDirectIndexing)
    }

    @Test
    fun ingestDirectText_whenSuccessful_updatesSuccessMessageAndClearsInput() = runTest {
        val mockKnowledgeRepo = mockk<com.example.persona.features.knowledge.data.repository.KnowledgeRepository>()
        coEvery { mockKnowledgeRepo.ingestMarkdownDocument(any(), any(), any(), any()) } returns Result.success(5)

        val vm = ChatImportViewModel(
            startImportUseCase = startImportUseCase,
            repository = repository,
            knowledgeRepository = mockKnowledgeRepo
        )

        vm.onDirectInputTextChanged("# منتجات المتجر\n- تيشيرت قطن: 250 ج.م")
        vm.ingestDirectText()

        val state = vm.uiState.value
        assertFalse(state.isDirectIndexing)
        assertEquals("تمت فهرسة 5 مقطعاً بنجاح!", state.directIndexSuccessMessage)
        assertEquals("", state.directInputText)
        assertNull(state.directIndexError)
    }

    @Test
    fun ingestDirectText_whenAlreadyIndexing_doesNotLaunchSecondJob() = runTest {
        val mockKnowledgeRepo = mockk<com.example.persona.features.knowledge.data.repository.KnowledgeRepository>()
        coEvery { mockKnowledgeRepo.ingestMarkdownDocument(any(), any(), any(), any()) } returns Result.success(1)

        val vm = ChatImportViewModel(
            startImportUseCase = startImportUseCase,
            repository = repository,
            knowledgeRepository = mockKnowledgeRepo
        )

        vm.onDirectInputTextChanged("Valid Text")
        vm.ingestDirectText()

        coVerify(exactly = 1) { mockKnowledgeRepo.ingestMarkdownDocument(any(), any(), any(), any()) }
    }
}
