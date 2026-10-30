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
        customerPhone = "01012345678",
        deliveryAddress = "القاهرة، المعادي، شارع 9",
        orderDetails = "2x تي شيرت قطن أسود مقاس L",
        totalAmount = 300.0,
        shippingFee = 50.0,
        customNotes = "الاستلام بعد الساعة 5",
        status = OrderStatus.PENDING.name,
        timestamp = System.currentTimeMillis()
    )

    private val order2 = OrderEntity(
        id = 2L,
        contactName = "01198765432",
        customerNameFromLlm = "سارة علي",
        customerPhone = "01198765432",
        deliveryAddress = "الجيزة، الدقي، ميدان المساحة",
        orderDetails = "1x فستان صيفي أزرق M",
        totalAmount = 450.0,
        shippingFee = 40.0,
        status = OrderStatus.PENDING.name,
        timestamp = System.currentTimeMillis()
    )

    private val order3 = OrderEntity(
        id = 3L,
        contactName = "01234567890",
        customerNameFromLlm = "محمود حسن",
        customerPhone = "01234567890",
        deliveryAddress = "الإسكندرية، سموحة",
        orderDetails = "3x قمصان كتان أبيض",
        merchantNote = "تم التغليف وجاهز للشحن",
        totalAmount = 600.0,
        shippingFee = 60.0,
        status = OrderStatus.IN_PREPARATION.name,
        timestamp = System.currentTimeMillis()
    )

    private val order4 = OrderEntity(
        id = 4L,
        contactName = "01555555555",
        customerNameFromLlm = "عمر فاروق",
        customerPhone = "01555555555",
        deliveryAddress = "طوخ، القليوبية",
        orderDetails = "1x جاكيت جلد أسود XL",
        totalAmount = 800.0,
        shippingFee = 50.0,
        status = OrderStatus.SHIPPED.name,
        timestamp = System.currentTimeMillis()
    )

    private val order5 = OrderEntity(
        id = 5L,
        contactName = "01000000000",
        customerNameFromLlm = "خالد إبراهيم",
        customerPhone = "01000000000",
        deliveryAddress = "المنصورة، الدقهلية",
        orderDetails = "حذاء كلاسيك أسود 43",
        status = OrderStatus.CANCELLED.name,
        cancellationReason = "العميل غير رأيه",
        timestamp = System.currentTimeMillis()
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
        assertEquals(0, state.returnedCount)

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
    fun `filter by COMPLETED shows strictly fulfilled orders excluding returned`() = runTest(testDispatcher) {
        val returnedOrder = OrderEntity(
            id = 6L,
            contactName = "01677777777",
            status = OrderStatus.RETURNED.name
        )
        fakeRepository.ordersFlow.value = listOf(order1, order2, order3, order4, order5, returnedOrder)

        val collectJob = launch { viewModel.uiState.collect() }
        advanceUntilIdle()

        // completedCount counts strictly SHIPPED (not RETURNED)
        assertEquals(1, viewModel.uiState.value.completedCount)
        assertEquals(1, viewModel.uiState.value.returnedCount)

        viewModel.onFilterSelected(OrderFilter.COMPLETED)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(OrderFilter.COMPLETED, state.selectedTab)
        assertEquals(1, state.ordersList.size)
        assertEquals(4L, state.ordersList.first().id)
        assertTrue(state.ordersList.all { it.status == OrderStatus.SHIPPED.name })

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
    fun `createOrder inserts manual order into repository`() = runTest(testDispatcher) {
        val collectJob = launch { viewModel.uiState.collect() }
        advanceUntilIdle()

        viewModel.createOrder(
            customerName = "يوسف عادل",
            customerPhone = "01099991111",
            deliveryAddress = "شبرا، القاهرة",
            governorate = "القاهرة",
            orderDetails = "ساعة يد كلاسيك",
            totalAmount = 650.0,
            shippingFee = 40.0
        )
        advanceUntilIdle()

        assertEquals(1, fakeRepository.createdOrders.size)
        val created = fakeRepository.createdOrders.first()
        assertEquals("يوسف عادل", created.customerNameFromLlm)
        assertEquals("01099991111", created.customerPhone)
        assertEquals("القاهرة", created.governorate)
        assertEquals(650.0, created.totalAmount, 0.01)
        assertEquals(OrderStatus.PENDING.name, created.status)

        collectJob.cancel()
    }

    @Test
    fun `updateOrder persists edited details into repository`() = runTest(testDispatcher) {
        val collectJob = launch { viewModel.uiState.collect() }
        advanceUntilIdle()

        viewModel.updateOrder(
            orderId = 1L,
            customerName = "أحمد محمد المعدل",
            customerPhone = "01012345678",
            deliveryAddress = "المعادي الجديدة",
            governorate = "القاهرة",
            orderDetails = "3x تي شيرت قطن أبيض",
            totalAmount = 450.0,
            shippingFee = 50.0
        )
        advanceUntilIdle()

        assertEquals(1, fakeRepository.updatedOrders.size)
        val updated = fakeRepository.updatedOrders.first()
        assertEquals(1L, updated.first)
        assertEquals("أحمد محمد المعدل", updated.second)
        assertEquals(450.0, updated.third, 0.01)

        collectJob.cancel()
    }

    @Test
    fun `cancelOrder sets status and mandatory cancellation reason`() = runTest(testDispatcher) {
        val collectJob = launch { viewModel.uiState.collect() }
        advanceUntilIdle()

        viewModel.cancelOrder(1L, "العميل لم يرد على مكالمة التأكيد")
        advanceUntilIdle()

        assertEquals(1, fakeRepository.cancelledOrders.size)
        assertEquals(1L to "العميل لم يرد على مكالمة التأكيد", fakeRepository.cancelledOrders.first())

        collectJob.cancel()
    }

    @Test
    fun `multi selection and bulk status advance`() = runTest(testDispatcher) {
        val collectJob = launch { viewModel.uiState.collect() }
        advanceUntilIdle()

        viewModel.toggleOrderSelection(1L)
        viewModel.toggleOrderSelection(2L)
        advanceUntilIdle()

        assertEquals(2, viewModel.uiState.value.selectedOrderIds.size)
        assertTrue(viewModel.uiState.value.isMultiSelectMode)

        viewModel.bulkAdvanceStatus()
        advanceUntilIdle()

        assertEquals(0, viewModel.uiState.value.selectedOrderIds.size)
        assertFalse(viewModel.uiState.value.isMultiSelectMode)

        collectJob.cancel()
    }

    @Test
    fun `exportSelectedBulkManifest exports all selected orders`() = runTest(testDispatcher) {
        val collectJob = launch { viewModel.uiState.collect() }
        advanceUntilIdle()

        viewModel.toggleOrderSelection(1L)
        advanceUntilIdle()

        val manifest = viewModel.exportSelectedBulkManifest()
        assertTrue(manifest.contains("كشف تسليم الشحنات المجمع"))
        assertTrue(manifest.contains("أحمد محمد"))

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
        assertTrue(manifest.contains("المطلوب تحصيله (COD): 350 ج.م (شامل الشحن)"))
        assertTrue(manifest.contains("الحالة: طلب جديد"))
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
        val createdOrders = mutableListOf<OrderEntity>()
        val updatedOrders = mutableListOf<Triple<Long, String?, Double>>()
        val cancelledOrders = mutableListOf<Pair<Long, String>>()

        override fun getAllOrders(): Flow<List<OrderEntity>> = ordersFlow

        override suspend fun createOrder(order: OrderEntity): Long {
            createdOrders.add(order)
            val newId = (ordersFlow.value.maxOfOrNull { it.id } ?: 0L) + 1L
            val created = order.copy(id = newId)
            ordersFlow.value = ordersFlow.value + created
            return newId
        }

        override suspend fun updateOrder(
            orderId: Long,
            name: String?,
            phone: String,
            address: String?,
            details: String?,
            total: Double,
            shipping: Double,
            governorate: String?
        ) {
            updatedOrders.add(Triple(orderId, name, total))
            ordersFlow.value = ordersFlow.value.map {
                if (it.id == orderId) {
                    it.copy(
                        customerNameFromLlm = name,
                        customerPhone = phone,
                        deliveryAddress = address,
                        orderDetails = details,
                        totalAmount = total,
                        shippingFee = shipping,
                        governorate = governorate
                    )
                } else it
            }
        }

        override suspend fun updateOrderStatus(orderId: Long, status: OrderStatus) {
            updatedStatuses.add(orderId to status)
            ordersFlow.value = ordersFlow.value.map {
                if (it.id == orderId) it.copy(status = status.name) else it
            }
        }

        override suspend fun cancelOrder(orderId: Long, reason: String) {
            cancelledOrders.add(orderId to reason)
            ordersFlow.value = ordersFlow.value.map {
                if (it.id == orderId) it.copy(status = OrderStatus.CANCELLED.name, cancellationReason = reason) else it
            }
        }

        override suspend fun updateMerchantNote(orderId: Long, note: String?) {
            updatedNotes.add(orderId to note)
            ordersFlow.value = ordersFlow.value.map {
                if (it.id == orderId) it.copy(merchantNote = note) else it
            }
        }

        override suspend fun updateOrderStatusBulk(orderIds: List<Long>, status: OrderStatus) {
            ordersFlow.value = ordersFlow.value.map {
                if (it.id in orderIds) it.copy(status = status.name) else it
            }
        }

        override suspend fun deleteOrder(orderId: Long) {
            ordersFlow.value = ordersFlow.value.filterNot { it.id == orderId }
        }
    }
}
