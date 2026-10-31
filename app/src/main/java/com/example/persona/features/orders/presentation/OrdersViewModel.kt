package com.example.persona.features.orders.presentation

import androidx.annotation.StringRes
import com.example.persona.R

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.persona.core.database.dao.ConversationMetadataDao
import com.example.persona.core.database.entity.OrderEntity
import com.example.persona.core.database.entity.OrderStatus
import com.example.persona.features.orders.domain.EgyptianOrderUtils
import com.example.persona.features.orders.domain.repository.OrderRepository
import com.example.persona.features.orders.domain.usecase.AdvanceOrderStatusUseCase
import com.example.persona.features.orders.domain.usecase.ExportBulkManifestUseCase
import com.example.persona.features.orders.domain.usecase.FormatCourierManifestUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

/**
 * Tab/status filter applied to the orders list.
 *
 * Covers all stages of the fulfillment pipeline so merchants can triage
 * new orders, packing in preparation, dispatches, and exceptions.
 */
enum class OrderFilter(
    val label: String,
    @StringRes val labelResId: Int = R.string.order_filter_all
) {
    ALL("الكل", R.string.order_filter_all),
    PENDING("جديد", R.string.order_filter_pending),
    IN_PREPARATION("قيد التجهيز", R.string.order_filter_in_prep),
    SHIPPING_IN_PROGRESS("جاري التوصيل", R.string.order_filter_shipping),
    COMPLETED("تم التسليم", R.string.order_filter_completed),
    CANCELLED("ملغي", R.string.order_filter_cancelled),
    RETURNED("مرتجع", R.string.order_filter_returned);

    /** Returns true when [order] passes this filter. */
    fun matches(order: OrderEntity): Boolean = when (this) {
        ALL -> true
        PENDING -> order.status == OrderStatus.PENDING.name
        IN_PREPARATION -> order.status == OrderStatus.IN_PREPARATION.name
        SHIPPING_IN_PROGRESS -> order.status == OrderStatus.SHIPPING_IN_PROGRESS.name
        COMPLETED -> order.status == OrderStatus.SHIPPED.name
        CANCELLED -> order.status == OrderStatus.CANCELLED.name
        RETURNED -> order.status == OrderStatus.RETURNED.name
    }

    companion object {
        internal val IN_PREP_STATE_NAMES: Set<String> = setOf(OrderStatus.IN_PREPARATION.name)
        internal val FULFILLED_STATE_NAMES: Set<String> = setOf(OrderStatus.SHIPPED.name)
    }
}

/**
 * Date range filter for daily shipping settlement and logistics dispatch.
 */
enum class DateFilter(
    val label: String,
    @StringRes val labelResId: Int = R.string.order_date_filter_all
) {
    ALL("الكل", R.string.order_date_filter_all),
    TODAY("اليوم", R.string.order_date_filter_today),
    YESTERDAY("أمس", R.string.order_date_filter_yesterday),
    LAST_7_DAYS("آخر 7 أيام", R.string.order_date_filter_last_7_days),
    THIS_MONTH("هذا الشهر", R.string.order_date_filter_this_month);

    fun matches(timestamp: Long, now: Long = System.currentTimeMillis()): Boolean {
        if (this == ALL) return true
        val calendar = java.util.Calendar.getInstance().apply { timeInMillis = now }
        val orderCal = java.util.Calendar.getInstance().apply { timeInMillis = timestamp }

        return when (this) {
            ALL -> true
            TODAY -> {
                calendar.get(java.util.Calendar.YEAR) == orderCal.get(java.util.Calendar.YEAR) &&
                calendar.get(java.util.Calendar.DAY_OF_YEAR) == orderCal.get(java.util.Calendar.DAY_OF_YEAR)
            }
            YESTERDAY -> {
                val yesterday = (calendar.clone() as java.util.Calendar).apply {
                    add(java.util.Calendar.DAY_OF_YEAR, -1)
                }
                yesterday.get(java.util.Calendar.YEAR) == orderCal.get(java.util.Calendar.YEAR) &&
                yesterday.get(java.util.Calendar.DAY_OF_YEAR) == orderCal.get(java.util.Calendar.DAY_OF_YEAR)
            }
            LAST_7_DAYS -> {
                val sevenDaysAgo = now - (7L * 24 * 60 * 60 * 1000)
                timestamp in sevenDaysAgo..now
            }
            THIS_MONTH -> {
                calendar.get(java.util.Calendar.YEAR) == orderCal.get(java.util.Calendar.YEAR) &&
                calendar.get(java.util.Calendar.MONTH) == orderCal.get(java.util.Calendar.MONTH)
            }
        }
    }
}

