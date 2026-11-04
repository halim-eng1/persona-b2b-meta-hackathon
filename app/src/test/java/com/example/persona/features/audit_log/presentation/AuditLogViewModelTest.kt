package com.example.persona.features.audit_log.presentation

import com.example.persona.features.audit_log.domain.model.AuditLogItem
import com.example.persona.features.audit_log.domain.repository.AuditLogRepository
import io.mockk.every
import io.mockk.mockk
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
class AuditLogViewModelTest {

    private val auditLogRepository = mockk<AuditLogRepository>()
    private val testDispatcher = StandardTestDispatcher()
    private val logsFlow = MutableStateFlow<List<AuditLogItem>>(emptyList())

    private val sampleItem1 = AuditLogItem(
        id = 1L,
        contactName = "كريم",
        incomingMessage = "عايز اشتري قميص مقاس L",
        generatedReply = "أهلاً بك، متوفر بسعر 200 ج.م",
        similarityScore = 0.95f,
        wasAutoSent = true,
        timestamp = Instant.fromEpochMilliseconds(1730000000000L),
        gatekeeperTier = "RULE",
        providerModel = "Meta / muse-spark-1.3",
        latencyMs = 350L,
        status = "SUCCESS"
    )

    private val sampleItem2 = AuditLogItem(
        id = 2L,
        contactName = "سارة أحمد",
        incomingMessage = "فين الشحنة بتاعتي؟",
        generatedReply = "",
        similarityScore = 0.60f,
        wasAutoSent = false,
        timestamp = Instant.fromEpochMilliseconds(1730001000000L),
        gatekeeperTier = "HEURISTIC",
        providerModel = "Meta / muse-spark-1.2",
        latencyMs = 700L,
        status = "ESCALATED_HITL"
    )

