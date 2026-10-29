package com.example.persona.features.audit_log.presentation

import com.example.persona.features.audit_log.domain.model.AuditLogItem
import com.example.persona.features.audit_log.domain.repository.AuditLogRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Instant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuditLogViewModelTest {

    private val auditLogRepository = mockk<AuditLogRepository>()
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initialization loads audit items and sets loading to false`() = runTest(testDispatcher) {
        val sampleItem = AuditLogItem(
            id = 1L,
            contactName = "كريم",
            incomingMessage = "أريد شراء منتج",
            generatedReply = "أهلاً بك، تفضل الرابط",
            similarityScore = 0.95f,
            wasAutoSent = true,
            timestamp = Instant.fromEpochMilliseconds(1730000000000L)
        )
        every { auditLogRepository.observeLogs(limit = 200) } returns flowOf(listOf(sampleItem))

        val viewModel = AuditLogViewModel(auditLogRepository)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(1, state.items.size)
        assertEquals("كريم", state.items.first().contactName)
    }
}
