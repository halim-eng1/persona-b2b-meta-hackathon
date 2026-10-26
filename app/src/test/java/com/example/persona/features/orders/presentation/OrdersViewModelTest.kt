package com.example.persona.features.orders.presentation

import com.example.persona.core.database.dao.ConversationMetadataDao
import com.example.persona.core.database.entity.OrderEntity
import com.example.persona.core.database.entity.OrderStatus
import com.example.persona.features.orders.domain.repository.OrderRepository
import com.example.persona.features.orders.presentation.components.formatCourierManifest
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
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
class OrdersViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeRepository: FakeOrderRepository
    private lateinit var viewModel: OrdersViewModel
    private val conversationMetadataDao = mockk<ConversationMetadataDao>(relaxed = true)

    private val order1 = OrderEntity(
        id = 1L,
        contactName = "01012345678",
        customerNameFromLlm = "أحمد محمد",
        deliveryAddress = "القاهرة، المعادي، شارع 9",
        orderDetails = "2x تي شيرت قطن أسود مقاس L",
        customNotes = "الاستلام بعد الساعة 5",
        status = OrderStatus.PENDING.name
    )

    private val order2 = OrderEntity(
        id = 2L,
        contactName = "01198765432",
        customerNameFromLlm = "سارة علي",
        deliveryAddress = "الجيزة، الدقي، ميدان المساحة",
        orderDetails = "1x فستان صيفي أزرق M",
        status = OrderStatus.PENDING.name
    )

    private val order3 = OrderEntity(
        id = 3L,
        contactName = "01234567890",
        customerNameFromLlm = "محمود حسن",
        deliveryAddress = "الإسكندرية، سموحة",
        orderDetails = "3x قمصان كتان أبيض",
        merchantNote = "تم التغليف وجاهز للشحن",
        status = OrderStatus.IN_PREPARATION.name
    )

    private val order4 = OrderEntity(
        id = 4L,
        contactName = "01555555555",
        customerNameFromLlm = "عمر فاروق",
        deliveryAddress = "طوخ، القليوبية",
        orderDetails = "1x جاكيت جلد أسود XL",
        status = OrderStatus.SHIPPED.name
    )

    private val order5 = OrderEntity(
        id = 5L,
        contactName = "01000000000",
        customerNameFromLlm = "خالد إبراهيم",
        deliveryAddress = "المنصورة، الدقهلية",
        orderDetails = "حذاء كلاسيك أسود 43",
        status = OrderStatus.CANCELLED.name
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeRepository = FakeOrderRepository()
        fakeRepository.ordersFlow.value = listOf(order1, order2, order3, order4, order5)
        viewModel = OrdersViewModel(fakeRepository, conversationMetadataDao)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state emits all orders and computes correct metrics`() = runTest(testDispatcher) {
        val collectJob = launch { viewModel.uiState.collect() }
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(5, state.totalCount)
        assertEquals(5, state.ordersList.size)
        assertEquals(2, state.pendingCount)
        assertEquals(1, state.inPreparationCount)
        assertEquals(1, state.completedCount)
        assertEquals(1, state.cancelledCount)

        collectJob.cancel()
    }

    @Test
    fun `filter by PENDING shows only new orders`() = runTest(testDispatcher) {
        val collectJob = launch { viewModel.uiState.collect() }
        advanceUntilIdle()

        viewModel.onFilterSelected(OrderFilter.PENDING)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(OrderFilter.PENDING, state.selectedTab)
        assertEquals(2, state.ordersList.size)
        assertTrue(state.ordersList.all { it.status == OrderStatus.PENDING.name })

        collectJob.cancel()
    }

    @Test
    fun `filter by IN_PREPARATION shows only in prep orders`() = runTest(testDispatcher) {
        val collectJob = launch { viewModel.uiState.collect() }
        advanceUntilIdle()

        viewModel.onFilterSelected(OrderFilter.IN_PREPARATION)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(OrderFilter.IN_PREPARATION, state.selectedTab)
        assertEquals(1, state.ordersList.size)
        assertEquals(3L, state.ordersList.first().id)

        collectJob.cancel()
    }

    @Test
    fun `filter by COMPLETED shows fulfilled orders`() = runTest(testDispatcher) {
        val collectJob = launch { viewModel.uiState.collect() }
        advanceUntilIdle()

        viewModel.onFilterSelected(OrderFilter.COMPLETED)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(OrderFilter.COMPLETED, state.selectedTab)
        assertEquals(1, state.ordersList.size)
        assertEquals(4L, state.ordersList.first().id)

        collectJob.cancel()
    }

    @Test
    fun `filter by CANCELLED shows cancelled orders`() = runTest(testDispatcher) {
        val collectJob = launch { viewModel.uiState.collect() }
        advanceUntilIdle()

        viewModel.onFilterSelected(OrderFilter.CANCELLED)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(OrderFilter.CANCELLED, state.selectedTab)
        assertEquals(1, state.ordersList.size)
        assertEquals(5L, state.ordersList.first().id)

        collectJob.cancel()
    }

    @Test
    fun `search query filters orders by customer name, details, contact, or address`() = runTest(testDispatcher) {
        val collectJob = launch { viewModel.uiState.collect() }
        advanceUntilIdle()

        // Match address "المعادي"
        viewModel.onSearchQueryChanged("المعادي")
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.ordersList.size)
        assertEquals(1L, viewModel.uiState.value.ordersList.first().id)

        // Match customer name "سارة"
        viewModel.onSearchQueryChanged("سارة")
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.ordersList.size)
        assertEquals(2L, viewModel.uiState.value.ordersList.first().id)

        // Match items "تي شيرت"
        viewModel.onSearchQueryChanged("تي شيرت")
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.ordersList.size)
        assertEquals(1L, viewModel.uiState.value.ordersList.first().id)

        // Clear query
        viewModel.onSearchQueryChanged("")
        advanceUntilIdle()
        assertEquals(5, viewModel.uiState.value.ordersList.size)

        collectJob.cancel()
    }

    @Test
    fun `advanceOrderStatus transitions PENDING to IN_PREPARATION`() = runTest(testDispatcher) {
        val collectJob = launch { viewModel.uiState.collect() }
        advanceUntilIdle()

        viewModel.advanceOrderStatus(order1)
        advanceUntilIdle()

        assertEquals(1, fakeRepository.updatedStatuses.size)
        assertEquals(1L to OrderStatus.IN_PREPARATION, fakeRepository.updatedStatuses.first())

        collectJob.cancel()
    }

    @Test
    fun `advanceOrderStatus transitions IN_PREPARATION to SHIPPED`() = runTest(testDispatcher) {
        val collectJob = launch { viewModel.uiState.collect() }
        advanceUntilIdle()

        viewModel.advanceOrderStatus(order3)
        advanceUntilIdle()

        assertEquals(1, fakeRepository.updatedStatuses.size)
        assertEquals(3L to OrderStatus.SHIPPED, fakeRepository.updatedStatuses.first())

        collectJob.cancel()
    }

    @Test
    fun `onStatusChanged updates order status in repository`() = runTest(testDispatcher) {
        val collectJob = launch { viewModel.uiState.collect() }
        advanceUntilIdle()

        viewModel.onStatusChanged(2L, OrderStatus.CANCELLED)
        advanceUntilIdle()

        assertEquals(1, fakeRepository.updatedStatuses.size)
        assertEquals(2L to OrderStatus.CANCELLED, fakeRepository.updatedStatuses.first())

        collectJob.cancel()
    }

    @Test
    fun `onMerchantNoteSaved trims and maps blank note to null`() = runTest(testDispatcher) {
        val collectJob = launch { viewModel.uiState.collect() }
        advanceUntilIdle()

        viewModel.onMerchantNoteSaved(1L, "  المندوب في الطريق  ")
        advanceUntilIdle()
        assertEquals("المندوب في الطريق", fakeRepository.updatedNotes.first().second)

        viewModel.onMerchantNoteSaved(1L, "    ")
        advanceUntilIdle()
        assertNull(fakeRepository.updatedNotes.last().second)

        collectJob.cancel()
    }

    @Test
    fun `formatCourierManifest produces structured Egyptian courier text`() {
        val manifest = formatCourierManifest(order1)

        assertTrue(manifest.contains("بيانات الشحن والتوصيل"))
        assertTrue(manifest.contains("أحمد محمد"))
        assertTrue(manifest.contains("01012345678"))
        assertTrue(manifest.contains("المعادي"))
        assertTrue(manifest.contains("2x تي شيرت"))
        assertTrue(manifest.contains("الاستلام بعد الساعة 5"))
        // Human-readable Arabic status (never the raw enum)
        assertTrue(manifest.contains("الحالة: طلب جديد"))

        val manifestInPrep = formatCourierManifest(order3)
        assertTrue(manifestInPrep.contains("الحالة: قيد التجهيز"))
    }

    @Test
    fun `COMPLETED chip count equals the items the COMPLETED tab displays`() = runTest(testDispatcher) {
        // Dedicated fixtures so existing metrics assertions are untouched:
        // order4 is SHIPPED (fulfilled), here RETURNED is added as the second fulfilled state.
        val order6 = OrderEntity(
            id = 6L,
            contactName = "01677777777",
            orderDetails = "مرتجع",
            status = OrderStatus.RETURNED.name
        )
        val localRepo = FakeOrderRepository()
        localRepo.ordersFlow.value = listOf(order1, order2, order3, order4, order5, order6)
        val localViewModel = OrdersViewModel(localRepo, conversationMetadataDao)
        val collectJob = launch { localViewModel.uiState.collect() }
        advanceUntilIdle()

        // completedCount counts SHIPPED + RETURNED, exactly OrderFilter.COMPLETED's state set
        assertEquals(2, localViewModel.uiState.value.completedCount)
        localViewModel.onFilterSelected(OrderFilter.COMPLETED)
        advanceUntilIdle()
        val completedOrders = localViewModel.uiState.value.ordersList
        assertEquals(2, completedOrders.size)
        assertTrue(completedOrders.all { it.status in listOf(OrderStatus.SHIPPED.name, OrderStatus.RETURNED.name) })

        collectJob.cancel()
    }

    @Test
    fun `getChatSourceClassForContact resolves real source and falls back to BUSINESS`() = runTest(testDispatcher) {
        val collectJob = launch { viewModel.uiState.collect() }
        advanceUntilIdle()

        coEvery { conversationMetadataDao.getLatestConversationForContact("01198765432") } returns mockk {
            every { sourceClass } returns "MAIN_APP"
        }
        coEvery { conversationMetadataDao.getLatestConversationForContact("العميل المفقود") } returns null

        assertEquals("MAIN_APP", viewModel.getChatSourceClassForContact("01198765432"))
        assertEquals("BUSINESS", viewModel.getChatSourceClassForContact("العميل المفقود"))

        collectJob.cancel()
    }

    private class FakeOrderRepository : OrderRepository {
        val ordersFlow = MutableStateFlow<List<OrderEntity>>(emptyList())
        val updatedStatuses = mutableListOf<Pair<Long, OrderStatus>>()
        val updatedNotes = mutableListOf<Pair<Long, String?>>()

        override fun getAllOrders(): Flow<List<OrderEntity>> = ordersFlow

        override suspend fun updateOrderStatus(orderId: Long, status: OrderStatus) {
            updatedStatuses.add(orderId to status)
            ordersFlow.value = ordersFlow.value.map {
                if (it.id == orderId) it.copy(status = status.name) else it
            }
        }

        override suspend fun updateMerchantNote(orderId: Long, note: String?) {
            updatedNotes.add(orderId to note)
            ordersFlow.value = ordersFlow.value.map {
                if (it.id == orderId) it.copy(merchantNote = note) else it
            }
        }
    }
}
