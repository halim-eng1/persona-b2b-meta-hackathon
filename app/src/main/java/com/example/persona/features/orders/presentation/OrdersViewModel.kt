package com.example.persona.features.orders.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.persona.core.database.dao.ConversationMetadataDao
import com.example.persona.core.database.entity.OrderEntity
import com.example.persona.core.database.entity.OrderStatus
import com.example.persona.features.orders.domain.repository.OrderRepository
import com.example.persona.features.orders.domain.usecase.AdvanceOrderStatusUseCase
import com.example.persona.features.orders.domain.usecase.FormatCourierManifestUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CancellationException
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
 *
 * Chip↔tab invariant: the metric chips and the filter tabs share the same
 * state-set definitions ([IN_PREP_STATES], [FULFILLED_STATES]), so a chip count
 * exactly equals the number of items displayed by the matching tab.
 */
enum class OrderFilter(val label: String) {
    ALL("All"),
    PENDING("New"),
    IN_PREPARATION("In Prep"),
    COMPLETED("Completed"),
    CANCELLED("Cancelled"),
    RETURNED("Returned");

    /** Returns true when [order] passes this filter. */
    fun matches(order: OrderEntity): Boolean = when (this) {
        ALL -> true
        PENDING -> order.status == OrderStatus.PENDING.name
        IN_PREPARATION -> order.status in IN_PREP_STATE_NAMES
        COMPLETED -> order.status in FULFILLED_STATE_NAMES
        CANCELLED -> order.status == OrderStatus.CANCELLED.name
        RETURNED -> order.status == OrderStatus.RETURNED.name
    }

    companion object {
        /** Orders currently being prepared or handed to the courier. */
        private val IN_PREP_STATES = setOf(OrderStatus.IN_PREPARATION, OrderStatus.SHIPPING_IN_PROGRESS)

        /** Terminal-success bucket: delivered or returned to stock. */
        private val FULFILLED_STATES = setOf(OrderStatus.SHIPPED, OrderStatus.RETURNED)

        /**
         * Pre-computed TEXT names of the state sets, for cheap `in`-matches
         * against the persisted TEXT column without per-order list allocation.
         */
        internal val IN_PREP_STATE_NAMES: Set<String> = IN_PREP_STATES.map { it.name }.toSet()
        internal val FULFILLED_STATE_NAMES: Set<String> = FULFILLED_STATES.map { it.name }.toSet()
    }
}

data class OrdersUiState(
    /** Currently selected status filter tab. */
    val selectedTab: OrderFilter = OrderFilter.ALL,
    /** Orders filtered by [selectedTab] AND the free-text search query. */
    val ordersList: List<OrderEntity> = emptyList(),
    /** Search query — matches contact name, customer name, address, or order details (case-insensitive). */
    val searchQuery: String = "",
    // ── Metric chips & counts (always aligned with [OrderFilter] tabs) ────────
    val pendingCount: Int = 0,
    val inPreparationCount: Int = 0,
    val completedCount: Int = 0,
    val cancelledCount: Int = 0,
    val returnedCount: Int = 0,
    val totalCount: Int = 0,
    // ── Indicators ───────────────────────────────────────────────────────────
    val isLoading: Boolean = true,
    /** True while any status/note write is in flight (used for progress UI). */
    val isUpdating: Boolean = false,
    /** Transient message for the last failed dashboard write (null = no error). */
    val errorMessage: String? = null
)

