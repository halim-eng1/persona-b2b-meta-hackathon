package com.example.persona.features.chat_import.domain.usecase

import android.net.Uri
import androidx.work.WorkInfo
import com.example.persona.core.common.AppException
import com.example.persona.core.common.Resource
import com.example.persona.features.chat_import.data.repository.ChatImportRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class StartChatImportUseCaseTest {

    private val repository = mockk<ChatImportRepository>()
    private lateinit var useCase: StartChatImportUseCase
    private val mockUri = mockk<Uri>()

    @Before
    fun setUp() {
        useCase = StartChatImportUseCase(repository)
    }

    @Test
    fun invoke_startsImportAndReturnsSuccess() = runTest {
        val mockFlow = mockk<Flow<WorkInfo?>>()
        every { repository.startImport(mockUri) } returns mockFlow

        val result = useCase(mockUri)

        assertTrue(result is Resource.Success)
        assertEquals(mockFlow, (result as Resource.Success).data)
    }

    @Test
    fun invoke_whenRepositoryThrows_returnsError() = runTest {
        every { repository.startImport(mockUri) } throws RuntimeException("IO error")

        val result = useCase(mockUri)

        assertTrue(result is Resource.Error)
        val exception = (result as Resource.Error).exception
        assertTrue(exception is AppException.DatabaseError)
    }
}
