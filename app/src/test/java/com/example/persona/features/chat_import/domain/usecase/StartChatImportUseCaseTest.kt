package com.example.persona.features.chat_import.domain.usecase

import android.net.Uri
import androidx.work.WorkInfo
import com.example.persona.core.common.AppException
import com.example.persona.core.common.Resource
import com.example.persona.core.data.repository.UserPreferencesRepository
import com.example.persona.core.database.dao.LinkedContactDao
import com.example.persona.features.chat_import.data.repository.ChatImportRepository
import io.mockk.coEvery
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
    private val linkedContactDao = mockk<LinkedContactDao>()
    private val prefsRepository = mockk<UserPreferencesRepository>()
    private lateinit var useCase: StartChatImportUseCase
    private val mockUri = mockk<Uri>()

    @Before
    fun setUp() {
        useCase = StartChatImportUseCase(repository, linkedContactDao, prefsRepository)
    }

    @Test
    fun invoke_whenOwnerNameNotConfigured_returnsError() = runTest {
        coEvery { prefsRepository.getOwnerNameSync() } returns ""

        val result = useCase(mockUri)

        assertTrue(result is Resource.Error)
        val exception = (result as Resource.Error).exception
        assertTrue(exception is AppException.OwnerNameNotConfigured)
    }

    // TODO: Uncomment when contact limits are re-enabled in Phase 4
    /*
    @Test
    fun invoke_whenLimitReached_returnsError() = runTest {
        coEvery { prefsRepository.getOwnerNameSync() } returns "User"
        coEvery { linkedContactDao.getActiveContactCount() } returns 1

        val result = useCase(mockUri, maxContacts = 1)

        assertTrue(result is Resource.Error)
        val exception = (result as Resource.Error).exception
        assertTrue(exception is AppException.ContactLimitReached)
        assertEquals(1, (exception as AppException.ContactLimitReached).limit)
    }
    */

    @Test
    fun invoke_whenOwnerNameConfigured_startsImportAndReturnsSuccess() = runTest {
        coEvery { prefsRepository.getOwnerNameSync() } returns "User"
        coEvery { linkedContactDao.getActiveContactCount() } returns 0
        val mockFlow = mockk<Flow<WorkInfo?>>()
        every { repository.startImport(mockUri) } returns mockFlow

        val result = useCase(mockUri, maxContacts = 1)

        assertTrue(result is Resource.Success)
        assertEquals(mockFlow, (result as Resource.Success).data)
    }
}