@HiltViewModel
class OrdersViewModel @Inject constructor(
    private val orderRepository: OrderRepository,
    private val conversationMetadataDao: ConversationMetadataDao,
    private val advanceOrderStatusUseCase: AdvanceOrderStatusUseCase,
    private val formatCourierManifestUseCase: FormatCourierManifestUseCase
) : ViewModel() {

    // Test seam constructor: allows existing unit tests calling OrdersViewModel(repo, dao) to work unchanged
    constructor(
        orderRepository: OrderRepository,
        conversationMetadataDao: ConversationMetadataDao
    ) : this(
        orderRepository = orderRepository,
        conversationMetadataDao = conversationMetadataDao,
        advanceOrderStatusUseCase = AdvanceOrderStatusUseCase(orderRepository),
        formatCourierManifestUseCase = FormatCourierManifestUseCase()
    )

    private val selectedTab = MutableStateFlow(OrderFilter.ALL)
    private val searchQuery = MutableStateFlow("")
    private val isUpdating = MutableStateFlow(false)
    private val errorMessage = MutableStateFlow<String?>(null)

    /** Tracks concurrent in-flight writes so [isUpdating] clears at the right time. */
    private val inFlightWrites = AtomicInteger(0)

    /**
     * Single reactive pipeline: orders flow combined with the tab filter, search
     * query and updating flag. Room re-emits after every write, so the list and
     * metric chips stay live without any manual refresh.
     */
    val uiState: StateFlow<OrdersUiState> = combine(
        orderRepository.getAllOrders(),
        selectedTab,
        searchQuery,
        isUpdating,
        errorMessage
    ) { allOrders, tab, query, updating, error ->
        val queryTrimmed = query.trim()
        val filtered = allOrders
            .filter { tab.matches(it) }
            .filter { order ->
                if (queryTrimmed.isEmpty()) return@filter true
                // Search by contact name, customer name, order details, notes, address
                listOfNotNull(
                    order.contactName,
                    order.customerNameFromLlm,
                    order.orderDetails,
                    order.customNotes,
                    order.deliveryAddress
                ).any { field -> field.contains(queryTrimmed, ignoreCase = true) }
            }

        val pending = allOrders.count { it.status == OrderStatus.PENDING.name }
        val inPrep = allOrders.count { it.status in OrderFilter.IN_PREP_STATE_NAMES }
        val fulfilled = allOrders.count { it.status in OrderFilter.FULFILLED_STATE_NAMES }
        val cancelled = allOrders.count { it.status == OrderStatus.CANCELLED.name }
        val returned = allOrders.count { it.status == OrderStatus.RETURNED.name }

        OrdersUiState(
            selectedTab = tab,
            ordersList = filtered,
            // Keep the RAW query in state so the TextField stays in sync with what
            // the user typed (trailing spaces included). Only filtering trims.
            searchQuery = query,
            pendingCount = pending,
            inPreparationCount = inPrep,
            completedCount = fulfilled,
            cancelledCount = cancelled,
            returnedCount = returned,
            totalCount = allOrders.size,
            isLoading = false,
            isUpdating = updating,
            errorMessage = error
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = OrdersUiState()
    )

    fun onFilterSelected(filter: OrderFilter) {
        selectedTab.value = filter
    }

    /**
     * Resolves the chat thread a given contact actually lives in. Orders are
     * ingested from any enabled WhatsApp source app, so the chat shortcut must query
     * [ConversationMetadataDao.getLatestConversationForContact] instead of
     * hard-coding "BUSINESS" — otherwise the jump lands on an empty thread.
     */
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

    /**
     * Tactile Kanban advancement: moves an order to its natural next lifecycle state
     * via [AdvanceOrderStatusUseCase].
     */
    fun advanceOrderStatus(order: OrderEntity) {
        runTrackedWrite {
            advanceOrderStatusUseCase(order)
        }
    }

    /**
     * Formats order information into a courier manifest via [FormatCourierManifestUseCase].
     */
    fun formatCourierManifest(order: OrderEntity): String =
        formatCourierManifestUseCase(order)

    fun onMerchantNoteSaved(orderId: Long, note: String) {
        runTrackedWrite {
            // Blank == "clear the note" — the DAO column is nullable, so map an
            // empty/whitespace-only save to null instead of persisting "".
            orderRepository.updateMerchantNote(orderId, note.trim().ifBlank { null })
        }
    }

    fun clearErrorMessage() {
        errorMessage.value = null
    }

    /**
     * Wraps a repository write with the [isUpdating] progress bookkeeping.
     *
     * Failures are caught and surfaced via [errorMessage] instead of crashing the
     * process. [CancellationException] is always re-thrown.
     */
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
                errorMessage.value = "Couldn't save changes. Please try again."
            } finally {
                if (inFlightWrites.decrementAndGet() == 0) {
                    isUpdating.value = false
                }
            }
        }
    }
}