data class OrdersUiState(
    /** Currently selected status filter tab. */
    val selectedTab: OrderFilter = OrderFilter.ALL,
    /** Orders filtered by [selectedTab], [selectedDateFilter], [selectedGovernorate], and search query. */
    val ordersList: List<OrderEntity> = emptyList(),
    /** Search query — matches contact name, customer name, address, phone, or order details. */
    val searchQuery: String = "",
    /** Selected date range filter. */
    val selectedDateFilter: DateFilter = DateFilter.ALL,
    /** Selected governorate filter (null = all governorates). */
    val selectedGovernorate: String? = null,
    /** Selected order IDs for bulk operations. */
    val selectedOrderIds: Set<Long> = emptySet(),
    /** Multi-select mode active. */
    val isMultiSelectMode: Boolean = false,

    // ── Metric counts & amounts ───────────────────────────────────────────────
    val pendingCount: Int = 0,
    val inPreparationCount: Int = 0,
    val shippingCount: Int = 0,
    val completedCount: Int = 0,
    val cancelledCount: Int = 0,
    val returnedCount: Int = 0,
    val totalCount: Int = 0,
    val totalCodAmount: Double = 0.0,

    // ── Indicators & Dialog states ───────────────────────────────────────────
    val isLoading: Boolean = true,
    /** True while any status/note write is in flight (used for progress UI). */
    val isUpdating: Boolean = false,
    /** Transient message for the last failed dashboard write (null = no error). */
    val errorMessage: String? = null,
    val editingOrder: OrderEntity? = null,
    val cancellingOrder: OrderEntity? = null,
    val isCreateOrderDialogOpen: Boolean = false
)

