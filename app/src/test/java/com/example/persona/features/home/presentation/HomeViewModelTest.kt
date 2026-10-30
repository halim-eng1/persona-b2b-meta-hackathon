package com.example.persona.features.home.presentation

import com.example.persona.core.data.repository.UserPreferencesRepository
import com.example.persona.core.database.dao.BusinessKnowledgeDao
import com.example.persona.core.database.dao.ConversationMetadataDao
import com.example.persona.core.database.dao.OrderDao
import com.example.persona.core.database.entity.ConversationMetadataEntity
import com.example.persona.core.database.entity.OrderEntity
import com.example.persona.features.audit_log.domain.model.AuditLogItem
import com.example.persona.features.audit_log.domain.repository.AuditLogRepository
import com.example.persona.features.auto_reply.domain.usecase.ToggleAutoReplyUseCase
import com.example.persona.features.onboarding.data.PermissionChecker
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
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val userPreferencesRepository = mockk<UserPreferencesRepository>(relaxed = true)
    private val toggleAutoReplyUseCase = mockk<ToggleAutoReplyUseCase>(relaxed = true)
    private val auditLogRepository = mockk<AuditLogRepository>()
    private val businessKnowledgeDao = mockk<BusinessKnowledgeDao>()
    private val conversationMetadataDao = mockk<ConversationMetadataDao>(relaxed = true)
    private val orderDao = mockk<OrderDao>()
    private val permissionChecker = mockk<PermissionChecker>(relaxed = true)

    private val isAutoReplyFlow = MutableStateFlow(true)
    private val replyCountFlow = MutableStateFlow(42)
    private val chunkCountFlow = MutableStateFlow(15)
    private val recentLogsFlow = MutableStateFlow<List<AuditLogItem>>(emptyList())
    private val allLogsFlow = MutableStateFlow<List<AuditLogItem>>(emptyList())
    private val attentionCountFlow = MutableStateFlow(0)
    private val needsAttentionConversationsFlow = MutableStateFlow<List<ConversationMetadataEntity>>(emptyList())
    private val allOrdersFlow = MutableStateFlow<List<OrderEntity>>(emptyList())

    private lateinit var viewModel: HomeViewModel

    private val fixedClockMillis = LocalDate.of(2026, 10, 30)
        .atTime(12, 0)
        .atZone(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        every { userPreferencesRepository.isAutoReplyEnabled } returns isAutoReplyFlow
        every { auditLogRepository.observeReplyCount() } returns replyCountFlow
        every { auditLogRepository.observeLogs(limit = 3) } returns recentLogsFlow
        every { auditLogRepository.observeLogs(limit = 200) } returns allLogsFlow
        every { businessKnowledgeDao.observeChunkCount() } returns chunkCountFlow
        every { conversationMetadataDao.observeTotalAttentionCount() } returns attentionCountFlow
        every { conversationMetadataDao.observeNeedsAttentionConversations() } returns needsAttentionConversationsFlow
        every { orderDao.observeAllOrders() } returns allOrdersFlow
        every { permissionChecker.isNotificationListenerEnabled() } returns true

        viewModel = HomeViewModel(
            userPreferencesRepository = userPreferencesRepository,
            toggleAutoReplyUseCase = toggleAutoReplyUseCase,
            auditLogRepository = auditLogRepository,
            businessKnowledgeDao = businessKnowledgeDao,
            conversationMetadataDao = conversationMetadataDao,
            orderDao = orderDao,
            permissionChecker = permissionChecker
        ).apply {
            clockEpochMillis = { fixedClockMillis }
            serviceAliveChecker = { true }
        }
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
        assertEquals(SystemHealthStatus.ACTIVE, state.systemHealth)
        assertEquals(42, state.totalRepliesSent)
        assertEquals(15, state.knowledgeChunkCount)
        assertTrue(state.recentActivity.isEmpty())
        assertEquals(0, state.pendingAttentionCount)
        assertEquals(0, state.todayOrdersCount)
        assertEquals(0.0, state.todayPendingRevenue, 0.001)

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
            timestamp = Instant.fromEpochMilliseconds(fixedClockMillis - 1000L),
            sourceClass = "BUSINESS"
        )
        val log2 = AuditLogItem(
            id = 2L,
            contactName = "01198765432",
            incomingMessage = "Price?",
            generatedReply = "100 EGP",
            similarityScore = 0.85f,
            wasAutoSent = true,
            timestamp = Instant.fromEpochMilliseconds(fixedClockMillis - 500L),
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

        collectJob.cancel()
    }

    @Test
    fun `setAutoReplyEnabled delegates to toggleAutoReplyUseCase`() = runTest(testDispatcher) {
        viewModel.setAutoReplyEnabled(false)
        advanceUntilIdle()

        coVerify(exactly = 1) { toggleAutoReplyUseCase.invoke(false) }
    }

    @Test
    fun `emergencyKillSwitch delegates to toggleAutoReplyUseCase with false`() = runTest(testDispatcher) {
        viewModel.emergencyKillSwitch()
        advanceUntilIdle()

        coVerify(exactly = 1) { toggleAutoReplyUseCase.invoke(false) }
    }

    @Test
    fun `systemHealth reflects PERMISSION_MISSING when notification permission is not granted`() = runTest(testDispatcher) {
        every { permissionChecker.isNotificationListenerEnabled() } returns false

        val collectJob = launch { viewModel.uiState.collect() }
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(SystemHealthStatus.PERMISSION_MISSING, state.systemHealth)

        collectJob.cancel()
    }

    @Test
    fun `systemHealth reflects SERVICE_KILLED when enabled and permitted but service is dead`() = runTest(testDispatcher) {
        viewModel.serviceAliveChecker = { false }

        val collectJob = launch { viewModel.uiState.collect() }
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(SystemHealthStatus.SERVICE_KILLED, state.systemHealth)

        collectJob.cancel()
    }

    @Test
    fun `systemHealth reflects PAUSED when isAutoReplyEnabled is false`() = runTest(testDispatcher) {
        isAutoReplyFlow.value = false

        val collectJob = launch { viewModel.uiState.collect() }
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(SystemHealthStatus.PAUSED, state.systemHealth)

        collectJob.cancel()
    }

    @Test
    fun `pendingAttentionCount and waitingCustomers observe dao updates`() = runTest(testDispatcher) {
        val collectJob = launch { viewModel.uiState.collect() }
        advanceUntilIdle()

        val customerEntity = ConversationMetadataEntity(
            contactName = "الحاج محمود",
            sourceClass = "BUSINESS",
            isAiPaused = true,
            needsHumanAttention = true,
            lastNotificationKey = "notif_1",
            lastMessageText = "عايز 50 دستة ضروري",
            lastMessageSenderType = "CUSTOMER",
            lastMessageTimestamp = fixedClockMillis - 2000L,
            unreadCount = 2
        )

        attentionCountFlow.value = 3
        needsAttentionConversationsFlow.value = listOf(customerEntity)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(3, state.pendingAttentionCount)
        assertEquals(3, state.waitingCustomersCount)
        assertEquals(1, state.waitingCustomers.size)
        assertEquals("الحاج محمود", state.waitingCustomers[0].contactName)
        assertEquals("عايز 50 دستة ضروري", state.waitingCustomers[0].lastMessage)

        collectJob.cancel()
    }

    @Test
    fun `daily B2B metrics compute today orders and pending revenue correctly`() = runTest(testDispatcher) {
        val collectJob = launch { viewModel.uiState.collect() }
        advanceUntilIdle()

        val todayStartMillis = LocalDate.of(2026, 10, 30)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        val orderTodayPending = OrderEntity(
            id = 101L,
            contactName = "عميل 1",
            totalAmount = 550.0,
            status = "PENDING",
            timestamp = todayStartMillis + 3600_000L
        )
        val orderTodayPrep = OrderEntity(
            id = 102L,
            contactName = "عميل 2",
            totalAmount = 300.0,
            status = "IN_PREPARATION",
            timestamp = todayStartMillis + 7200_000L
        )
        val orderTodayDelivered = OrderEntity(
            id = 103L,
            contactName = "عميل 3",
            totalAmount = 1000.0,
            status = "DELIVERED",
            timestamp = todayStartMillis + 10800_000L
        )
        val orderYesterday = OrderEntity(
            id = 104L,
            contactName = "عميل قديم",
            totalAmount = 800.0,
            status = "PENDING",
            timestamp = todayStartMillis - 3600_000L // Yesterday
        )

        allOrdersFlow.value = listOf(orderTodayPending, orderTodayPrep, orderTodayDelivered, orderYesterday)

        val logTodayAuto = AuditLogItem(
            id = 201L,
            contactName = "عميل 1",
            incomingMessage = "بكام الشحن؟",
            generatedReply = "الشحن 50 جنيه",
            similarityScore = 0.9f,
            wasAutoSent = true,
            timestamp = Instant.fromEpochMilliseconds(todayStartMillis + 1000L)
        )
        val logTodayManual = AuditLogItem(
            id = 202L,
            contactName = "عميل 2",
            incomingMessage = "فين مقاس XL؟",
            generatedReply = "",
            similarityScore = 0.6f,
            wasAutoSent = false,
            timestamp = Instant.fromEpochMilliseconds(todayStartMillis + 2000L)
        )
        val logYesterday = AuditLogItem(
            id = 203L,
            contactName = "عميل 3",
            incomingMessage = "عايز اطلب",
            generatedReply = "أهلاً بك",
            similarityScore = 0.95f,
            wasAutoSent = true,
            timestamp = Instant.fromEpochMilliseconds(todayStartMillis - 10000L)
        )

        allLogsFlow.value = listOf(logTodayAuto, logTodayManual, logYesterday)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        // Today orders: 101, 102, 103 = 3 orders
        assertEquals(3, state.todayOrdersCount)
        // Today pending revenue: 550.0 (PENDING) + 300.0 (IN_PREPARATION) = 850.0
        assertEquals(850.0, state.todayPendingRevenue, 0.001)
        // Today replies: only logTodayAuto was sent today and wasAutoSent=true
        assertEquals(1, state.todayRepliesCount)

        collectJob.cancel()
    }

    @Test
    fun `refreshHealth triggers re-evaluation of systemHealth`() = runTest(testDispatcher) {
        every { permissionChecker.isNotificationListenerEnabled() } returns false

        val collectJob = launch { viewModel.uiState.collect() }
        advanceUntilIdle()

        assertEquals(SystemHealthStatus.PERMISSION_MISSING, viewModel.uiState.value.systemHealth)

        // User grants permission in OS settings and returns to app
        every { permissionChecker.isNotificationListenerEnabled() } returns true
        viewModel.refreshHealth()
        advanceUntilIdle()

        assertEquals(SystemHealthStatus.ACTIVE, viewModel.uiState.value.systemHealth)

        collectJob.cancel()
    }

    @Test
    fun `daily B2B metrics gracefully handles lowercase and mixed-case order statuses`() = runTest(testDispatcher) {
        val collectJob = launch { viewModel.uiState.collect() }
        advanceUntilIdle()

        val todayStartMillis = LocalDate.of(2026, 10, 30)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        val order1 = OrderEntity(
            id = 301L,
            contactName = "عميل أ",
            totalAmount = 400.0,
            status = "pending", // lowercase
            timestamp = todayStartMillis + 1000L
        )
        val order2 = OrderEntity(
            id = 302L,
            contactName = "عميل ب",
            totalAmount = 250.0,
            status = "in_preparation", // lowercase
            timestamp = todayStartMillis + 2000L
        )

        allOrdersFlow.value = listOf(order1, order2)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(2, state.todayOrdersCount)
        assertEquals(650.0, state.todayPendingRevenue, 0.001)

        collectJob.cancel()
    }
}