    private val sampleItem3 = AuditLogItem(
        id = 3L,
        contactName = "محمود علي",
        incomingMessage = "عندكم خصم للجملة؟",
        generatedReply = "نعم، خصم 15% للطلبات فوق 10 قطع",
        similarityScore = 0.88f,
        wasAutoSent = true,
        timestamp = Instant.fromEpochMilliseconds(1730002000000L),
        gatekeeperTier = "MODEL",
        providerModel = "Meta / muse-spark-1.3-contributor",
        latencyMs = 950L,
        status = "SUCCESS"
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { auditLogRepository.observeLogs(limit = 200) } returns logsFlow
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initialization loads audit items and sets loading to false`() = runTest(testDispatcher) {
        logsFlow.value = listOf(sampleItem1)
        val viewModel = AuditLogViewModel(auditLogRepository)
        val collectJob = launch { viewModel.uiState.collect() }
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(1, state.items.size)
        assertEquals(1, state.filteredItems.size)
        assertEquals("كريم", state.items.first().contactName)

        collectJob.cancel()
    }

    @Test
    fun `search query filters items by contactName, incomingMessage, and generatedReply`() = runTest(testDispatcher) {
        logsFlow.value = listOf(sampleItem1, sampleItem2, sampleItem3)
        val viewModel = AuditLogViewModel(auditLogRepository)
        val collectJob = launch { viewModel.uiState.collect() }
        advanceUntilIdle()

        // 1. Search by contact name (exact and normalized Arabic)
        viewModel.onSearchQueryChange("سارة")
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.filteredItems.size)
        assertEquals("سارة أحمد", viewModel.uiState.value.filteredItems.first().contactName)

        // 1b. Search with Teh Marbuta -> Heh normalization ("ساره" matches "سارة أحمد")
        viewModel.onSearchQueryChange("ساره")
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.filteredItems.size)
        assertEquals("سارة أحمد", viewModel.uiState.value.filteredItems.first().contactName)

        // 1c. Search with Alef Hamza normalization ("احمد" matches "أحمد")
        viewModel.onSearchQueryChange("احمد")
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.filteredItems.size)
        assertEquals("سارة أحمد", viewModel.uiState.value.filteredItems.first().contactName)

        // 2. Search by incoming message keyword (Egyptian dialect "فين")
        viewModel.onSearchQueryChange("فين")
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.filteredItems.size)
        assertEquals(2L, viewModel.uiState.value.filteredItems.first().id)

        // 2b. Search by Egyptian dialect synonym ("عاوز" matches "عايز")
        viewModel.onSearchQueryChange("عاوز")
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.filteredItems.size)
        assertEquals(1L, viewModel.uiState.value.filteredItems.first().id)

        // 2c. Search by Egyptian shipping keyword ("شحن")
        viewModel.onSearchQueryChange("شحن")
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.filteredItems.size)
        assertEquals(2L, viewModel.uiState.value.filteredItems.first().id)

        // 3. Search by generated reply keyword
        viewModel.onSearchQueryChange("خصم 15%")
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.filteredItems.size)
        assertEquals(3L, viewModel.uiState.value.filteredItems.first().id)

        // 4. Clear search
        viewModel.onSearchQueryChange("")
        advanceUntilIdle()
        assertEquals(3, viewModel.uiState.value.filteredItems.size)

        collectJob.cancel()
    }

    @Test
    fun `tier filter filters items by gatekeeperTier`() = runTest(testDispatcher) {
        logsFlow.value = listOf(sampleItem1, sampleItem2, sampleItem3)
        val viewModel = AuditLogViewModel(auditLogRepository)
        val collectJob = launch { viewModel.uiState.collect() }
        advanceUntilIdle()

        viewModel.onTierSelected("RULE")
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.filteredItems.size)
        assertEquals("RULE", viewModel.uiState.value.filteredItems.first().gatekeeperTier)

        viewModel.onTierSelected("MODEL")
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.filteredItems.size)
        assertEquals("MODEL", viewModel.uiState.value.filteredItems.first().gatekeeperTier)

        viewModel.onTierSelected("ALL")
        advanceUntilIdle()
        assertEquals(3, viewModel.uiState.value.filteredItems.size)

        collectJob.cancel()
    }

    @Test
    fun `provider filter filters items by providerModel substring`() = runTest(testDispatcher) {
        logsFlow.value = listOf(sampleItem1, sampleItem2, sampleItem3)
        val viewModel = AuditLogViewModel(auditLogRepository)
        val collectJob = launch { viewModel.uiState.collect() }
        advanceUntilIdle()

        viewModel.onProviderSelected("muse-spark-1.3")
        advanceUntilIdle()
        assertEquals(2, viewModel.uiState.value.filteredItems.size)
        assertTrue(viewModel.uiState.value.filteredItems.all { it.providerModel.contains("muse-spark-1.3") })

        viewModel.onProviderSelected("muse-spark-1.2")
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.filteredItems.size)
        assertEquals("Meta / muse-spark-1.2", viewModel.uiState.value.filteredItems.first().providerModel)

        collectJob.cancel()
    }

    @Test
    fun `status filter filters items by status`() = runTest(testDispatcher) {
        logsFlow.value = listOf(sampleItem1, sampleItem2, sampleItem3)
        val viewModel = AuditLogViewModel(auditLogRepository)
        val collectJob = launch { viewModel.uiState.collect() }
        advanceUntilIdle()

        viewModel.onStatusSelected("ESCALATED_HITL")
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.filteredItems.size)
        assertEquals("ESCALATED_HITL", viewModel.uiState.value.filteredItems.first().status)

        viewModel.onStatusSelected("SUCCESS")
        advanceUntilIdle()
        assertEquals(2, viewModel.uiState.value.filteredItems.size)

        collectJob.cancel()
    }

    @Test
    fun `toggleExpand toggles expanded item id in set`() = runTest(testDispatcher) {
        val viewModel = AuditLogViewModel(auditLogRepository)
        val collectJob = launch { viewModel.uiState.collect() }
        advanceUntilIdle()

        viewModel.toggleExpand(101L)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.expandedItemIds.contains(101L))

        viewModel.toggleExpand(101L)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.expandedItemIds.contains(101L))

        collectJob.cancel()
    }

    @Test
    fun `exportAuditTrail exports valid CSV and JSON`() = runTest(testDispatcher) {
        logsFlow.value = listOf(sampleItem1)
        val viewModel = AuditLogViewModel(auditLogRepository)
        val collectJob = launch { viewModel.uiState.collect() }
        advanceUntilIdle()

        // 1. CSV Export
        val csv = viewModel.exportAuditTrail("CSV")
        assertTrue(csv.startsWith("ID,Timestamp,Contact,Status,Tier,Model,LatencyMs,Similarity,IncomingMessage,GeneratedReply"))
        assertTrue(csv.contains("كريم"))
        assertTrue(csv.contains("SUCCESS"))
        assertTrue(csv.contains("RULE"))

        // 2. JSON Export
        val json = viewModel.exportAuditTrail("JSON")
        assertTrue(json.startsWith("["))
        assertTrue(json.contains("\"contactName\": \"كريم\""))
        assertTrue(json.contains("\"status\": \"SUCCESS\""))
        assertTrue(json.endsWith("]"))

        // 3. Clear payload
        viewModel.clearExportPayload()
        advanceUntilIdle()
        assertEquals(null, viewModel.uiState.value.exportPayload)

        collectJob.cancel()
    }
}