@HiltViewModel
class OrdersViewModel @Inject constructor(
    private val orderRepository: OrderRepository,
    private val conversationMetadataDao: ConversationMetadataDao,
    private val advanceOrderStatusUseCase: AdvanceOrderStatusUseCase,
    private val formatCourierManifestUseCase: FormatCourierManifestUseCase,
    private val exportBulkManifestUseCase: ExportBulkManifestUseCase
) : ViewModel() {

    // Test seam constructor: allows existing unit tests calling OrdersViewModel(repo, dao) to work unchanged
    constructor(
        orderRepository: OrderRepository,
        conversationMetadataDao: ConversationMetadataDao
    ) : this(
        orderRepository = orderRepository,
        conversationMetadataDao = conversationMetadataDao,
        advanceOrderStatusUseCase = AdvanceOrderStatusUseCase(orderRepository),
        formatCourierManifestUseCase = FormatCourierManifestUseCase(),
        exportBulkManifestUseCase = ExportBulkManifestUseCase(FormatCourierManifestUseCase())
    )

    private val selectedTab = MutableStateFlow(OrderFilter.ALL)
    private val searchQuery = MutableStateFlow("")
    private val selectedDateFilter = MutableStateFlow(DateFilter.ALL)
    private val selectedGovernorate = MutableStateFlow<String?>(null)
    private val selectedOrderIds = MutableStateFlow<Set<Long>>(emptySet())
    private val isMultiSelectMode = MutableStateFlow(false)

    private val isUpdating = MutableStateFlow(false)
    private val errorMessage = MutableStateFlow<String?>(null)
    private val editingOrder = MutableStateFlow<OrderEntity?>(null)
    private val cancellingOrder = MutableStateFlow<OrderEntity?>(null)
    private val isCreateOrderDialogOpen = MutableStateFlow(false)

    /** Tracks concurrent in-flight writes so [isUpdating] clears at the right time. */
    private val inFlightWrites = AtomicInteger(0)

    private data class FilterParams(
        val tab: OrderFilter,
        val query: String,
        val dateFilter: DateFilter,
        val governorate: String?
    )

    private val filterParamsFlow = combine(
        selectedTab,
        searchQuery,
        selectedDateFilter,
        selectedGovernorate
    ) { tab, query, date, gov ->
        FilterParams(tab, query, date, gov)
    }

    private data class SelectionParams(
        val selectedIds: Set<Long>,
        val isMultiSelect: Boolean
    )

    private val selectionParamsFlow = combine(
        selectedOrderIds,
        isMultiSelectMode
    ) { ids, multi ->
        SelectionParams(ids, multi)
    }

    private data class DialogParams(
        val editOrder: OrderEntity?,
        val cancelOrder: OrderEntity?,
        val createOpen: Boolean
    )

    private val dialogParamsFlow = combine(
        editingOrder,
        cancellingOrder,
        isCreateOrderDialogOpen
    ) { edit, cancel, create ->
        DialogParams(edit, cancel, create)
    }

    private data class StatusParams(
        val updating: Boolean,
        val error: String?
    )

    private val statusParamsFlow = combine(
        isUpdating,
        errorMessage
    ) { updating, error ->
        StatusParams(updating, error)
    }

    val uiState: StateFlow<OrdersUiState> = combine(
        orderRepository.getAllOrders(),
        filterParamsFlow,
        selectionParamsFlow,
        dialogParamsFlow,
        statusParamsFlow
    ) { allOrders, filters, selection, dialogs, status ->
        val queryTrimmed = filters.query.trim()
        val filtered = allOrders
            .filter { filters.tab.matches(it) }
            .filter { filters.dateFilter.matches(it.timestamp) }
            .filter { order ->
                filters.governorate == null || order.governorate.equals(filters.governorate, ignoreCase = true)
            }
            .filter { order ->
                if (queryTrimmed.isEmpty()) return@filter true
                listOfNotNull(
                    order.contactName,
                    order.customerNameFromLlm,
                    order.customerPhone,
                    order.orderDetails,
                    order.customNotes,
                    order.deliveryAddress,
                    order.governorate
                ).any { field -> field.contains(queryTrimmed, ignoreCase = true) }
            }

        val pending = allOrders.count { it.status == OrderStatus.PENDING.name }
        val inPrep = allOrders.count { it.status == OrderStatus.IN_PREPARATION.name }
        val shipping = allOrders.count { it.status == OrderStatus.SHIPPING_IN_PROGRESS.name }
        val fulfilled = allOrders.count { it.status == OrderStatus.SHIPPED.name }
        val cancelled = allOrders.count { it.status == OrderStatus.CANCELLED.name }
        val returned = allOrders.count { it.status == OrderStatus.RETURNED.name }
        val totalCod = filtered.sumOf { it.totalAmount + it.shippingFee }

        OrdersUiState(
            selectedTab = filters.tab,
            ordersList = filtered,
            searchQuery = filters.query,
            selectedDateFilter = filters.dateFilter,
            selectedGovernorate = filters.governorate,
            selectedOrderIds = selection.selectedIds,
            isMultiSelectMode = selection.isMultiSelect,
            pendingCount = pending,
            inPreparationCount = inPrep,
            shippingCount = shipping,
            completedCount = fulfilled,
            cancelledCount = cancelled,
            returnedCount = returned,
            totalCount = allOrders.size,
            totalCodAmount = totalCod,
            isLoading = false,
            isUpdating = status.updating,
            errorMessage = status.error,
            editingOrder = dialogs.editOrder,
            cancellingOrder = dialogs.cancelOrder,
            isCreateOrderDialogOpen = dialogs.createOpen
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = OrdersUiState()
    )

    fun onFilterSelected(filter: OrderFilter) {
        selectedTab.value = filter
    }

    fun onDateFilterSelected(filter: DateFilter) {
        selectedDateFilter.value = filter
    }

    fun onGovernorateFilterSelected(governorate: String?) {
        selectedGovernorate.value = governorate
    }

    suspend fun getChatSourceClassForContact(contactName: String): String =
        conversationMetadataDao.getLatestConversationForContact(contactName)?.sourceClass
            ?: "BUSINESS"

    fun onSearchQueryChanged(query: String) {
        searchQuery.value = query
    }

    fun onStatusChanged(orderId: Long, newStatus: OrderStatus) {
        runTrackedWrite {
            orderRepository.updateOrderStatus(orderId, newStatus)
        }
    }

    fun advanceOrderStatus(order: OrderEntity) {
        runTrackedWrite {
            advanceOrderStatusUseCase(order)
        }
    }

    fun formatCourierManifest(order: OrderEntity): String =
        formatCourierManifestUseCase(order)

    fun exportBulkManifest(orders: List<OrderEntity>): String =
        exportBulkManifestUseCase(orders)

    fun exportSelectedBulkManifest(): String {
        val allOrders = uiState.value.ordersList
        val selectedIds = selectedOrderIds.value
        val targetOrders = if (selectedIds.isNotEmpty()) {
            allOrders.filter { it.id in selectedIds }
        } else {
            allOrders
        }
        return exportBulkManifestUseCase(targetOrders)
    }

    fun onMerchantNoteSaved(orderId: Long, note: String) {
        runTrackedWrite {
            orderRepository.updateMerchantNote(orderId, note.trim().ifBlank { null })
        }
    }

    fun createOrder(
        customerName: String,
        customerPhone: String,
        deliveryAddress: String,
        governorate: String?,
        orderDetails: String,
        totalAmount: Double,
        shippingFee: Double,
        customNotes: String? = null
    ) {
        runTrackedWrite {
            val normalizedPhone = EgyptianOrderUtils.extractEgyptianMobilePhone(customerPhone)
                ?: customerPhone.trim()
            val newOrder = OrderEntity(
                contactName = normalizedPhone.ifBlank { customerName },
                customerNameFromLlm = customerName.ifBlank { null },
                customerPhone = normalizedPhone,
                deliveryAddress = deliveryAddress.ifBlank { null },
                governorate = governorate?.ifBlank { null },
                orderDetails = orderDetails.ifBlank { null },
                totalAmount = totalAmount,
                shippingFee = shippingFee,
                customNotes = customNotes?.ifBlank { null },
                status = OrderStatus.PENDING.name,
                timestamp = System.currentTimeMillis()
            )
            orderRepository.createOrder(newOrder)
            closeCreateOrderDialog()
        }
    }

    fun updateOrder(
        orderId: Long,
        customerName: String?,
        customerPhone: String,
        deliveryAddress: String?,
        governorate: String?,
        orderDetails: String?,
        totalAmount: Double,
        shippingFee: Double
    ) {
        runTrackedWrite {
            val normalizedPhone = EgyptianOrderUtils.extractEgyptianMobilePhone(customerPhone)
                ?: customerPhone.trim()
            orderRepository.updateOrder(
                orderId = orderId,
                name = customerName?.ifBlank { null },
                phone = normalizedPhone,
                address = deliveryAddress?.ifBlank { null },
                details = orderDetails?.ifBlank { null },
                total = totalAmount,
                shipping = shippingFee,
                governorate = governorate?.ifBlank { null }
            )
            closeEditOrderDialog()
        }
    }

    fun cancelOrder(orderId: Long, reason: String) {
        require(reason.isNotBlank()) { "Cancellation reason must not be blank" }
        runTrackedWrite {
            orderRepository.cancelOrder(orderId, reason.trim())
            closeCancelOrderDialog()
        }
    }

    fun toggleOrderSelection(orderId: Long) {
        val current = selectedOrderIds.value
        val updated = if (orderId in current) current - orderId else current + orderId
        selectedOrderIds.value = updated
        isMultiSelectMode.value = updated.isNotEmpty()
    }

    fun selectAllOrders() {
        val currentFilteredIds = uiState.value.ordersList.map { it.id }.toSet()
        selectedOrderIds.value = currentFilteredIds
        isMultiSelectMode.value = currentFilteredIds.isNotEmpty()
    }

    fun clearSelection() {
        selectedOrderIds.value = emptySet()
        isMultiSelectMode.value = false
    }

    fun bulkAdvanceStatus() {
        val ids = selectedOrderIds.value
        val ordersToAdvance = uiState.value.ordersList.filter { it.id in ids }
        if (ordersToAdvance.isEmpty()) return
        runTrackedWrite {
            ordersToAdvance.forEach { advanceOrderStatusUseCase(it) }
            clearSelection()
        }
    }

    fun bulkUpdateStatus(newStatus: OrderStatus) {
        val ids = selectedOrderIds.value.toList()
        if (ids.isEmpty()) return
        runTrackedWrite {
            orderRepository.updateOrderStatusBulk(ids, newStatus)
            clearSelection()
        }
    }

    fun openCreateOrderDialog() {
        isCreateOrderDialogOpen.value = true
    }

    fun closeCreateOrderDialog() {
        isCreateOrderDialogOpen.value = false
    }

    fun openEditOrderDialog(order: OrderEntity) {
        editingOrder.value = order
    }

    fun closeEditOrderDialog() {
        editingOrder.value = null
    }

    fun openCancelOrderDialog(order: OrderEntity) {
        cancellingOrder.value = order
    }

    fun closeCancelOrderDialog() {
        cancellingOrder.value = null
    }

    fun clearErrorMessage() {
        errorMessage.value = null
    }

    private fun runTrackedWrite(write: suspend () -> Unit) {
        viewModelScope.launch {
            inFlightWrites.incrementAndGet()
            errorMessage.value = null
            isUpdating.value = true
            try {
                write()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errorMessage.value = "تعذر حفظ التغييرات. يرجى المحاولة مرة أخرى."
            } finally {
                if (inFlightWrites.decrementAndGet() == 0) {
                    isUpdating.value = false
                }
            }
        }
    }
}
